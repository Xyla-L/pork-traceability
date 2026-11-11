package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.service.RecordGroupService;
import com.pork.breeding.support.FarmScope;
import com.pork.breeding.vo.RecordGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 分组视图实现：一次 join 取全，再在内存里归组、排序、分页。
 * <p>
 * 为什么在内存里分组而不是写 GROUP BY：分组后主行要带上「头数、耳标区间、作废数、状态摘要」，
 * 同时还要把逐头明细一起返回；SQL 分组只能给汇总，明细还得再查一次，反而更绕。
 * 这里用取数上限兜住规模（{@link #MAX_ROWS}），中小体量下最直白也最好维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordGroupServiceImpl implements RecordGroupService {

    /**
     * 单次取数上限。分组必须看到全量才能算准头数与区间，所以不能分页下推；
     * 达到上限说明该用真单据表（方案 B）了，日志会留痕。
     */
    private static final int MAX_ROWS = 50000;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final VaccineRecordMapper vaccineRecordMapper;
    private final QuarantineCertMapper quarantineCertMapper;
    private final SlaughterApplyMapper slaughterApplyMapper;
    private final FarmScope farmScope;

    // ==================== 疫苗 ====================

    @Override
    public Page<RecordGroupVO> pageVaccine(Long current, Long size, String earTagNo,
                                           String vaccineName, boolean includeVoid) {
        List<Map<String, Object>> rows = vaccineRecordMapper.selectGroupRows(
                blankToNull(earTagNo), includeVoid, farmScope.scopedFarmId(), MAX_ROWS);
        warnIfTruncated(rows, "VACCINE");

        // 分组键：批次 + 疫苗名称 + 疫苗批号 + 注射日期。
        // 同一批同一天打同一种疫苗 = 一次免疫动作；换疫苗或换日期就是另一次，不能并。
        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = groupKey("VACCINE", row, "injectTime",
                    str(row.get("vaccineName")) + "\u0001" + str(row.get("batchNo")));
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<RecordGroupVO> groups = new ArrayList<>();
        buckets.forEach((key, members) -> {
            RecordGroupVO g = baseGroup(key, "VACCINE", members, "injectTime");
            Map<String, Object> first = members.get(0);
            g.setTitle(str(first.get("vaccineName")));
            String vacBatch = str(first.get("batchNo"));
            g.setSubTitle(vacBatch != null ? "疫苗批号 " + vacBatch : str(first.get("manufacturer")));
            // 状态摘要只给结论，明细进 statusDetail：
            // 原来「有效 0 / 已作废 1」是在描述一条被作废的单头记录，读起来却像「有一批但全废了」，
            // 三态分开——全有效 / 部分有效 / 全部作废——才和用户脑子里的判断对得上。
            int voided = countVoided(members);
            int total = members.size();
            if (voided == 0) {
                g.setStatusSummary("全部有效");
                g.setStatusDetail(total + " 条免疫记录全部有效");
            } else if (voided == total) {
                g.setStatusSummary("全部作废");
                g.setStatusDetail("已作废 " + voided);
            } else {
                g.setStatusSummary("部分有效");
                g.setStatusDetail("有效 " + (total - voided) + " / 已作废 " + voided);
            }
            groups.add(g);
        });

        if (StringUtils.hasText(vaccineName)) {
            String kw = vaccineName.trim();
            // 疫苗名称在分组键里，所以这里是整组命中，不会把一次免疫拆散
            groups.removeIf(g -> g.getTitle() == null || !g.getTitle().contains(kw));
        }
        return paginate(sortDesc(groups), current, size);
    }

    // ==================== 产地检疫 ====================

    @Override
    public Page<RecordGroupVO> pageCert(Long current, Long size, String earTagNo, String certState) {
        List<Map<String, Object>> rows = quarantineCertMapper.selectGroupRows(blankToNull(earTagNo), farmScope.scopedFarmId(), MAX_ROWS);
        warnIfTruncated(rows, "CERT");

        // 分组键：批次 + 出证机构 + 出证日期。
        // 不按证号分组：本系统存量是「一证一头」（设备逐头出证），按证号分等于没分组。
        // 现实里一张证覆盖一批，将来补了主单（方案 B）这里换成证号即可。
        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = groupKey("CERT", row, "issueTime", str(row.get("issueOrg")));
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<RecordGroupVO> groups = new ArrayList<>();
        buckets.forEach((key, members) -> {
            RecordGroupVO g = baseGroup(key, "CERT", members, "issueTime");
            Set<String> certNos = distinct(members, "certNo");
            g.setTitle(certNos.isEmpty() ? "未记录证号"
                    : certNos.size() == 1 ? certNos.iterator().next()
                    : certNos.iterator().next() + " 等 " + certNos.size() + " 张");
            g.setSubTitle(str(members.get(0).get("issueOrg")));
            int voided = countVoided(members);
            int expired = 0;
            for (Map<String, Object> m : members) {
                if (isVoided(m)) continue;
                if (isExpired(m.get("validUntil"))) expired++;
            }
            int alive = members.size() - voided;
            int aliveUnexpired = alive - expired;
            // 与疫苗同一套三态：结论文案（全部有效 / 部分有效 / 全部失效）挂标签，
            // 「有效 0 / 已作废 1」这类计数只做 tooltip —— 计数本身不会错，错在它被当成了结论。
            if (voided == 0 && expired == 0) {
                g.setStatusSummary("全部有效");
                g.setStatusDetail(members.size() + " 张证全部在有效期内");
            } else if (voided == members.size()) {
                g.setStatusSummary("全部作废");
                g.setStatusDetail("已作废 " + voided);
            } else if (aliveUnexpired == 0) {
                g.setStatusSummary("全部失效");
                g.setStatusDetail(detailOf(aliveUnexpired, expired, voided));
            } else {
                g.setStatusSummary("部分有效");
                g.setStatusDetail(detailOf(aliveUnexpired, expired, voided));
            }
            groups.add(g);
        });

        if ("valid".equalsIgnoreCase(certState)) {
            groups.removeIf(g -> !hasAliveUnexpired(g));
        } else if ("expired".equalsIgnoreCase(certState)) {
            groups.removeIf(this::hasAliveUnexpired);
        }
        return paginate(sortDesc(groups), current, size);
    }

    // ==================== 出栏申报 ====================

    @Override
    public Page<RecordGroupVO> pageApply(Long current, Long size, Integer approvalStatus) {
        List<Map<String, Object>> rows = slaughterApplyMapper.selectGroupRows(approvalStatus, farmScope.scopedFarmId(), MAX_ROWS);
        warnIfTruncated(rows, "APPLY");

        // 分组键：批次 + 申报日期。申报单一号一头是本系统的存量形态，
        // 现实里一份单含多头，按「批次 + 当天」归即一次出栏申报动作。
        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = groupKey("APPLY", row, "applyTime", "");
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<RecordGroupVO> groups = new ArrayList<>();
        buckets.forEach((key, members) -> {
            RecordGroupVO g = baseGroup(key, "APPLY", members, "applyTime");
            Set<String> applyNos = distinct(members, "applyNo");
            g.setTitle(applyNos.isEmpty() ? "未记录单号"
                    : applyNos.size() == 1 ? applyNos.iterator().next()
                    : applyNos.iterator().next() + " 等 " + applyNos.size() + " 单");
            g.setSubTitle(str(members.get(0).get("targetSlaughterhouse")));

            int pass = 0;
            int wait = 0;
            int reject = 0;
            for (Map<String, Object> m : members) {
                Integer st = toInt(m.get("approvalStatus"));
                if (st == null) continue;
                if (st == 1) pass++;
                else if (st == 2) reject++;
                else wait++;
            }
            int total = members.size();
            // 状态摘要只给结论，计数进 statusDetail：
            // 原来直接拼「待审 1」这种计数，单头组看起来像「待审的单号 1」，读不成句子。
            if (wait == total) {
                g.setStatusSummary("全部待审");
            } else if (wait > 0) {
                g.setStatusSummary("待审中");
            } else if (reject == 0) {
                g.setStatusSummary("全部通过");
            } else if (pass == 0) {
                g.setStatusSummary("全部驳回");
            } else {
                g.setStatusSummary("含驳回");
            }
            StringBuilder detail = new StringBuilder();
            if (pass > 0) detail.append("已通过 ").append(pass);
            if (wait > 0) detail.append(detail.length() > 0 ? " / " : "").append("待审 ").append(wait);
            if (reject > 0) detail.append(detail.length() > 0 ? " / " : "").append("已驳回 ").append(reject);
            g.setStatusDetail(detail.length() > 0 ? detail.toString() : "--");
            groups.add(g);
        });

        return paginate(sortDesc(groups), current, size);
    }

    // ==================== 分组骨架 ====================

    /**
     * 组键 = 类型 + 归属范围 + 业务签名 + 发生日期。
     * <p>
     * <b>未归批的猪按头独立成组</b>（范围用 {@code P+pigId} 而不是 {@code B+batchId}）。
     * 这一点很关键：未归批的猪没有批次可依靠，若只按「日期 + 签名」归组，
     * 不同养殖场、毫无关系的几头猪会被并到一行，看起来像同一批——那是凭空造出批次关系。
     */
    private String groupKey(String kind, Map<String, Object> row, String timeField, String signature) {
        Object batchId = row.get("batchId");
        Object pigId = row.get("pigId");
        String scope = batchId != null ? "B" + batchId : "P" + pigId;
        return kind + "|" + scope + "|" + signature + "|" + dateText(row.get(timeField));
    }

    /** 组内公共字段：批次、场、头数、耳标区间、操作人、日期、逐头明细 */
    private RecordGroupVO baseGroup(String key, String kind,
                                    List<Map<String, Object>> members, String timeField) {
        RecordGroupVO g = new RecordGroupVO();
        g.setGroupKey(key);
        g.setKind(kind);
        Map<String, Object> first = members.get(0);

        Object batchId = first.get("batchId");
        g.setBatchId(batchId == null ? null : ((Number) batchId).longValue());
        g.setBatchNo(str(first.get("pigBatchNo")));
        // 品种优先取批次档的：批次登记时定的品种是整批口径，个体行只是登记快照
        g.setBreed(str(first.get("batchBreed")) != null ? str(first.get("batchBreed")) : str(first.get("pigBreed")));
        Object farmId = first.get("farmId");
        g.setFarmId(farmId == null ? null : ((Number) farmId).longValue());
        g.setFarmName(str(first.get("farmName")));

        g.setHeadCount(members.size());
        g.setVoidCount(countVoided(members));
        g.setHappenedOn(dateText(first.get(timeField)));
        g.setOperator(operatorSummary(members));

        List<String> tags = new ArrayList<>();
        for (Map<String, Object> m : members) {
            String tag = str(m.get("earTagNo"));
            if (tag != null) tags.add(tag);
        }
        if (!tags.isEmpty()) {
            tags.sort(Comparator.naturalOrder());
            g.setTagStart(tags.get(0));
            g.setTagEnd(tags.get(tags.size() - 1));
        }
        g.setMembers(members);
        return g;
    }

    /** 操作人汇总：一个人就报名字，多个人报「首位 等N人」（实名信息不省略成「系统」） */
    private String operatorSummary(List<Map<String, Object>> members) {
        Set<String> names = distinct(members, "operator");
        names.removeIf(n -> "未记录".equals(n));
        if (names.isEmpty()) return null;
        String first = names.iterator().next();
        return names.size() == 1 ? first : first + " 等" + names.size() + "人";
    }

    /** 检疫证状态明细：「有效 28 / 已过期 2 / 已作废 1」，只拼非零项 */
    private String detailOf(int alive, int expired, int voided) {
        StringBuilder sb = new StringBuilder("有效 ").append(alive);
        if (expired > 0) sb.append(" / 已过期 ").append(expired);
        if (voided > 0) sb.append(" / 已作废 ").append(voided);
        return sb.toString();
    }

    private Set<String> distinct(List<Map<String, Object>> members, String field) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> m : members) {
            String v = str(m.get(field));
            if (v != null) out.add(v);
        }
        return out;
    }

    private int countVoided(List<Map<String, Object>> members) {
        int n = 0;
        for (Map<String, Object> m : members) {
            if (isVoided(m)) n++;
        }
        return n;
    }

    private boolean isVoided(Map<String, Object> m) {
        Integer f = toInt(m.get("voidFlag"));
        return f != null && f == 1;
    }

    private boolean isExpired(Object validUntil) {
        LocalDate d = toDate(validUntil);
        return d != null && d.isBefore(LocalDate.now());
    }

    /** 组内是否还有「未作废且未过期」的证 —— 用于证状态筛选 */
    private boolean hasAliveUnexpired(RecordGroupVO g) {
        return g.getMembers().stream()
                .anyMatch(m -> !isVoided(m) && !isExpired(m.get("validUntil")));
    }

    // ==================== 排序与分页 ====================

    /** 组内时间倒序（新操作在前）；同一天按组键稳定排序，翻页不会跳来跳去 */
    private List<RecordGroupVO> sortDesc(List<RecordGroupVO> groups) {
        groups.sort(Comparator
                .comparing((RecordGroupVO g) -> Objects.toString(g.getHappenedOn(), ""))
                .thenComparing(g -> Objects.toString(g.getGroupKey(), ""))
                .reversed());
        return groups;
    }

    /** 按「组」分页：total 是组数而不是记录条数，前端翻页才不会错位 */
    private Page<RecordGroupVO> paginate(List<RecordGroupVO> groups, Long current, Long size) {
        long cur = current == null || current < 1 ? 1 : current;
        long sz = size == null || size < 1 ? 10 : size;
        Page<RecordGroupVO> page = new Page<>(cur, sz);
        page.setTotal(groups.size());
        int from = (int) Math.min((cur - 1) * sz, groups.size());
        int to = (int) Math.min(from + sz, groups.size());
        page.setRecords(new ArrayList<>(groups.subList(from, to)));
        return page;
    }

    // ==================== 小工具 ====================

    private void warnIfTruncated(List<Map<String, Object>> rows, String kind) {
        if (rows.size() >= MAX_ROWS) {
            log.warn("record_group_truncated kind={} rows={} —— 已达取数上限，分组统计可能不准，该补单据表了", kind, rows.size());
        }
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 日期统一成 yyyy-MM-dd 字符串：分组键要比字符串，前端也直接显示，不来回转时区 */
    private String dateText(Object v) {
        LocalDate d = toDate(v);
        return d == null ? null : d.format(DAY);
    }

    private LocalDate toDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof LocalDateTime dt) return dt.toLocalDate();
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (v instanceof java.sql.Date sd) return sd.toLocalDate();
        String s = String.valueOf(v);
        if (s.length() < 10) return null;
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
