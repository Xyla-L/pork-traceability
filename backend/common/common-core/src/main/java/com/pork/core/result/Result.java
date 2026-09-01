package com.pork.core.result;

import com.pork.core.enums.ErrorCode;
import lombok.Builder;
import lombok.Data;

/**
 * 全局统一API响应体
 * 符合RESTful API规范，支持泛型返回
 */
@Data
@Builder
public class Result<T> {
    
    /**
     * 业务状态码（200表示成功，其他为异常码）
     */
    private Integer code;
    
    /**
     * 响应消息/错误提示
     */
    private String message;
    
    /**
     * 业务数据载荷
     */
    private T data;
    
    /**
     * 链路追踪ID（用于微服务日志排查，后期由拦截器自动填充）
     */
    private String traceId;

    /**
     * 成功返回（无数据）
     */
    public static <T> Result<T> success() {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .build();
    }

    /**
     * 成功返回（带数据）
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    /**
     * 成功返回（自定义成功提示 + 数据）
     */
    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 失败返回（使用枚举错误码）
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return Result.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    /**
     * 失败返回（使用枚举错误码 + 自定义覆盖提示）
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String customMessage) {
        return Result.<T>builder()
                .code(errorCode.getCode())
                .message(customMessage)
                .build();
    }

    /**
     * 失败返回（自定义错误码和提示，用于对接第三方非标接口）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}