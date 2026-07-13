package com.leo.thumbbackend.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.model.enums.ThumbTypeEnum;
import com.leo.thumbbackend.service.ThumbService;
import com.leo.thumbbackend.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库  
 *  
 */  
@Component  
@Slf4j  
public class SyncThumb2DBJob {  
  
    @Resource  
    private ThumbService thumbService;  
  
    @Resource  
    private BlogMapper blogMapper;  
  
    @Resource  
    private RedisTemplate<String, Object> redisTemplate;  
  
    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {  
        log.info("开始执行");  
        DateTime nowDate = DateUtil.date();
        // 如果秒数为0~9 则回到上一分钟的50秒
        int second = (DateUtil.second(nowDate) / 10 - 1) * 10;
        if (second == -10) {
            second = 50;
            // 回到上一分钟
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }
        String date = DateUtil.format(nowDate, "HH:mm:") + second;
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }  
  
    public void syncThumb2DBByDate(String date) {  
        // 获取到临时点赞和取消点赞数据  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);  
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);  
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);  
  
        // 同步 点赞 到数据库  
        // 构建插入列表并收集blogId  <Key, Value> 分别为 <BlogId, 点赞数增量(可为负数)>
        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        if (thumbMapEmpty) {  
            return;  
        }  
        List<TempThumbRecord> tempThumbRecords = new ArrayList<>();
        Set<Long> blogIds = new HashSet<>();
        for (Map.Entry<Object, Object> entry : allTempThumbMap.entrySet()) {
            String userIdBlogId = entry.getKey().toString();
            String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);
            if (userIdAndBlogId.length != 2) {
                log.warn("临时点赞记录 key 异常：{}", userIdBlogId);
                continue;
            }
            Long userId = Long.valueOf(userIdAndBlogId[0]);
            Long blogId = Long.valueOf(userIdAndBlogId[1]);
            Integer thumbType = Integer.valueOf(entry.getValue().toString());
            tempThumbRecords.add(new TempThumbRecord(userId, blogId, thumbType));
            blogIds.add(blogId);
        }
        if (tempThumbRecords.isEmpty()) {
            return;
        }

        // 批量查询博客是否存在
        Set<Long> existingBlogIds = new HashSet<>(blogMapper.listExistingBlogIds(blogIds));
        ArrayList<Thumb> thumbList = new ArrayList<>();  
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();  
        boolean needRemove = false;  
        for (TempThumbRecord tempThumbRecord : tempThumbRecords) {  
            Long userId = tempThumbRecord.userId();  
            Long blogId = tempThumbRecord.blogId();
            // -1 取消点赞，1 点赞  
            Integer thumbType = tempThumbRecord.thumbType();
            if (!existingBlogIds.contains(blogId)) {
                log.warn("博客不存在，跳过临时点赞同步：userId={}, blogId={}, thumbType={}", userId, blogId, thumbType);
                redisTemplate.opsForHash().delete(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
                continue;
            }
            if (thumbType == ThumbTypeEnum.INCR.getValue()) {  
                Thumb thumb = new Thumb();  
                thumb.setUserId(userId);  
                thumb.setBlogId(blogId);  
                thumbList.add(thumb);  
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {  
                // 拼接查询条件，批量删除  
                needRemove = true;  
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);  
            } else {  
                if (thumbType != ThumbTypeEnum.NON.getValue()) {  
                    log.warn("数据异常：{}", userId + "," + blogId + "," + thumbType);  
                }  
                continue;  
            }  
            // 计算点赞增量  
            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);  
        }  
        // 批量插入  
        if (!thumbList.isEmpty()) {
            thumbService.saveBatch(thumbList);
        }
        // 批量删除  
        if (needRemove) {  
            thumbService.remove(wrapper);  
        }  
        // 批量更新博客点赞量  
        if (!blogThumbCountMap.isEmpty()) {  
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);  
        }  
        // 异步删除  
        deleteTempKeyAfterCommit(tempThumbKey);
    }
    private void deleteTempKeyAfterCommit(String tempThumbKey) {
        Runnable deleteTask = () -> {
            try {
                redisTemplate.delete(tempThumbKey);
            } catch (RuntimeException e) {
                log.error("删除临时点赞数据失败，key={}", tempThumbKey, e);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteTask.run();
                    }
                }
        );
    }

    private record TempThumbRecord(Long userId, Long blogId, Integer thumbType) {
    }
}  