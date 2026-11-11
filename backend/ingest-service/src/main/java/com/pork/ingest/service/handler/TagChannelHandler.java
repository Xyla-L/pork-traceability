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
 * 养殖建档通道：耳标读写器。
 * <p>
 * <b>设备能力边界（2026-10-24 收紧）</b>：电子耳标芯片里只有一个编号，读写器物理上
 * 能提供的只有「耳标号 + 读取时刻」。品种、性别、出生日期、圈舍、来源是人的知识
 * （产房记录、购销单据），任何设备都读不出来 —— 这些信息一律由人在生猪档案
 * <b>人工建档</b>时录入，设备报文带了也不认。
 * <p>
 * 因此本通道只有两种结局：
 * <ol>
 *   <li>耳标已建档（人工建的）→ 幂等返回既有档案，入账；</li>
 *   <li>耳标是新号 → 转待人工，提示先人工建档再确认入账。</li>
 * </ol>
 * 「设备 ≠ 人」：建档这件事从此不落在设备头上。
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
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.TAG.targetTable();
        Map<String, Object> data = ctx.data();

        // 只透传读写器真正能提供的东西。报文里即使混进了 breed/gender/farmName 等
        // 人工字段也不往下发 —— 建档走人工入口，不让设备报文变成档案信息的后门。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("earTagNo", Payloads.str(data, "earTagNo"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(breedingUrl + "/breeding/internal/ingest/tag", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，暂时无法核对耳标：" + e.getMessage());
            }
            throw e;
        }
    }
}
