package com.leo.thumbbackend.service;

import com.leo.thumbbackend.model.dto.blog.BlogAddRequest;
import com.leo.thumbbackend.model.dto.blog.BlogUpdateRequest;
import com.leo.thumbbackend.model.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leo.thumbbackend.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
* @author zhengsmacbook
* @description 针对表【blog】的数据库操作Service
* @createDate 2026-07-05 18:21:27
*/
public interface BlogService extends IService<Blog> {

    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    Long addBlog(BlogAddRequest blogAddRequest, HttpServletRequest request);

    Boolean updateBlog(BlogUpdateRequest blogUpdateRequest, HttpServletRequest request);

    @Transactional(rollbackFor = Exception.class)
    Boolean deleteBlog(long blogId, HttpServletRequest request);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}