package com.pork.distribution.support;

import com.pork.distribution.mapper.SplitJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 分割作业号生成：{@code SV + yyyyMMdd + '-' + 3 位当日流水}，例 {@code SV20261009-001}。
 *
 * <h3>为什么和批次号一样要排流水</h3>
 * 一个胴体批次下会有多份作业（一批 200 头就是 200 份），作业号是现场核对「这头猪的产出清单」
 * 时唯一的口头称呼；随机号叫不出来，也无法从号上看出一共做了几头。
 *
 * <h3>流水怎么算</h3>
 * 与胴体批次号同口径：按<b>当日全局</b>排，已作废的也算作占用（原生 SQL 绕开逻辑删除）。
 * 唯一性最终由 {@code split_job.uk_job_no} 保证，并发算重了由调用方换序号重试。
 */
@Component
@RequiredArgsConstructor
public class SplitJobNo {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final SplitJobMapper splitJobMapper;

    /** 取当日第 {@code skip + 1} 个候选作业号（{@code skip} 为已重试次数，从 0 开始） */
    public String next(LocalDate day, int skip) {
        String prefix = "SV" + DAY.format(day) + "-";
        long seq = splitJobMapper.countByJobNoPrefix(prefix) + 1 + skip;
        return prefix + String.format("%03d", seq);
    }

    /** 候选作业号是否已被占用（含已作废的） */
    public boolean taken(String jobNo) {
        return splitJobMapper.countByJobNo(jobNo) > 0;
    }
}
