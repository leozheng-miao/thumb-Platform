package com.leo.thumbbackend.service;

import com.leo.thumbbackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leo.thumbbackend.model.dto.user.UserLoginRequest;
import com.leo.thumbbackend.model.dto.user.UserRegisterRequest;
import com.leo.thumbbackend.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author zhengsmacbook
* @description 针对表【user】的数据库操作Service
* @createDate 2026-07-05 18:23:47
*/
public interface UserService extends IService<User> {

    long userRegister(UserRegisterRequest registerRequest);

    UserVO userLogin(UserLoginRequest loginRequest, HttpServletRequest request);

    boolean userLogout(HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    UserVO getLoginUserVO(HttpServletRequest request);
}
