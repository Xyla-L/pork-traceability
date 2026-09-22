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
 * 养殖建档通道：耳标读写器，佩戴耳标即建档。
 * <p>
 * 边界说明：耳标必须由饲养员亲手打到猪耳朵上——设备记录的是「哪头猪、什么耳标、
 * 在哪个场哪个圈」，不能代替人完成佩戴动作，所以建档记录的采集人默认落设备标识。
 * 养殖场必须已登记（按名称反查），否则转人工核对，避免机器给不存在的场建档。
 */
@Slf4j
@Component
public class TagChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;

    public TagChannelHandler(BizClient bizClient,
                             @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.TAG;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "farmName") == null) errors.add("缺少养殖场名称(farmName)，建档必须归属已登记的养殖场");
        Integer gender = Payloads.integer(data, "gender");
        if (gender != null && gender != 1 && gender != 2) {
            errors.add("性别(gender)只允许 1公/2母");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.TAG.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("earTagNo", Payloads.str(data, "earTagNo"));
        body.put("farmName", Payloads.str(data, "farmName"));
        body.put("breed", Payloads.str(data, "breed"));
        body.put("birthDate", Payloads.str(data, "birthDate"));
        body.put("gender", Payloads.integer(data, "gender"));
        body.put("penNo", Payloads.str(data, "penNo"));
        body.put("origin", Payloads.str(data, "origin"));
        body.put("operator", Payloads.str(data, "operator") == null ? ctx.deviceLabel() : Payloads.str(data, "operator"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(breedingUrl + "/breeding/internal/ingest/tag", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，暂时无法建档：" + e.getMessage());
            }
            throw e;
        }
    }
}
