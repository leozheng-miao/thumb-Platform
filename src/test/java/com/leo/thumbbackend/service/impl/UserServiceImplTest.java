package com.leo.thumbbackend.service.impl;

import com.leo.thumbbackend.constant.UserConstant;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.mapper.UserMapper;
import com.leo.thumbbackend.model.dto.user.UserLoginRequest;
import com.leo.thumbbackend.model.dto.user.UserRegisterRequest;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.model.vo.UserVO;
import com.leo.thumbbackend.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private HttpServletRequest request;
    private HttpSession session;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        loginAttemptService = mock(LoginAttemptService.class);
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        userService = new UserServiceImpl(userMapper, passwordEncoder, loginAttemptService);
    }

    @Test
    void registerEncryptsPasswordAndReturnsUserId() {
        UserRegisterRequest registerRequest = registerRequest();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-password");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });

        long userId = userService.userRegister(registerRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals(10L, userId);
        assertEquals("test_user", userCaptor.getValue().getUserAccount());
        assertEquals("bcrypt-password", userCaptor.getValue().getUserPassword());
        assertNotEquals("password123", userCaptor.getValue().getUserPassword());
        assertEquals(0, userCaptor.getValue().getIsDelete());
    }

    @Test
    void duplicateAccountIsRejected() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.userRegister(registerRequest())
        );

        assertEquals(ErrorCode.USER_EXIST.getCode(), exception.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void mismatchedPasswordsAreRejected() {
        UserRegisterRequest registerRequest = registerRequest();
        registerRequest.setCheckPassword("different123");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.userRegister(registerRequest)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void loginReturnsSafeUserAndStoresOnlyUserIdInSession() {
        User user = user();
        UserLoginRequest loginRequest = loginRequest();
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(loginAttemptService.isBlocked("test_user", "127.0.0.1")).thenReturn(false);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("password123", "bcrypt-password")).thenReturn(true);
        when(request.getSession()).thenReturn(session);

        UserVO userVO = userService.userLogin(loginRequest, request);

        assertEquals(1L, userVO.getId());
        assertEquals("test_user", userVO.getUserAccount());
        verify(session).setAttribute(UserConstant.LOGIN_USER, 1L);
        verify(loginAttemptService).clearFailures("test_user", "127.0.0.1");
    }

    @Test
    void wrongPasswordRecordsFailure() {
        User user = user();
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(loginAttemptService.isBlocked("test_user", "127.0.0.1")).thenReturn(false);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("password123", "bcrypt-password")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.userLogin(loginRequest(), request)
        );

        assertEquals(ErrorCode.PASSWORD_ERROR.getCode(), exception.getCode());
        verify(loginAttemptService).recordFailure("test_user", "127.0.0.1");
    }

    @Test
    void blockedLoginDoesNotQueryUser() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(loginAttemptService.isBlocked("test_user", "127.0.0.1")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.userLogin(loginRequest(), request)
        );

        assertEquals(ErrorCode.TOO_MANY_REQUEST.getCode(), exception.getCode());
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void logoutInvalidatesExistingSession() {
        when(request.getSession(false)).thenReturn(session);

        assertTrue(userService.userLogout(request));

        verify(session).invalidate();
    }

    @Test
    void logoutWithoutSessionReturnsFalse() {
        when(request.getSession(false)).thenReturn(null);

        assertFalse(userService.userLogout(request));
    }

    @Test
    void getLoginUserReloadsUserFromDatabase() {
        User user = user();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(UserConstant.LOGIN_USER)).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertEquals(user, userService.getLoginUser(request));
        assertEquals("test_user", userService.getLoginUserVO(request).getUserAccount());
    }

    @Test
    void getLoginUserAcceptsNumericSessionUserId() {
        User user = user();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(UserConstant.LOGIN_USER)).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertEquals(user, userService.getLoginUser(request));
    }

    private UserRegisterRequest registerRequest() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserAccount("test_user");
        request.setUserPassword("password123");
        request.setCheckPassword("password123");
        return request;
    }

    private UserLoginRequest loginRequest() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUserAccount("test_user");
        request.setUserPassword("password123");
        return request;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("test_user");
        user.setUserPassword("bcrypt-password");
        user.setIsDelete(0);
        return user;
    }
}
