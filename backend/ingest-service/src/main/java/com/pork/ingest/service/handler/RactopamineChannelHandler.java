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
 * 瘦肉精快速检测通道（胶体金读数仪串口取数）。
 * <p>
 * 最容易出的漏洞是「仪器只推了耳标、没推结果」，如果这时默认写一条"阴性"，
 * 等于机器替兽医签了合格结论。所以这里把「结果缺失」列为结构性错误，直接拒绝。
 */
@Slf4j
@Component
public class RactopamineChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;
    private final String slaughterUrl;

    public RactopamineChannelHandler(BizClient bizClient,
                                     @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
                                     @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
        this.slaughterUrl = slaughterUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.RACTOPAMINE;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "sampleNo") == null) errors.add("缺少样本编号(sampleNo)");
        // 批次是检测记录的归属维度：缺了它这条记录没法挂到任何一批猪上，
        // 只能在这里挡住，否则会在写库时炸成"系统内部异常"，设备端拿到 500 只会盲目重推。
        if (Payloads.str(data, "batchNo") == null) errors.add("缺少批次号(batchNo)，检测记录无法归属批次");
        if (parseResult(data) == null) {
            errors.add("仪器未输出检测结果(result)，禁止入库——不得默认按阴性处理");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.RACTOPAMINE.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");
        Integer result = parseResult(data);
        if (result == null) {
            return DispatchResult.rejected(table, "仪器未输出检测结果(result)，禁止入库");
        }

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
        body.put("sampleNo", Payloads.str(data, "sampleNo"));
        body.put("testType", Payloads.str(data, "testType") == null ? "瘦肉精快速检测" : Payloads.str(data, "testType"));
        body.put("testTime", Handlers.time(Payloads.time(data, "testTime") == null ? ctx.reportTime() : Payloads.time(data, "testTime")));
        body.put("testMethod", Payloads.str(data, "testMethod") == null ? "胶体金免疫层析法" : Payloads.str(data, "testMethod"));
        body.put("testTarget", Payloads.str(data, "testTarget"));
        body.put("samplePart", Payloads.str(data, "samplePart"));
        body.put("result", result);
        body.put("detectionLimit", Payloads.str(data, "detectionLimit"));
        body.put("reportUrl", Payloads.str(data, "reportUrl"));
        body.put("remark", Payloads.str(data, "remark"));
        body.put("operator", Payloads.str(data, "operator") == null ? ctx.deviceLabel() : Payloads.str(data, "operator"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        Map<String, Object> response = bizClient.post(slaughterUrl + "/slaughter/internal/ingest/ractopamine", body);
        return Handlers.toResult(response, table);
    }

    /** 1=阴性 0=阳性；"待检测"/空 一律视为未出结果 */
    private Integer parseResult(Map<String, Object> data) {
        Object raw = data.get("result");
        if (raw == null) return null;
        if (raw instanceof Boolean bool) return bool ? 1 : 0;
        if (raw instanceof Number number) {
            int value = number.intValue();
            return value == 1 ? 1 : value == 0 ? 0 : null;
        }
        return switch (raw.toString().trim()) {
            case "1", "阴性", "合格" -> 1;
            case "0", "阳性", "不合格" -> 0;
            default -> null;
        };
    }
}
