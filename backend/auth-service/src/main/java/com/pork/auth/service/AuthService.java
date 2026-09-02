package com.pork.auth.service;

import com.pork.auth.dto.LoginDTO;
import com.pork.auth.dto.RegisterDTO;
import com.pork.auth.vo.GetUserInfoVO;
import com.pork.auth.vo.LoginVO;
import com.pork.auth.vo.RegisterVO;

public interface AuthService {

    /**
     * 用户登录认证
     *
     * @param dto 登录请求参数
     * @return 登录响应结果（包含 Token 等）
     */
    LoginVO login(LoginDTO dto);


    /**
     * 用户注册
     *
     * @param dto 注册参数
     * @return 注册结果（含用户ID、用户名、角色等）
     */
    RegisterVO register(RegisterDTO dto);

    /**
     * 获取当前登录用户信息
     */
    GetUserInfoVO getUserInfo();


    /**
     * 退出登录 / 注销 Token
     *
     * @param token 待注销的 Token
     */
    void logout(String token);
}