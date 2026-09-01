package com.pork.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {
    /**
     * 登录凭证 Token (JWT)
     */
    private String token;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色
     */
    private String role;

    /**
     * 所属企业/个体名称
     */
    private String orgName;
}