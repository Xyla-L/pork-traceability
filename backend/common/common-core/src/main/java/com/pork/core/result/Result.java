package com.pork.core.result;

import com.pork.core.enums.ErrorCode;
import com.pork.core.util.TraceContext;
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

    private Long timestamp;

    /**
     * 成功返回（无数据）
     */
    public static <T> Result<T> success() {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功返回（带数据）
     */
    public static <T> Result<T> success(T data) {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功返回（自定义成功提示 + 数据）
     */
    public static <T> Result<T> success(String message, T data) {
        return build(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败返回（使用枚举错误码）
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return build(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败返回（使用枚举错误码 + 自定义覆盖提示）
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String customMessage) {
        return build(errorCode.getCode(), customMessage, null);
    }

    /**
     * 失败返回（自定义错误码和提示，用于对接第三方非标接口）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(Integer code, String message, T data) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(data)
                .traceId(TraceContext.getOrCreate())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
