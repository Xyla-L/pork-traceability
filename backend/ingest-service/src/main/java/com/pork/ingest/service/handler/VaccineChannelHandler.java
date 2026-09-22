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
 * 免疫注射通道：智能连续注射器。
 * <p>
 * 注射器自动记录「哪头猪、什么疫苗、什么批号、多少剂量、几点打的」；
 * 兽医批号（疫苗批号）必须由扫码或人工提供——没有批号的免疫记录无法溯源疫苗来源，
 * 一律拒绝，不允许设备默认填"未知批号"。
 */
@Slf4j
@Component
public class VaccineChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;

    public VaccineChannelHandler(BizClient bizClient,
                                 @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.VACCINE;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "vaccineName") == null) errors.add("缺少疫苗名称(vaccineName)");
        if (Payloads.str(data, "vaccineBatchNo") == null) {
            errors.add("缺少疫苗批号(vaccineBatchNo)，无批号的免疫记录无法溯源疫苗来源，禁止入库");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.VACCINE.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

        // 耳标必须在档：智能注射器读不到档内耳标，说明耳标造假或档案缺失，交人工核对
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
        body.put("vaccineName", Payloads.str(data, "vaccineName"));
        body.put("vaccineBatchNo", Payloads.str(data, "vaccineBatchNo"));
        body.put("manufacturer", Payloads.str(data, "manufacturer"));
        body.put("injectTime", Payloads.time(data, "injectTime") == null ? Handlers.time(ctx.reportTime()) : Handlers.time(Payloads.time(data, "injectTime")));
        body.put("dosage", Payloads.str(data, "dosage"));
        body.put("injectSite", Payloads.str(data, "injectSite"));
        body.put("operator", Payloads.str(data, "operator") == null ? ctx.deviceLabel() : Payloads.str(data, "operator"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(breedingUrl + "/breeding/internal/ingest/vaccine", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
