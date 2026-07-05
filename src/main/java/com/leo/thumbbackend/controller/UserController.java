package com.leo.thumbbackend.controller;

import com.leo.thumbbackend.common.BaseResponse;
import com.leo.thumbbackend.common.ResultUtils;
import com.leo.thumbbackend.constant.UserConstant;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {  
    @Resource  
    private UserService userService;  
  
    @GetMapping("/login")  
    public BaseResponse<User> login(long userId, HttpServletRequest request) {  
        User user = userService.getById(userId);  
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);  
        return ResultUtils.success(user);  
    }

    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        return ResultUtils.success(loginUser);
    }

}