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
 * 分割线扫码称重台通道。
 * <p>
 * 白条上线时扫一下挂钩标签，系统自动创建分割批次——批次号本来就不该人手编，
 * 它是系统逻辑产物。设备侧只提供「挂在哪条胴体批次下、切出来是什么产品、多重」；
 * 胴体批次必须已存在（按批次号反查），反查不到转人工，避免凭空造出溯源断链的批次。
 */
@Slf4j
@Component
public class SplitChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public SplitChannelHandler(BizClient bizClient,
                               @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.SPLIT;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "parentBatchNo") == null) {
            errors.add("缺少胴体批次号(parentBatchNo)：分割批次必须挂在已存在的胴体批次下");
        }
        if (Payloads.str(data, "productName") == null) errors.add("缺少产品名称(productName)");
        if (Payloads.decimal(data, "weightKg") == null) {
            errors.add("缺少重量(weightKg)：称重台必须先出重量再上报");
        } else if (Payloads.decimal(data, "weightKg").signum() <= 0) {
            errors.add("重量(weightKg)必须大于 0");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.SPLIT.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentBatchNo", Payloads.str(data, "parentBatchNo"));
        body.put("productName", Payloads.str(data, "productName"));
        body.put("weightKg", Payloads.decimal(data, "weightKg"));
        body.put("packageCount", Payloads.integer(data, "packageCount"));
        body.put("packageType", Payloads.str(data, "packageType"));
        body.put("workshop", Payloads.str(data, "workshop"));
        body.put("workshopTemp", Payloads.decimal(data, "workshopTemp"));
        body.put("operator", Payloads.str(data, "operator") == null ? ctx.deviceLabel() : Payloads.str(data, "operator"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/split", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
