package com.pork.auth.controller;

import com.pork.auth.dto.LoginDTO;
import com.pork.auth.dto.RegisterDTO;
import com.pork.auth.service.AuthService;
import com.pork.auth.vo.GetUserInfoVO;
import com.pork.auth.vo.LoginVO;
import com.pork.auth.vo.RegisterVO;
import com.pork.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        LoginVO vo = authService.login(dto);
        return Result.success(vo);
    }


    /**
     * 用户注册
     * POST /api/v1/auth/register
     *
     * @param dto 注册参数（用户名 + 密码 + 确认密码 + 可选姓名/手机/角色）
     * @return 注册结果（用户ID、用户名、角色）
     */
    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterDTO dto) {
        RegisterVO registerVO = authService.register(dto);
        return Result.success(registerVO);
    }

    /**
     * 获取当前登录用户信息
     * GET /api/v1/auth/info
     * 需要携带 Authorization: Bearer &lt;token&gt; 请求头
     */
    @GetMapping("/info")
    public Result<GetUserInfoVO> getUserInfo() {
        return Result.success(authService.getUserInfo());
    }




    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(null);
        return Result.success();
    }
}