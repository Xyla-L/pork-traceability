package com.pork.distribution.controller;

import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.*;
import com.pork.distribution.service.DistributionService;
import com.pork.web.source.VoidRequest;
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

    /**
     * 封批：此后分拣台不再把当日白条并入本批次。
     * 达到头数上限会自动封批；这个入口补的是「换班 / 换圈」这类上限兜不住的中断点。
     */
    @PostMapping("/batches/{id}/seal")
    public Result<Void> sealBatch(@PathVariable Long id) {
        service.sealBatch(id);
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

    /* 人工创建分割批次入口已删除：分割批次由分割线扫码称重台设备通道自动创建
       （/distribution/internal/ingest/split），不再提供人工建批次接口。 */

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

    /** 作废：批次号是下游引用键，不提供删除入口，只允许作废（实名 + 理由必填，作废动作单独上链存证） */
    @PostMapping("/splits/{id}/void")
    public Result<Void> voidSplit(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        service.voidSplit(id, request.getReason());
        return Result.success();
    }

    @GetMapping("/splits/tree/{batchNo}")
    public Result<Map<String, Object>> tree(@PathVariable String batchNo) { return Result.success(service.getSplitTree(batchNo)); }

    /** 分割作业列表：作业 = 一头白条的整份产出清单，部位是它的明细 */
    @GetMapping("/split-jobs")
    public Result<PageResult<SplitJob>> splitJobs(String keyword, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pageSplitJobs(keyword, page.getPageNum(), page.getPageSize())));
    }

    /** 部位字典：设备上报只认这里的部位编码，前端下拉也取自这里 */
    @GetMapping("/meat-parts")
    public Result<List<MeatPart>> meatParts() {
        return Result.success(service.listMeatParts());
    }

    /**
     * 分割车间监控快照：现场照片（默认最近 10 张）。
     * <p>
     * 两种用法：① 不传 {@code batchNo} —— 某台相机最近的若干张（相机的滚动窗口）；
     * ② 传 {@code batchNo} —— <b>这一次分割</b>的现场照片（分割操作页按选中批次看的那一份）。
     * <p>
     * 只读展示：照片不参与任何业务判定，这里不做校验，也不触发写动作。
     */
    @GetMapping("/split-snapshots")
    public Result<List<Map<String, Object>>> splitSnapshots(String deviceNo,
                                                            String batchNo,
                                                            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(service.recentSnapshots(deviceNo, batchNo, limit));
    }

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

    /* 人工补录温度入口已删除：冷链温度由车载/冷库探头设备通道自动采集
       （/distribution/internal/ingest/temperature），不再提供人工补录接口。 */

    @GetMapping("/transports/{id}/temperature")
    public Result<List<TemperatureLog>> temperatures(@PathVariable Long id) { return Result.success(service.temperatureLogs(id)); }

    /**
     * 运单的站点清单（一车多目的地）。
     * <p>
     * 单站直送的运单也返回一条 —— 前端不必为「一车一店」和「一车多店」写两套渲染。
     */
    @GetMapping("/transports/{id}/stops")
    public Result<List<Map<String, Object>>> stops(@PathVariable Long id) { return Result.success(service.transportStops(id)); }

    /**
     * 确认到达：推进本趟车<b>当前第一个未到达的站点</b>（多站车次逐站确认）。
     * <p>
     * 返回本次到达的是哪一站 —— 前端据此提示「已到达第 2 站（XX 门店）」，
     * 而不是笼统地说「已到达」（多站时那句话没有信息量）。
     */
    @PutMapping("/transports/{id}/arrive")
    public Result<Map<String, Object>> arrive(@PathVariable Long id) { return Result.success(service.arrive(id)); }

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

    /* ==================== 发货计划：运单的上游决策单 ==================== */

    /** 新建发货计划：勾选待发运的分割批次 + 目的地门店 + 车次信息；车与司机可留空，发车前再补 */
    @PostMapping("/delivery-plans")
    public Result<DeliveryPlan> createPlan(@Valid @RequestBody DistributionRequests.PlanCreate request) {
        return Result.success(service.createPlan(request));
    }

    @GetMapping("/delivery-plans")
    public Result<PageResult<DeliveryPlan>> plans(Integer status, String keyword,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                  @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pagePlans(status, keyword, startDate, endDate,
                page.getPageNum(), page.getPageSize())));
    }

    /** 计划详情：含明细（这一车要装的批次清单），生成运单前复核用 */
    @GetMapping("/delivery-plans/{id}")
    public Result<Map<String, Object>> plan(@PathVariable Long id) { return Result.success(service.getPlan(id)); }

    @PutMapping("/delivery-plans/{id}")
    public Result<Void> updatePlan(@PathVariable Long id, @Valid @RequestBody DistributionRequests.PlanUpdate request) {
        service.updatePlan(id, request);
        return Result.success();
    }

    /** 取消计划：置状态不删行（计划号不复用，「发过什么计划」要查得到） */
    @PostMapping("/delivery-plans/{id}/cancel")
    public Result<Void> cancelPlan(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) VoidRequest request) {
        service.cancelPlan(id, request == null ? null : request.getReason());
        return Result.success();
    }

    /**
     * 生成运单：目的地/计划时间/车辆司机全部从计划带出，人不再重复录。
     * <p>
     * 请求体可空：不传 splitBatchIds = 把剩余未排车的货物全部发走；
     * 传了子集 = 分车，只发这几批，剩下的留在计划里等下一车（计划转「部分排车」）。
     */
    @PostMapping("/delivery-plans/{id}/generate")
    public Result<ColdChainTransport> generateTransport(@PathVariable Long id,
                                                       @RequestBody(required = false) DistributionRequests.PlanGenerate request) {
        return Result.success(service.generateTransportFromPlan(id,
                request == null ? null : request.splitBatchIds()));
    }

    /** 待发运的分割批次：既没被运单装载过、也没被待发货计划占用，供建计划时勾选 */
    @GetMapping("/splits/pending")
    public Result<PageResult<SplitBatch>> pendingSplits(String keyword, @Valid PageQuery page) {
        return Result.success(PageResult.of(service.pagePendingSplits(keyword, page.getPageNum(), page.getPageSize())));
    }
}
