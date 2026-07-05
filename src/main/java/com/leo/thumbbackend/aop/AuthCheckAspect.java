package com.leo.thumbbackend.aop;

import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthCheckAspect {

    private final UserService userService;

    public AuthCheckAspect(UserService userService) {
        this.userService = userService;
    }

    @Before("@annotation(com.leo.thumbbackend.annotation.AuthCheck) || "
            + "@within(com.leo.thumbbackend.annotation.AuthCheck)")
    public void checkLogin() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        HttpServletRequest request = attributes.getRequest();
        if (userService.getLoginUser(request) == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }
}
