package com.pork.ingest.service.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.ingest.client.BizClient;
import com.pork.ingest.entity.IngestStaging;
import com.pork.ingest.mapper.IngestStagingMapper;
import com.pork.ingest.service.ChannelHandler;
import com.pork.ingest.service.IngestContext;
import com.pork.ingest.support.DispatchResult;
import com.pork.ingest.support.IngestChannel;
import com.pork.ingest.support.IngestStatus;
import com.pork.ingest.support.Payloads;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冷链温度采集通道。
 * <p>
 * 三条硬规则：
 * <ol>
 *   <li>越界值（-999、null、非数字）一律拒绝，不进库——否则会污染冷链台账。</li>
 *   <li>温度超出允许区间 ⇒ is_abnormal=1，异常值永远保留，不做降采样。</li>
 *   <li>非异常值按最小间隔降采样：车载探头 30 秒一条，全量入库会把表撑爆，
 *       降采样丢弃的记录仍留在待确认队列（status=4）可查。</li>
 * </ol>
 */
@Slf4j
@Component
public class TemperatureChannelHandler implements ChannelHandler {

    /** 物理合理性区间，超出即视为脏数据 */
    private static final BigDecimal SANITY_MIN = new BigDecimal("-60");
    private static final BigDecimal SANITY_MAX = new BigDecimal("60");

    private final BizClient bizClient;
    private final IngestStagingMapper stagingMapper;
    private final String distributionUrl;
    private final BigDecimal rangeMin;
    private final BigDecimal rangeMax;
    private final int minIntervalSeconds;

    public TemperatureChannelHandler(BizClient bizClient,
                                     IngestStagingMapper stagingMapper,
                                     @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl,
                                     @Value("${ingest.temperature.range-min:-18.0}") BigDecimal rangeMin,
                                     @Value("${ingest.temperature.range-max:0.0}") BigDecimal rangeMax,
                                     @Value("${ingest.temperature.min-interval-seconds:60}") int minIntervalSeconds) {
        this.bizClient = bizClient;
        this.stagingMapper = stagingMapper;
        this.distributionUrl = distributionUrl;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.minIntervalSeconds = minIntervalSeconds;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.TEMPERATURE;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.decimal(data, "temperature") == null) {
            errors.add("温度值缺失或非数字");
        } else {
            BigDecimal temperature = Payloads.decimal(data, "temperature");
            if (temperature.compareTo(SANITY_MIN) < 0 || temperature.compareTo(SANITY_MAX) > 0) {
                errors.add("温度值超出合理区间[" + SANITY_MIN + "," + SANITY_MAX + "]：" + temperature);
            }
        }
        if (Payloads.str(data, "transportNo") == null && Payloads.integer(data, "transportId") == null) {
            errors.add("缺少运输单号(transportNo)，无法归属到冷链运单");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        BigDecimal temperature = Payloads.decimal(ctx.data(), "temperature");
        boolean abnormal = temperature.compareTo(rangeMin) < 0 || temperature.compareTo(rangeMax) > 0;

        if (!abnormal && throttled(ctx)) {
            return DispatchResult.dropped(IngestChannel.TEMPERATURE.targetTable(),
                    "非异常值，距上次入账不足 " + minIntervalSeconds + " 秒，按最小间隔降采样丢弃");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transportNo", Payloads.str(ctx.data(), "transportNo"));
        body.put("transportId", Payloads.integer(ctx.data(), "transportId"));
        body.put("temperature", temperature);
        body.put("tempRangeMin", rangeMin);
        body.put("tempRangeMax", rangeMax);
        body.put("isAbnormal", abnormal ? 1 : 0);
        body.put("recorder", ctx.deviceLabel());
        body.put("recordMethod", "DEVICE");
        body.put("recordTime", Handlers.time(ctx.reportTime()));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/temperature", body);
        return Handlers.toResult(result, IngestChannel.TEMPERATURE.targetTable());
    }

    /** 同一设备上一条已入账记录距今不足最小间隔 ⇒ 降采样丢弃 */
    private boolean throttled(IngestContext ctx) {
        IngestStaging last = stagingMapper.selectOne(Wrappers.<IngestStaging>lambdaQuery()
                .eq(IngestStaging::getChannel, IngestChannel.TEMPERATURE.name())
                .eq(IngestStaging::getDeviceNo, ctx.device().getDeviceNo())
                .eq(IngestStaging::getStatus, IngestStatus.ACCEPTED)
                .orderByDesc(IngestStaging::getId)
                .last("LIMIT 1"));
        if (last == null || last.getReportTime() == null) return false;
        LocalDateTime current = ctx.reportTime();
        // 补传的历史数据（时间戳早于上一条）不做降采样判断，否则会把补传整段吃掉
        if (current.isBefore(last.getReportTime())) return false;
        return Duration.between(last.getReportTime(), current).getSeconds() < minIntervalSeconds;
    }
}
