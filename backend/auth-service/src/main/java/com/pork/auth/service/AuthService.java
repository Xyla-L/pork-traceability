package com.pork.auth.service;

import com.pork.auth.dto.AuthRequests;
import com.pork.auth.dto.LoginDTO;
import com.pork.auth.dto.RegisterDTO;
import com.pork.auth.vo.LoginVO;
import com.pork.auth.vo.RegisterVO;
import com.pork.auth.vo.UserInfoVO;

public interface AuthService {

    /**
     * 用户登录认证
     *
     * @param dto 登录请求参数
     * @return 登录响应结果（包含 Token 等）
     */
    LoginVO login(LoginDTO dto);


    LoginVO refresh(String refreshToken);

    /**
     * 用户注册
     *
     * @param dto 注册请求参数
     * @return 注册结果
     */
    RegisterVO register(RegisterDTO dto);

    /**
     * 获取当前登录用户信息
     */
    UserInfoVO getUserInfo();

    UserInfoVO updateProfile(AuthRequests.Profile request);

    void updatePassword(AuthRequests.Password request);


    /**
     * 退出登录 / 注销 Token
     *
     * @param token 待注销的 Token
     */
    void logout(String token);
}
