package com.pork.auth.controller;

import com.pork.auth.dto.AuthRequests;
import com.pork.auth.dto.LoginDTO;
import com.pork.auth.dto.RegisterDTO;
import com.pork.auth.service.AuthService;
import com.pork.auth.vo.LoginVO;
import com.pork.auth.vo.RegisterVO;
import com.pork.auth.vo.UserInfoVO;
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
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = authService.login(dto);
        return Result.success(vo);
    }


    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody AuthRequests.Refresh request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(authService.register(dto));
    }

    /**
     * 获取当前登录用户信息
     * GET /api/v1/auth/me（经网关访问）；直连服务调试时为 /auth/me
     * 需要携带 Authorization: Bearer &lt;token&gt; 请求头
     */
    @GetMapping("/me")
    public Result<UserInfoVO> getUserInfo() {
        return Result.success(authService.getUserInfo());
    }

    @PutMapping("/me")
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody AuthRequests.Profile request) {
        return Result.success(authService.updateProfile(request));
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody AuthRequests.Password request) {
        authService.updatePassword(request);
        return Result.success();
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(bearer(authorization));
        return Result.success();
    }

    private String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
    }
}
