package com.leo.thumbbackend.service.impl;

import com.leo.thumbbackend.exception.BusinessException;
import com.leo.thumbbackend.exception.ErrorCode;
import com.leo.thumbbackend.service.LoginAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisLoginAttemptServiceImpl implements LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final String WINDOW_SECONDS = "900";
    private static final String KEY_PREFIX = "login:failure:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isBlocked(String userAccount, String clientIp) {
        String count = redisTemplate.opsForValue().get(buildKey(userAccount, clientIp));
        return count != null && Long.parseLong(count) >= MAX_FAILURES;
    }

    @Override
    public long recordFailure(String userAccount, String clientIp) {
        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(buildKey(userAccount, clientIp)),
                WINDOW_SECONDS
        );
        if (count == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录限制服务异常");
        }
        return count;
    }

    @Override
    public void clearFailures(String userAccount, String clientIp) {
        redisTemplate.delete(buildKey(userAccount, clientIp));
    }

    private String buildKey(String userAccount, String clientIp) {
        return KEY_PREFIX + userAccount + ":" + clientIp;
    }
}
