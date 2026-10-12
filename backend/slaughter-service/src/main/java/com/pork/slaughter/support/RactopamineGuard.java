package com.pork.slaughter.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.mapper.EntryInspectionMapper;
import com.pork.slaughter.mapper.RactopamineTestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 瘦肉精阻断判断 —— 全项目唯一口径。判两件事，任一命中即阻断。
 *
 * <h3>一、阳性：一批阳性整批停</h3>
 * <b>为什么是「一批」而不是「一头」</b>：屠宰环节的瘦肉精检测是<b>按批抽检</b>，不是逐头检。
 * 抽检阳性时，整批猪都会被判定为不可放行（暂扣、不得屠宰、产品不得出厂，并追溯来源养殖场）——
 * 因为同批猪同栏同料同源，阳性个体说明这一批的来源可疑，抽样推断的结论本来就落在「批」上。
 * 所以这里判两件事：
 * <ol>
 *   <li><b>该猪自身检出阳性</b>（按 pig_id 精确命中）；</li>
 *   <li><b>该猪所属批次里有阳性样本</b>（按 batch_no 命中）——同一次抽检归批的样本共用一个批次号，
 *       只要其中一例阳性，同批次的猪一条都不放行。</li>
 * </ol>
 *
 * <h3>二、未检：未检不得屠宰</h3>
 * 反过来同样成立：<b>没检过的猪也不放行</b>。批批检是屠宰厂的法定动作，检测记录本身就是
 * 「这批猪被检过」的凭证——查不到记录只有两种可能，要么真没检，要么记录丢了，
 * 两者都不该让猪肉往下走。判定沿用同一套「批」的粒度：该猪<b>自身有出结果的检测记录</b>，
 * 或<b>所属批次已有出结果的检测记录</b>（同批抽检的代表样本覆盖整批），即算已检；
 * 两者都查不到就是「未检」，确定性拒绝。
 * <p>
 * 「属哪些批次」以生猪的<b>入场查验批次</b>为准（必要时并上报文里的批次号），
 * 因为这头猪真正跟谁同批，是入场时归的批，不是后面某个环节随手填的批次号。
 *
 * <h3>三、共同口径</h3>
 * 阳性和未检都是<b>业务事实</b>（要么结果已经产生，要么检测确实没做），不是「缺个字段补上就行」，
 * 因此调用方一律确定性拒绝，人工确认（force）也绕不过去。作废的检测记录不参与判断
 * （{@code @TableLogic} 自动过滤）：复检后作废原记录，猪即可放行；反过来把仅有的检测记录作废，
 * 猪就回到「未检」状态，同样不得屠宰。
 *
 * <h3>四、按场景裁剪调用（阳性与未检是两件事）</h3>
 * <ul>
 *   <li>{@link #blockReason} 判「阳性 + 未检」，用于<b>宰后检验、胴体盖章、胴体批次创建</b>——
 *       这些环节都意味着猪/肉要往下走了，两道前提缺一不可；</li>
 *   <li>{@link #isPositive} 只判「阳性」（含同批次），用于<b>宰前检查</b>——宰前是屠宰前的临床筛查，
 *       检测可能还没做，不能拿「未检」把它拦住；但已经检出阳性的猪再去判「宰前合格」就是自相矛盾
 *       （阳性生猪本就不准屠宰），所以要挡住，只允许如实记录不合格结论与处置方式。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RactopamineGuard {

    /** result 取值：1=阴性 0=阳性（见 RactopamineTest.result） */
    private static final Integer POSITIVE = 0;
    /** 摘要里最多点名的生猪数量，避免回执过长 */
    private static final int MAX_LISTED = 5;

    private final RactopamineTestMapper testMapper;
    private final EntryInspectionMapper entryMapper;

    /**
     * 单头猪是否被阻断（屠宰检验 / 胴体盖章入账前调用）。
     *
     * @param pigId   生猪档案 id
     * @param batchNo 本次上报的批次号；可与入场批次不同，两个批次都会参与判断
     * @return null 表示放行；否则返回可直接展示给业务人员的中文原因
     */
    public String blockReason(Long pigId, String batchNo) {
        if (pigId == null) return null;

        RactopamineTest self = testMapper.selectOne(Wrappers.<RactopamineTest>lambdaQuery()
                .eq(RactopamineTest::getPigId, pigId)
                .eq(RactopamineTest::getResult, POSITIVE)
                .last("LIMIT 1"));
        if (self != null) {
            return "该猪（耳标 " + orDash(self.getEarTagNo()) + "）瘦肉精检测结果阳性（检测编号 "
                    + orDash(self.getTestNo()) + "），不得进入屠宰检验及后续环节";
        }

        // 这头猪算「跟谁同批」：上报文里的批次 + 入场查验批次 + 它自己检测记录上的批次，三个来源取并集。
        // 判错方向的代价是放一头可疑的猪过去，宁可保守——比如设备这次填了个不相干的批次号，
        // 也不能因此绕过「它所属的那一批里有阳性」这件事。
        Set<String> batches = batchesOf(pigId, batchNo);
        if (batches.isEmpty()) {
            return hasResultOnPig(pigId) ? null : untestedText(earTagOf(pigId), batches);
        }
        List<RactopamineTest> inBatches = testMapper.selectList(Wrappers.<RactopamineTest>lambdaQuery()
                .in(RactopamineTest::getBatchNo, batches));
        long positives = 0;
        Set<String> positiveBatches = new LinkedHashSet<>();
        for (RactopamineTest test : inBatches) {
            if (!POSITIVE.equals(test.getResult())) continue;
            positives++;
            if (StringUtils.hasText(test.getBatchNo())) positiveBatches.add(test.getBatchNo());
        }
        if (positives > 0) {
            return "该猪所属批次「" + String.join("、", positiveBatches) + "」已有 " + positives + " 例瘦肉精阳性样本，"
                    + "按「一批阳性整批受控」不得进入屠宰检验及后续环节（阳性样本须隔离处理并按规定上报）";
        }

        boolean tested = hasResultOnPig(pigId) || inBatches.stream().anyMatch(t -> t.getResult() != null);
        return tested ? null : untestedText(earTagOf(pigId), batches);
    }

    /**
     * 只判「阳性」——自身检出阳性，或所属批次检出过阳性（同批受控），<b>不含「未检」</b>。
     * <p>
     * 供<b>宰前检查</b>使用。为什么不复用 {@link #blockReason}：宰前检查是屠宰前的临床筛查，
     * 实验室检测可能还没做，拿「未检」把它拦住等于「体检前先证明你没病」；但已经检出阳性的猪
     * （含同批次受控）再去判「宰前合格」就是自相矛盾——阳性生猪本就不准屠宰，只应如实记录
     * 不合格结论与处置方式。判定粒度与 {@link #blockReason} 的阳性部分完全一致。
     *
     * @return true 表示该猪自身或其所属批次已检出阳性
     */
    public boolean isPositive(Long pigId, String batchNo) {
        if (pigId == null) return false;

        Long self = testMapper.selectCount(Wrappers.<RactopamineTest>lambdaQuery()
                .eq(RactopamineTest::getPigId, pigId)
                .eq(RactopamineTest::getResult, POSITIVE));
        if (self != null && self > 0) return true;

        Set<String> batches = batchesOf(pigId, batchNo);
        if (batches.isEmpty()) return false;
        Long inBatches = testMapper.selectCount(Wrappers.<RactopamineTest>lambdaQuery()
                .in(RactopamineTest::getBatchNo, batches)
                .eq(RactopamineTest::getResult, POSITIVE));
        return inBatches != null && inBatches > 0;
    }

    /**
     * 批量判断（供分割配送侧创建/变更胴体批次前核对）。
     *
     * @return 「被阻断的猪 id → 原因」，空 map 表示全部放行
     */
    public Map<Long, String> blockReasons(Collection<Long> pigIds) {
        Map<Long, String> blocked = new LinkedHashMap<>();
        if (pigIds == null || pigIds.isEmpty()) return blocked;
        List<Long> ids = pigIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return blocked;

        // ① 本批猪自己的检测记录：既找出「自身阳性」「自身已检」，也拿到各自归属的批次号
        List<RactopamineTest> own = testMapper.selectList(Wrappers.<RactopamineTest>lambdaQuery()
                .in(RactopamineTest::getPigId, ids));
        Map<Long, Set<String>> pigBatches = new HashMap<>();
        Set<String> batches = new LinkedHashSet<>();
        Set<Long> pigsWithResult = new LinkedHashSet<>();
        for (RactopamineTest test : own) {
            if (test.getResult() != null) pigsWithResult.add(test.getPigId());
            if (StringUtils.hasText(test.getBatchNo())) {
                batches.add(test.getBatchNo());
                pigBatches.computeIfAbsent(test.getPigId(), k -> new LinkedHashSet<>()).add(test.getBatchNo());
            }
            if (POSITIVE.equals(test.getResult())) {
                blocked.put(test.getPigId(), "该猪（耳标 " + orDash(test.getEarTagNo()) + "）瘦肉精检测结果阳性（检测编号 "
                        + orDash(test.getTestNo()) + "）");
            }
        }

        // ①′ 入场批次：自身没检测记录的猪，它「跟谁同批」只能从入场查验记录里找。
        // 这一条同时服务于「同批次阳性」和「未检」两个判断。
        Map<Long, String> entryEarTags = new HashMap<>();
        for (EntryInspection entry : entryMapper.selectList(Wrappers.<EntryInspection>lambdaQuery()
                .in(EntryInspection::getPigId, ids)
                .isNotNull(EntryInspection::getBatchNo))) {
            if (!StringUtils.hasText(entry.getBatchNo())) continue;
            batches.add(entry.getBatchNo());
            pigBatches.computeIfAbsent(entry.getPigId(), k -> new LinkedHashSet<>()).add(entry.getBatchNo());
            if (StringUtils.hasText(entry.getEarTagNo())) {
                entryEarTags.putIfAbsent(entry.getPigId(), entry.getEarTagNo());
            }
        }

        // ② 这些批次里只要有一例阳性，同批次的猪整批受控——阳性是对「批」下的结论；
        //    同时记录「哪些批次已经有出结果的检测」，供未检判断使用。
        Set<String> positiveBatches = new LinkedHashSet<>();
        Set<String> testedBatches = new LinkedHashSet<>();
        if (!batches.isEmpty()) {
            for (RactopamineTest test : testMapper.selectList(Wrappers.<RactopamineTest>lambdaQuery()
                    .in(RactopamineTest::getBatchNo, batches))) {
                if (test.getResult() == null || !StringUtils.hasText(test.getBatchNo())) continue;
                testedBatches.add(test.getBatchNo());
                if (POSITIVE.equals(test.getResult())) positiveBatches.add(test.getBatchNo());
            }
        }
        for (Long pigId : ids) {
            if (blocked.containsKey(pigId)) continue;
            Set<String> ownBatches = pigBatches.get(pigId);
            if (ownBatches == null) continue;
            Set<String> hit = new LinkedHashSet<>(ownBatches);
            hit.retainAll(positiveBatches);
            if (!hit.isEmpty()) {
                blocked.put(pigId, "该猪与瘦肉精阳性样本同属批次「" + String.join("、", hit) + "」，整批不得放行");
            }
        }

        // ③ 未检不得屠宰：自身没有出结果的记录，所属批次也没有，就是没检过
        for (Long pigId : ids) {
            if (blocked.containsKey(pigId)) continue;
            if (pigsWithResult.contains(pigId)) continue;
            Set<String> ownBatches = pigBatches.get(pigId);
            if (ownBatches != null && ownBatches.stream().anyMatch(testedBatches::contains)) continue;
            blocked.put(pigId, untestedText(orDash(entryEarTags.get(pigId)), ownBatches));
        }
        return blocked;
    }

    /** 把批量判断结果拼成一句可直接展示给业务人员的话；无阻断时返回 null */
    public String summarize(Map<Long, String> blocked) {
        if (blocked == null || blocked.isEmpty()) return null;
        List<String> parts = blocked.entrySet().stream()
                .limit(MAX_LISTED)
                .map(e -> "生猪#" + e.getKey() + "：" + e.getValue())
                .toList();
        String more = blocked.size() > MAX_LISTED ? "（另有 " + (blocked.size() - MAX_LISTED) + " 头同样受控）" : "";
        return String.join("；", parts) + more;
    }

    /** 该猪自身有没有出结果的检测记录 */
    private boolean hasResultOnPig(Long pigId) {
        Long n = testMapper.selectCount(Wrappers.<RactopamineTest>lambdaQuery()
                .eq(RactopamineTest::getPigId, pigId)
                .isNotNull(RactopamineTest::getResult));
        return n != null && n > 0;
    }

    /**
     * 这头猪「跟谁同批」：上报文里的批次号 + 入场查验批次 + 它自己检测记录上的批次，取并集。
     * 入场批次是这头猪真正被归批的地方；上报文里的批次号可能来自下游环节，一并算上只会更保守。
     */
    private Set<String> batchesOf(Long pigId, String batchNo) {
        Set<String> batches = new LinkedHashSet<>();
        if (StringUtils.hasText(batchNo)) batches.add(batchNo.trim());
        EntryInspection entry = entryMapper.selectOne(Wrappers.<EntryInspection>lambdaQuery()
                .eq(EntryInspection::getPigId, pigId)
                .isNotNull(EntryInspection::getBatchNo)
                .orderByDesc(EntryInspection::getArriveTime)
                .last("LIMIT 1"));
        if (entry != null && StringUtils.hasText(entry.getBatchNo())) batches.add(entry.getBatchNo().trim());
        for (RactopamineTest test : testMapper.selectList(Wrappers.<RactopamineTest>lambdaQuery()
                .eq(RactopamineTest::getPigId, pigId)
                .isNotNull(RactopamineTest::getBatchNo))) {
            if (StringUtils.hasText(test.getBatchNo())) batches.add(test.getBatchNo().trim());
        }
        return batches;
    }

    /** 回执里要用的耳标：优先取检测记录上的，其次入场查验记录（两者都只在这头猪身上找，不跨猪拼） */
    private String earTagOf(Long pigId) {
        RactopamineTest test = testMapper.selectOne(Wrappers.<RactopamineTest>lambdaQuery()
                .eq(RactopamineTest::getPigId, pigId)
                .orderByDesc(RactopamineTest::getCreateTime)
                .last("LIMIT 1"));
        if (test != null && StringUtils.hasText(test.getEarTagNo())) return test.getEarTagNo();
        EntryInspection entry = entryMapper.selectOne(Wrappers.<EntryInspection>lambdaQuery()
                .eq(EntryInspection::getPigId, pigId)
                .orderByDesc(EntryInspection::getArriveTime)
                .last("LIMIT 1"));
        if (entry != null && StringUtils.hasText(entry.getEarTagNo())) return entry.getEarTagNo();
        return null;
    }

    /** 「未检不得屠宰」的统一文案；批量场景下由 summarize 再冠以生猪编号 */
    private String untestedText(String earTag, Set<String> batches) {
        String who = "该猪（耳标 " + orDash(earTag) + "）";
        // 「没记录」和「有记录但还没出结果」都算没检过——只有出了结果的记录才是可放行的凭证
        String scope = (batches == null || batches.isEmpty())
                ? "尚无瘦肉精检测记录或检测未出结果（也核不到所属入场批次）"
                : "及所属批次「" + String.join("、", batches) + "」尚无瘦肉精检测记录或检测未出结果";
        return who + scope + "，按「未检不得屠宰」不得进入屠宰检验及后续环节"
                + "（瘦肉精按批抽检，须先完成检测并留证）";
    }

    private String orDash(String value) {
        return StringUtils.hasText(value) ? value : "未记录";
    }
}
