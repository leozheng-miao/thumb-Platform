package com.leo.thumbbackend.service;

public interface LoginAttemptService {

    boolean isBlocked(String userAccount, String clientIp);

    long recordFailure(String userAccount, String clientIp);

    void clearFailures(String userAccount, String clientIp);
}
