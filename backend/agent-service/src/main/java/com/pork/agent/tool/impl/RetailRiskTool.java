package com.pork.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pork.agent.support.AgentContext;
import com.pork.agent.support.AnalyticsSupport;
import com.pork.agent.support.ReadOnlyRemote;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临期与召回：还剩多少件临期、多少件已经过期未处置、召回执行到哪一步。
 * <p>
 * 口径全部跟下游走，本工具不重算：
 * 预警级别 {@code expire_warning.warning_level} 1=临期3天 / 2=临期1天 / 3=已过期；
 * 召回状态 {@code recall_order.status} 1=已发布 / 2=执行中 / 3=已完成 / 4=已撤销。
 * <p>
 * 为什么这两件事放一个工具：它们回答的是同一个问题——「已经卖出去的货有没有风险」。
 * 拆成两个工具会让「临期和召回都有问题吗」这类问法落到一个上，另一个被漏掉。
 * <p>
 * 只读：处置（下架、销毁、完成召回）都要在销售管理页面里由人操作并留痕，这里没有写入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetailRiskTool implements AgentTool {

    private static final List<String> HINTS = List.of(
            "临期", "过期", "召回", "下架", "效期", "保质期", "快到期", "要坏的");

    /**
     * 让位：问的是「检疫证/免疫档案过期」时归检疫工具，
     * 问的是「这批货走到哪」时归全链路追溯——这里只管零售端的商品效期。
     */
    private static final List<String> YIELD_HINTS = List.of(
            "检疫", "免疫", "疫苗", "耳标", "追溯", "流向", "到哪", "出栏", "屠宰");

    private final ReadOnlyRemote remote;

    @Value("${services.sales-url:http://localhost:8085}")
    private String salesUrl;

    @Override
    public String name() {
        return "retail-risk";
    }

    @Override
    public String label() {
        return "临期与召回";
    }

    @Override
    public String description() {
        return "零售端效期风险：临期多少件、已过期未处置多少件、涉及哪些门店，以及召回单的执行进度（应召回多少件、已收回多少件）；问「临期有多少」「有没有过期的没处理」「召回到哪一步了」时用";
    }

    @Override
    public int priority() {
        return 17;
    }

    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR", "RETAILER");
    }

    @Override
    public String hint() {
        return "临期与召回：临期几件、过期未处置几件、召回进度";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        if (YIELD_HINTS.stream().anyMatch(question::contains)) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        return Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        List<String> facts = new ArrayList<>();
        Map<String, String> metrics = new LinkedHashMap<>();

        int pendingWarn = warnings(facts, metrics, ctx);
        int openRecall = recalls(facts, metrics, ctx);

        // 两类记录都为空时不必让模型组织语言，直接如实回一句——空数据最容易诱发「编个数字」的发挥
        if ("0".equals(metrics.get("warningTotal")) && "0".equals(metrics.get("recallTotal"))) {
            return ToolResult.empty(name(), label(), "当前没有临期预警，也没有召回单。");
        }
        facts.add("说明: 临期处置在「销售管理-临期预警」，召回执行在「销售管理-产品召回」，两处都需人工操作并留痕；本工具只报统计。");

        StringBuilder note = new StringBuilder("临期未处置 " + pendingWarn + " 件");
        if (metrics.containsKey("expiredPending")) {
            note.append("（其中已过期 ").append(metrics.get("expiredPending")).append(" 件）");
        }
        note.append("，未结召回 ").append(openRecall).append(" 单");
        if (metrics.containsKey("recallGap")) {
            note.append("、还有 ").append(metrics.get("recallGap")).append(" 件未收回");
        }
        note.append("。");
        return new ToolResult(name(), label(), note.toString(), facts, metrics);
    }

    /** @return 未处置的临期件数；全程无数据时不写 facts 也不登记指标 */
    private int warnings(List<String> facts, Map<String, String> metrics, AgentContext ctx) {
        JsonNode data = remote.getData(salesUrl + "/sales/warnings?pageNum=1&pageSize=200", ctx);
        JsonNode records = data == null ? null : data.get("records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            facts.add("临期预警: 当前没有任何预警记录。");
            metrics.put("warningTotal", "0");
            metrics.put("warningPending", "0");
            return 0;
        }

        int pending = 0, handled = 0;
        int[] byLevel = new int[4];
        Map<String, Integer> pendingByStore = new LinkedHashMap<>();
        List<String> expiredItems = new ArrayList<>();

        for (JsonNode row : records) {
            int level = row.path("warningLevel").asInt(0);
            boolean done = row.path("handled").asInt(0) == 1;
            if (done) {
                handled++;
                continue;
            }
            pending++;
            if (level >= 1 && level <= 3) byLevel[level]++;
            String store = row.path("storeName").asText("");
            if (!store.isBlank()) pendingByStore.merge(store, 1, Integer::sum);
            // 已过期且未处置是合规问题，必须列到具体商品，不能只报个总数
            if (level == 3) {
                expiredItems.add(row.path("productName").asText("未命名商品")
                        + "（" + store + "，到期日 " + row.path("expireDate").asText("未记录") + "）");
            }
        }

        facts.add("临期预警共 " + records.size() + " 条，未处置 " + pending + " 条、已处置 " + handled + " 条。");
        if (pending > 0) {
            facts.add("未处置按级别: 已过期 " + byLevel[3] + " 条、临期 1 天 " + byLevel[2]
                    + " 条、临期 3 天 " + byLevel[1] + " 条。");
            if (!expiredItems.isEmpty()) {
                facts.add("已过期未处置明细（前 5）:");
                expiredItems.stream().limit(5).forEach(i -> facts.add("  " + i));
                if (expiredItems.size() > 5) facts.add("  另有 " + (expiredItems.size() - 5) + " 件未列出");
            }
            if (!pendingByStore.isEmpty()) {
                facts.add("未处置按门店: " + String.join("、",
                        pendingByStore.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(5)
                                .map(e -> e.getKey() + " " + e.getValue() + " 件")
                                .toList()) + "。");
            }
        }

        metrics.put("warningPending", String.valueOf(pending));
        metrics.put("expiredPending", String.valueOf(byLevel[3]));
        metrics.put("warningTotal", String.valueOf(records.size()));
        return pending;
    }

    /** @return 未结（已发布 + 执行中）的召回单数 */
    private int recalls(List<String> facts, Map<String, String> metrics, AgentContext ctx) {
        JsonNode data = remote.getData(salesUrl + "/sales/recalls?pageNum=1&pageSize=200", ctx);
        JsonNode records = data == null ? null : data.get("records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            facts.add("产品召回: 当前没有召回单。");
            metrics.put("recallTotal", "0");
            metrics.put("recallOpen", "0");
            return 0;
        }

        int published = 0, executing = 0, done = 0, cancelled = 0;
        int gapTotal = 0;
        Map<String, Integer> byRisk = new LinkedHashMap<>();
        Map<String, Integer> byReason = new LinkedHashMap<>();

        for (JsonNode row : records) {
            int status = row.path("status").asInt(0);
            int affected = row.path("affectedCount").asInt(0);
            int recalled = row.path("recalledCount").asInt(0);
            if (status == 3) {
                done++;
            } else if (status == 4) {
                cancelled++;
            } else {
                if (status == 2) executing++;
                else published++;
                // 未结的单才算缺口：已完成的不该再计入「还没收回多少」
                gapTotal += Math.max(0, affected - recalled);
            }
            String risk = switch (row.path("riskLevel").asInt(0)) {
                case 1 -> "一般";
                case 2 -> "严重";
                case 3 -> "紧急";
                default -> "未标风险等级";
            };
            byRisk.merge(risk, 1, Integer::sum);
            String reason = AnalyticsSupport.normalizeReason(row.path("reason").asText(""));
            if (!reason.isBlank()) byReason.merge(reason, 1, Integer::sum);
        }

        int open = published + executing;
        facts.add("产品召回共 " + records.size() + " 单：已发布 " + published + " 单、执行中 " + executing
                + " 单、已完成 " + done + " 单、已撤销 " + cancelled + " 单。");
        if (open > 0) {
            facts.add("未结（已发布 + 执行中）共 " + open + " 单，合计还有 " + gapTotal
                    + " 件未收回（应召回数减已收回数，只算未结的单）。");
        }
        facts.add("风险等级分布: " + String.join("、",
                byRisk.entrySet().stream().map(e -> e.getKey() + " " + e.getValue() + " 单").toList()) + "。");
        if (!byReason.isEmpty()) {
            facts.add("召回原因（按单数前 5）:");
            byReason.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> facts.add("  「" + e.getKey() + "」: " + e.getValue() + " 单"));
        }

        metrics.put("recallOpen", String.valueOf(open));
        metrics.put("recallGap", String.valueOf(gapTotal));
        metrics.put("recallTotal", String.valueOf(records.size()));
        return open;
    }
}
