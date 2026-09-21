package com.pork.ingest.service.handler;

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
 * 门店签收通道（PDA 扫码 + 门店冷柜测温）。
 * <p>
 * 签收是冷链责任转移点，所以「签收人」是必填项：设备可以自动读温度、自动读数量，
 * 但不能替人承担签收责任。缺签收人时进待人工处理，绝不用设备号冒充签收人。
 */
@Slf4j
@Component
public class ReceiptChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public ReceiptChannelHandler(BizClient bizClient,
                                 @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.RECEIPT;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "transportNo") == null && Payloads.integer(data, "transportId") == null) {
            errors.add("缺少运输单号(transportNo)，无法定位运单");
        }
        if (Payloads.str(data, "storeName") == null) errors.add("缺少门店名称(storeName)");
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.RECEIPT.targetTable();
        Map<String, Object> data = ctx.data();
        String receiver = Payloads.str(data, "receiver");
        if (receiver == null && !force) {
            return DispatchResult.manual(table, "缺少签收人(receiver)，责任转移必须落具体人，请人工确认后补录");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transportNo", Payloads.str(data, "transportNo"));
        body.put("transportId", Payloads.integer(data, "transportId"));
        body.put("storeId", Payloads.integer(data, "storeId"));
        body.put("storeName", Payloads.str(data, "storeName"));
        body.put("receiver", receiver == null ? ctx.deviceLabel() : receiver);
        body.put("receiverPhone", Payloads.str(data, "receiverPhone"));
        body.put("qtyCheck", Payloads.flag(data, "qtyCheck", 1));
        body.put("qtyDiffNote", Payloads.str(data, "qtyDiffNote"));
        body.put("tempCheck", Payloads.flag(data, "tempCheck", 1));
        body.put("tempValue", Payloads.decimal(data, "tempValue"));
        body.put("packageIntact", Payloads.flag(data, "packageIntact", 1));
        body.put("signature", Payloads.str(data, "signature"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/store-receipt", body);
        return Handlers.toResult(result, table);
    }
}
