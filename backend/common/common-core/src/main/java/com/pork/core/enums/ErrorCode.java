package com.pork.core.enums;

import lombok.Getter;

/**
 * 全局统一错误码枚举
 * 规范：
 * 200 - 成功
 * 1xxx - 客户端参数错误 (1001-1999)
 * 2xxx - 认证/授权失败 (2001-2999)
 * 3xxx - 业务通用错误 (3001-3999)
 * 5xxx - 系统/数据库/第三方服务错误 (5001-5999)
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    
    // 1. 客户端参数错误 (1xxx)
    PARAM_ERROR(1001, "参数校验失败"),
    PARAM_MISSING(1002, "缺少必填参数"),
    
    // 2. 认证授权错误 (2xxx)
    UNAUTHORIZED(2001, "未登录或Token已失效"),
    LOGIN_FAILED(2002, "用户名或密码错误"),
    FORBIDDEN(2003, "没有操作权限"),
    TOKEN_INVALID(2004, "无效的Token"),
    ACCOUNT_DISABLED(2005, "账号已被禁用，请联系管理员"),
    PASSWORD_NOT_MATCH(20001, "两次输入的密码不一致"),
    USER_NOT_FOUND(20010, "用户不存在"),
    USERNAME_EXISTS(20002, "用户名已存在"),
    REGISTER_FAILED(20003, "用户注册失败，请重试"),
    // 3. 业务通用错误 (3xxx)
    RECORD_NOT_FOUND(3001, "数据不存在"),
    RECORD_ALREADY_EXISTS(3002, "数据已存在"),
    BUSINESS_ERROR(3999, "业务处理失败"),
    
    // 5. 系统级错误 (5xxx)
    SYSTEM_ERROR(5000, "系统内部异常"),
    DATABASE_ERROR(5001, "数据库操作失败"),
    REMOTE_CALL_ERROR(5002, "远程服务调用失败"),
    BLOCKCHAIN_ERROR(5010, "区块链上链失败");

    /**
     * 错误码
     */
    private final Integer code;
    
    /**
     * 错误提示
     */
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}