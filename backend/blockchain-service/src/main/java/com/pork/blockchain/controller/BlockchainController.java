package com.pork.blockchain.controller;

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
    public Result<BlockchainRecord> record(@PathVariable Long id) {
        return Result.success(service.record(id));
    }

    @PostMapping("/records/{id}/retry")
    public Result<BlockchainRecord> retry(@PathVariable Long id) {
        return Result.success(service.retry(id));
    }

    /**
     * 验真：返回哈希比对结果 + 当前存证载体（local/evm）。
     * <p>
     * 调用方必须看 `onChain`：本地账本模式下的「一致」只说明平台库内的记录没被改动，
     * 不能当成「区块链已验证」对外表述。
     */
    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@Valid @RequestBody VerifyRequest request) {
        return Result.success(service.verifyView(request.bizType(), request.bizId()));
    }

    /** 当前存证模式与如实描述，供页面区分「本地存证」和「链上存证」 */
    @GetMapping("/mode")
    public Result<Map<String, Object>> mode() {
        return Result.success(service.mode());
    }

    /** 存证对账体检：失败积压、悬空存证、孤儿块 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(service.health());
    }

    /** 批量查存证状态（列表页一次取一页，别逐行查） */
    @GetMapping("/records/status-map")
    public Result<Map<Long, Integer>> statusMap(@RequestParam String bizType, @RequestParam List<Long> bizIds) {
        return Result.success(service.statusMap(bizType, bizIds));
    }

    /**
     * 把 local 时期的历史存证补写到真实链上（只在 evm 模式可用，幂等）。
     * <p>
     * 默认走后台执行，立即返回；用 {@link #migrateStatus()} 看进度。
     * 需要「等这批跑完再返回」的老用法显式传 {@code async=false}。
     * 两个入口共用一把单飞锁：已有任务在跑时直接拒绝，不会重复上链。
     */
    @PostMapping("/migrate/legacy")
    public Result<Map<String, Object>> migrateLegacy(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "true") boolean async) {
        if (!async) return Result.success(service.migrateLegacy(limit, dryRun));
        // dryRun 只是数条数，没必要排队：任务在跑时也允许
        if (dryRun) return Result.success(service.migrateLegacy(limit, true));
        return Result.success(service.startMigrate(limit));
    }

    /** 迁移进度：running / 上一轮结果 / 剩余待迁条数 */
    @GetMapping("/migrate/legacy/status")
    public Result<Map<String, Object>> migrateStatus() {
        return Result.success(service.migrateStatus());
    }

    @GetMapping("/status/{batchNo}")
    public Result<List<BlockchainRecord>> status(@PathVariable String batchNo) {
        return Result.success(service.status(batchNo));
    }

    @GetMapping("/audit-logs")
    public Result<PageResult<BlockchainRecord>> auditLogs(String operType, String operator, @Valid PageQuery page) {
        return Result.success(service.records(operType, null, null, page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/audit-stats")
    public Result<Map<String, Long>> stats() {
        return Result.success(service.stats());
    }

    public record VerifyRequest(@NotBlank String bizType, @NotNull Long bizId) {
    }
}
