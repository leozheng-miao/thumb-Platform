package com.leo.thumbbackend.model.dto.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validLoginRequestPassesValidation() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUserAccount("test_user");
        request.setUserPassword("password123");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void invalidAccountFailsValidation() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUserAccount("a-");
        request.setUserPassword("password123");

        Set<ConstraintViolation<UserLoginRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("账号只能包含字母、数字和下划线，长度为 4-32 位",
                violations.iterator().next().getMessage());
    }

    @Test
    void passwordWithoutNumberFailsValidation() {
        UserLoginRequest request = new UserLoginRequest();
        request.setUserAccount("test_user");
        request.setUserPassword("onlyletters");

        Set<ConstraintViolation<UserLoginRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("密码长度为 8-32 位，且必须同时包含字母和数字",
                violations.iterator().next().getMessage());
    }

    @Test
    void validRegisterRequestPassesValidation() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserAccount("test_user");
        request.setUserPassword("password123");
        request.setCheckPassword("password123");

        assertTrue(validator.validate(request).isEmpty());
    }
}
