package com.pork.ingest.service.handler;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.ingest.client.BizClient;
import com.pork.ingest.service.ChannelHandler;
import com.pork.ingest.service.IngestContext;
import com.pork.ingest.support.DispatchResult;
import com.pork.ingest.support.IngestChannel;
import com.pork.ingest.support.Payloads;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 装车口通道（1 号装车位上的两件套：车牌识别相机 + 扫码枪）。
 * <p>
 * 这条通道和别的通道有个根本区别：<b>它不产生任何业务判定</b>。
 * 别的通道都是「设备测到什么就记什么」（温度、重量、读数），而运单的核心字段——目的地门店——
 * 是经营决策，设备根本测不出来。所以装车口只提供两个现场事实：哪台车、哪几批货真的上了车，
 * 由配送服务拿这两样去<b>倒查</b>待发货计划：扫到的批次属于哪张计划，就发到那张计划的目的地去。
 * 扫码是「确认」不是「选择」。
 * <p>
 * <b>一个点位、一个通道，但不是一台设备</b>：相机面朝车道、扫码枪在装卸位，
 * 安装位置与故障模式各不相同，所以报文里把「车牌怎么来的」单独标（{@code vehicleSource}）——
 * 相机坏了可以用人工录入兜底，不影响扫码上报。
 * <p>
 * <b>这里没有读卡器</b>（2026-10-10 起）：现场只在上车那一刻刷一次卡，司机身份由车载终端
 * （{@link IngestChannel#DEPART}）确认。装车时司机还没上车，所以报文不带卡号 ——
 * 带了说明设备接错点位了（把驾驶室的读卡器算到装车位），按结构性拒绝挡下，早报错早修。
 * 姓名仍可带（装卸工或调度代填），但那只是「人工录入、非本人确认」。
 * <p>
 * 责任边界：
 * <ul>
 *   <li>目的地不由设备推断 —— 匹配不上（货不属于任何计划、分属多张计划）转人工，绝不猜；</li>
 *   <li>司机允许留空（装车时还不知道谁开），但<b>发车时必须落到具体的人</b> ——
 *       那颗闸门在 {@code depart} 与 DEPART 通道上，不在装车口；</li>
 *   <li>车牌与计划安排的车不一致，由配送服务拒绝并转人工 —— 最可能的解释就是装错车了。</li>
 * </ul>
 * 设备能读到的车牌与批次号是物理事实，缺了直接拒绝（不是设备没扫，是报文没带）。
 * <p>
 * 这里<b>不判司机</b>：卡号对谁、车牌反查谁，都要查配送端的主数据（司机备案 / 车辆备案），
 * 判断下沉到 {@code DistributionDeviceIngestService} 一处，
 * 避免接入层与业务层各写一份「谁算司机」的口径。
 */
@Slf4j
@Component
public class LoadChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public LoadChannelHandler(BizClient bizClient,
                              @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.LOAD;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        // 车牌是「哪台车」的唯一凭据，批次清单是「装了哪些货」的唯一凭据：
        // 这两样都是装车口设备物理上必然读到的，缺了说明报文本身不完整，直接拒绝
        if (Payloads.str(data, "vehicleNo") == null) {
            errors.add("缺少车牌号(vehicleNo)：装车口必须先识别到车牌才能生成运单");
        }
        if (Payloads.strings(data, "batchNos").isEmpty()) {
            errors.add("缺少装车批次号(batchNos)：扫码枪须扫出本车装载的分割批次，系统才能倒查出发货计划");
        }
        // 反过来，装车口物理上**读不到**的东西报上来了，说明设备接错点位了
        //（把驾驶室里的读卡器算到了装车位），同样属于报文不成立：早报错早修。
        if (Payloads.str(data, "cardNo") != null) {
            errors.add("装车口没有读卡器，报文不应携带司机卡号(cardNo)："
                    + "司机身份由车载终端在发车时刷卡确认（发车确认通道）");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.LOAD.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleNo", Payloads.str(data, "vehicleNo"));
        // 车牌是怎么来的：相机自动识别 / 人工录入（相机故障时的兜底），供排查用
        body.put("vehicleSource", Payloads.str(data, "vehicleSource"));
        // 司机在装车口是**可选**的：现场只在上车那一刻刷一次卡，装车时司机还没上车。
        // 姓名（装卸工/调度代填）照传，来源只能是「人工录入、非本人确认」；
        // 卡号不传 —— 上面 validate 已按结构性拒绝挡下（这条通道没有读卡器）。
        body.put("driverName", Payloads.str(data, "driverName"));
        body.put("driverPhone", Payloads.str(data, "driverPhone"));
        body.put("driverSource", Payloads.str(data, "driverSource"));
        body.put("batchNos", Payloads.strings(data, "batchNos"));
        body.put("loadTime", Handlers.time(Payloads.time(data, "loadTime")));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/load", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
