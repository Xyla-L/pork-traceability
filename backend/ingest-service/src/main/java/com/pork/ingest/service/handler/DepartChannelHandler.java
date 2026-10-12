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
 * 发车确认通道（车载终端）。
 * <p>
 * 装车口已经能自动建单，但建完停在 {@code status=1 待发车}，发车还要人去点一下按钮 ——
 * 自动化断在最后一步。这条通道补的就是这一步：司机上车刷卡 → 系统按车牌找到该车名下
 * 「待发车」的运单 → 推进为「运输中」。
 * <p>
 * <b>为什么发车适合设备化</b>：它是<b>物理事实</b>（道闸抬杆 / 地磅出场 / GPS 出电子围栏 /
 * 点火信号都能作证，人反而会忘了点），而且它<b>不产生任何业务判定</b> ——
 * 不像瘦肉精、宰前门槛那种要下结论的环节。所以这里只推进状态 + 记时间戳。
 * <p>
 * <b>车牌怎么来</b>：报文里带了就用报文的；没带则取设备台账里结构化的
 * {@code ingest_device.vehicle_no}（车载终端的 device_no 本身就代表这台车）。
 * 两者都没有 ⇒ 直接拒绝：没有车牌就找不到是哪一趟，猜不得。
 * <p>
 * <b>这里不判司机</b>：卡号换人、与运单上司机的比对，都下沉到
 * {@code DistributionDeviceIngestService.ingestDepart} 一处，避免接入层与业务层
 * 各写一份「谁算司机」的口径。
 */
@Slf4j
@Component
public class DepartChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public DepartChannelHandler(BizClient bizClient,
                                @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.DEPART;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        // 车牌是「哪一趟车」的唯一凭据：没有它就只能靠设备名里的字符串猜是哪台车，那是不可接受的
        if (vehicleOf(ctx) == null) {
            errors.add("缺少车牌号(vehicleNo)：车载终端也没在设备台账里绑定车牌，无法确定是哪一趟车");
        }
        // 司机至少要有一个可解析的凭据（卡号优先，其次姓名）。两样都没有也能走 ——
        // 业务侧会按「车牌 → 常用主驾」反查，但那只是推定，仍旧转人工，所以不算报文缺字段。
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.DEPART.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleNo", vehicleOf(ctx));
        body.put("vehicleSource", Payloads.str(data, "vehicleSource"));
        // 司机三条路：卡号（最可靠）→ 姓名 → 都没有则业务侧按车牌反查（只推定，仍旧转人工）
        body.put("cardNo", Payloads.str(data, "cardNo"));
        body.put("driverName", Payloads.str(data, "driverName"));
        body.put("driverPhone", Payloads.str(data, "driverPhone"));
        body.put("driverSource", Payloads.str(data, "driverSource"));
        // 发车时刻：以设备现场读到的为准（补传时可能是过去的时间），没给则由业务侧取服务器当前时间
        body.put("departTime", Handlers.time(Payloads.time(data, "departTime")));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/depart", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            // 业务侧抛出的业务异常一律转人工：拒绝的语义是「数据本身不成立、不要重推」，
            // 而发车失败大多是「还没有待发车的运单」这种**条件没到位**，外部系统重推是合理的。
            return DispatchResult.manual(table, "发车未完成：" + e.getMessage());
        }
    }

    /** 车牌：报文优先，其次取设备台账里结构化的绑定值（车载终端的 device_no 本身就代表车） */
    private String vehicleOf(IngestContext ctx) {
        String fromReport = Payloads.str(ctx.data(), "vehicleNo");
        if (fromReport != null) return fromReport;
        String bound = ctx.device().getVehicleNo();
        return (bound == null || bound.isBlank()) ? null : bound;
    }
}
