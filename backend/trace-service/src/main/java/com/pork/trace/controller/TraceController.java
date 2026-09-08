package com.pork.trace.controller;

import com.pork.core.result.Result;
import com.pork.trace.service.TraceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/trace")
@RequiredArgsConstructor
public class TraceController {
    private final TraceQueryService service;

    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String keyword) { return Result.success(service.search(keyword)); }
    @GetMapping("/upstream/{batchNo}")
    public Result<Object> upstream(@PathVariable String batchNo) { return Result.success(service.upstream(batchNo)); }
    @GetMapping("/downstream/{batchNo}")
    public Result<Object> downstream(@PathVariable String batchNo) { return Result.success(service.downstream(batchNo)); }
    @GetMapping("/full/{batchNo}")
    public Result<Map<String, Object>> full(@PathVariable String batchNo) { return Result.success(service.full(batchNo)); }
    @GetMapping("/verify/{qrCode}")
    public Result<Map<String, Object>> verify(@PathVariable String qrCode) { return Result.success(service.verify(qrCode)); }
}
