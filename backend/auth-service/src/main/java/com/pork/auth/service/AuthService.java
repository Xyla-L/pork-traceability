package com.pork.auth.service;

import com.pork.auth.dto.LoginDTO;
import com.pork.auth.vo.LoginVO;

public interface AuthService {

    /**
     * 用户登录认证
     *
     * @param dto 登录请求参数
     * @return 登录响应结果（包含 Token 等）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 退出登录 / 注销 Token
     *
     * @param token 待注销的 Token
     */
    void logout(String token);
}