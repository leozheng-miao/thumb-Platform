package com.leo.thumbbackend.controller;

import com.leo.thumbbackend.annotation.AuthCheck;
import com.leo.thumbbackend.common.BaseResponse;
import com.leo.thumbbackend.common.ResultUtils;
import com.leo.thumbbackend.model.dto.user.UserLoginRequest;
import com.leo.thumbbackend.model.dto.user.UserRegisterRequest;
import com.leo.thumbbackend.model.vo.UserVO;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public BaseResponse<Long> register(@Valid @RequestBody UserRegisterRequest registerRequest) {
        return ResultUtils.success(userService.userRegister(registerRequest));
    }

    @PostMapping("/login")
    public BaseResponse<UserVO> login(@Valid @RequestBody UserLoginRequest loginRequest,
                                      HttpServletRequest request) {
        return ResultUtils.success(userService.userLogin(loginRequest, request));
    }

    @PostMapping("/logout")
    @AuthCheck
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }

    @GetMapping("/get/login")
    @AuthCheck
    public BaseResponse<UserVO> getLoginUser(HttpServletRequest request) {
        return ResultUtils.success(userService.getLoginUserVO(request));
    }
}
