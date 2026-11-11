package com.pork.breeding.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.VaccineRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产地检疫的免疫前置判断 —— 全项目唯一口径。
 *
 * <h3>为什么要卡这一道</h3>
 * 《动物检疫管理办法》要求产地检疫查验<b>强制免疫档案</b>：免疫不在有效期内，动物不得调运。
 * 也就是说「免疫记录」不是给检疫做参考的背景资料，而是<b>出证这件事的前置条件</b>。
 * 以前这里没有校验，结果是「一头从没打过疫苗的猪也能开出产地检疫证」——
 * 证一旦开出、猪装车拉走，链上就永久留一条违规记录，而没人能拦。
 *
 * <h3>判定（只判两件事）</h3>
 * <ol>
 *   <li><b>有没有</b>：一条有效免疫记录都没有 → 拦。不按病种判——疫苗名称是现场填的自由文本，
 *       系统没有「强制免疫病种」主数据，硬按名字匹配只会把「猪瘟活疫苗」「猪瘟疫苗」判成两回事。</li>
 *   <li><b>顺不顺</b>：有记录，但全部晚于本次出证时间 → 拦。先出证后免疫，时间顺序不成立。</li>
 * </ol>
 *
 * <h3>边界</h3>
 * 只看这头猪自己的记录，不跨猪、不跨批——一头猪一个免疫档案。
 * 只拦新增与设备登记，不追溯存量：历史数据里「先出证、免疫记录缺失」的形态很常见，
 * 回溯改判等于用今天的规则否定昨天的现场，正确做法是在页面上标出来，而不是批量作废已发出的证。
 */
@Component
@RequiredArgsConstructor
public class ImmunityGate {

    /** 出证与免疫的时间判定容差（分钟）：同一现场前后脚，秒级先后不构成「先出证后免疫」 */
    private static final long TIME_TOLERANCE_MINUTES = 5;

    private final VaccineRecordMapper vaccineMapper;

    /**
     * @param pigId         生猪主键
     * @param certIssueTime 出证时间（可空；空则不判时间顺序，只判有无）
     * @return null 表示放行；非空为拦截原因（面向现场的中文说明）
     */
    public String blockReason(Long pigId, LocalDateTime certIssueTime) {
        if (pigId == null) return null;
        // void_flag 是逻辑删除标记，常规查询已自动排除作废记录
        List<VaccineRecord> records = vaccineMapper.selectList(
                Wrappers.<VaccineRecord>lambdaQuery().eq(VaccineRecord::getPigId, pigId));
        if (records.isEmpty()) {
            return "该生猪没有任何有效免疫记录：产地检疫须查验强制免疫档案。"
                    + "免疫由智能注射器现场采集入账，请先确认这头猪已完成接种（记录未上报的由养殖场重新上报），再登记检疫证";
        }
        if (certIssueTime == null) return null;
        // 容差 5 分钟：接种和出证常常是同一个现场前后脚完成，秒级先后没有意义。
        // 只有免疫明确晚于出证（超过一个现场排队的时间量级）才算「先出证后免疫」。
        LocalDateTime deadline = certIssueTime.plusMinutes(TIME_TOLERANCE_MINUTES);
        boolean anyBefore = records.stream()
                .anyMatch(r -> r.getInjectTime() == null || !r.getInjectTime().isAfter(deadline));
        if (!anyBefore) {
            return "该生猪的免疫记录时间全部晚于本次出证时间，时间顺序不成立（等于先出证、后免疫），"
                    + "请核对免疫记录时间或本次出证时间";
        }
        return null;
    }
}
