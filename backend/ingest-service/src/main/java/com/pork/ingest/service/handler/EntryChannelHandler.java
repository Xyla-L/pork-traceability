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
 * 入场查验通道：门禁地磅（车牌/重量）+ 耳标识读器（耳标）+ 检疫证核验终端 合成一条查验记录。
 * <p>
 * 关键约束：耳标必须在生猪档案里有档、检疫证必须能核到且与耳标一致，否则不准自动入账。
 * 这类情况不丢数据，进「待人工处理」，由门岗核对纸质材料后确认。
 */
@Slf4j
@Component
public class EntryChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;
    private final String slaughterUrl;

    public EntryChannelHandler(BizClient bizClient,
                               @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
                               @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
        this.slaughterUrl = slaughterUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.ENTRY;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "batchNo") == null) errors.add("缺少批次号(batchNo)");
        if (Payloads.decimal(data, "weight") != null && Payloads.decimal(data, "weight").signum() <= 0) {
            errors.add("地磅重量必须大于 0");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.ENTRY.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

        // 1) 耳标反查生猪档案——这是自动入账的前提，查不到就没法生成可信的查验记录
        Map<String, Object> pig;
        try {
            pig = bizClient.get(breedingUrl + "/breeding/internal/pigs/by-ear-tag",
                    Map.of("earTagNo", earTagNo));
        } catch (BusinessException e) {
            // 养殖服务不可用属于临时故障，进待人工处理；耳标无档属于业务事实，提示人工核对
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，暂时无法核验耳标：" + e.getMessage());
            }
            return DispatchResult.manual(table, "耳标在生猪档案中不存在（" + earTagNo + "），请人工核对来源");
        }
        Long pigId = Handlers.longValue(pig.get("id"));
        if (pigId == null) {
            return DispatchResult.manual(table, "耳标查到的生猪档案缺少主键，无法自动入账");
        }

        // 2) 检疫证核验：证要存在、未过期、且证上耳标与闸口扫的一致
        String certNo = Payloads.str(data, "quarantineCert");
        int certVerified = 0;
        if (certNo == null) {
            if (!force) return DispatchResult.manual(table, "未提供产地检疫证号，需人工核验纸质证明后补录");
        } else {
            Map<String, Object> verify = bizClient.get(breedingUrl + "/breeding/internal/quarantine-certs/verify",
                    queryOf("certNo", certNo, "earTagNo", earTagNo));
            boolean valid = Boolean.TRUE.equals(verify.get("valid"));
            certVerified = valid ? 1 : 0;
            if (!valid && !force) {
                return DispatchResult.manual(table, "检疫证核验未通过：" + verify.get("reason"));
            }
        }

        // 3) 落业务表
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pigId", pigId);
        body.put("batchNo", Payloads.str(data, "batchNo"));
        body.put("earTagNo", earTagNo);
        body.put("sourceFarm", pig.get("farmName"));
        body.put("arriveTime", Handlers.time(ctx.reportTime()));
        body.put("weight", Payloads.decimal(data, "weight"));
        body.put("quarantineCert", certNo);
        body.put("vehicleNo", Payloads.str(data, "vehicleNo"));
        body.put("healthCheck", Payloads.flag(data, "healthCheck", 1));
        body.put("certVerified", certVerified);
        body.put("abnormalNote", Payloads.str(data, "abnormalNote"));
        body.put("inspector", Payloads.str(data, "inspector") == null ? ctx.deviceLabel() : Payloads.str(data, "inspector"));
        body.put("remark", Payloads.str(data, "remark"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        Map<String, Object> result = bizClient.post(slaughterUrl + "/slaughter/internal/ingest/entry", body);
        return Handlers.toResult(result, table);
    }

    private Map<String, Object> queryOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(k1, v1);
        query.put(k2, v2);
        return query;
    }
}
