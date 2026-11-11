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
     * 建议先 dryRun=true 看待迁条数，再按批（如 limit=100）反复调用直到 remaining=0。
     */
    @PostMapping("/migrate/legacy")
    public Result<Map<String, Object>> migrateLegacy(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return Result.success(service.migrateLegacy(limit, dryRun));
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
