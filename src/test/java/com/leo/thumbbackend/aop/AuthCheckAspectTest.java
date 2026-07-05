package com.leo.thumbbackend.aop;

import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthCheckAspectTest {

    private UserService userService;
    private MockHttpServletRequest request;
    private AuthCheckAspect authCheckAspect;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        authCheckAspect = new AuthCheckAspect(userService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        when(userService.getLoginUser(request)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                authCheckAspect::checkLogin
        );

        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), exception.getCode());
    }

    @Test
    void authenticatedRequestIsAllowed() {
        when(userService.getLoginUser(request)).thenReturn(new User());

        assertDoesNotThrow(authCheckAspect::checkLogin);
    }
}
