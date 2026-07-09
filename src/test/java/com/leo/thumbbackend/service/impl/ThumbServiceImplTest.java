package com.leo.thumbbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.mapper.ThumbMapper;
import com.leo.thumbbackend.model.dto.thumb.DoThumbRequest;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThumbServiceImplTest {

    private ThumbMapper thumbMapper;
    private BlogMapper blogMapper;
    private UserService userService;
    private TransactionTemplate transactionTemplate;
    private RedissonClient redissonClient;
    private RLock lock;
    private HttpServletRequest request;
    private ThumbServiceImpl thumbService;

    @BeforeEach
    void setUp() {
        thumbMapper = mock(ThumbMapper.class);
        blogMapper = mock(BlogMapper.class);
        userService = mock(UserService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        request = mock(HttpServletRequest.class);
        thumbService = new ThumbServiceImpl(
                thumbMapper,
                blogMapper,
                userService,
                transactionTemplate,
                redissonClient
        );

        User user = new User();
        user.setId(1L);
        when(userService.getLoginUser(request)).thenReturn(user);
        when(redissonClient.getLock("thumb:lock:1:2")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void lockContentionReturnsTooManyRequestWithoutStartingTransaction() {
        when(lock.tryLock()).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> thumbService.doThumb(request(2L), request)
        );

        assertEquals(ErrorCode.TOO_MANY_REQUEST.getCode(), exception.getCode());
        verify(transactionTemplate, never()).execute(any());
        verify(lock, never()).unlock();
    }

    @Test
    void doThumbUsesUserAndBlogScopedLockAndReleasesIt() {
        when(lock.tryLock()).thenReturn(true);
        when(thumbMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(blogMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(thumbMapper.insert(any(Thumb.class))).thenReturn(1);

        assertTrue(thumbService.doThumb(request(2L), request));

        ArgumentCaptor<Thumb> thumbCaptor = ArgumentCaptor.forClass(Thumb.class);
        verify(redissonClient).getLock("thumb:lock:1:2");
        verify(thumbMapper).insert(thumbCaptor.capture());
        assertEquals(1L, thumbCaptor.getValue().getUserId());
        assertEquals(2L, thumbCaptor.getValue().getBlogId());
        verify(lock).unlock();
    }

    @Test
    void doThumbThrowsWhenThumbRecordCannotBeSaved() {
        when(lock.tryLock()).thenReturn(true);
        when(thumbMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(blogMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(thumbMapper.insert(any(Thumb.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> thumbService.doThumb(request(2L), request)
        );

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), exception.getCode());
        verify(lock).unlock();
    }

    @Test
    void undoThumbUpdatesCountAndDeletesThumbInOneTransaction() {
        Thumb thumb = new Thumb();
        thumb.setId(10L);
        thumb.setUserId(1L);
        thumb.setBlogId(2L);
        when(lock.tryLock()).thenReturn(true);
        when(thumbMapper.selectOne(any(Wrapper.class))).thenReturn(thumb);
        when(blogMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(thumbMapper.deleteById(any(Serializable.class))).thenReturn(1);

        assertTrue(thumbService.undoThumb(request(2L), request));

        verify(thumbMapper).deleteById(10L);
        verify(lock).unlock();
    }

    @Test
    void blogUpdateFailureDoesNotWriteThumbRecord() {
        when(lock.tryLock()).thenReturn(true);
        when(thumbMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(blogMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> thumbService.doThumb(request(2L), request)
        );

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        verify(thumbMapper, never()).insert(any(Thumb.class));
        verify(lock).unlock();
    }

    private DoThumbRequest request(long blogId) {
        DoThumbRequest request = new DoThumbRequest();
        request.setBlogId(blogId);
        return request;
    }
}
