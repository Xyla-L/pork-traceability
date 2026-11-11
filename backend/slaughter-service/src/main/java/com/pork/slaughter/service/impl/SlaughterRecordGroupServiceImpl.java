package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.service.CarcassStampService;
import com.pork.slaughter.service.EntryInspectionService;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.service.SlaughterInspectionService;
import com.pork.slaughter.service.SlaughterRecordGroupService;
import com.pork.slaughter.vo.CarcassStampVO;
import com.pork.slaughter.vo.EntryInspectionVO;
import com.pork.slaughter.vo.RactopamineTestVO;
import com.pork.slaughter.vo.SlaughterGroupVO;
import com.pork.slaughter.vo.SlaughterInspectionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 屠宰四张列表「按批次折叠」的实现。
 * <p>
 * 取数复用各业务 service 的既有分页查询（pageSize 拉满再在内存归组），不新写 SQL：
 * 折叠是读法，不是新事实——一旦这里自己写 select，筛选条件就有两份，早晚跟原列表对不上。
 * <p>
 * 分组键只有「批次号」：屠宰是流水线，同一批猪陆续进场、陆续宰、陆续盖章，跨天是常态，
 * 按「批次 + 日期」切会把一批剁成好几行，反而看不懂。没有批次号的记录<b>按头独立成组</b>，
 * 绝不与别的无批次猪合并——那等于凭空造出批次关系。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaughterRecordGroupServiceImpl implements SlaughterRecordGroupService {

    /** 单次取数上限：分组要看全量才能算准头数与区间，达到上限说明该走聚合表了，日志留痕 */
    private static final int MAX_ROWS = 50000;

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EntryInspectionService entryInspectionService;
    private final SlaughterInspectionService slaughterInspectionService;
    private final CarcassStampService carcassStampService;
    private final RactopamineTestService ractopamineTestService;

    // ==================== 入场查验 ====================

    @Override
    public Page<SlaughterGroupVO> pageEntries(Long current, Long size, EntryInspectionDTO dto) {
        List<EntryInspectionVO> rows = entryInspectionService.pageQuery(dto, 1, MAX_ROWS).getRecords();
        warnIfTruncated(rows.size(), "ENTRY");

        List<SlaughterGroupVO> groups = group("ENTRY", rows,
                EntryInspectionVO::getBatchNo, EntryInspectionVO::getPigId, EntryInspectionVO::getId,
                EntryInspectionVO::getEarTagNo, EntryInspectionVO::getArriveTime,
                (g, members) -> {
                    int pass = 0, wait = 0, bad = 0;
                    for (EntryInspectionVO m : members) {
                        Integer s = m.getStatus();
                        if (s == null || s == 0) wait++;
                        else if (s == 1) pass++;
                        else bad++;
                    }
                    // 结论先看有没有不合格：不合格是不看不行的，合格与否只是计数
                    if (bad > 0) {
                        g.setStatusSummary(pass == 0 && wait == 0 ? "全部不合格" : "含不合格");
                    } else if (wait == 0) {
                        g.setStatusSummary("全部合格");
                    } else if (pass == 0) {
                        g.setStatusSummary("全部待查验");
                    } else {
                        g.setStatusSummary("部分合格");
                    }
                    g.setStatusDetail(countText("合格", pass, "待查验", wait, "不合格", bad));
                });
        return paginate(groups, current, size);
    }

    // ==================== 屠宰检验 ====================

    @Override
    public Page<SlaughterGroupVO> pageInspections(Long current, Long size, SlaughterInspectionDTO dto) {
        SlaughterInspectionDTO query = dto == null ? new SlaughterInspectionDTO() : dto;
        // 折叠必须看全量，分页参数在这里强制覆盖，避免前端传进来的 pageSize 把分组截断
        query.setPageNum(1);
        query.setPageSize(MAX_ROWS);
        List<SlaughterInspectionVO> rows = slaughterInspectionService.pageQuery(query).getRecords();
        warnIfTruncated(rows.size(), "INSPECT");

        List<SlaughterGroupVO> groups = group("INSPECT", rows,
                SlaughterInspectionVO::getBatchNo, SlaughterInspectionVO::getPigId, SlaughterInspectionVO::getId,
                SlaughterInspectionVO::getEarTagNo, SlaughterInspectionVO::getInspectTime,
                (g, members) -> {
                    int pass = 0, wait = 0, bad = 0, voided = 0;
                    for (SlaughterInspectionVO m : members) {
                        Integer s = m.getStatus();
                        if (s == null || s == 0) wait++;
                        else if (s == 1) pass++;
                        else if (s == 2) bad++;
                        else voided++;
                    }
                    if (voided == members.size()) {
                        g.setStatusSummary("全部作废");
                    } else if (bad > 0) {
                        g.setStatusSummary(pass == 0 && wait == 0 ? "全部不合格" : "含不合格");
                    } else if (wait == 0) {
                        g.setStatusSummary("全部合格");
                    } else if (pass == 0) {
                        g.setStatusSummary("全部待检验");
                    } else {
                        g.setStatusSummary("部分合格");
                    }
                    g.setStatusDetail(countText("合格", pass, "待检验", wait, "不合格", bad, "已作废", voided));
                });
        return paginate(groups, current, size);
    }

    // ==================== 检疫盖章 ====================

    @Override
    public Page<SlaughterGroupVO> pageStamps(Long current, Long size, CarcassStampDTO dto,
                                             String status, String startDate, String endDate) {
        CarcassStampDTO query = dto == null ? new CarcassStampDTO() : dto;
        List<CarcassStampVO> rows = carcassStampService
                .pageQuery(new Page<>(1, MAX_ROWS), query, status, startDate, endDate)
                .getRecords();
        warnIfTruncated(rows.size(), "STAMP");

        List<SlaughterGroupVO> groups = group("STAMP", rows,
                CarcassStampVO::getBatchNo, CarcassStampVO::getPigId, CarcassStampVO::getId,
                CarcassStampVO::getEarTagNo, CarcassStampVO::getStampTime,
                (g, members) -> {
                    int stamped = 0, pending = 0, voided = 0;
                    for (CarcassStampVO m : members) {
                        Integer s = m.getStatus();
                        if (s == null || s == 0) pending++;
                        else if (s == 1) stamped++;
                        else voided++;
                    }
                    if (voided == members.size()) {
                        g.setStatusSummary("全部作废");
                    } else if (pending == 0) {
                        g.setStatusSummary("全部已盖章");
                    } else if (stamped == 0) {
                        g.setStatusSummary("全部待盖章");
                    } else {
                        g.setStatusSummary("部分已盖章");
                    }
                    g.setStatusDetail(countText("已盖章", stamped, "待盖章", pending, "已作废", voided));
                });
        return paginate(groups, current, size);
    }

    // ==================== 瘦肉精检测 ====================

    @Override
    public Page<SlaughterGroupVO> pageRactopamine(Long current, Long size, RactopamineTestDTO dto) {
        RactopamineTestDTO query = dto == null ? new RactopamineTestDTO() : dto;
        query.setPageNum(1);
        query.setPageSize(MAX_ROWS);
        List<RactopamineTestVO> rows = ractopamineTestService.pageQuery(query).getRecords();
        warnIfTruncated(rows.size(), "RACTO");

        List<SlaughterGroupVO> groups = group("RACTO", rows,
                RactopamineTestVO::getBatchNo, RactopamineTestVO::getPigId, RactopamineTestVO::getId,
                RactopamineTestVO::getEarTagNo, RactopamineTestVO::getTestTime,
                (g, members) -> {
                    int negative = 0, positive = 0, undone = 0;
                    for (RactopamineTestVO m : members) {
                        Integer st = m.getStatus();
                        // 未完成（待检测/检测中）不算结果：结果为空时不能当成阴性
                        if (st != null && st != 2) {
                            undone++;
                        } else if (m.getResult() != null && m.getResult() == 0) {
                            positive++;
                        } else if (m.getResult() != null) {
                            negative++;
                        } else {
                            undone++;
                        }
                    }
                    // 阳性是必须拦下来的结论，优先于一切计数
                    if (positive > 0) {
                        g.setStatusSummary(positive == members.size() ? "全部阳性" : "含阳性");
                    } else if (undone == 0) {
                        g.setStatusSummary("全部阴性");
                    } else if (negative == 0) {
                        g.setStatusSummary("全部待检测");
                    } else {
                        g.setStatusSummary("部分已检");
                    }
                    g.setStatusDetail(countText("阴性", negative, "阳性", positive, "未完成", undone));
                });
        return paginate(groups, current, size);
    }

    // ==================== 分组骨架 ====================

    /**
     * 按批次归组；没有批次号的按头独立成组。
     *
     * @param idOf       记录主键：批次号与 pigId 都缺失时用它兜底，保证不同记录不会被并到一起
     * @param summarizer 每类记录自己的结论与计数口径（不在这里写 switch，免得四套口径混在一起）
     */
    private <T> List<SlaughterGroupVO> group(String kind, List<T> rows,
                                             Function<T, String> batchNoOf,
                                             Function<T, Long> pigIdOf,
                                             Function<T, Long> idOf,
                                             Function<T, String> earTagOf,
                                             Function<T, LocalDateTime> timeOf,
                                             BiConsumer<SlaughterGroupVO, List<T>> summarizer) {
        Map<String, List<T>> buckets = new LinkedHashMap<>();
        for (T row : rows) {
            String batchNo = trimToNull(batchNoOf.apply(row));
            String scope;
            if (batchNo != null) {
                scope = "B:" + batchNo;
            } else {
                Long pigId = pigIdOf.apply(row);
                scope = "P:" + (pigId != null ? pigId : "ROW" + idOf.apply(row));
            }
            buckets.computeIfAbsent(kind + "|" + scope, k -> new ArrayList<>()).add(row);
        }

        List<SlaughterGroupVO> groups = new ArrayList<>();
        buckets.forEach((key, members) -> {
            SlaughterGroupVO g = new SlaughterGroupVO();
            g.setGroupKey(key);
            g.setKind(kind);
            g.setHeadCount(members.size());

            String batchNo = trimToNull(batchNoOf.apply(members.get(0)));
            g.setBatchNo(batchNo);
            // 未归批才带 pigId：归批的行带 pigId 会让前端误以为这一组只属于那头猪
            g.setPigId(batchNo == null ? pigIdOf.apply(members.get(0)) : null);

            List<String> tags = new ArrayList<>();
            String minTime = null;
            String maxTime = null;
            for (T m : members) {
                String tag = trimToNull(earTagOf.apply(m));
                if (tag != null) tags.add(tag);
                String t = timeText(timeOf.apply(m));
                if (t != null) {
                    if (minTime == null || t.compareTo(minTime) < 0) minTime = t;
                    if (maxTime == null || t.compareTo(maxTime) > 0) maxTime = t;
                }
            }
            if (!tags.isEmpty()) {
                tags.sort(Comparator.naturalOrder());
                g.setTagStart(tags.get(0));
                g.setTagEnd(tags.get(tags.size() - 1));
            }
            g.setFirstTime(minTime);
            g.setLastTime(maxTime);

            summarizeSafely(g, members, summarizer);
            g.setMembers(new ArrayList<Object>(members));
            groups.add(g);
        });

        // 最新动作在前；同一时刻按组键稳定排序，翻页不会来回跳
        groups.sort(Comparator
                .comparing((SlaughterGroupVO g) -> Objects.toString(g.getLastTime(), ""))
                .thenComparing(g -> Objects.toString(g.getGroupKey(), ""))
                .reversed());
        return groups;
    }

    /** 摘要口径异常不该拖垮整页列表：兜底成「待人工核对」，绝不冒充「全部合格」 */
    private <T> void summarizeSafely(SlaughterGroupVO g, List<T> members,
                                     BiConsumer<SlaughterGroupVO, List<T>> summarizer) {
        try {
            summarizer.accept(g, members);
        } catch (RuntimeException e) {
            log.warn("slaughter_group_summary_failed kind={} key={}", g.getKind(), g.getGroupKey(), e);
            g.setStatusSummary("待核对");
            g.setStatusDetail(null);
        }
        if (g.getStatusSummary() == null) {
            g.setStatusSummary("待核对");
        }
    }

    /** 计数文案：「合格 28 / 待查验 1 / 不合格 1」，只拼非零项 */
    private String countText(Object... pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String label = String.valueOf(pairs[i]);
            int n = (int) pairs[i + 1];
            if (n <= 0) continue;
            if (sb.length() > 0) sb.append(" / ");
            sb.append(label).append(' ').append(n);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 按「组」分页：total 是组数，前端翻页才不会错位 */
    private Page<SlaughterGroupVO> paginate(List<SlaughterGroupVO> groups, Long current, Long size) {
        long cur = current == null || current < 1 ? 1 : current;
        long sz = size == null || size < 1 ? 10 : size;
        Page<SlaughterGroupVO> page = new Page<>(cur, sz);
        page.setTotal(groups.size());
        int from = (int) Math.min((cur - 1) * sz, groups.size());
        int to = (int) Math.min(from + sz, groups.size());
        page.setRecords(new ArrayList<>(groups.subList(from, to)));
        return page;
    }

    private void warnIfTruncated(int rows, String kind) {
        if (rows >= MAX_ROWS) {
            log.warn("slaughter_group_truncated kind={} rows={} —— 已达取数上限，折叠统计可能不准", kind, rows);
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String timeText(LocalDateTime t) {
        return t == null ? null : t.format(MINUTE);
    }
}
