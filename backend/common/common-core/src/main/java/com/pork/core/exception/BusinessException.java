package com.pork.core.exception;

import com.pork.core.enums.ErrorCode;
import lombok.Getter;

/**
 * 自定义业务异常类
 * 规范：所有业务逻辑中抛出的异常统一继承此类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    /**
     * 使用枚举构造
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用枚举 + 自定义消息构造
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
    }

    /**
     * 直接指定错误码和消息（对接第三方时使用）
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}