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
 * 屠宰检验工位终端通道。
 * <p>
 * 法规边界：《生猪屠宰管理条例》要求肉品品质检验由检验人员实施、检疫由官方兽医实施——
 * <b>合格与否的判定权永远在人</b>。终端自动化的只是"录入"这一步：兽医在工位做出判定，
 * 终端一键回传。所以本通道有两条硬约束：
 * <ol>
 *   <li>检验结论(result)必须由设备端明确给出——终端没有结论就不入账，绝不默认"合格"；</li>
 *   <li>检验人(veterinary，官方兽医/检验员)必须落具体人——机器不能替兽医签字。</li>
 * </ol>
 */
@Slf4j
@Component
public class InspectionChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;
    private final String slaughterUrl;

    public InspectionChannelHandler(BizClient bizClient,
                                    @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
                                    @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
        this.slaughterUrl = slaughterUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.INSPECTION;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "batchNo") == null) errors.add("缺少批次号(batchNo)");
        Integer result = Payloads.integer(data, "result");
        if (result == null) {
            errors.add("缺少检验结论(result)：终端未给出结论不入账，绝不默认按合格处理");
        } else if (result != 0 && result != 1) {
            errors.add("检验结论(result)只允许 1合格/0不合格");
        }
        if (Payloads.str(data, "veterinary") == null) {
            errors.add("缺少检验人(veterinary)：检验结论必须落具体兽医，机器不得代签");
        }
        Integer inspectType = Payloads.integer(data, "inspectType");
        if (inspectType != null && inspectType != 1 && inspectType != 2 && inspectType != 3) {
            errors.add("检验类型(inspectType)只允许 1宰前/2宰后/3同步");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.INSPECTION.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

        // 耳标必须在档：检验记录要挂到生猪个体上，挂不上就不算有效检验
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
        body.put("inspectType", Payloads.integer(data, "inspectType") == null ? 2 : Payloads.integer(data, "inspectType"));
        body.put("result", Payloads.integer(data, "result"));
        body.put("temperature", Payloads.decimal(data, "temperature"));
        body.put("organCheck", Payloads.str(data, "organCheck"));
        body.put("conclusion", Payloads.str(data, "conclusion"));
        body.put("issueDesc", Payloads.str(data, "issueDesc"));
        body.put("disposal", Payloads.str(data, "disposal"));
        body.put("veterinary", Payloads.str(data, "veterinary"));
        body.put("licenseNo", Payloads.str(data, "licenseNo"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(slaughterUrl + "/slaughter/internal/ingest/inspection", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "屠宰服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
