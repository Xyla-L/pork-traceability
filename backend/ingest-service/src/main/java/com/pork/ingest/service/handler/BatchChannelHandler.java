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
 * 屠宰线胴体称重分拣台通道。
 * <p>
 * 白条下线时识读挂钩标签并过磅，系统按「屠宰场 + 屠宰日」把同一场的白条自动归集成胴体批次。
 * 分工与分割批次一致：<b>设备只报单头事实</b>——耳标、胴体重、哪条线、什么时候；
 * <b>批次边界与批次号都由系统算</b>。设备报文里不带批次号，因为「哪几头算一批」是业务归集决策，
 * 让一台称重台决定分组，必然造出跨场混批、一猪两批。
 * <p>
 * 写入前还有三道业务侧硬校验（盖章 / 瘦肉精 / 未被占用），任一不过都不入账，force 也绕不过。
 */
@Slf4j
@Component
public class BatchChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;
    private final String distributionUrl;

    public BatchChannelHandler(BizClient bizClient,
                               @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
                               @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.BATCH;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) {
            errors.add("缺少耳标号(earTagNo)：分拣台须识读挂钩标签，系统才知道这头白条是谁的");
        }
        if (Payloads.str(data, "slaughterhouse") == null) {
            errors.add("缺少屠宰场(slaughterhouse)：归集口径是「屠宰场 + 屠宰日」，缺了没法成批");
        }
        if (Payloads.decimal(data, "carcassWeightKg") == null) {
            errors.add("缺少胴体重量(carcassWeightKg)：分拣台必须先出重量再上报");
        } else if (Payloads.decimal(data, "carcassWeightKg").signum() <= 0) {
            errors.add("胴体重量(carcassWeightKg)必须大于 0");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.BATCH.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

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
            return DispatchResult.manual(table, "耳标查到的生猪档案缺少主键，无法自动归集");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pigId", pigId);
        body.put("earTagNo", earTagNo);
        body.put("carcassWeightKg", Payloads.decimal(data, "carcassWeightKg"));
        body.put("slaughterhouse", Payloads.str(data, "slaughterhouse"));
        body.put("carcassNo", Payloads.str(data, "carcassNo"));
        body.put("lineNo", Payloads.str(data, "lineNo"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/batch", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "配送服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
