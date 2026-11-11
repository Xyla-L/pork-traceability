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
import com.pork.sales.vo.ExpireWarningVO;
import com.pork.sales.vo.QrCodeVO;
import com.pork.sales.vo.SaleRecordVO;
import com.pork.web.scope.OrgScope;
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
    private final OrgScope scope;

    /**
     * 本请求能看哪家门店：零售商＝自己门店（来自令牌，不可伪造），其余角色＝null（不限）。
     * <p>
     * 注意 {@link OrgScope#storeOr} 的语义：调用方传 {@code storeId} 时，零售商的那一份会被
     * 直接覆盖为本人门店 —— 传别人的门店号不会变成别人的视角。
     */
    private Long scopedStore(Long requested) {
        return scope.storeOr(requested);
    }

    @PostMapping("/qrcodes/batch")
    public Result<Map<String, List<String>>> generate(@Valid @RequestBody SalesRequests.QrBatch request) {
        return Result.success(Map.of("qrcodes", service.generateQrs(request, scopedStore(null))));
    }

    @GetMapping("/qrcodes")
    public Result<PageResult<QrCodeVO>> qrs(Integer status, String qrCode, String batchNo,
                                            @RequestParam(required = false) Integer current,
                                            @RequestParam(required = false) Integer size,
                                            @Valid PageQuery page) {
        long pageNum = current != null ? current : page.getPageNum();
        long pageSize = size != null ? size : page.getPageSize();
        return Result.success(service.pageQrs(status, qrCode, batchNo, scopedStore(null), pageNum, pageSize));
    }

    @PutMapping("/products/{id}/activate")
    public Result<RetailSale> activate(@PathVariable Long id) {
        return Result.success(service.activate(id, scopedStore(null)));
    }

    @PostMapping("/records")
    public Result<RetailSale> sell(@Valid @RequestBody SalesRequests.SaleCreate request) {
        return Result.success(service.sell(request, scopedStore(null)));
    }

    @GetMapping("/records")
    public Result<PageResult<SaleRecordVO>> records(Long storeId, String status, String productName, String batchNo,
                                                    String storeName, String startDate, String endDate,
                                                    @Valid PageQuery page) {
        return Result.success(service.pageSales(scopedStore(storeId), status, productName, batchNo, storeName,
                startDate, endDate, page.getPageNum(), page.getPageSize()));
    }

    @GetMapping("/warnings")
    public Result<PageResult<ExpireWarningVO>> warnings(Integer warningLevel, Integer handled, Long storeId,
                                                        @Valid PageQuery page) {
        return Result.success(service.pageWarnings(warningLevel, handled, scopedStore(storeId),
                page.getPageNum(), page.getPageSize()));
    }

    @PutMapping("/warnings/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody SalesRequests.WarningHandle request) {
        service.handleWarning(id, request, scopedStore(null)); return Result.success();
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

    /**
     * 发起召回弹窗用：按所选批次算出售到过这批货的门店与受影响件数。
     * 门店不能再由人勾选 —— 货卖到哪几家是既成事实，勾错就等于漏召。
     */
    @GetMapping("/recalls/affected-stores")
    public Result<Map<String, Object>> affectedStores(@RequestParam(required = false) List<Long> batchIds) {
        return Result.success(service.previewRecallScope(batchIds));
    }

    /**
     * 内部：召回收运送到无害化处理中心并签收后，把召回执行状态收口为「已完成」并通知管理员。
     * 由配送服务调用；重复回调幂等。
     */
    @PostMapping("/internal/recalls/{id}/complete")
    public Result<Void> completeRecall(@PathVariable Long id,
                                       @RequestParam(required = false) String transportNo) {
        service.completeRecall(id, transportNo);
        return Result.success();
    }

    @GetMapping("/internal/products/qr/{qrCode}")
    public Result<RetailSale> productByQr(@PathVariable String qrCode) {
        RetailSale row = saleMapper.selectOne(Wrappers.<RetailSale>lambdaQuery().eq(RetailSale::getProductQrCode, qrCode));
        if (row == null) {
            // 有效商品里查不到时，再看是不是「曾经存在但已作废」——扫码场景必须区分这两件事
            RetailSale voided = saleMapper.selectVoidedByQr(qrCode);
            if (voided != null) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND,
                        "该商品已被作废，不再作为有效销售记录。作废理由：" + voided.getVoidReason()
                                + "（操作人 " + voided.getVoidOperator() + "）");
            }
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "二维码对应产品不存在");
        }
        return Result.success(row);
    }

    /**
     * 零售经营统计（供工作台按角色取数，服务间内部接口）。
     * <p>
     * {@code storeId} 为空 = 全部门店汇总；{@code days} 默认 30，上限 90。
     * 返回趋势与部位排行，工作台直接渲染，不再二次加工。
     */
    @GetMapping("/internal/stats/summary")
    public Result<Map<String, Object>> statsSummary(@RequestParam(required = false) Integer days,
                                                    @RequestParam(required = false) Long storeId) {
        // 服务间调用（工作台）不带身份头，storeOr 原样返回传入的门店；
        // 但如果有人从网关带着零售商的令牌直接打这个地址，这里的 scopedStore 就会把
        // storeId 改写成他自己门店 —— 「internal」是命名约定，不是访问控制。
        return Result.success(service.statsSummary(days, scopedStore(storeId)));
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
