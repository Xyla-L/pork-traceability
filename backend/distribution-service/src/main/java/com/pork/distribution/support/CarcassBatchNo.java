package com.pork.distribution.support;

import com.pork.distribution.mapper.CarcassBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 胴体批次号生成：{@code CB + yyyyMMdd + '-' + 3 位当日流水}，例 {@code CB20261009-001}。
 *
 * <h3>为什么要有序流水，而不是随机号</h3>
 * 加了头数上限之后，一天一个屠宰场会产出好几个批次，批号是现场与追溯查询里唯一的口头称呼——
 * 「今天甲场第 3 批」要能从号上看出来。加随机数做不到这一点（原来的 6 位随机号是「一天一批」时代的产物）。
 *
 * <h3>流水怎么算</h3>
 * 按<b>当日全局</b>排（不分屠宰场），免得两个场同一天各排出 -001 撞在一起。
 * 唯一性最终由 {@code carcass_batch.uk_batch_no} 保证：本类只给候选号，
 * 并发下两个请求可能算出同一个数，插入撞键时调用方换下一个序号重试即可。
 */
@Component
@RequiredArgsConstructor
public class CarcassBatchNo {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final CarcassBatchMapper carcassBatchMapper;

    /**
     * 取当日第 {@code skip + 1} 个候选批号（{@code skip} 为已重试次数，从 0 开始）。
     * 已作废的批号也计入占用，不复用。
     */
    public String next(LocalDate day, int skip) {
        String prefix = "CB" + DAY.format(day) + "-";
        long seq = carcassBatchMapper.countByBatchNoPrefix(prefix) + 1 + skip;
        return prefix + String.format("%03d", seq);
    }

    /** 候选批号是否已被占用（含已作废的） */
    public boolean taken(String batchNo) {
        return carcassBatchMapper.countByBatchNo(batchNo) > 0;
    }
}
