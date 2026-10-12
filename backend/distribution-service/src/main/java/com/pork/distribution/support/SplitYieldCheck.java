package com.pork.distribution.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.distribution.entity.MeatPart;
import com.pork.distribution.mapper.MeatPartMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分割出肉率对账判定器（唯一口径，禁止各处再写一份）。
 *
 * <h3>为什么需要它</h3>
 * 分割是固定部位切割，出肉率是刚性的：一条白条约 75~80kg，红肉占 65~70%，其余是骨、脂、皮和副产。
 * 「刚性」意味着可以校验——而这正是食品追溯最要命的一环：如果少记了一块肉，系统毫无感知，
 * 真出事要召回时范围就圈不准。本判定器把三件事钉住：
 *
 * <ol>
 *   <li><b>必出项</b>：字典里 {@code required=1} 的部位必须出现（前腿、大排、后腿是行业三段主产，
 *       一头猪不可能没切出这三样）。</li>
 *   <li><b>总重上限</b>：各部位合计不可能超过白条重量 × {@code max-over-rate}（默认 105%）。
 *       超了说明重复上报或虚报。</li>
 *   <li><b>总重下限</b>：合计不该远低于白条重量。口径按报了什么自动切换——
 *       报了骨/脂/皮/副产就按总重判（默认 85%）；<b>只报红肉</b>时总重本来就只占六七成，
 *       改按红肉口径判（默认 60%）；报的是整条/半片出货（{@code WHOLE}）则不做下限判定——
 *       整条卖本来就没有部位比例可言。</li>
 * </ol>
 *
 * <h3>结论只用于「转人工复核」，不用于拒绝入账</h3>
 * 分割事实已经发生，不入账等于把现场数据弄丢。对账不通过一律落到 {@code check_status=1}
 * 等人工复核，与瘦肉精阳性记录「照常入账留证」同一个口径。
 *
 * <h3>没有基准重量时</h3>
 * 胴体批次没记单头重量时只写说明、不告警——那是数据不全，不是数据可疑，
 * 不该制造噪音让人对告警麻木。必出项检查不受影响，仍然执行。
 */
@Component
public class SplitYieldCheck {

    /** 分割作业的一条产出明细 */
    public record Line(String partCode, String partName, BigDecimal weightKg) {
    }

    /** 对账结论 */
    public record Conclusion(int status, String note, BigDecimal totalWeightKg,
                             BigDecimal totalYieldRate, BigDecimal leanYieldRate) {
    }

    public static final int PASS = 0;
    public static final int MANUAL = 1;

    private static final String CAT_LEAN = "LEAN";
    private static final String CAT_WHOLE = "WHOLE";
    private static final Set<String> CAT_BULK = Set.of("BONE", "FAT", "SKIN", "BYPRODUCT");

    private final MeatPartMapper meatPartMapper;

    /** 报了骨/脂/皮/副产时的总重下限（%） */
    private final int minYieldRate;
    /** 只报红肉时的总重下限（%） */
    private final int minLeanOnlyRate;
    /** 总重上限（%），超过即视为重复上报或虚报 */
    private final int maxOverRate;

    public SplitYieldCheck(MeatPartMapper meatPartMapper,
                           @Value("${ingest.split-job.min-yield-rate:85}") int minYieldRate,
                           @Value("${ingest.split-job.min-lean-only-rate:60}") int minLeanOnlyRate,
                           @Value("${ingest.split-job.max-over-rate:105}") int maxOverRate) {
        this.meatPartMapper = meatPartMapper;
        this.minYieldRate = minYieldRate;
        this.minLeanOnlyRate = minLeanOnlyRate;
        this.maxOverRate = maxOverRate;
    }

    /**
     * @param lines          本次作业切出的全部部位
     * @param parentWeightKg 对账基准：这头白条的重量；为 null 或非正数则跳过重量类判定
     */
    public Conclusion check(List<Line> lines, BigDecimal parentWeightKg) {
        // 字典只有十几条且几乎不变，直接查库；分割上报不是高频路径，暂不引入缓存
        Map<String, MeatPart> dict = meatPartMapper.selectList(null).stream()
                .collect(Collectors.toMap(MeatPart::getPartCode, p -> p, (a, b) -> a, LinkedHashMap::new));

        Set<String> present = new LinkedHashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal lean = BigDecimal.ZERO;
        boolean hasWhole = false;
        boolean hasBulk = false;
        for (Line line : lines) {
            if (line.partCode() == null) {
                continue;
            }
            present.add(line.partCode());
            MeatPart part = dict.get(line.partCode());
            String category = part == null || part.getCategory() == null ? "" : part.getCategory();
            BigDecimal weight = line.weightKg() == null ? BigDecimal.ZERO : line.weightKg();
            total = total.add(weight);
            if (CAT_LEAN.equals(category)) {
                lean = lean.add(weight);
            }
            if (CAT_WHOLE.equals(category)) {
                hasWhole = true;
            }
            if (CAT_BULK.contains(category)) {
                hasBulk = true;
            }
        }

        List<String> problems = new ArrayList<>();

        // ① 必出项
        List<String> missing = dict.values().stream()
                .filter(p -> p.getRequired() != null && p.getRequired() == 1)
                .filter(p -> !present.contains(p.getPartCode()))
                .sorted(Comparator.comparing(MeatPart::getPartCode))
                .map(MeatPart::getPartName)
                .toList();
        if (!missing.isEmpty()) {
            problems.add("缺少必出部位：" + String.join("、", missing));
        }

        // ② 出肉率对账
        BigDecimal totalRate = null;
        BigDecimal leanRate = null;
        String noBaselineNote = "";
        if (parentWeightKg != null && parentWeightKg.signum() > 0) {
            totalRate = percent(total, parentWeightKg);
            leanRate = percent(lean, parentWeightKg);
            if (totalRate.compareTo(BigDecimal.valueOf(maxOverRate)) > 0) {
                problems.add("各部位合计 " + plain(total) + "kg，占白条 " + plain(parentWeightKg) + "kg 的 "
                        + plain(totalRate) + "%，超过上限 " + maxOverRate + "%（疑似重复上报或虚报）");
            } else if (!hasWhole) {
                int floor = hasBulk ? minYieldRate : minLeanOnlyRate;
                if (totalRate.compareTo(BigDecimal.valueOf(floor)) < 0) {
                    problems.add("各部位合计 " + plain(total) + "kg，仅占白条 " + plain(parentWeightKg) + "kg 的 "
                            + plain(totalRate) + "%，低于下限 " + floor + "%"
                            + (hasBulk ? "" : "（本次未报骨/脂/皮/副产，按只报红肉口径判）")
                            + "，疑似有产出未上报");
                }
            }
        } else {
            noBaselineNote = "胴体批次未记录单头重量，本次未做出肉率对账";
        }

        if (problems.isEmpty()) {
            String summary = totalRate == null ? noBaselineNote
                    : "出肉率核对通过：合计 " + plain(total) + "kg / 白条 " + plain(parentWeightKg) + "kg = "
                      + plain(totalRate) + "%（其中红肉 " + plain(leanRate) + "%）";
            return new Conclusion(PASS, summary, total, totalRate, leanRate);
        }
        return new Conclusion(MANUAL, String.join("；", problems), total, totalRate, leanRate);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "--" : value.stripTrailingZeros().toPlainString();
    }

    // ========== 「齐不齐」的对照口径（供拆分树显示「未产出标准部位」用） ==========

    /**
     * 标准部位清单：启用中的字典项，<b>排除整条/半片形态（{@code WHOLE}）</b>。
     * <p>
     * 二分体是「这头白条要不要再分割」的问题，不是「切出了什么部位」。把它算进对照清单，
     * 整条出货的作业会被显示成「缺十几个部位」——那不是可疑，是口径串了。
     * 清单本身是固定的（字典不变，一头猪按标准该切出哪些部位就不变），变的只是本次报了什么。
     */
    public List<MeatPart> standardParts() {
        return meatPartMapper.selectList(Wrappers.<MeatPart>lambdaQuery()
                .eq(MeatPart::getEnabled, 1)
                .ne(MeatPart::getCategory, CAT_WHOLE)
                .orderByAsc(MeatPart::getSortNo));
    }

    /** 本次作业未报的标准部位（按字典顺序）——拆分树据此做「实产 vs 标准」对照 */
    public List<MeatPart> missingStandardParts(Collection<String> presentCodes) {
        Set<String> present = presentCodes == null ? Set.of() : new LinkedHashSet<>(presentCodes);
        return standardParts().stream()
                .filter(p -> !present.contains(p.getPartCode()))
                .toList();
    }
}
