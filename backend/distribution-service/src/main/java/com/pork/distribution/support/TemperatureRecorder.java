package com.pork.distribution.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.distribution.entity.TemperatureLog;
import com.pork.distribution.mapper.TemperatureLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 温度记录的<b>唯一落库口径</b>：越界判定、断档判定、间隔固化都在这里。
 * <p>
 * 为什么要抽出来：温度有两条写入路径 —— 车载探头走接入层（{@code ingestTemperature}），
 * 演示/无真机时由定时采集器（{@code TemperatureAutoSampler}）按间隔补一条。
 * 两条路径各写一份「断档怎么算」，迟早会算出两个答案：同样的间隔，
 * 一条标了断档、另一条没标，界面上就会出现「有空白却显示正常」。所以判定只留这一份。
 * <p>
 * 两条铁律（来自 2026-10-10 评审）：
 * <ul>
 *   <li><b>空白 ≠ 正常</b>：只判单点越界不够，两条之间隔了两个半小时，界面照样写「温度正常」。
 *       断档标记标在<b>断档之后的第一条</b>上 —— 用户看到的是「从这一刻起中间是空白」。</li>
 *   <li><b>补传不判断档</b>：设备补历史数据时时间戳可能早于上一条，
 *       否则整段补传会被标成一片红，反而掩盖真正的空白。</li>
 * </ul>
 */
@Slf4j
@Component
public class TemperatureRecorder {

    private final TemperatureLogMapper temperatureLogMapper;
    /**
     * 断档判定的默认基准间隔（秒），探头没声明 {@code sample_interval_seconds} 时用它。
     * 「多久采一次」本该由探头自己声明，这里只是兜底，不是假设。
     */
    private final int defaultSampleIntervalSeconds;
    /** 断档容差倍数：间隔 &gt; 额定 × 本值才算断档。给足余量，免得降采样与网络抖动被误判 */
    private final int gapToleranceMultiplier;

    public TemperatureRecorder(TemperatureLogMapper temperatureLogMapper,
                               @Value("${ingest.temperature.sample-interval-seconds:300}") int defaultSampleIntervalSeconds,
                               @Value("${ingest.temperature.gap-tolerance-multiplier:3}") int gapToleranceMultiplier) {
        this.temperatureLogMapper = temperatureLogMapper;
        this.defaultSampleIntervalSeconds = defaultSampleIntervalSeconds;
        this.gapToleranceMultiplier = gapToleranceMultiplier;
    }

    /**
     * 记一条温度。
     *
     * @param declaredInterval 本次采集所属探头声明的额定间隔（秒），空则用服务默认值
     * @return 已落库的记录（含算好的 isAbnormal / gapSeconds / isGap），供调用方拼回执文案
     */
    public TemperatureLog record(Long transportId, BigDecimal temperature, LocalDateTime recordTime,
                                 BigDecimal rangeMin, BigDecimal rangeMax,
                                 String recorder, String recordMethod, String source,
                                 String sourceRef, String rawPayload, Integer declaredInterval) {

        int interval = declaredInterval != null && declaredInterval > 0
                ? declaredInterval : defaultSampleIntervalSeconds;
        LocalDateTime at = recordTime == null ? LocalDateTime.now() : recordTime;

        TemperatureLog entity = new TemperatureLog();
        entity.setTransportId(transportId);
        entity.setTemperature(temperature);
        // 设备补传历史数据时按设备时间落库，避免补传整段被记成"现在"
        entity.setRecordTime(at);
        entity.setTempRangeMin(rangeMin);
        entity.setTempRangeMax(rangeMax);
        entity.setIsAbnormal(temperature.compareTo(rangeMin) < 0 || temperature.compareTo(rangeMax) > 0 ? 1 : 0);
        entity.setRecorder(recorder);
        entity.setRecordMethod(recordMethod);
        entity.setSource(source);
        entity.setSourceRef(sourceRef);
        entity.setRawPayload(rawPayload);
        entity.setCreateTime(LocalDateTime.now());

        TemperatureLog last = lastOf(transportId);
        Integer gapSeconds = null;
        boolean gap = false;
        if (last != null && last.getRecordTime() != null) {
            long seconds = Duration.between(last.getRecordTime(), at).getSeconds();
            gapSeconds = (int) seconds;
            // 补传的历史数据（时间戳早于上一条）不判断档
            gap = seconds > (long) interval * gapToleranceMultiplier;
        }
        entity.setSampleIntervalSeconds(interval);
        entity.setGapSeconds(gapSeconds);
        entity.setIsGap(gap ? 1 : 0);
        temperatureLogMapper.insert(entity);
        return entity;
    }

    /** 该运单最近一条温度（时间倒序；同刻按 id 兜底，避免批量补传时顺序不定） */
    public TemperatureLog lastOf(Long transportId) {
        return temperatureLogMapper.selectOne(Wrappers.<TemperatureLog>lambdaQuery()
                .eq(TemperatureLog::getTransportId, transportId)
                .orderByDesc(TemperatureLog::getRecordTime)
                .orderByDesc(TemperatureLog::getId)
                .last("LIMIT 1"));
    }

    /**
     * 这张运单有没有真机探头报过数据。
     * <p>
     * 定时采集只服务「没有真机」的场景。一旦探头自己报过，就必须停手 ——
     * 模拟读数混进真实读数里，事后谁也分不清哪条是车厢温度、哪条是程序编的。
     */
    public boolean hasDeviceReading(Long transportId) {
        return temperatureLogMapper.selectCount(Wrappers.<TemperatureLog>lambdaQuery()
                .eq(TemperatureLog::getTransportId, transportId)
                .eq(TemperatureLog::getSource, "DEVICE")) > 0;
    }
}
