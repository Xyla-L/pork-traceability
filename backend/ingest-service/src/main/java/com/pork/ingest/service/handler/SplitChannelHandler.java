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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分割线扫码称重台通道。
 * <p>
 * 白条上线时扫一下挂钩标签，<b>一次上报一整头猪的产出清单</b>——分割是固定部位切割
 * （二分体 → 六分体 → 一号到五号肉等标准部位），一头猪本来就会切出多个部位，
 * 逐条报送只会把它拆成一堆互不相干的散件，既看不出「这头猪切了什么」，
 * 也没法做出肉率对账。
 * <p>
 * 设备侧的职责边界：只提供「挂在哪条胴体批次下、切出什么部位、多重、谁切的」。
 * <b>作业号与部位编码都不由设备编造</b>——作业号由配送服务生成（SV + 当日流水），
 * 部位必须落在 {@code meat_part} 字典里（只报名称时按字典反查），反查不到转人工。
 * 胴体批次必须已存在（按批次号反查），反查不到转人工，避免凭空造出溯源断链的作业。
 * <p>
 * 兼容：报文里没有 {@code items} 时按单条处理（读顶层的 partCode/productName/weightKg），
 * 老设备与演示表单无需改造。
 */
@Slf4j
@Component
public class SplitChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;
    private final String breedingUrl;

    public SplitChannelHandler(BizClient bizClient,
                               @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl,
                               @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
        this.breedingUrl = breedingUrl;
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
            errors.add("缺少胴体批次号(parentBatchNo)：分割作业必须挂在已存在的胴体批次下");
        }

        List<Map<String, Object>> items = readItems(data);
        if (items.isEmpty()) {
            // 单条兼容模式
            if (Payloads.str(data, "partCode") == null && Payloads.str(data, "productName") == null) {
                errors.add("缺少部位（partCode 或 productName）：称重台须说明切出的是哪个部位");
            }
            BigDecimal weight = Payloads.decimal(data, "weightKg");
            if (weight == null) {
                errors.add("缺少重量(weightKg)：称重台必须先出重量再上报");
            } else if (weight.signum() <= 0) {
                errors.add("重量(weightKg)必须大于 0");
            }
            return errors;
        }

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            int no = i + 1;
            if (Payloads.str(item, "partCode") == null && Payloads.str(item, "partName") == null) {
                errors.add("第 " + no + " 个部位缺少部位编码(partCode)或名称(partName)");
            }
            BigDecimal weight = Payloads.decimal(item, "weightKg");
            if (weight == null) {
                errors.add("第 " + no + " 个部位缺少重量(weightKg)");
            } else if (weight.signum() <= 0) {
                errors.add("第 " + no + " 个部位重量(weightKg)必须大于 0");
            }
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.SPLIT.targetTable();
        Map<String, Object> data = ctx.data();

        // 分割操作员是现场操作分割线终端的人，终端只是工具，缺人转人工（追溯要能查到人）
        String operator = Payloads.str(data, "operator");
        if (operator == null && !force) {
            return DispatchResult.manual(table, "缺少操作员（operator），分割作业必须记录现场操作人员，请补录后入账");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentBatchNo", Payloads.str(data, "parentBatchNo"));
        body.put("operator", operator == null ? "未记录" : operator);
        body.put("workshop", Payloads.str(data, "workshop"));
        body.put("lineNo", Payloads.str(data, "lineNo"));
        body.put("workshopTemp", Payloads.decimal(data, "workshopTemp"));

        List<Map<String, Object>> items = readItems(data);
        if (items.isEmpty()) {
            body.put("partCode", Payloads.str(data, "partCode"));
            body.put("productName", Payloads.str(data, "productName"));
            body.put("weightKg", Payloads.decimal(data, "weightKg"));
            body.put("packageCount", Payloads.integer(data, "packageCount"));
            body.put("packageType", Payloads.str(data, "packageType"));
        } else {
            body.put("items", items);
        }

        // 耳标可选：带了就把耳标换成生猪主键，业务侧才能按这头白条的实际重量做出肉率对账；
        // 不带则退到「胴体批次单头均重」，仍然可对账，只是基准粗一些。
        String earTagNo = Payloads.str(data, "earTagNo");
        if (earTagNo != null) {
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
                return DispatchResult.manual(table, "耳标查到的生猪档案缺少主键，无法自动对账");
            }
            body.put("pigId", pigId);
            body.put("earTagNo", earTagNo);
        }

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

    /**
     * 读整作业产出清单。只白名单搬运字典关心的字段——原始报文里可能有别的键，
     * 透传出去会让下游 DTO 绑定的语义变模糊。
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readItems(Map<String, Object> data) {
        Object raw = data.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> source = (Map<String, Object>) element;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("partCode", Payloads.str(source, "partCode"));
            item.put("partName", Payloads.str(source, "partName"));
            item.put("weightKg", Payloads.decimal(source, "weightKg"));
            item.put("packageCount", Payloads.integer(source, "packageCount"));
            item.put("packageType", Payloads.str(source, "packageType"));
            items.add(item);
        }
        return items;
    }
}
