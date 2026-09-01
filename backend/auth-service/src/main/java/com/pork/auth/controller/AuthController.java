package com.pork.auth.controller;

import com.pork.auth.dto.LoginDTO;
import com.pork.auth.service.AuthService;
import com.pork.auth.vo.LoginVO;
import com.pork.core.result.Result;
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
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(null);
        return Result.success();
    }
}