package com.leo.thumbbackend.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.model.dto.blog.BlogAddRequest;
import com.leo.thumbbackend.model.entity.Blog;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.service.ThumbService;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlogServiceImplTest {

    private BlogMapper blogMapper;
    private UserService userService;
    private ThumbService thumbService;
    private StringRedisTemplate stringRedisTemplate;
    private SetOperations<String, String> setOperations;
    private HttpServletRequest request;
    private BlogServiceImpl blogService;

    @BeforeEach
    void setUp() {
        blogMapper = mock(BlogMapper.class);
        userService = mock(UserService.class);
        thumbService = mock(ThumbService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        request = mock(HttpServletRequest.class);
        blogService = new BlogServiceImpl();

        ReflectionTestUtils.setField(blogService, "baseMapper", blogMapper);
        ReflectionTestUtils.setField(blogService, "userService", userService);
        ReflectionTestUtils.setField(blogService, "thumbService", thumbService);
        ReflectionTestUtils.setField(blogService, "stringRedisTemplate", stringRedisTemplate);

        User user = new User();
        user.setId(1L);
        when(userService.getLoginUser(request)).thenReturn(user);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void addBlogWritesBlogExistsSetAfterSaveSuccess() {
        when(blogMapper.insert(any(Blog.class))).thenAnswer(invocation -> {
            Blog blog = invocation.getArgument(0);
            blog.setId(10L);
            return 1;
        });

        Long blogId = blogService.addBlog(addRequest(), request);

        assertEquals(10L, blogId);
        verify(setOperations).add("blog:exists", "10");
    }

    @Test
    void deleteBlogRemovesBlogExistsSetAfterDeleteSuccess() {
        Blog blog = new Blog();
        blog.setId(10L);
        blog.setUserId(1L);
        when(blogMapper.selectById(anyLong())).thenReturn(blog);
        when(blogMapper.deleteById(any(Serializable.class))).thenReturn(1);

        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<Thumb> updateChainWrapper = mock(LambdaUpdateChainWrapper.class);
        when(thumbService.lambdaUpdate()).thenReturn(updateChainWrapper);
        when(updateChainWrapper.eq(any(), eq(10L))).thenReturn(updateChainWrapper);
        when(updateChainWrapper.remove()).thenReturn(true);

        blogService.deleteBlog(10L, request);

        verify(setOperations).remove("blog:exists", "10");
    }

    private BlogAddRequest addRequest() {
        BlogAddRequest request = new BlogAddRequest();
        request.setTitle("title");
        request.setContent("content");
        return request;
    }
}
