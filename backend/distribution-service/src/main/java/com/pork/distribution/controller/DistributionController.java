package com.pork.distribution.controller;

import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.*;
import com.pork.distribution.service.DistributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/distribution")
@RequiredArgsConstructor
public class DistributionController {
    private final DistributionService service;

    @PostMapping("/batches")
    public Result<CarcassBatch> createBatch(@Valid @RequestBody DistributionRequests.BatchCreate request) {
        return Result.success(service.createBatch(request));
    }

    @GetMapping("/batches")
    public Result<PageResult<CarcassBatch>> batches(String batchNo, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageBatches(batchNo, page.getPageNum(), page.getPageSize())));
    }

    @PostMapping("/splits")
    public Result<SplitBatch> createSplit(@Valid @RequestBody DistributionRequests.SplitCreate request) {
        return Result.success(service.createSplit(request));
    }

    @GetMapping("/splits/{id}")
    public Result<SplitBatch> split(@PathVariable Long id) { return Result.success(service.getSplit(id)); }

    @GetMapping("/splits/tree/{batchNo}")
    public Result<Map<String, Object>> tree(@PathVariable String batchNo) { return Result.success(service.getSplitTree(batchNo)); }

    @GetMapping("/internal/splits/{id}/upstream")
    public Result<List<Map<String, Object>>> upstream(@PathVariable Long id) { return Result.success(service.upstream(id)); }

    @GetMapping("/internal/batches/{batchNo}/downstream")
    public Result<Map<String, Object>> downstream(@PathVariable String batchNo) {
        return Result.success(service.downstream(batchNo));
    }

    @PostMapping("/transports")
    public Result<ColdChainTransport> createTransport(@Valid @RequestBody DistributionRequests.TransportCreate request) {
        return Result.success(service.createTransport(request));
    }

    @GetMapping("/transports")
    public Result<PageResult<ColdChainTransport>> transports(Integer status, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageTransports(status, page.getPageNum(), page.getPageSize())));
    }

    @GetMapping("/transports/{id}")
    public Result<ColdChainTransport> transport(@PathVariable Long id) { return Result.success(service.getTransport(id)); }

    @PutMapping("/transports/{id}/depart")
    public Result<Void> depart(@PathVariable Long id) { service.depart(id); return Result.success(); }

    @PostMapping("/transports/{id}/temperature")
    public Result<TemperatureLog> temperature(@PathVariable Long id, @Valid @RequestBody DistributionRequests.TemperatureCreate request) {
        return Result.success(service.addTemperature(id, request));
    }

    @GetMapping("/transports/{id}/temperature")
    public Result<List<TemperatureLog>> temperatures(@PathVariable Long id) { return Result.success(service.temperatureLogs(id)); }

    @PutMapping("/transports/{id}/arrive")
    public Result<Void> arrive(@PathVariable Long id) { service.arrive(id); return Result.success(); }

    @PostMapping("/receipts")
    public Result<StoreReceipt> receipt(@Valid @RequestBody DistributionRequests.ReceiptCreate request) {
        return Result.success(service.createReceipt(request));
    }

    @GetMapping("/receipts")
    public Result<PageResult<StoreReceipt>> receipts(Long storeId, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageReceipts(storeId, page.getPageNum(), page.getPageSize())));
    }

    @GetMapping("/stores")
    public Result<List<Map<String, Object>>> stores() { return Result.success(service.stores()); }
}
