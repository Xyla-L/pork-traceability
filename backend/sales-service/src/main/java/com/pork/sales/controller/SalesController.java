package com.pork.sales.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.sales.dto.SalesRequests;
import com.pork.sales.entity.ExpireWarning;
import com.pork.sales.entity.RecallOrder;
import com.pork.sales.entity.RetailSale;
import com.pork.sales.mapper.RetailSaleMapper;
import com.pork.sales.service.SalesService;
import com.pork.sales.vo.QrCodeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SalesController {
    private final SalesService service;
    private final RetailSaleMapper saleMapper;

    @PostMapping("/qrcodes/batch")
    public Result<Map<String, List<String>>> generate(@Valid @RequestBody SalesRequests.QrBatch request) {
        return Result.success(Map.of("qrcodes", service.generateQrs(request)));
    }

    @GetMapping("/qrcodes")
    public Result<PageResult<QrCodeVO>> qrs(Integer status, String qrCode, @Valid PageQuery page) {
        return Result.success(service.pageQrs(status, qrCode, page.getPageNum(), page.getPageSize()));
    }

    @PutMapping("/products/{id}/activate")
    public Result<RetailSale> activate(@PathVariable Long id) { return Result.success(service.activate(id)); }

    @PostMapping("/records")
    public Result<RetailSale> sell(@Valid @RequestBody SalesRequests.SaleCreate request) { return Result.success(service.sell(request)); }

    @GetMapping("/records")
    public Result<PageResult<RetailSale>> records(Long storeId, Integer status, @Valid PageQuery page) {
        return Result.success(service.pageSales(storeId, status, page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/warnings")
    public Result<PageResult<ExpireWarning>> warnings(Integer warningLevel, Integer handled, @Valid PageQuery page) {
        return Result.success(service.pageWarnings(warningLevel, handled, page.getPageNum(), page.getPageSize()));
    }

    @PutMapping("/warnings/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody SalesRequests.WarningHandle request) {
        service.handleWarning(id, request); return Result.success();
    }

    @PostMapping("/recalls")
    public Result<RecallOrder> recall(@Valid @RequestBody SalesRequests.RecallCreate request) { return Result.success(service.createRecall(request)); }

    @GetMapping("/recalls")
    public Result<PageResult<RecallOrder>> recalls(Integer status, @Valid PageQuery page) {
        return Result.success(service.pageRecalls(status, page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/recalls/{id}")
    public Result<RecallOrder> recall(@PathVariable Long id) { return Result.success(service.getRecall(id)); }

    @PutMapping("/recalls/{id}/status")
    public Result<Void> recallStatus(@PathVariable Long id, @Valid @RequestBody SalesRequests.RecallStatus request) {
        service.updateRecall(id, request); return Result.success();
    }

    @GetMapping("/internal/products/qr/{qrCode}")
    public Result<RetailSale> productByQr(@PathVariable String qrCode) {
        RetailSale row = saleMapper.selectOne(Wrappers.<RetailSale>lambdaQuery().eq(RetailSale::getProductQrCode, qrCode));
        if (row == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "二维码对应产品不存在");
        return Result.success(row);
    }

    @GetMapping("/internal/products")
    public Result<List<RetailSale>> productsBySplitIds(@RequestParam List<Long> splitIds) {
        if (splitIds == null || splitIds.isEmpty()) return Result.success(List.of());
        if (splitIds.size() > 500)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多查询500个分割批次");
        return Result.success(saleMapper.selectList(Wrappers.<RetailSale>lambdaQuery()
                .in(RetailSale::getSplitBatchId, splitIds.stream().distinct().toList())
                .orderByAsc(RetailSale::getCreateTime)));
    }
}
