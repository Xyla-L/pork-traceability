package com.pork.trace.controller;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;
import com.pork.security.util.JwtUtil;
import com.pork.trace.dto.NotificationCreate;
import com.pork.trace.service.NotificationService;
import com.pork.trace.vo.NotificationVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping("/list")
    public Result<List<NotificationVO>> list(Boolean read, HttpServletRequest request) {
        return Result.success(service.list(userId(request), read));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        service.markRead(userId(request), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        service.markAllRead(userId(request));
        return Result.success();
    }

    /**
     * 内部接口：给一批用户推同一条通知（仅业务服务调用，不经过网关）。
     * <p>
     * 谁该收到由调用方按机构/角色解析后传进来 —— 通知中心不猜收件人。
     * 返回实际写入条数（0 = 一个收件人都没解析出来，调用方据此排查账号配置）。
     */
    @PostMapping("/internal/create")
    public Result<Integer> create(@Valid @RequestBody NotificationCreate request) {
        return Result.success(service.createAll(request));
    }

    private Long userId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        if (token == null || !JwtUtil.isValid(token) || !"access".equals(JwtUtil.getTokenType(token)))
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证或令牌已失效");
        try { return Long.valueOf(JwtUtil.getUsername(token)); }
        catch (RuntimeException e) { throw new BusinessException(ErrorCode.UNAUTHORIZED, "令牌用户标识无效"); }
    }
}
