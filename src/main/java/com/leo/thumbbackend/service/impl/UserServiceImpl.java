package com.leo.thumbbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leo.thumbbackend.constant.UserConstant;
import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.mapper.UserMapper;
import com.leo.thumbbackend.model.dto.user.UserLoginRequest;
import com.leo.thumbbackend.model.dto.user.UserRegisterRequest;
import com.leo.thumbbackend.model.entity.User;
import com.leo.thumbbackend.model.vo.UserVO;
import com.leo.thumbbackend.service.LoginAttemptService;
import com.leo.thumbbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public UserServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService) {
        this.baseMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public long userRegister(UserRegisterRequest registerRequest) {
        if (registerRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (!registerRequest.getUserPassword().equals(registerRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        String userAccount = registerRequest.getUserAccount();
        Long accountCount = baseMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUserAccount, userAccount)
        );
        if (accountCount != null && accountCount > 0) {
            throw new BusinessException(ErrorCode.USER_EXIST);
        }

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(passwordEncoder.encode(registerRequest.getUserPassword()));
        user.setIsDelete(0);

        try {
            if (baseMapper.insert(user) != 1 || user.getId() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USER_EXIST);
        }
        return user.getId();
    }

    @Override
    public UserVO userLogin(UserLoginRequest loginRequest, HttpServletRequest request) {
        if (loginRequest == null || request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String userAccount = loginRequest.getUserAccount();
        String clientIp = request.getRemoteAddr();
        if (loginAttemptService.isBlocked(userAccount, clientIp)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "登录失败次数过多，请稍后再试");
        }

        User user = baseMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserAccount, userAccount)
        );
        if (user == null || !passwordEncoder.matches(
                loginRequest.getUserPassword(),
                user.getUserPassword()
        )) {
            loginAttemptService.recordFailure(userAccount, clientIp);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        loginAttemptService.clearFailures(userAccount, clientIp);
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user.getId());
        return toUserVO(user);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        session.invalidate();
        return true;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object loginUserId = session.getAttribute(UserConstant.LOGIN_USER);
        if (!(loginUserId instanceof Long userId)) {
            return null;
        }
        return baseMapper.selectById(userId);
    }

    @Override
    public UserVO getLoginUserVO(HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return toUserVO(loginUser);
    }

    private UserVO toUserVO(User user) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUserAccount(user.getUserAccount());
        return userVO;
    }
}
