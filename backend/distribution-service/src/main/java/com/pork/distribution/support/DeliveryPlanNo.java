package com.pork.distribution.support;

import com.pork.distribution.mapper.DeliveryPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 发货计划号生成：{@code DP + yyyyMMdd + '-' + 3 位当日流水}，例 {@code DP20261009-001}。
 * <p>
 * 与胴体批次号（{@code CB}）、分割作业号（{@code SV}）同一口径：<b>当日全局</b>排流水，
 * 不分门店也不分车次，免得同一天开出的两张计划单都叫 -001。计划号是调度、司机、
 * 门店三方在电话里唯一能对齐的称呼，必须有序、可读、不复用。
 * <p>
 * 唯一性最终由 {@code delivery_plan.uk_plan_no} 保证：本类只给候选号，
 * 并发下两个请求可能算出同一个数，插入撞键时调用方换个序号重试。
 */
@Component
@RequiredArgsConstructor
public class DeliveryPlanNo {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final DeliveryPlanMapper deliveryPlanMapper;

    /** 取当日第 {@code skip + 1} 个候选计划号（{@code skip} 为已重试次数，从 0 开始） */
    public String next(LocalDate day, int skip) {
        String prefix = "DP" + DAY.format(day) + "-";
        long seq = deliveryPlanMapper.countByPlanNoPrefix(prefix) + 1 + skip;
        return prefix + String.format("%03d", seq);
    }

    /** 候选计划号是否已被占用（含已取消的） */
    public boolean taken(String planNo) {
        return deliveryPlanMapper.countByPlanNo(planNo) > 0;
    }
}
