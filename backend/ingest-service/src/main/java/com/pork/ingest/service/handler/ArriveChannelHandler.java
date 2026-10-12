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
 * 到达确认通道（车载终端，司机到站刷卡）。
 * <p>
 * 发车（{@link DepartChannelHandler}）已经设备化，到达却还要人去点按钮 —— 不对称。
 * 而「到了没」与「发车了没」一样是<b>物理事实</b>：车载终端读到车与地点、地磅过磅、
 * GPS 进了电子围栏都能作证，人反而会忘了点。所以照同一套写法补上这一步。
 * <p>
 * <b>设备只报现场事实</b>：哪台车（{@code vehicleNo}）、司机刷的是哪张卡（{@code cardNo}）、
 * 什么时刻（{@code arriveTime}），运单与站点由配送服务<b>倒查与推导</b>。
 * <p>
 * <b>报文里没有「卸货门店」（2026-10-11 去掉）</b>：车载终端不是门店收货区的固定设备 ——
 * 车上装的是 T-Box、读卡头、显示屏，读得到车牌/卡号/时刻/GPS，<b>读不到门店编码</b>；
 * 门店是 ERP/OMS 的主数据，不会下发到每台车上。这件事本来也不该由设备回答：
 * 车牌已经把运单锁定到唯一一张「未结束」的单子，站点清单由装车扫码顺序决定，
 * 所以「当前第一个未到达的站」在服务端是唯一确定的 —— 报文既不需要报站，也就报不错站。
 * <p>
 * <b>到达必须由司机本人刷卡确认</b>（2026-10-11 起）：卡在司机手上、由车载终端读出
 * （与发车同一台设备），所以「谁把货物送到了这一站」第一次有了责任人。没带卡号一律转人工 ——
 * 车载终端一定读得到卡，「没读到」就是司机没刷，不拿车牌或人名顶替。
 * <p>
 * <b>到达的粒度是「卸货门店」，不是「批次」</b>：一车装 12 批货送 3 家店就是 3 次到达，
 * 同一家店的多批货只到一次。每刷一次卡推进一站，站点顺序在服务端按装车扫码顺序排定。
 * <p>
 * <b>这里不判站点、也不判司机</b>：倒查运单、卡号换人、司机比对、站点推导、
 * 按站点汇总回写运单状态，全部下沉到 {@code DistributionDeviceIngestService.ingestArrive} 一处，
 * 避免接入层与业务层各写一份「哪一站算到了」的口径。
 */
@Slf4j
@Component
public class ArriveChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public ArriveChannelHandler(BizClient bizClient,
                               @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.ARRIVE;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        // 车牌是「哪一趟车」的唯一凭据：没有它就只能靠设备名里的字符串猜是哪台车，那是不可接受的
        if (vehicleOf(ctx) == null) {
            errors.add("缺少车牌号(vehicleNo)：没有车牌就无法按车牌倒查是哪一趟车到了");
        }
        // 不再要求报「卸货门店」：车载终端读不到门店主数据，而「到了第几站」由服务端按
        // 车牌 + 站点顺序推导（见类注释）。这里只校验设备真的能读到的物理量。
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.ARRIVE.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleNo", vehicleOf(ctx));
        body.put("vehicleSource", Payloads.str(data, "vehicleSource"));
        // 司机到站刷的卡号：到达必须由本人刷卡确认，卡号换人与比对下沉到业务侧
        //（接入层只做透传，不判司机 —— 与 DEPART / LOAD 同一条纪律）
        body.put("cardNo", Payloads.str(data, "cardNo"));
        // 不传门店：设备读不到门店主数据，「到了第几站」由业务侧按站点顺序推导
        // 到达时刻：以设备现场读到的为准（补传时可能是过去的时间），没给则由业务侧取服务器当前时间
        body.put("arriveTime", Handlers.time(Payloads.time(data, "arriveTime")));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/arrive", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            // 业务侧抛出的业务异常一律转人工：拒绝的语义是「数据本身不成立、不要重推」，
            // 而到达失败大多是「运单还没发车 / 站点都到齐了」这种**条件没到位**，外部系统重推是合理的。
            return DispatchResult.manual(table, "到达确认未完成：" + e.getMessage());
        }
    }

    /** 车牌：报文优先，其次取设备台账里结构化的绑定值（车载终端可以绑在某一台车上） */
    private String vehicleOf(IngestContext ctx) {
        String fromReport = Payloads.str(ctx.data(), "vehicleNo");
        if (fromReport != null) return fromReport;
        String bound = ctx.device().getVehicleNo();
        return (bound == null || bound.isBlank()) ? null : bound;
    }
}
