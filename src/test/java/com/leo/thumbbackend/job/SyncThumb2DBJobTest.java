package com.leo.thumbbackend.job;

import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.service.ThumbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncThumb2DBJobTest {

    private ThumbService thumbService;
    private BlogMapper blogMapper;
    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private SyncThumb2DBJob syncThumb2DBJob;

    @BeforeEach
    void setUp() {
        thumbService = mock(ThumbService.class);
        blogMapper = mock(BlogMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        syncThumb2DBJob = new SyncThumb2DBJob();

        ReflectionTestUtils.setField(syncThumb2DBJob, "thumbService", thumbService);
        ReflectionTestUtils.setField(syncThumb2DBJob, "blogMapper", blogMapper);
        ReflectionTestUtils.setField(syncThumb2DBJob, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void syncSkipsMissingBlogAndCleansUserThumbState() {
        Map<Object, Object> tempThumbMap = new LinkedHashMap<>();
        tempThumbMap.put("1:10", 1);
        tempThumbMap.put("2:20", 1);
        when(hashOperations.entries("thumb:temp:12:00:00")).thenReturn(tempThumbMap);
        when(blogMapper.listExistingBlogIds(anyCollection())).thenReturn(List.of(10L));

        syncThumb2DBJob.syncThumb2DBByDate("12:00:00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Thumb>> thumbListCaptor = ArgumentCaptor.forClass(Collection.class);
        thumbListCaptor.getAllValues();
        verify(thumbService).saveBatch(thumbListCaptor.capture());
        assertEquals(1, thumbListCaptor.getValue().size());
        Thumb thumb = thumbListCaptor.getValue().iterator().next();
        assertEquals(1L, thumb.getUserId());
        assertEquals(10L, thumb.getBlogId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> countMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(blogMapper).batchUpdateThumbCount(countMapCaptor.capture());
        assertEquals(Map.of(10L, 1L), countMapCaptor.getValue());
        verify(hashOperations).delete("thumb:2", "20");
    }
}
