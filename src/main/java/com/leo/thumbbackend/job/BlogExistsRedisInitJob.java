package com.leo.thumbbackend.job;

import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.util.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class BlogExistsRedisInitJob implements ApplicationRunner {

    private static final int BATCH_SIZE = 1000;
    private static final String EMPTY_MARKER = "__empty__";

    private final BlogMapper blogMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        String blogExistsKey = RedisKeyUtil.getBlogExistsKey();
        String initKey = RedisKeyUtil.getBlogExistsInitKey(String.valueOf(System.currentTimeMillis()));
        long lastId = 0L;
        long count = 0L;
        try {
            stringRedisTemplate.delete(initKey);
            stringRedisTemplate.opsForSet().add(initKey, EMPTY_MARKER);
            while (true) {
                List<Long> blogIds = blogMapper.listBlogIdsAfterId(lastId, BATCH_SIZE);
                if (blogIds == null || blogIds.isEmpty()) {
                    break;
                }
                String[] members = blogIds.stream()
                        .map(String::valueOf)
                        .toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(initKey, members);
                lastId = blogIds.getLast();
                count += blogIds.size();
            }
            stringRedisTemplate.rename(initKey, blogExistsKey);
            stringRedisTemplate.opsForSet().remove(blogExistsKey, EMPTY_MARKER);
            log.info("博客存在性 Redis 初始化完成，count={}", count);
        } catch (RuntimeException e) {
            log.error("博客存在性 Redis 初始化失败", e);
            try {
                stringRedisTemplate.delete(initKey);
            } catch (RuntimeException deleteException) {
                log.warn("清理博客存在性初始化临时 key 失败，key={}", initKey, deleteException);
            }
        }
    }
}
