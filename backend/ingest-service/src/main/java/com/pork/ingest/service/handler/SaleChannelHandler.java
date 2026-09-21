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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门店收银 POS 通道：扫码即激活 + 售出。
 * <p>
 * 幂等是这条通道的命门——收银台重复扫码、POS 断网补传都会造成同一条码多次上报。
 * 业务侧用「唯一码 + 状态条件更新」兜底，重复扫只会有一条销售记录。
 */
@Slf4j
@Component
public class SaleChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String salesUrl;

    public SaleChannelHandler(BizClient bizClient,
                              @Value("${services.sales-url:http://localhost:8085}") String salesUrl) {
        this.bizClient = bizClient;
        this.salesUrl = salesUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.SALE;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "qrCode") == null) {
            errors.add("缺少产品二维码(qrCode)");
        }
        BigDecimal price = Payloads.decimal(data, "sellPrice");
        if (price != null && price.signum() < 0) errors.add("销售价格不能为负数");
        BigDecimal weight = Payloads.decimal(data, "sellWeightKg");
        if (weight != null && weight.signum() <= 0) errors.add("销售重量必须大于 0");
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        Map<String, Object> data = ctx.data();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("qrCode", Payloads.str(data, "qrCode"));
        body.put("sellPrice", Payloads.decimal(data, "sellPrice"));
        body.put("sellWeightKg", Payloads.decimal(data, "sellWeightKg"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        Map<String, Object> result = bizClient.post(salesUrl + "/sales/internal/ingest/retail-sale", body);
        return Handlers.toResult(result, IngestChannel.SALE.targetTable());
    }
}
