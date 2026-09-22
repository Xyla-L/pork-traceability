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
 * 胴体自动盖章机通道。
 * <p>
 * 法规边界：盖章的<b>授权</b>是官方兽医的法定行为，盖章机只是执行机构——
 * 触发条件（检验合格）由业务服务校验：该猪必须已有一条结论为"合格"的屠宰检验记录，
 * 否则即使设备上报了也不准打章。这个校验在业务侧是硬性的，人工确认（force）也绕不过。
 */
@Slf4j
@Component
public class StampChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;
    private final String slaughterUrl;

    public StampChannelHandler(BizClient bizClient,
                               @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
                               @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
        this.slaughterUrl = slaughterUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.STAMP;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "batchNo") == null) errors.add("缺少批次号(batchNo)");
        if (Payloads.str(data, "carcassNo") == null) errors.add("缺少胴体编号(carcassNo)");
        if (Payloads.str(data, "veterinary") == null) {
            errors.add("缺少授权兽医(veterinary)：盖章授权必须落具体人，机器不得代签");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.STAMP.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

        Map<String, Object> pig;
        try {
            pig = bizClient.get(breedingUrl + "/breeding/internal/pigs/by-ear-tag", Map.of("earTagNo", earTagNo));
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，暂时无法核验耳标：" + e.getMessage());
            }
            return DispatchResult.manual(table, "耳标在生猪档案中不存在（" + earTagNo + "），请人工核对");
        }
        Long pigId = Handlers.longValue(pig.get("id"));
        if (pigId == null) {
            return DispatchResult.manual(table, "耳标查到的生猪档案缺少主键，无法自动入账");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pigId", pigId);
        body.put("earTagNo", earTagNo);
        body.put("batchNo", Payloads.str(data, "batchNo"));
        body.put("carcassNo", Payloads.str(data, "carcassNo"));
        body.put("stampType", Payloads.str(data, "stampType"));
        body.put("stampPosition", Payloads.str(data, "stampPosition"));
        body.put("veterinary", Payloads.str(data, "veterinary"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(slaughterUrl + "/slaughter/internal/ingest/stamp", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "屠宰服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
