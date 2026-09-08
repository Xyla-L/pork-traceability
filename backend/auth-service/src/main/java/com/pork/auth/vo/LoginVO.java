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

    private String refreshToken;

    private long expiresIn;

    private UserInfoVO user;
}
