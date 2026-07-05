package com.leo.thumbbackend.controller;

import com.leo.thumbbackend.common.BaseResponse;
import com.leo.thumbbackend.common.ResultUtils;
import com.leo.thumbbackend.model.dto.blog.BlogAddRequest;
import com.leo.thumbbackend.model.dto.blog.BlogUpdateRequest;
import com.leo.thumbbackend.model.entity.Blog;
import com.leo.thumbbackend.model.vo.BlogVO;
import com.leo.thumbbackend.service.BlogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("blog")  
public class BlogController {  
    @Resource  
    private BlogService blogService;  
  
    @GetMapping("/get")  
    public BaseResponse<BlogVO> get(long blogId, HttpServletRequest request) {  
        BlogVO blogVO = blogService.getBlogVOById(blogId, request);  
        return ResultUtils.success(blogVO);  
    }

    @GetMapping("/list")
    public BaseResponse<List<BlogVO>> list(HttpServletRequest request) {
        List<Blog> blogList = blogService.list();
        List<BlogVO> blogVOList = blogService.getBlogVOList(blogList, request);
        return ResultUtils.success(blogVOList);
    }

    /**
     * 创建博客
     */
    @PostMapping("/add")
    public BaseResponse<Long> addBlog(@RequestBody BlogAddRequest blogAddRequest,
                                      HttpServletRequest request) {
        Long blogId = blogService.addBlog(blogAddRequest, request);
        return ResultUtils.success(blogId);
    }

    /**
     * 修改博客
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateBlog(@RequestBody BlogUpdateRequest blogUpdateRequest,
                                            HttpServletRequest request) {
        Boolean result = blogService.updateBlog(blogUpdateRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 删除博客
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteBlog(long blogId, HttpServletRequest request) {
        Boolean result = blogService.deleteBlog(blogId, request);
        return ResultUtils.success(result);
    }
}