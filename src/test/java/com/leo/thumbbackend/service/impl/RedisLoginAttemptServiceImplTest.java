package com.leo.thumbbackend.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginAttemptServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisLoginAttemptServiceImpl loginAttemptService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        loginAttemptService = new RedisLoginAttemptServiceImpl(redisTemplate);
    }

    @Test
    void fewerThanFiveFailuresIsNotBlocked() {
        when(valueOperations.get("login:failure:test_user:127.0.0.1")).thenReturn("4");

        assertFalse(loginAttemptService.isBlocked("test_user", "127.0.0.1"));
    }

    @Test
    void fiveFailuresIsBlocked() {
        when(valueOperations.get("login:failure:test_user:127.0.0.1")).thenReturn("5");

        assertTrue(loginAttemptService.isBlocked("test_user", "127.0.0.1"));
    }

    @Test
    void recordFailureUsesAtomicIncrementWithFifteenMinuteExpiry() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("login:failure:test_user:127.0.0.1")),
                eq("900")
        )).thenReturn(1L);

        long failures = loginAttemptService.recordFailure("test_user", "127.0.0.1");

        assertTrue(failures == 1L);
    }

    @Test
    void clearFailuresDeletesRedisKey() {
        loginAttemptService.clearFailures("test_user", "127.0.0.1");

        verify(redisTemplate).delete("login:failure:test_user:127.0.0.1");
    }
}
