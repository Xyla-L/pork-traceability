package com.pork.blockchain.controller;

import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.entity.BlockchainRecord;
import com.pork.blockchain.service.BlockchainService;
import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/blockchain")
@RequiredArgsConstructor
public class BlockchainController {
    private final BlockchainService service;

    @GetMapping("/records")
    public Result<PageResult<BlockchainRecord>> records(String bizType, Long bizId, Integer status, @Valid PageQuery page) {
        return Result.success(service.records(bizType, bizId, status, page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/records/{id}")
    public Result<BlockchainRecord> record(@PathVariable Long id) { return Result.success(service.record(id)); }

    @PostMapping("/records/{id}/retry")
    public Result<BlockchainRecord> retry(@PathVariable Long id) { return Result.success(service.retry(id)); }

    @PostMapping("/verify")
    public Result<BlockchainAdapter.Verification> verify(@Valid @RequestBody VerifyRequest request) {
        return Result.success(service.verify(request.bizType(), request.bizId()));
    }

    @GetMapping("/status/{batchNo}")
    public Result<List<BlockchainRecord>> status(@PathVariable String batchNo) { return Result.success(service.status(batchNo)); }

    @GetMapping("/audit-logs")
    public Result<PageResult<BlockchainRecord>> auditLogs(String operType, String operator, @Valid PageQuery page) {
        return Result.success(service.records(operType, null, null, page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/audit-stats")
    public Result<Map<String, Long>> stats() { return Result.success(service.stats()); }

    public record VerifyRequest(@NotBlank String bizType, @NotNull Long bizId) { }
}
