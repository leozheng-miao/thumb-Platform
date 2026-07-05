package com.leo.thumbbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.model.dto.blog.BlogAddRequest;
import com.leo.thumbbackend.model.dto.blog.BlogUpdateRequest;
import com.leo.thumbbackend.model.entity.Blog;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.model.vo.BlogVO;
import com.leo.thumbbackend.service.BlogService;
import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.service.ThumbService;
import com.leo.thumbbackend.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author zhengsmacbook
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2026-07-05 18:21:27
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{
    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    @Override
    public Long addBlog(BlogAddRequest blogAddRequest, HttpServletRequest request) {
        if (blogAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        String title = blogAddRequest.getTitle();
        String coverImg = blogAddRequest.getCoverImg();
        String content = blogAddRequest.getContent();

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容不能为空");
        }

        if (title != null && title.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }

        if (coverImg != null && coverImg.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "封面地址过长");
        }

        Blog blog = new Blog();
        blog.setUserId(loginUser.getId());
        blog.setTitle(title);
        blog.setCoverImg(coverImg);
        blog.setContent(content);
        blog.setThumbCount(0);

        boolean saveResult = this.save(blog);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }

        return blog.getId();
    }

    @Override
    public Boolean updateBlog(BlogUpdateRequest blogUpdateRequest, HttpServletRequest request) {
        if (blogUpdateRequest == null || blogUpdateRequest.getId() == null || blogUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        Long blogId = blogUpdateRequest.getId();

        Blog oldBlog = this.getById(blogId);
        if (oldBlog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        if (!oldBlog.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        String title = blogUpdateRequest.getTitle();
        String coverImg = blogUpdateRequest.getCoverImg();
        String content = blogUpdateRequest.getContent();

        if (title != null && title.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }

        if (coverImg != null && coverImg.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "封面地址过长");
        }

        if (content != null && content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容不能为空");
        }

        Blog blog = new Blog();
        blog.setId(blogId);

        if (title != null) {
            blog.setTitle(title);
        }

        if (coverImg != null) {
            blog.setCoverImg(coverImg);
        }

        if (content != null) {
            blog.setContent(content);
        }

        boolean updateResult = this.updateById(blog);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }

        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean deleteBlog(long blogId, HttpServletRequest request) {
        if (blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        Blog oldBlog = this.getById(blogId);
        if (oldBlog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        if (!oldBlog.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        boolean removeBlogResult = this.removeById(blogId);
        if (!removeBlogResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }

        thumbService.lambdaUpdate()
                .eq(Thumb::getBlogId, blogId)
                .remove();

        return true;
    }

    private BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

        if (loginUser == null) {
            return blogVO;
        }

        Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getUserId, loginUser.getId())
                .eq(Thumb::getBlogId, blog.getId())
                .one();
        blogVO.setHasThumb(thumb != null);

        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        if (ObjUtil.isNotEmpty(loginUser)) {
            Set<Long> blogIdSet = blogList.stream().map(Blog::getId).collect(Collectors.toSet());
            // 获取点赞
            List<Thumb> thumbList = thumbService.lambdaQuery()
                    .eq(Thumb::getUserId, loginUser.getId())
                    .in(Thumb::getBlogId, blogIdSet)
                    .list();

            thumbList.forEach(blogThumb -> blogIdHasThumbMap.put(blogThumb.getBlogId(), true));
        }

        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumbMap.get(blog.getId()));
                    return blogVO;
                })
                .toList();
    }


}