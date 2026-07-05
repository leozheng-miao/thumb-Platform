package com.leo.thumbbackend.service;

import com.leo.thumbbackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author zhengsmacbook
* @description 针对表【user】的数据库操作Service
* @createDate 2026-07-05 18:23:47
*/
public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);
}