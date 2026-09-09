package com.pork.trace.controller;

import com.pork.core.result.Result;
import com.pork.trace.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(service.overview());
    }
}
