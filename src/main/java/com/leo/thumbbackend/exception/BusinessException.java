package com.leo.thumbbackend.exception;

import lombok.Getter;

/**
 * @program: yu-picture
 * @description: 自定义业务异常
 * @author: Miao Zheng
 * @date: 2025-10-20 16:26
 **/
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}