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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖系统同步通道：外部 ERP / 猪场生产管理系统推送批次档案。
 * <p>
 * <b>与设备通道的分工</b>：设备报的是「这一刻量到了什么」（耳标、温度、重量），
 * 外部 ERP 报的是「我们场里本来是怎么记的」（批次、品种、耳标区间、进场与出生日期）。
 * 后者是<b>人的知识被系统化了</b>，所以这条通道能写档案本体，而耳标读写器只能触发建档。
 * <p>
 * 报文两种动作：
 * <ul>
 *   <li>{@code UPSERT}（默认）—— 新建或更新批次，按 {@code external_system + externalId} 幂等；</li>
 *   <li>{@code REVOKE} —— 源系统撤批（录错、合并、退养），要求写撤销理由。</li>
 * </ul>
 * 结构性错误（动作不认识、场名/批次号缺失、既没区间也没明细、撤销无理由）直接拒绝，
 * 不进人工队列 —— 这类报文重发一百次也还是错的，得让外部系统改。业务性冲突
 * （场名在本系统不存在、批次号与人工作废/人工登记撞车、耳标已被别批占用）
 * 由养殖服务回 {@code retryable=true}，进待人工处理。
 */
@Slf4j
@Component
public class FarmErpChannelHandler implements ChannelHandler {

    private static final String ACTION_UPSERT = "UPSERT";
    private static final String ACTION_REVOKE = "REVOKE";

    private final BizClient bizClient;
    private final String breedingUrl;

    public FarmErpChannelHandler(BizClient bizClient,
                                 @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.FARM_ERP;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();

        String action = action(data);
        if (!ACTION_UPSERT.equals(action) && !ACTION_REVOKE.equals(action)) {
            errors.add("不支持的动作(action)：" + action + "（只接受 UPSERT 新建/更新、REVOKE 源撤销）");
        }
        if (Payloads.str(data, "farmName") == null) {
            errors.add("缺少养殖场名称(farmName)——外部系统的场编码需先与本系统养殖场对齐");
        }
        if (Payloads.str(data, "batchNo") == null) {
            errors.add("缺少批次号(batchNo)");
        }
        if (ACTION_REVOKE.equals(action)) {
            if (Payloads.str(data, "voidReason") == null) {
                errors.add("撤销批次必须写明理由(voidReason)");
            }
            return errors;
        }
        // 成员来源二选一：连续区间（自繁常见）或明细清单（外购拼车/跨号段领标常见）
        boolean hasRange = Payloads.str(data, "earTagStart") != null && Payloads.str(data, "earTagEnd") != null;
        boolean hasList = !Payloads.strings(data, "earTags").isEmpty();
        if (!hasRange && !hasList) {
            errors.add("缺少批次成员：请给出耳标起止号(earTagStart/earTagEnd)，或耳标明细清单(earTags)");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.FARM_ERP.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        // 外部系统代号：同一套接入可以对接多家 ERP，各自的外部主键互不相干
        body.put("systemCode", StringUtils.hasText(Payloads.str(data, "systemCode"))
                ? Payloads.str(data, "systemCode") : "FARM_ERP");
        body.put("action", action(data));
        body.put("externalId", Payloads.str(data, "externalId") == null
                ? Payloads.str(data, "batchNo") : Payloads.str(data, "externalId"));
        body.put("farmName", Payloads.str(data, "farmName"));
        body.put("batchNo", Payloads.str(data, "batchNo"));
        body.put("breed", Payloads.str(data, "breed"));
        body.put("entryDate", date(Payloads.str(data, "entryDate")));
        body.put("birthDate", date(Payloads.str(data, "birthDate")));
        body.put("source", Payloads.str(data, "source"));
        body.put("earTagStart", Payloads.str(data, "earTagStart"));
        body.put("earTagEnd", Payloads.str(data, "earTagEnd"));
        body.put("earTags", Payloads.strings(data, "earTags"));
        body.put("quantity", Payloads.integer(data, "quantity"));
        body.put("voidReason", Payloads.str(data, "voidReason"));
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(breedingUrl + "/breeding/internal/ingest/farm-batch", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，批次暂未同步：" + e.getMessage());
            }
            throw e;
        }
    }

    private String action(Map<String, Object> data) {
        String action = Payloads.str(data, "action");
        return action == null ? ACTION_UPSERT : action.trim().toUpperCase();
    }

    /**
     * 日期归一：ERP 侧的日期格式五花八门（2026-10-01 / 2026/10/01 / 20261001），
     * 在这里统一成 yyyy-MM-dd，养殖服务只认一种。认不出的原样带回，
     * 由养殖服务的 DTO 校验兜底（转人工而不是默默丢掉一个日期）。
     */
    private String date(String raw) {
        if (raw == null) return null;
        String text = raw.trim().replace('/', '-');
        if (text.matches("\\d{8}")) {
            return text.substring(0, 4) + "-" + text.substring(4, 6) + "-" + text.substring(6, 8);
        }
        return text;
    }
}
