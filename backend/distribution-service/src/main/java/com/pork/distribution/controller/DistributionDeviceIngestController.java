package com.pork.distribution.controller;

import com.pork.core.result.Result;
import com.pork.distribution.dto.DeviceIngestRequests;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.service.DistributionDeviceIngestService;
import com.pork.distribution.service.DistributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备通道内部写入接口（仅接入层调用，不经过网关）。
 * <p>
 * 回执 {{@code written, id, reason, retryable}} 是接入层判断「入账 / 待人工 / 拒绝」的唯一依据。
 */
@RestController
@RequestMapping("/distribution/internal/ingest")
@RequiredArgsConstructor
public class DistributionDeviceIngestController {

    private final DistributionDeviceIngestService ingestService;
    private final DistributionService distributionService;

    /** 冷链温度采集（车载探头 / 冷库探头） */
    @PostMapping("/temperature")
    public Result<Map<String, Object>> temperature(@Valid @RequestBody DeviceIngestRequests.DeviceTemperature request) {
        return Result.success(ingestService.ingestTemperature(request));
    }

    /** 门店签收（PDA 扫码 + 冷柜测温） */
    @PostMapping("/store-receipt")
    public Result<Map<String, Object>> storeReceipt(@Valid @RequestBody DeviceIngestRequests.DeviceReceipt request) {
        return Result.success(ingestService.ingestReceipt(request));
    }

    /** 胴体批次归集（屠宰线胴体称重分拣台：扫挂钩标签 + 过磅，系统按「屠宰场 + 屠宰日」自动归集） */
    @PostMapping("/batch")
    public Result<Map<String, Object>> batch(@Valid @RequestBody DeviceIngestRequests.DeviceBatch request) {
        return Result.success(ingestService.ingestBatch(request));
    }

    /** 分割批次（分割线扫码称重台，扫白条钩标签自动建批次） */
    @PostMapping("/split")
    public Result<Map<String, Object>> split(@Valid @RequestBody DeviceIngestRequests.DeviceSplit request) {
        return Result.success(ingestService.ingestSplit(request));
    }

    /**
     * 分割车间监控快照（摄像头定时拍照）。
     * <p>
     * 这是唯一一条<b>不产生溯源事实</b>的设备通道：照片只说明「此刻车间是什么样」，
     * 不回答任何业务问题。所以这里没有任何业务校验，落库后按滚动窗口淘汰最老的快照。
     */
    @PostMapping("/split-snapshot")
    public Result<Map<String, Object>> splitSnapshot(@Valid @RequestBody DeviceIngestRequests.DeviceSplitSnapshot request) {
        return Result.success(ingestService.ingestSplitSnapshot(request));
    }

    /**
     * 装车发运（装车口：车牌识别 + 司机实名 + 扫码枪）。
     * <p>
     * 设备只提供「车牌 / 司机 / 装了哪几批」三个现场事实，目的地由系统倒查待发货计划得到 ——
     * 匹配不上转人工，设备无权替人决定货发去哪家店。
     */
    @PostMapping("/load")
    public Result<Map<String, Object>> load(@Valid @RequestBody DeviceIngestRequests.DeviceLoad request) {
        return Result.success(ingestService.ingestLoad(request));
    }

    /**
     * 发车确认（车载终端：司机上车刷卡）。
     * <p>
     * 装车口建完单停在「待发车」，这一步把它推进为「运输中」。与装车口同一套设计：
     * <b>设备只报「我（某车）发车了」，运单号由系统按车牌倒查</b>。
     * 该车牌名下没有待发车运单、或同时有多张（不敢猜是哪一趟）都转人工。
     */
    @PostMapping("/depart")
    public Result<Map<String, Object>> depart(@Valid @RequestBody DeviceIngestRequests.DeviceDepart request) {
        return Result.success(ingestService.ingestDepart(request));
    }

    /**
     * 到达确认（车载终端，司机到站刷卡）。
     * <p>
     * 与发车同一套设计：<b>设备只报「哪台车、司机刷的是哪张卡、什么时刻」，运单按车牌倒查、
     * 站点按顺序推导</b>。报文里<b>没有「卸货门店」</b>（2026-10-11 去掉）：车载终端读不到门店主数据，
     * 「当前第一个还没到达的站」在服务端是唯一确定的，报文既不需要报站、也报不错站。
     * <p>
     * 三条红线一律转人工：该车牌名下没有未结束的运单 / 同时有多张（不敢猜是哪一趟） /
     * 没有司机卡号（或卡换不出人、刷卡人与运单安排的司机不一致）。站点台账为空、
     * 或所有站点都已到达（重复刷卡）同样转人工 —— 共同点是「条件没到位」而不是「数据不成立」。
     */
    @PostMapping("/arrive")
    public Result<Map<String, Object>> arrive(@Valid @RequestBody DeviceIngestRequests.DeviceArrive request) {
        return Result.success(ingestService.ingestArrive(request));
    }

    /**
     * 拒收回运装车扫码（冷链中心装车位上的专用扫码枪）。
     * <p>
     * 与装车口（{@link #load}）互为镜像，但只做核对：回运单由调度按载重生成，
     * 这条通道证明「单上列的拒收货确实装上了这台车」，<b>不建单、不推进状态</b>。
     * <p>
     * 三条分档：扫的不是回运单 / 已签收 / 车牌不是这单排的车 → <b>拒绝</b>（现场重扫即可自愈）；
     * 已发车才补扫 / 没有货物清单 / 扫到不属于本车的货 → <b>转人工</b>（命中的照样落库）。
     */
    @PostMapping("/return-load")
    public Result<Map<String, Object>> returnLoad(@Valid @RequestBody DeviceIngestRequests.DeviceReturnLoad request) {
        return Result.success(ingestService.ingestReturnLoad(request));
    }

    /**
     * 召回到店扫码收货（随车手持扫码枪）：
     * 车牌 → 在途收运运单 → 当前已到达的门店 → 本站召回货，四步唯一才认。
     */
    @PostMapping("/recall-collect")
    public Result<Map<String, Object>> recallCollect(@Valid @RequestBody DeviceIngestRequests.DeviceRecallCollect request) {
        return Result.success(distributionService.ingestRecallCollect(request));
    }

    /**
     * 订单转入（外部 ERP/OMS 的选购事实，走接入层 ORDER 通道）。
     * <p>
     * 与上面几条通道有一处本质差别：那些通道落的都是「已经发生的事实」，
     * <b>这条落的是「待排车的计划」（决策还没做完）</b> —— 订单不等于计划，更不等于运单。
     * 链条：外部订单 → 本接口 → 发货计划(plan_source=ORDER) → 装车口 → 运单。
     * <p>
     * 只收「要什么货、发去哪家店、什么时候要」，不含任何金额字段；同一 order_no 重复推送幂等。
     */
    @PostMapping("/order")
    public Result<Map<String, Object>> order(@Valid @RequestBody DistributionRequests.OrderIntake request) {
        return Result.success(distributionService.createPlanFromOrder(request));
    }
}
