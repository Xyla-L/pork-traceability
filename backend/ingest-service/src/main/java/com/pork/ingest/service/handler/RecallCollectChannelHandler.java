package com.pork.ingest.service.handler;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.ingest.client.BizClient;
import com.pork.ingest.service.ChannelHandler;
import com.pork.ingest.service.IngestContext;
import com.pork.ingest.support.DispatchResult;
import com.pork.ingest.support.IngestChannel;
import com.pork.ingest.support.Payloads;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 召回到店收货通道（随车手持扫码枪）。
 * <p>
 * 现场动作：司机把收运车停到门店，先在车载终端刷卡确认到店（{@link IngestChannel#ARRIVE}），
 * 再拿这把扫码枪扫门店里的召回货；扫完这一站才能往下一家走。
 * <p>
 * 报文只报<b>车牌 + 货码</b>，和回运装车扫码（{@link ReturnLoadChannelHandler}）同构：
 * <ul>
 *   <li><b>不报门店</b>：门店主数据不会下发到车上，报了也只是司机手敲，反而可能敲错；
 *       「现在在哪家店」由业务侧按运单站点顺序推导（当前第一个已到达、未收完的站）。</li>
 *   <li><b>不带卡号</b>：这把枪没有读卡器，司机身份由车载终端在发车/到店时刷卡确认；
 *       带了说明设备接错点位，按结构性拒绝挡下（与装车口同一条纪律）。</li>
 * </ul>
 * <p>
 * 判定与落库全在 {@code DistributionServiceImpl.ingestRecallCollect} 一处：
 * 接入层只做校验、透传、留档，不判断「这一枪算不算收货」。
 */
@Component
public class RecallCollectChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public RecallCollectChannelHandler(BizClient bizClient,
                                       @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.RECALL_COLLECT;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "vehicleNo") == null) {
            errors.add("缺少车牌号(vehicleNo)：门店收货必须先识别到是哪台收运车，才能确定收的是哪一趟");
        }
        if (Payloads.strings(data, "scanCodes").isEmpty()) {
            errors.add("缺少货物码(scanCodes)：须扫出门店里的召回货批次标签，至少一件");
        }
        if (Payloads.str(data, "cardNo") != null) {
            errors.add("收货扫码枪没有读卡器，报文不应携带司机卡号(cardNo)："
                    + "司机身份由车载终端在发车/到店时刷卡确认");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.RECALL_COLLECT.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleNo", Payloads.str(data, "vehicleNo"));
        body.put("vehicleSource", Payloads.str(data, "vehicleSource"));
        body.put("scanCodes", Payloads.strings(data, "scanCodes"));
        body.put("scanTime", Handlers.time(Payloads.time(data, "scanTime")));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());
        body.put("orgId", ctx.device().getOrgId());

        try {
            Map<String, Object> result = bizClient.post(
                    distributionUrl + "/distribution/internal/ingest/recall-collect", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            // 收货失败大多是「车还没到店 / 上一站没收完 / 货码对不上本店」这类条件没到位，
            // 现场重扫或补扫即可，转人工比拒绝更合适（拒绝的语义是「别重推」）
            return DispatchResult.manual(table, "到店收货未完成：" + e.getMessage());
        }
    }
}
