package com.pork.distribution.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.entity.TemperatureLog;
import com.pork.distribution.mapper.ColdChainTransportMapper;
import com.pork.distribution.support.TemperatureRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冷链温度定时采集：<b>发车后按固定间隔自动打卡，送达（全部站点到达）后自动停</b>。
 * <p>
 * 采集窗口刻意只取「运输中（status=2）」：运单状态由站点汇总推导，2 表示已发车、
 * 但还没到齐所有卸货门店；一旦全部站点到达就变 3（待签收），再往后是 4（已签收）。
 * 所以「车在跑」与「status=2」是同一件事，不需要另设开关，也不会出现
 * 「已经签收了还在往里写温度」这种账。
 * <p>
 * <b>数据来源必须说清楚</b>：真机探头接入前没有真实读数可读，这里的读数是
 * <b>模拟探头</b>产生的（{@code source=SIMULATED}），页面上单独标「模拟探头」。
 * 一旦该运单有真机（{@code source=DEVICE}）报过数据，本采集器立刻对该运单停手 ——
 * 模拟值混进真实温度里，事后就分不清哪条是车厢温度、哪条是程序编的。
 * <p>
 * 断档 / 越界判定不在这里做，统一走 {@link TemperatureRecorder}：两条写入路径
 * （设备上报、定时采集）必须用同一把尺子。
 */
@Slf4j
@Component
public class TemperatureAutoSampler {

    /** 运单状态：2=运输中（已发车、尚有站点未到达） */
    private static final int TRANSPORT_IN_TRANSIT = 2;
    private static final String SOURCE_SIMULATED = "SIMULATED";
    private static final String RECORD_METHOD_AUTO = "AUTO";
    private static final String RECORDER_NAME = "模拟探头";
    private static final String SOURCE_REF = "PROBE-SIM-01";

    private final ColdChainTransportMapper transportMapper;
    private final TemperatureRecorder recorder;

    private final boolean enabled;
    /** 采样间隔（秒）：默认 300 秒（5 分钟），冷链行业常规；按需改配置 */
    private final int intervalSeconds;
    private final BigDecimal rangeMin;
    private final BigDecimal rangeMax;

    public TemperatureAutoSampler(ColdChainTransportMapper transportMapper,
                                  TemperatureRecorder recorder,
                                  @Value("${distribution.temperature.auto-sample.enabled:true}") boolean enabled,
                                  @Value("${distribution.temperature.auto-sample.interval-seconds:300}") int intervalSeconds,
                                  @Value("${ingest.temperature.range-min:-18.0}") BigDecimal rangeMin,
                                  @Value("${ingest.temperature.range-max:0.0}") BigDecimal rangeMax) {
        this.transportMapper = transportMapper;
        this.recorder = recorder;
        this.enabled = enabled;
        this.intervalSeconds = intervalSeconds > 0 ? intervalSeconds : 300;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
    }

    /**
     * 按采样间隔扫描一次（定时任务与默认触发都走这里）。
     */
    public Map<String, Object> sampleAll() {
        return sampleAll(false);
    }

    /**
     * 扫描一次。返回扫描/写入/跳过明细，供内部触发接口与回归使用。
     * <p>
     * 刻意<b>不补历史</b>：错过的间隔就是空白，由记录自身带 gapSeconds/isGap 标出来。
     * 若一次补齐十条「当时没采到」的读数，等于替探头编造它并没采过的数据 ——
     * 空白可以解释，伪造的连续曲线不能。
     *
     * @param force true 时忽略「还没到下一个采样点」的等待，立即采一条。
     *              只给演示/回归手动触发用；状态窗口与真机让位两条约束照样生效，
     *              写入口径也不变（仍是 SIMULATED），不会绕过任何判定。
     */
    public Map<String, Object> sampleAll(boolean force) {
        LocalDateTime now = LocalDateTime.now();
        List<ColdChainTransport> moving = transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getStatus, TRANSPORT_IN_TRANSIT));
        List<Map<String, Object>> items = new ArrayList<>();
        int written = 0;
        int skipped = 0;
        for (ColdChainTransport transport : moving) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("transportId", transport.getId());
            item.put("transportNo", transport.getTransportNo());

            // 真机已经报过这一单 → 模拟探头让位，不混数据
            if (recorder.hasDeviceReading(transport.getId())) {
                item.put("written", false);
                item.put("reason", "该运单已由车载探头上报，不再模拟");
                items.add(item);
                skipped++;
                continue;
            }

            TemperatureLog last = recorder.lastOf(transport.getId());
            if (!force && last != null && last.getRecordTime() != null) {
                long since = Duration.between(last.getRecordTime(), now).getSeconds();
                if (since < intervalSeconds) {
                    // 未到下一个采样点：定时任务每 30 秒扫一次，但读数只在整间隔落一条
                    item.put("written", false);
                    item.put("reason", "距上一条 " + since + " 秒，未到采样间隔 " + intervalSeconds + " 秒");
                    items.add(item);
                    skipped++;
                    continue;
                }
            }

            BigDecimal reading = reading(transport.getId(), now);
            // force 触发的记录打上 #force：它只是「接口能写」的证据，不是采集节奏的一部分，
            // 回归收尾要能精确删掉它（按间隔猜会误删定时任务自己写的正常记录）
            String sourceRef = SOURCE_REF + "/" + transport.getTransportNo() + (force ? "#force" : "");
            TemperatureLog saved = recorder.record(transport.getId(), reading, now, rangeMin, rangeMax,
                    RECORDER_NAME, RECORD_METHOD_AUTO, SOURCE_SIMULATED,
                    sourceRef, null, intervalSeconds);
            item.put("written", true);
            item.put("temperature", saved.getTemperature());
            item.put("logId", saved.getId());
            item.put("isGap", saved.getIsGap());
            item.put("gapSeconds", saved.getGapSeconds());
            items.add(item);
            written++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", moving.size());
        result.put("written", written);
        result.put("skipped", skipped);
        result.put("intervalSeconds", intervalSeconds);
        result.put("items", items);
        return result;
    }

    /**
     * 定时扫描：每 30 秒看一次哪些车「到点了」，真正落库仍按 {@code intervalSeconds}。
     * <p>
     * 用 fixedDelay 而不是 cron：上一轮扫完再排下一轮，扫描变慢时不会堆叠。
     */
    @Scheduled(fixedDelayString = "${distribution.temperature.auto-sample.scan-interval-ms:30000}")
    public void tick() {
        if (!enabled) return;
        try {
            Map<String, Object> result = sampleAll();
            int written = (int) result.get("written");
            if (written > 0) {
                log.info("temperature_auto_sample written={} scanned={} interval={}s",
                        written, result.get("scanned"), intervalSeconds);
            }
        } catch (RuntimeException e) {
            // 采集失败不能把服务拖垮，也不能静默：下一轮会重试，但日志要留痕
            log.error("temperature_auto_sample_failed interval={}s", intervalSeconds, e);
        }
    }

    /**
     * 造一条读数的<b>形状</b>：在 -18~-16℃ 之间缓慢起伏，按运单号错开相位，
     * 让不同车的曲线不重叠、同一辆车前后两条连续（不是随机跳变）。
     * <p>
     * 刻意不生成越界值：异常必须来自真实探头。演示里编一条「-5℃」出来，
     * 就是在用假数据证明系统能发现异常。
     */
    private BigDecimal reading(Long transportId, LocalDateTime now) {
        long slot = now.toEpochSecond(ZoneOffset.UTC) / intervalSeconds;
        double phase = (transportId % 11) * 0.7 + slot * 0.9;
        double value = -17.0 + Math.sin(phase) * 1.0;   // [-18.0, -16.0]
        return BigDecimal.valueOf(Math.round(value * 10) / 10.0).setScale(1, RoundingMode.HALF_UP);
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
