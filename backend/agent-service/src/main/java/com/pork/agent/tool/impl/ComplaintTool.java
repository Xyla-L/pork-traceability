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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报与投诉：待受理积压、处理时效、涉及批次、问题类型分布。
 * <p>
 * 状态口径跟 {@code complaint_report.status} 走：0 待受理 / 2 已处理 / 3 已驳回，
 * 文案直接用下游返回的 {@code statusText}，本工具不另造一套说法。
 * <p>
 * 只统计不结案：结案要写结论并留痕，是监管人员在「追溯监管-举报管理」里的动作，
 * 这里连写接口都没有。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintTool implements AgentTool {

    private static final List<String> HINTS = List.of(
            "举报", "投诉", "结案", "被举报", "举报人", "申诉", "监管投诉");

    /** 问的是「这批货走到哪」时让位全链路追溯：举报只是入口，答案在链条上 */
    private static final List<String> TRACE_HINTS = List.of("到哪", "追溯", "流向", "耳标", "全链路", "走到哪");

    private final ReadOnlyRemote remote;

    @Value("${services.trace-url:http://localhost:8085}")
    private String traceUrl;

    @Override
    public String name() {
        return "complaint";
    }

    @Override
    public String label() {
        return "举报与投诉";
    }

    @Override
    public String description() {
        return "举报待受理积压、处理时效、涉及哪些批次、问题类型分布；问「举报有多少没处理」「举报都涉及哪些批次」时用";
    }

    @Override
    public int priority() {
        return 14;
    }

    /**
     * 举报台账归监管：接口层 {@code GET /trace/complaints} 没有机构条件，
     * 一旦把工具放给养殖户/门店，等于让它们读到全平台举报（实测养殖户能问出 21 条全量）。
     * 所以这里按角色关掉，不指望下游兜。
     */
    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR");
    }

    @Override
    public String hint() {
        return "举报与投诉：待受理几条、压了多久、涉及哪些批次";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        if (TRACE_HINTS.stream().anyMatch(question::contains)) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        return Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        JsonNode data = remote.getData(traceUrl + "/trace/complaints?pageNum=1&pageSize=200", ctx);
        JsonNode records = data == null ? null : data.get("records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            return ToolResult.empty(name(), label(), "当前没有举报记录。");
        }

        int pending = 0, handled = 0, rejected = 0;
        Duration longestWait = Duration.ZERO;
        String longestNo = "", longestBatch = "";
        List<Duration> handleDurations = new ArrayList<>();
        Map<String, Long> byBatch = new LinkedHashMap<>();
        Map<String, Long> byType = new LinkedHashMap<>();

        for (JsonNode row : records) {
            int status = row.path("status").asInt(0);
            LocalDateTime created = AnalyticsSupport.parseTime(row.path("createTime").asText(null));
            LocalDateTime handledAt = AnalyticsSupport.parseTime(row.path("handleTime").asText(null));
            String batch = row.path("targetBatch").asText("");

            if (status == 0) {
                pending++;
                if (created != null) {
                    Duration wait = Duration.between(created, LocalDateTime.now());
                    if (wait.compareTo(longestWait) > 0) {
                        longestWait = wait;
                        longestNo = row.path("reportNo").asText("");
                        longestBatch = batch;
                    }
                }
            } else if (status == 2) {
                handled++;
            } else if (status == 3) {
                rejected++;
            }
            if (created != null && handledAt != null) {
                handleDurations.add(Duration.between(created, handledAt));
            }
            if (!batch.isBlank()) byBatch.merge(batch, 1L, Long::sum);
            String type = AnalyticsSupport.normalizeReason(row.path("complaintText").asText(""));
            if (!type.isBlank()) byType.merge(type, 1L, Long::sum);
        }

        List<String> facts = new ArrayList<>();
        facts.add("统计范围: 全部 " + records.size() + " 条举报");
        facts.add("状态分布: 待受理 " + pending + " 条、已处理 " + handled + " 条、已驳回 " + rejected + " 条");
        if (pending > 0 && !longestNo.isBlank()) {
            facts.add("压得最久的一条: " + longestNo + (longestBatch.isBlank() ? "" : "（涉及批次 " + longestBatch + "）")
                    + "，已等 " + AnalyticsSupport.durationText(longestWait));
        } else if (pending == 0) {
            facts.add("待受理: 0 条，没有积压");
        }
        if (!handleDurations.isEmpty()) {
            Duration avg = handleDurations.stream().reduce(Duration.ZERO, Duration::plus)
                    .dividedBy(handleDurations.size());
            facts.add("已处理平均时效: " + AnalyticsSupport.durationText(avg)
                    + "（按 " + handleDurations.size() + " 条有处理时间的记录算）");
        }
        if (!byBatch.isEmpty()) {
            facts.add("涉及批次: " + byBatch.size() + " 个，被举报最多的是：");
            byBatch.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> facts.add("  批次 " + e.getKey() + ": " + e.getValue() + " 条"));
        }
        if (!byType.isEmpty()) {
            facts.add("问题类型（按条数前 5）:");
            byType.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> facts.add("  「" + e.getKey() + "」: " + e.getValue() + " 条"));
        }
        facts.add("说明: 举报结案需要给结论并留痕，由监管人员在「追溯监管-举报管理」处理；本工具只报统计。");

        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("pending", String.valueOf(pending));
        metrics.put("total", String.valueOf(records.size()));

        String note = "共 " + records.size() + " 条举报，待受理 " + pending + " 条"
                + (pending > 0 && !longestNo.isBlank()
                ? "，最久一条已等 " + AnalyticsSupport.durationText(longestWait) : "")
                + "。";
        return new ToolResult(name(), label(), note, facts, metrics);
    }
}
