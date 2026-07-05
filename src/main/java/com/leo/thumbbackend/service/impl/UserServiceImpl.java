package com.leo.thumbbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.thumbbackend.constant.UserConstant;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.service.UserService;
import com.leo.thumbbackend.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author zhengsmacbook
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-07-05 18:23:47
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{
    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }
}