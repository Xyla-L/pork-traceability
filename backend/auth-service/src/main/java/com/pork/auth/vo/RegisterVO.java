package com.pork.auth.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户注册响应 VO
 */
@Data
@Builder
public class RegisterVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 角色
     */
    private String role;
}