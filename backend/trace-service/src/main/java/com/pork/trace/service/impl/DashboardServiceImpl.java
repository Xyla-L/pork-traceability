package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.trace.client.TraceRemoteClient;
import com.pork.trace.entity.ComplaintReport;
import com.pork.trace.mapper.ComplaintReportMapper;
import com.pork.trace.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private final TraceRemoteClient clients;
    private final ComplaintReportMapper complaintMapper;

    @Override
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        Map<String, Object> pigs = map(clients.breedingPigs(1));
        Map<String, Object> stamps = map(clients.slaughterStamps(today.toString()));
        Map<String, Object> transports = map(clients.transports(2));
        Map<String, Object> batches = map(clients.batches());
        Map<String, Object> sales = map(clients.salesQrs());
        Map<String, Object> warnings = map(clients.warnings());
        Map<String, Object> chain = map(clients.chainRecords());

        long pigCount = total(pigs);
        long slaughterCount = total(stamps);
        long transportCount = total(transports);
        long expireCount = total(warnings);
        long complaintCount = complaintMapper.selectCount(Wrappers.<ComplaintReport>lambdaQuery()
                .in(ComplaintReport::getStatus, 0, 1));
        List<Map<String, Object>> chainRows = records(chain);
        long chainCount = chainRows.stream().filter(row -> inCurrentMonth(row.get("chainTime"), row.get("createTime"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statCards", List.of(
                stat("pigCount", "在养生猪数", pigCount),
                stat("slaughterCount", "今日屠宰量", slaughterCount),
                stat("transportCount", "在途批次", transportCount),
                stat("expireCount", "临期产品数", expireCount),
                stat("complaintCount", "待处理举报", complaintCount),
                stat("chainCount", "本月上链数", chainCount)));
        result.put("sankey", sankey(pigCount, total(batches), total(sales)));
        result.put("expireDistribution", warningDistribution(records(warnings), true));
        result.put("warningDistribution", warningDistribution(records(warnings), false));
        result.put("chainTxTrend", chainTrend(chainRows));
        result.put("recentWarnings", recentWarnings(records(warnings)));
        result.put("todoList", complaintTodos());
        return result;
    }

    private Map<String, Object> stat(String key, String label, long value) {
        return Map.of("key", key, "label", label, "value", value);
    }

    private Map<String, Object> sankey(long pigs, long batchCount, long saleCount) {
        long slaughter = Math.min(pigs, batchCount == 0 ? pigs : batchCount);
        long distribution = batchCount;
        long sales = saleCount;
        long consumer = sales;
        List<Map<String, Object>> nodes = List.of(node("养殖免疫", pigs), node("屠宰检疫", slaughter),
                node("分割配送", distribution), node("市场销售", sales), node("消费者", consumer));
        List<Map<String, Object>> links = List.of(link("养殖免疫", "屠宰检疫", slaughter),
                link("屠宰检疫", "分割配送", distribution), link("分割配送", "市场销售", sales),
                link("市场销售", "消费者", consumer));
        return Map.of("nodes", nodes, "links", links);
    }

    private Map<String, Object> node(String name, long value) { return Map.of("name", name, "value", value); }
    private Map<String, Object> link(String source, String target, long value) {
        return Map.of("source", source, "target", target, "value", value);
    }

    private List<Map<String, Object>> warningDistribution(List<Map<String, Object>> rows, boolean includeSafe) {
        long level1 = countLevel(rows, 1);
        long level2 = countLevel(rows, 2);
        long level3 = countLevel(rows, 3);
        List<Map<String, Object>> result = new ArrayList<>();
        if (includeSafe) result.add(node("安全（>7天）", 0));
        result.add(node("临期3天", level1));
        result.add(node("临期1天", level2));
        result.add(node("已过期", level3));
        return result;
    }

    private long countLevel(List<Map<String, Object>> rows, int level) {
        return rows.stream().filter(row -> number(row.get("warningLevel")) == level).count();
    }

    private List<Map<String, Object>> recentWarnings(List<Map<String, Object>> rows) {
        return rows.stream().limit(5).map(row -> {
            long saleId = number(row.get("saleId"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productName", "产品 #" + saleId);
            item.put("batchNo", "SALE-" + saleId);
            item.put("warningLevel", levelLabel((int) number(row.get("warningLevel"))));
            item.put("expireDate", date(row.get("warningTime")));
            item.put("storeName", "-");
            return item;
        }).toList();
    }

    private List<Map<String, Object>> complaintTodos() {
        return complaintMapper.selectList(Wrappers.<ComplaintReport>lambdaQuery().in(ComplaintReport::getStatus, 0, 1)
                        .orderByAsc(ComplaintReport::getCreateTime).last("LIMIT 10")).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", "处理举报 #" + row.getReportNo());
            item.put("type", "举报处理");
            item.put("time", row.getCreateTime());
            item.put("status", Integer.valueOf(0).equals(row.getStatus()) ? "待处理" : "进行中");
            return item;
        }).toList();
    }

    private List<Map<String, Object>> chainTrend(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = localDate(row.get("chainTime"));
            if (date == null) date = localDate(row.get("createTime"));
            if (date != null) counts.merge(date, 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter label = DateTimeFormatter.ofPattern("M/d");
        for (int i = 29; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            result.add(Map.of("date", label.format(day), "count", counts.getOrDefault(day, 0L)));
        }
        return result;
    }

    private boolean inCurrentMonth(Object primary, Object fallback) {
        LocalDate value = localDate(primary);
        if (value == null) value = localDate(fallback);
        return value != null && YearMonth.from(value).equals(YearMonth.now());
    }

    private LocalDate localDate(Object value) {
        if (value == null) return null;
        String text = value.toString();
        try { return LocalDate.parse(text.substring(0, Math.min(10, text.length()))); }
        catch (RuntimeException ignored) { return null; }
    }

    private String date(Object value) {
        LocalDate date = localDate(value);
        return date == null ? "" : date.toString();
    }

    private String levelLabel(int level) {
        return switch (level) { case 1 -> "临期3天"; case 2 -> "临期1天"; case 3 -> "已过期"; default -> "未知"; };
    }

    private long total(Map<String, Object> page) { return number(page.get("total")); }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> page) {
        Object value = page.get("records");
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(Map.class::isInstance).map(valueMap -> (Map<String, Object>) valueMap).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0 : Long.parseLong(value.toString()); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
