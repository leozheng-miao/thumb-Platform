package com.leo.thumbbackend.manager.cache;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class HeavyKeeper implements TopK {

    private static final int LOOKUP_TABLE_SIZE = 256;
    // 指纹与桶下标使用不同种子，降低“不同 key 被识别为同一元素”的概率。
    private static final int FINGERPRINT_SEED = 0x7f4a7c15;
    private static final int ROW_SEED_BASE = 0x9e3779b9;

    private final int k;
    private final int width;
    private final int depth;
    private final double[] lookupTable;
    private final Bucket[][] buckets;
    private final PriorityQueue<Node> minHeap;
    private final Map<String, Node> nodeMap;
    private final BlockingQueue<Item> expelledQueue;
    private final AtomicLong total;
    private final int minCount;

    public HeavyKeeper(int k, int width, int depth, double decay, int minCount) {
        if (k <= 0 || width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("k, width and depth must be positive");
        }
        if (decay <= 0 || decay >= 1) {
            throw new IllegalArgumentException("decay must be between 0 and 1");
        }
        if (minCount <= 0) {
            throw new IllegalArgumentException("minCount must be positive");
        }

        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;
        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        // 预计算衰减概率：count 越大，旧元素被替换的概率越低。
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            lookupTable[i] = Math.pow(decay, i);
        }

        // HeavyKeeper 的核心桶矩阵：depth 行用于多次独立观测，width 列用于分散冲突。
        this.buckets = new Bucket[depth][width];
        for (int row = 0; row < depth; row++) {
            for (int column = 0; column < width; column++) {
                buckets[row][column] = new Bucket();
            }
        }

        // 小顶堆维护 TopK，nodeMap 用于快速定位堆内节点，避免每次线性查找。
        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(node -> node.count));
        this.nodeMap = new HashMap<>();
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.total = new AtomicLong();
    }

    @Override
    public AddResult add(String key, int increment) {
        if (key == null || key.isBlank() || increment <= 0) {
            return new AddResult(null, false, key);
        }

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // fingerprint 只用于判断桶内元素是否为当前 key，不参与桶下标计算。
        long fingerprint = fingerprint(keyBytes);
        int maxCount = 0;

        for (int row = 0; row < depth; row++) {
            // 每一行使用不同 seed 计算桶下标，保证 depth 的多行统计真正生效。
            Bucket bucket = buckets[row][bucketIndex(keyBytes, row)];
            synchronized (bucket) {
                if (bucket.count == 0) {
                    bucket.fingerprint = fingerprint;
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, bucket.count);
                    continue;
                }

                if (bucket.fingerprint == fingerprint) {
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                    continue;
                }

                // fingerprint 不同表示发生冲突，按 HeavyKeeper 衰减规则尝试淘汰旧元素。
                maxCount = Math.max(maxCount, decayAndTryReplace(bucket, fingerprint, increment));
            }
        }

        total.addAndGet(increment);

        if (maxCount < minCount) {
            return new AddResult(null, false, key);
        }

        return updateTopK(key, maxCount);
    }

    @Override
    public List<Item> list() {
        synchronized (minHeap) {
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {
                result.add(new Item(node.key, node.count));
            }
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return result;
        }
    }

    @Override
    public BlockingQueue<Item> expelled() {
        return expelledQueue;
    }

    @Override
    public void fading() {
        // 周期性衰减历史热度，让旧热点逐步退出，避免 TopK 长期被历史访问占用。
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket) {
                    bucket.count = bucket.count >> 1;
                    if (bucket.count == 0) {
                        bucket.fingerprint = 0;
                    }
                }
            }
        }

        synchronized (minHeap) {
            minHeap.clear();

            // TopK 节点同步衰减，并移除已经低于最小热度阈值的 key。
            List<String> removedKeys = new ArrayList<>();
            for (Node node : nodeMap.values()) {
                node.count = node.count >> 1;
                if (node.count < minCount) {
                    removedKeys.add(node.key);
                } else {
                    minHeap.add(node);
                }
            }

            for (String key : removedKeys) {
                nodeMap.remove(key);
            }
        }

        total.updateAndGet(value -> value >> 1);
    }

    @Override
    public long total() {
        return total.get();
    }

    private int decayAndTryReplace(Bucket bucket, long fingerprint, int increment) {
        for (int i = 0; i < increment; i++) {
            // bucket.count 越大，lookupTable[count] 越小，说明高频旧元素越难被新元素挤掉。
            double probability = bucket.count < LOOKUP_TABLE_SIZE
                    ? lookupTable[bucket.count]
                    : lookupTable[LOOKUP_TABLE_SIZE - 1];

            if (ThreadLocalRandom.current().nextDouble() >= probability) {
                continue;
            }

            bucket.count--;
            if (bucket.count == 0) {
                bucket.fingerprint = fingerprint;
                // 当前这次成功替换也要计入新元素，避免 increment=1 时新元素 count 变成 0。
                bucket.count = increment - i;
                return bucket.count;
            }
        }
        return 0;
    }

    private AddResult updateTopK(String key, int count) {
        synchronized (minHeap) {
            Node existing = nodeMap.get(key);
            if (existing != null) {
                minHeap.remove(existing);
                existing.count = count;
                minHeap.add(existing);
                return new AddResult(null, true, key);
            }

            // 堆未满时，达到 minCount 的 key 直接进入 TopK。
            if (minHeap.size() < k) {
                Node node = new Node(key, count);
                minHeap.add(node);
                nodeMap.put(key, node);
                return new AddResult(null, true, key);
            }

            Node smallest = minHeap.peek();
            if (smallest == null || count < smallest.count) {
                return new AddResult(null, false, key);
            }

            // 当前 key 比堆顶更热时，挤出最小热点并进入 TopK。
            Node expelled = minHeap.poll();
            nodeMap.remove(expelled.key);
            expelledQueue.offer(new Item(expelled.key, expelled.count));

            Node node = new Node(key, count);
            minHeap.add(node);
            nodeMap.put(key, node);
            return new AddResult(expelled.key, true, key);
        }
    }

    private int bucketIndex(byte[] keyBytes, int row) {
        int hash = hash(keyBytes, ROW_SEED_BASE * (row + 1));
        // floorMod 可以安全处理 Integer.MIN_VALUE，避免负下标。
        return Math.floorMod(hash, width);
    }

    private long fingerprint(byte[] keyBytes) {
        return Integer.toUnsignedLong(hash(keyBytes, FINGERPRINT_SEED));
    }

    private static int hash(byte[] data, int seed) {
        int hash = seed;
        for (byte value : data) {
            hash ^= value & 0xff;
            hash *= 0x5bd1e995;
            hash ^= hash >>> 15;
        }
        return hash;
    }

    private static class Bucket {
        private long fingerprint;
        private int count;
    }

    private static class Node {
        private final String key;
        private int count;

        private Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }
}
