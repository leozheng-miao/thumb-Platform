package com.leo.thumbbackend.service.impl;

import com.leo.thumbbackend.constant.RedisLuaScriptConstant;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.model.dto.thumb.DoThumbRequest;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.model.enums.LuaStatusEnum;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThumbServiceRedisImplTest {

    private UserService userService;
    private StringRedisTemplate stringRedisTemplate;
    private HttpServletRequest request;
    private ThumbServiceRedisImpl thumbService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        request = mock(HttpServletRequest.class);
        thumbService = new ThumbServiceRedisImpl(userService, stringRedisTemplate);

        User user = new User();
        user.setId(1L);
        when(userService.getLoginUser(request)).thenReturn(user);
    }

    @Test
    void doThumbThrowsNotFoundWhenLuaReturnsBlogNotExist() {
        when(stringRedisTemplate.execute(
                eq(RedisLuaScriptConstant.THUMB_SCRIPT),
                anyList(),
                eq("1"),
                eq("2")
        )).thenReturn(LuaStatusEnum.BLOG_NOT_EXIST.getValue());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> thumbService.doThumb(request(2L), request)
        );

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        assertEquals("博客不存在", exception.getMessage());
    }

    @Test
    void doThumbPassesBlogExistsKeyToLua() {
        when(stringRedisTemplate.execute(
                eq(RedisLuaScriptConstant.THUMB_SCRIPT),
                anyList(),
                eq("1"),
                eq("2")
        )).thenReturn(LuaStatusEnum.SUCCESS.getValue());

        assertTrue(thumbService.doThumb(request(2L), request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(stringRedisTemplate).execute(
                eq(RedisLuaScriptConstant.THUMB_SCRIPT),
                keysCaptor.capture(),
                eq("1"),
                eq("2")
        );
        assertEquals("thumb:1", keysCaptor.getValue().get(1));
        assertEquals("blog:exists", keysCaptor.getValue().get(2));
    }

    @Test
    void undoThumbThrowsNotFoundWhenLuaReturnsBlogNotExist() {
        when(stringRedisTemplate.execute(
                eq(RedisLuaScriptConstant.UNTHUMB_SCRIPT),
                anyList(),
                eq("1"),
                eq("2")
        )).thenReturn(LuaStatusEnum.BLOG_NOT_EXIST.getValue());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> thumbService.undoThumb(request(2L), request)
        );

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        assertEquals("博客不存在", exception.getMessage());
    }

    private DoThumbRequest request(long blogId) {
        DoThumbRequest request = new DoThumbRequest();
        request.setBlogId(blogId);
        return request;
    }
}
