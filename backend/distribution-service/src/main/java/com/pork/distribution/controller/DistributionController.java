package com.pork.distribution.controller;

import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.*;
import com.pork.distribution.service.DistributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @PutMapping("/batches/{id}")
    public Result<Void> updateBatch(@PathVariable Long id, @Valid @RequestBody DistributionRequests.BatchCreate request) {
        service.updateBatch(id, request);
        return Result.success();
    }

    @DeleteMapping("/batches/{id}")
    public Result<Void> deleteBatch(@PathVariable Long id) {
        service.deleteBatch(id);
        return Result.success();
    }

    @GetMapping("/batches/pig-occupancy")
    public Result<List<Map<String, Object>>> pigOccupancy() {
        return Result.success(service.getPigOccupancy());
    }

    @GetMapping("/batches")
    public Result<PageResult<CarcassBatch>> batches(String batchNo,
                                                    @RequestParam(required = false) String operator,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                    @RequestParam(required = false) Integer current,
                                                    @RequestParam(required = false) Integer size,
                                                    @Valid PageQuery page) {
        long pageNum = current != null ? current : page.getPageNum();
        long pageSize = size != null ? size : page.getPageSize();
        return Result.success(PageResult.of(service.pageBatches(batchNo, operator, startDate, endDate, pageNum, pageSize)));
    }

    @PostMapping("/splits")
    public Result<SplitBatch> createSplit(@Valid @RequestBody DistributionRequests.SplitCreate request) {
        return Result.success(service.createSplit(request));
    }

    @GetMapping("/splits")
    public Result<PageResult<SplitBatch>> splits(String keyword, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageSplits(keyword, page.getPageNum(), page.getPageSize())));
    }

    @GetMapping("/splits/{id}")
    public Result<SplitBatch> split(@PathVariable Long id) { return Result.success(service.getSplit(id)); }

    @PutMapping("/splits/{id}")
    public Result<Void> updateSplit(@PathVariable Long id, @Valid @RequestBody DistributionRequests.SplitUpdate request) {
        service.updateSplit(id, request);
        return Result.success();
    }

    @DeleteMapping("/splits/{id}")
    public Result<Void> deleteSplit(@PathVariable Long id) {
        service.deleteSplit(id);
        return Result.success();
    }

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
    public Result<PageResult<ColdChainTransport>> transports(Integer status, String keyword, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageTransports(status, keyword, page.getPageNum(), page.getPageSize())));
    }

    @GetMapping("/transports/{id}")
    public Result<ColdChainTransport> transport(@PathVariable Long id) { return Result.success(service.getTransport(id)); }

    @PutMapping("/transports/{id}")
    public Result<Void> updateTransport(@PathVariable Long id, @Valid @RequestBody DistributionRequests.TransportUpdate request) {
        service.updateTransport(id, request);
        return Result.success();
    }

    @DeleteMapping("/transports/{id}")
    public Result<Void> deleteTransport(@PathVariable Long id) {
        service.deleteTransport(id);
        return Result.success();
    }

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

    @GetMapping("/receipts/{id}")
    public Result<StoreReceipt> receiptDetail(@PathVariable Long id) {
        return Result.success(service.getReceipt(id));
    }

    @DeleteMapping("/receipts/{id}")
    public Result<Void> deleteReceipt(@PathVariable Long id) {
        service.deleteReceipt(id);
        return Result.success();
    }

    @GetMapping("/receipts")
    public Result<PageResult<StoreReceipt>> receipts(Long storeId,
                                                     String storeName,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                     @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageReceipts(storeId, storeName, startDate, endDate,
                page.getPageNum(), page.getPageSize())));
    }

    @GetMapping("/stores")
    public Result<List<Map<String, Object>>> stores() { return Result.success(service.stores()); }
}
