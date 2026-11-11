package com.pork.distribution.controller;

import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.*;
import com.pork.distribution.service.DistributionService;
import com.pork.distribution.service.TemperatureAutoSampler;
import com.pork.web.scope.OrgScope;
import com.pork.web.source.VoidRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/distribution")
@RequiredArgsConstructor
public class DistributionController {
    private final DistributionService service;
    private final OrgScope scope;
    private final TemperatureAutoSampler temperatureAutoSampler;

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
    public Result<PageResult<ColdChainTransport>> transports(Integer status, String keyword,
                                                             @RequestParam(required = false) Boolean rejected,
                                                             @RequestParam(required = false) String kind,
                                                             @Valid PageQuery page) {
        // 零售商的「待签收」只该出现自己门店的站点所在的运单：一车多店时，
        // 别家的那趟车跟他没关系，列出来只会让人以为货已经到门口了。
        // rejected=1 时只返回「有本店已拒收站点」的运单（门店「拒收/处置」页签）。
        // kind=RETURN/DISPOSAL 取回/回运段落单；缺省只看正常配送，老页面口径不变。
        return Result.success(PageResult.of(service.pageTransports(status, keyword, scope.storeId(), rejected,
                kind, page.getPageNum(), page.getPageSize())));
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
     * 立即执行一次温度定时采集（演示与回归用；正常由 {@code TemperatureAutoSampler} 定时触发）。
     * <p>
     * 刻意<b>不</b>做成「人工补一条读数」——温度是设备环节，人手填的读数进不了台账。
     * 这里触发的是同一个采集器，写进去的仍是「模拟探头」来源的数据。
     */
    @PostMapping("/internal/temperature/sample")
    public Result<Map<String, Object>> sampleTemperature(
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        return Result.success(temperatureAutoSampler.sampleAll(force));
    }

    /**
     * 运单的站点清单（一车多目的地）。
     * <p>
     * 单站直送的运单也返回一条 —— 前端不必为「一车一店」和「一车多店」写两套渲染。
     */
    @GetMapping("/transports/{id}/stops")
    public Result<List<Map<String, Object>>> stops(@PathVariable Long id) {
        List<Map<String, Object>> stops = service.transportStops(id);
        Long storeId = scope.storeId();
        // 零售商只看得见自己那一站：别的站点的门店名、到达时刻、签收人都不是他的数据
        if (storeId == null) return Result.success(stops);
        return Result.success(stops.stream().filter(s -> storeId.equals(s.get("storeId"))).toList());
    }

    /**
     * 确认到达：推进本趟车<b>当前第一个未到达的站点</b>（多站车次逐站确认）。
     * <p>
     * 返回本次到达的是哪一站 —— 前端据此提示「已到达第 2 站（XX 门店）」，
     * 而不是笼统地说「已到达」（多站时那句话没有信息量）。
     */
    @PutMapping("/transports/{id}/arrive")
    public Result<Map<String, Object>> arrive(@PathVariable Long id) { return Result.success(service.arrive(id)); }

    /* ==================== 门店拒收：冷链异常货物的正式出口 ==================== */

    /**
     * 门店拒收：本店站点转入「已拒收」终态（理由必填、实名、上链）。
     * <p>
     * 零售商只能拒本店那一站（令牌门店边界覆写请求体）；单店拒收不影响其他店签收。
     */
    @PostMapping("/transports/{id}/stops/reject")
    public Result<Map<String, Object>> rejectStop(@PathVariable Long id,
                                                  @Valid @RequestBody DistributionRequests.StopReject request) {
        return Result.success(service.rejectStop(id, request, scope.storeId()));
    }

    /** 处置登记已收敛（2026-11-02）：三选一删除，拒收唯一出口是运回；保留端点给旧客户端明确报错 */
    @PostMapping("/transports/{id}/stops/disposal")
    public Result<Map<String, Object>> registerDisposal(@PathVariable Long id,
                                                        @Valid @RequestBody DistributionRequests.StopDisposal request) {
        return Result.success(service.registerDisposal(id, request, scope.storeId()));
    }

    // ==================== 拒收回运链（2026-11-02）====================

    /** 拒收货链条池：stage=DISPOSAL 待回运池（拒收即入池；RETURN 段已取消，传了会明确报错） */
    @GetMapping("/return-pool")
    public Result<List<Map<String, Object>>> returnPool(@RequestParam(defaultValue = "DISPOSAL") String stage) {
        return Result.success(service.returnPool(stage));
    }

    /** 取回计划：2026-11-03 起取消（门店 → 冷链中心不再排车），调用返回明确说明 */
    @PostMapping("/return-plan/generate")
    public Result<List<Map<String, Object>>> generateReturnLegs(@RequestBody(required = false) DistributionRequests.LegPlanGenerate request) {
        return Result.success(service.generateReturnLegs(request));
    }

    /** 冷链中心一键生成回运计划（拒收回运）：待回运拒收货 → 无害化处理中心 */
    @PostMapping("/disposal-plan/generate")
    public Result<List<Map<String, Object>>> generateDisposalLegs(@RequestBody(required = false) DistributionRequests.LegPlanGenerate request) {
        return Result.success(service.generateDisposalLegs(request));
    }

    /** 生成收运运单（召回收运）：门店的召回货 → 冷链中心逐店上门收 → 无害化处理中心 */
    @PostMapping("/recall-transports")
    public Result<Map<String, Object>> generateRecallTransport(@Valid @RequestBody DistributionRequests.RecallTransportGenerate request) {
        return Result.success(service.generateRecallTransport(request));
    }

    /** 待收运池（召回）：还没排上收运运单的召回货，一件（店 × 批次）一行 */
    @GetMapping("/recall-pool")
    public Result<List<Map<String, Object>>> recallCollectPool() {
        return Result.success(service.recallCollectPool());
    }

    /** 收运车辆候选：启用中的备案车，页面选车不给空白手填 */
    @GetMapping("/vehicles")
    public Result<List<Map<String, Object>>> vehicles() {
        return Result.success(service.vehicleFleet());
    }

    /** 收运运单的装载明细：哪家店的哪批召回货、收没收、什么时候收的 */
    @GetMapping("/recall-transports/{id}/items")
    public Result<List<Map<String, Object>>> recallItems(@PathVariable Long id) {
        return Result.success(service.recallItems(id));
    }

    /** 取回入库：2026-11-03 起取消（拒收即在冷链中心，无需入库确认），调用返回明确说明 */
    @PostMapping("/transports/{id}/return-intake")
    public Result<Map<String, Object>> returnIntake(@PathVariable Long id) {
        return Result.success(service.returnIntake(id));
    }

    /** 无害化处理中心确认签收：回运段（拒收回运）终点（只有确认没有拒收；签收即链条终结） */
    @PostMapping("/transports/{id}/disposal-confirm")
    public Result<Map<String, Object>> disposalConfirm(@PathVariable Long id) {
        return Result.success(service.disposalConfirm(id));
    }

    /** 回运单装载明细：装了哪几家门店的拒收货，以及各自「为什么拒」 */
    @GetMapping("/transports/{id}/leg-items")
    public Result<List<Map<String, Object>>> legItems(@PathVariable Long id) {
        return Result.success(service.legItems(id));
    }

    /** 拒收改签收：协商成功后按正常签收录入（带凭证、上链，站点标记「曾拒收后改签」） */
    @PostMapping("/transports/{id}/stops/convert-receipt")
    public Result<StoreReceipt> convertRejectToReceipt(@PathVariable Long id,
                                                       @Valid @RequestBody DistributionRequests.ReceiptCreate request) {
        return Result.success(service.convertRejectToReceipt(id, request, scope.storeId()));
    }

    @PostMapping("/receipts")
    public Result<StoreReceipt> receipt(@Valid @RequestBody DistributionRequests.ReceiptCreate request) {
        // 签收是「我这家店的货到了」，门店必须是自己那家：先校验、再落库，不能先写后查
        return Result.success(service.createReceipt(request, scope.storeId()));
    }

    /**
     * 批量签收：一次勾选多张待签收运单，按统一核验结果逐单签收。
     * <p>
     * 返回逐单结果而不是一句「成功」—— 批量操作必须能说清哪几单没签、为什么，
     * 否则操作员只能一张张点回去复核。
     */
    @PostMapping("/receipts/batch")
    public Result<Map<String, Object>> batchReceipt(@Valid @RequestBody DistributionRequests.ReceiptBatch request) {
        List<Map<String, Object>> items = service.batchReceipts(request, scope.storeId());
        long ok = items.stream().filter(i -> Boolean.TRUE.equals(i.get("success"))).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", items.size());
        body.put("successCount", ok);
        body.put("failedCount", items.size() - ok);
        body.put("items", items);
        return Result.success(body);
    }

    @GetMapping("/receipts/{id}")
    public Result<StoreReceipt> receiptDetail(@PathVariable Long id) {
        StoreReceipt receipt = service.getReceipt(id);
        scope.requireStore(receipt.getStoreId(), "该签收单");
        return Result.success(receipt);
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
        // 零售商的门店边界：传别的门店号会被改写为自己门店
        return Result.success(PageResult.of(service.pageReceipts(scope.storeOr(storeId), storeName,
                startDate, endDate, page.getPageNum(), page.getPageSize())));
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
