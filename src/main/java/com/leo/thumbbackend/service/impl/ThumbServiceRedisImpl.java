package com.leo.thumbbackend.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.thumbbackend.constant.RedisLuaScriptConstant;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.manager.cache.CacheManager;
import com.leo.thumbbackend.mapper.ThumbMapper;
import com.leo.thumbbackend.model.dto.thumb.DoThumbRequest;
import com.leo.thumbbackend.model.entity.Thumb;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.model.enums.LuaStatusEnum;
import com.leo.thumbbackend.service.ThumbService;
import com.leo.thumbbackend.service.UserService;
import com.leo.thumbbackend.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheManager cacheManager;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Long blogId = validateAndGetBlogId(doThumbRequest);
        User loginUser = getLoginUser(request);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        String blogIdKey = blogId.toString();
        Long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                List.of(getTempThumbKey(), userThumbKey, RedisKeyUtil.getBlogExistsKey()),
                loginUser.getId().toString(),
                blogIdKey
        );
        long luaResult = getLuaResult(result);
        if (LuaStatusEnum.BLOG_NOT_EXIST.getValue() == luaResult) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "博客不存在");
        }
        if (LuaStatusEnum.FAIL.getValue() == luaResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
        }
        boolean success = LuaStatusEnum.SUCCESS.getValue() == luaResult;
        if (success) {
            cacheManager.putIfPresent(userThumbKey, blogIdKey, "1");
        }
        return success;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Long blogId = validateAndGetBlogId(doThumbRequest);
        User loginUser = getLoginUser(request);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        String blogIdKey = blogId.toString();
        Long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                List.of(getTempThumbKey(), userThumbKey, RedisKeyUtil.getBlogExistsKey()),
                loginUser.getId().toString(),
                blogIdKey
        );
        long luaResult = getLuaResult(result);
        if (LuaStatusEnum.BLOG_NOT_EXIST.getValue() == luaResult) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "博客不存在");
        }
        if (LuaStatusEnum.FAIL.getValue() == luaResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未点赞");
        }
        boolean success = LuaStatusEnum.SUCCESS.getValue() == luaResult;
        if (success) {
            cacheManager.delete(userThumbKey, blogIdKey);
        }
        return success;
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        if (blogId == null || userId == null || blogId <= 0 || userId <= 0) {
            return false;
        }
        return cacheManager.get(RedisKeyUtil.getUserThumbKey(userId), blogId.toString()) != null;
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

    private String getTempThumbKey() {
        DateTime nowDate = DateUtil.date();
        String timeSlice = DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;
        return RedisKeyUtil.getTempThumbKey(timeSlice);
    }

    private long getLuaResult(Long result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞操作失败");
        }
        return result;
    }
}
