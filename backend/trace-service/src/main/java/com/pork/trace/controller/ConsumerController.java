package com.pork.trace.controller;

import com.pork.core.result.Result;
import com.pork.trace.service.TraceQueryService;
import com.pork.trace.vo.SafeBuyVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/consumer")
@RequiredArgsConstructor
public class ConsumerController {
    private final TraceQueryService service;

    @GetMapping("/scan/{qrCode}")
    public Result<Map<String, Object>> scan(@PathVariable String qrCode) {
        return Result.success(service.scan(qrCode));
    }

    @GetMapping("/safe-buy/{qrCode}")
    public Result<SafeBuyVO> safeBuy(@PathVariable String qrCode) {
        return Result.success(service.safeBuy(qrCode));
    }

    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@Valid @RequestBody VerifyRequest request) {
        return Result.success(service.verify(request.qrCode()));
    }

    public record VerifyRequest(@NotBlank String qrCode) {
    }
}
