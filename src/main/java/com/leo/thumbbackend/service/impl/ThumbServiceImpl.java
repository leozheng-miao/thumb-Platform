package com.leo.thumbbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.thumbbackend.constant.ThumbConstant;
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
import com.leo.thumbbackend.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    private final BlogMapper blogMapper;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Long blogId = validateAndGetBlogId(doThumbRequest);
        User loginUser = getLoginUser(request);
        return executeWithLock(loginUser.getId(), blogId, () -> doThumbWithCache(loginUser.getId(), blogId));
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Long blogId = validateAndGetBlogId(doThumbRequest);
        User loginUser = getLoginUser(request);
        return executeWithLock(loginUser.getId(), blogId, () -> undoThumbWithCache(loginUser.getId(), blogId));
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        if (blogId == null || userId == null || blogId <= 0 || userId <= 0) {
            return false;
        }
        Long cachedThumbId = getCachedThumbId(userId, blogId);
        if (cachedThumbId != null) {
            return true;
        }
        Thumb thumb = getThumbFromDb(userId, blogId);
        if (thumb == null) {
            return false;
        }
        saveThumbCache(userId, blogId, thumb.getId());
        return true;
    }

    private Boolean doThumbWithCache(Long userId, Long blogId) {
        if (Boolean.TRUE.equals(hasThumb(blogId, userId))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
        }
        ThumbResult thumbResult = doThumbInTransaction(userId, blogId);
        saveThumbCache(userId, blogId, thumbResult.thumbId());
        return true;
    }

    private ThumbResult doThumbInTransaction(Long userId, Long blogId) {
        return transactionTemplate.execute(status -> {
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
            return new ThumbResult(thumb.getId());
        });
    }

    private Boolean undoThumbWithCache(Long userId, Long blogId) {
        Thumb thumb = getThumbFromDb(userId, blogId);
        if (thumb == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未点赞");
        }
        undoThumbInTransaction(thumb.getId(), blogId);
        deleteThumbCache(userId, blogId);
        return true;
    }

    private Boolean undoThumbInTransaction(Long thumbId, Long blogId) {
        return transactionTemplate.execute(status -> {
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

            if (baseMapper.deleteById(thumbId) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
            }
            return true;
        });
    }

    private Boolean executeWithLock(Long userId, Long blogId, Supplier<Boolean> operation) {
        RLock lock = redissonClient.getLock(RedisKeyUtil.getThumbLockKey(userId, blogId));
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

    private Thumb getThumbFromDb(Long userId, Long blogId) {
        return baseMapper.selectOne(
                new LambdaQueryWrapper<Thumb>()
                        .eq(Thumb::getUserId, userId)
                        .eq(Thumb::getBlogId, blogId)
        );
    }

    private Long getCachedThumbId(Long userId, Long blogId) {
        Object value = redisTemplate.opsForHash().get(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private void saveThumbCache(Long userId, Long blogId, Long thumbId) {
        if (thumbId == null) {
            return;
        }
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        redisTemplate.opsForHash().put(userThumbKey, blogId.toString(), thumbId);
        redisTemplate.expire(userThumbKey, Duration.ofDays(ThumbConstant.USER_THUMB_CACHE_TTL_DAYS));
    }

    private void deleteThumbCache(Long userId, Long blogId) {
        redisTemplate.opsForHash().delete(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }


    private record ThumbResult(Long thumbId) {
    }
}