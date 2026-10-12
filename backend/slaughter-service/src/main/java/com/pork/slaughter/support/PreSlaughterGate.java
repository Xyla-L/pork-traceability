package com.pork.slaughter.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.mapper.SlaughterInspectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 宰前检查前置判断 —— 全项目唯一口径。
 *
 * <h3>为什么要卡这一道</h3>
 * 《动物检疫管理办法》第二十条：「动物屠宰前<b>应当逐头（只）进行临床检查，健康的动物方可屠宰</b>」；
 * 《生猪屠宰检疫规程》把「宰前检查」列为独立法定环节，并且写死了时点——<b>屠宰前 2 小时内</b>逐头检查，
 * 合格的<b>准予屠宰</b>，不合格的按隔离观察、急宰或无害化处理。也就是说「宰前检查合格」不是一个
 * 可以事后补录的说明性字段，而是<b>屠宰这件事本身的前置许可</b>。
 *
 * <h3>判定</h3>
 * 只约束<b>宰后类</b>环节（宰后检验 / 宰后同步检验）。宰前检查记录本身当然不受这道约束，
 * 否则谁也进不来。判定结果分三种：
 * <ol>
 *   <li>该猪有结论为「合格」的宰前检查记录 → <b>放行</b>（合格即准予屠宰）；</li>
 *   <li>有宰前检查记录但都不是合格 → <b>拒绝</b>，原因写明「宰前检查结论为不合格」，
 *       这比笼统说「没有合格宰前记录」更能让现场对上号（是检了没过，不是没检）；</li>
 *   <li>一条宰前检查记录都没有 → <b>拒绝</b>，原因写明「尚未做宰前检查」。</li>
 * </ol>
 *
 * <h3>与瘦肉精阻断的区别</h3>
 * 两者都是确定性拒绝（{@code retryable=false}，人工确认 force 也绕不过去），都放在幂等查找之前——
 * 被拦的猪之后设备再推，要拿到「被拒绝」而不是「返回既有记录」，否则等于让幂等把阻断结论洗成一条正常记录。
 * 区别在粒度：瘦肉精判「批」（按批抽检，一批阳性整批停），宰前检查判<b>头</b>——规程要求的是逐头临床检查，
 * 一头猪一个结论，所以这里只看这头猪自己的记录，不跨猪、不跨批。
 */
@Component
@RequiredArgsConstructor
public class PreSlaughterGate {

    /** inspect_type 取值：1=宰前检验（见 SlaughterInspection.inspectType） */
    private static final Integer PRE_SLAUGHTER = 1;
    /** result 取值：1=合格 0=不合格（见 SlaughterInspection.result） */
    private static final Integer PASSED = 1;

    private final SlaughterInspectionMapper inspectionMapper;

    /**
     * 这头猪是否被挡在宰后环节之外。
     *
     * @param pigId     生猪档案 id
     * @param earTagNo  本次上报的耳标号，只用于把中文原因写清楚
     * @return null 表示放行；否则返回可直接展示给业务人员的中文原因
     */
    public String blockReason(Long pigId, String earTagNo) {
        if (pigId == null) return null;

        Long passed = inspectionMapper.selectCount(Wrappers.<SlaughterInspection>lambdaQuery()
                .eq(SlaughterInspection::getPigId, pigId)
                .eq(SlaughterInspection::getInspectType, PRE_SLAUGHTER)
                .eq(SlaughterInspection::getResult, PASSED));
        if (passed != null && passed > 0) return null;

        // 有宰前记录但没一条合格：猪是检了没过，不是没检——原因要分开写，否则现场会去翻「是不是漏录了」
        Long checked = inspectionMapper.selectCount(Wrappers.<SlaughterInspection>lambdaQuery()
                .eq(SlaughterInspection::getPigId, pigId)
                .eq(SlaughterInspection::getInspectType, PRE_SLAUGHTER));
        String who = "该猪（耳标 " + (StringUtils.hasText(earTagNo) ? earTagNo : "未记录") + "）";
        if (checked != null && checked > 0) {
            return who + "的宰前检查结论为不合格，未准予屠宰，不得进入宰后检验及后续环节"
                    + "（不合格生猪须按规定隔离观察、急宰或无害化处理）";
        }
        return who + "尚未做宰前检查，按《生猪屠宰检疫规程》未做宰前检查不得屠宰，"
                + "不得进入宰后检验及后续环节（须在屠宰前完成逐头临床检查并判合格）";
    }
}
