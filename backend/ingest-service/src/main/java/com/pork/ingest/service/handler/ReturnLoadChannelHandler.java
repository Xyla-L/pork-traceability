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
 * 拒收回运装车扫码通道（冷链中心装车位上的专用扫码枪）。
 * <p>
 * 与装车口（{@link LoadChannelHandler}）互为镜像：都是「车牌 + 货码 → 倒查业务对象」。
 * 区别只在倒查的目标 —— 装车口倒查待发货计划，这里倒查「已拒收、还没排上回运车」的门店站点，
 * 登记「这批拒收货上了这台车」这个现场事实。它<b>不建单、不推进状态</b>：
 * 处理运单由调度按扫码结果生成，发车由车载终端刷卡（{@link IngestChannel#DEPART}）推进。
 * <p>
 * <b>为什么单独一把枪</b>见 {@link IngestChannel#RETURN_LOAD}：装车口那把枪的两条判定
 * （批次未曾发运 / 扫批次倒查计划）与回运货天生冲突，混用只能靠格式猜场景，而分错的代价是
 * 废品混进正常运单。通道隔离比事后判断可靠。
 * <p>
 * <b>这里没有读卡器</b>：与装车口同一条纪律（2026-10-10）—— 现场只在上车那一刻刷一次卡，
 * 司机身份由车载终端确认。报文带 {@code cardNo} 说明设备接错点位，按结构性拒绝挡下。
 * <p>
 * <b>验证只查设备物理上必然读得到的量</b>：车牌、扫到的货码清单。
 * 判断（这批货属于哪家门店的拒收、能不能装、上没上过别的车）全部下沉到
 * {@code DistributionDeviceIngestService.ingestReturnLoad} 一处，接入层只做透传。
 */
@Slf4j
@Component
public class ReturnLoadChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public ReturnLoadChannelHandler(BizClient bizClient,
                                    @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.RETURN_LOAD;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        // 车牌是「这批货上了哪台车」的唯一凭据。2026-11-03 起扫码前移到排车之前：
        // 那时还没有回运单号，能读到、也必须读到的就是车牌与货码（与装车口同一条口径）
        if (Payloads.str(data, "vehicleNo") == null) {
            errors.add("缺少车牌号(vehicleNo)：装车位必须先识别到车牌，才能把货挂到具体车上");
        }
        // 一件货码都没扫到 = 报文不成立（不是「漏扫几件」，是这一枪什么也没读出来）
        if (Payloads.strings(data, "scanCodes").isEmpty()) {
            errors.add("缺少货物码(scanCodes)：须扫出本次装车的拒收货，至少一件");
        }
        // 装车位没有读卡器：报了卡号说明设备接错点位，不解析、不入账
        if (Payloads.str(data, "cardNo") != null) {
            errors.add("装车位没有读卡器，报文不应携带司机卡号(cardNo)："
                    + "司机身份由车载终端在发车时刷卡确认（发车确认通道）");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.RETURN_LOAD.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("legTransportNo", Payloads.str(data, "legTransportNo"));
        // 车牌可选：有相机就读得到，用来核对「扫的这单是不是排给这台车」；读不到也不影响核对清单
        body.put("vehicleNo", Payloads.str(data, "vehicleNo"));
        body.put("vehicleSource", Payloads.str(data, "vehicleSource"));
        body.put("scanCodes", Payloads.strings(data, "scanCodes"));
        body.put("scanTime", Handlers.time(Payloads.time(data, "scanTime")));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());
        body.put("orgId", ctx.device().getOrgId());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/return-load", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
