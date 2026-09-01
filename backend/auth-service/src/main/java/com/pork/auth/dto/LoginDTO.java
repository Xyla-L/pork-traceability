package com.pork.auth.dto;

import lombok.Data;

@Data
public class LoginDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 角色（可选，如果登录时需指定角色，如多角色用户）
     */
    private String role;
}