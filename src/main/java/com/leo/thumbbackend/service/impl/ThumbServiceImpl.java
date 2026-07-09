package com.leo.thumbbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.mapper.BlogMapper;
import com.leo.thumbbackend.mapper.ThumbMapper;
import com.leo.thumbbackend.model.dto.thumb.DoThumbRequest;
import com.leo.thumbbackend.model.entity.Blog;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.service.ThumbService;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    private static final String LOCK_KEY_PREFIX = "thumb:lock:";

    private final BlogMapper blogMapper;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redissonClient;

    public ThumbServiceImpl(ThumbMapper thumbMapper,
                            BlogMapper blogMapper,
                            UserService userService,
                            TransactionTemplate transactionTemplate,
                            RedissonClient redissonClient) {
        this.baseMapper = thumbMapper;
        this.blogMapper = blogMapper;
        this.userService = userService;
        this.transactionTemplate = transactionTemplate;
        this.redissonClient = redissonClient;
    }

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Long blogId = validateAndGetBlogId(doThumbRequest);
        User loginUser = getLoginUser(request);
        return executeWithLock(loginUser.getId(), blogId, () -> doThumbInTransaction(loginUser.getId(), blogId));
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Long blogId = validateAndGetBlogId(doThumbRequest);
        User loginUser = getLoginUser(request);
        return executeWithLock(loginUser.getId(), blogId, () -> undoThumbInTransaction(loginUser.getId(), blogId));
    }

    private Boolean doThumbInTransaction(Long userId, Long blogId) {
        return transactionTemplate.execute(status -> {
            Long thumbCount = baseMapper.selectCount(
                    new LambdaQueryWrapper<Thumb>()
                            .eq(Thumb::getUserId, userId)
                            .eq(Thumb::getBlogId, blogId)
            );
            if (thumbCount != null && thumbCount > 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
            }

            int updatedRows = blogMapper.update(
                    null,
                    new LambdaUpdateWrapper<Blog>()
                            .eq(Blog::getId, blogId)
                            .setSql("thumbCount = thumbCount + 1")
            );
            if (updatedRows != 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "博客不存在");
            }

            Thumb thumb = new Thumb();
            thumb.setUserId(userId);
            thumb.setBlogId(blogId);
            if (baseMapper.insert(thumb) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败");
            }
            return true;
        });
    }

    private Boolean undoThumbInTransaction(Long userId, Long blogId) {
        return transactionTemplate.execute(status -> {
            Thumb thumb = baseMapper.selectOne(
                    new LambdaQueryWrapper<Thumb>()
                            .eq(Thumb::getUserId, userId)
                            .eq(Thumb::getBlogId, blogId)
            );
            if (thumb == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未点赞");
            }

            int updatedRows = blogMapper.update(
                    null,
                    new LambdaUpdateWrapper<Blog>()
                            .eq(Blog::getId, blogId)
                            .gt(Blog::getThumbCount, 0)
                            .setSql("thumbCount = thumbCount - 1")
            );
            if (updatedRows != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
            }

            if (baseMapper.deleteById(thumb.getId()) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
            }
            return true;
        });
    }

    private Boolean executeWithLock(Long userId, Long blogId, Supplier<Boolean> operation) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId + ":" + blogId);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "操作频繁");
        }
        try {
            Boolean result = operation.get();
            if (!Boolean.TRUE.equals(result)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR);
            }
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Long validateAndGetBlogId(DoThumbRequest doThumbRequest) {
        if (doThumbRequest == null
                || doThumbRequest.getBlogId() == null
                || doThumbRequest.getBlogId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return doThumbRequest.getBlogId();
    }

    private User getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return loginUser;
    }
}
