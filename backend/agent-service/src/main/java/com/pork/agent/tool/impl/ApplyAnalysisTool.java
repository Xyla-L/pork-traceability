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
 * 出栏申报分析：待审积压、通过/驳回比例、驳回原因分布、平均审批耗时。
 * <p>
 * 口径完全跟 breeding 的 {@code /applies} 列表走（分页拉最近一批），
 * 这里只做计数、求平均、归并原因——<b>不判断谁该过谁该驳</b>，
 * 审批结论永远是人工的决定，本工具只描述现状。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyAnalysisTool implements AgentTool {

    /** 与实体注释一致的审批状态：0 待审 1 通过 2 驳回 */
    private static final Map<Integer, String> STATUS = Map.of(0, "待审批", 1, "已通过", 2, "已驳回");
    private static final List<String> HINTS = List.of(
            "申报", "审批", "驳回", "通过率", "等待", "排队", "积压", "出栏申请");

    private final ReadOnlyRemote remote;

    @Value("${services.breeding-url:http://localhost:8082}")
    private String breedingUrl;

    @Override
    public String name() {
        return "apply-analysis";
    }

    @Override
    public String label() {
        return "出栏申报分析";
    }

    @Override
    public String description() {
        return "出栏申报的待审积压、驳回数量与理由、平均审批耗时；问「申报驳回多少」「审批堵在哪」时用";
    }

    @Override
    public int priority() {
        return 16;
    }

    /** 出栏申报是养殖发起、屠宰/监管审的事；配送与门店不参与这条链路 */
    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR", "FARMER", "SLAUGHTER_OP");
    }

    @Override
    public String hint() {
        return "出栏申报分析：积压、驳回率、驳回原因、平均审批耗时";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        return Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        // 养殖侧申报列表：直连无服务前缀（/applies），分页参数是 current/size
        JsonNode data = remote.getData(breedingUrl + "/applies?current=1&size=200", ctx);
        JsonNode records = data == null ? null : data.get("records");
        long total = data == null ? 0 : data.path("total").asLong(0);
        if (records == null || !records.isArray() || records.isEmpty()) {
            return ToolResult.empty(name(), label(), "近期没有出栏申报记录，无从分析。");
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int s : new int[]{0, 1, 2}) counts.put(s, 0L);
        List<Duration> approvalDurations = new ArrayList<>();
        List<Duration> pendingAges = new ArrayList<>();
        Map<String, Long> rejectReasons = new LinkedHashMap<>();

        for (JsonNode row : records) {
            int status = row.path("approvalStatus").asInt(-1);
            counts.merge(status, 1L, Long::sum);
            LocalDateTime applyTime = AnalyticsSupport.parseTime(row.path("applyTime").asText(null));
            LocalDateTime approvalTime = AnalyticsSupport.parseTime(row.path("approvalTime").asText(null));
            if (status == 0 && applyTime != null) {
                pendingAges.add(Duration.between(applyTime, now));
            }
            if ((status == 1 || status == 2) && applyTime != null && approvalTime != null) {
                approvalDurations.add(Duration.between(applyTime, approvalTime));
            }
            if (status == 2) {
                rejectReasons.merge(AnalyticsSupport.normalizeReason(row.path("rejectReason").asText("")), 1L, Long::sum);
            }
        }

        long approved = counts.get(1), rejected = counts.get(2), pending = counts.get(0);
        StringBuilder note = new StringBuilder();
        note.append("最近 ").append(records.size()).append(" 条申报（共 ").append(total).append(" 条）：")
                .append("待审批 ").append(pending).append("、已通过 ").append(approved)
                .append("、已驳回 ").append(rejected)
                .append("，驳回占已审结的 ").append(AnalyticsSupport.percent(rejected, approved + rejected)).append("。");
        if (!pendingAges.isEmpty()) {
            Duration avg = pendingAges.stream().reduce(Duration.ZERO, Duration::plus)
                    .dividedBy(pendingAges.size());
            Duration max = pendingAges.stream().max(Duration::compareTo).orElse(Duration.ZERO);
            note.append("待审批的平均已等待 ").append(AnalyticsSupport.durationText(avg))
                    .append("，最久一条等了 ").append(AnalyticsSupport.durationText(max)).append("。");
        }
        if (!approvalDurations.isEmpty()) {
            Duration avg = approvalDurations.stream().reduce(Duration.ZERO, Duration::plus)
                    .dividedBy(approvalDurations.size());
            note.append("已审结的平均耗时 ").append(AnalyticsSupport.durationText(avg)).append("。");
        }

        List<String> facts = new ArrayList<>();
        facts.add("统计范围: 最近 " + records.size() + " 条 / 全部 " + total + " 条");
        facts.add("状态分布: 待审批=" + pending + " 已通过=" + approved + " 已驳回=" + rejected);
        facts.add("驳回占比(已审结): " + AnalyticsSupport.percent(rejected, approved + rejected));
        if (!pendingAges.isEmpty()) {
            facts.add("待审批平均等待: " + AnalyticsSupport.durationText(
                    pendingAges.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(pendingAges.size())));
            facts.add("待审批最久等待: " + AnalyticsSupport.durationText(
                    pendingAges.stream().max(Duration::compareTo).orElse(Duration.ZERO)));
        }
        if (!approvalDurations.isEmpty()) {
            facts.add("已审结平均耗时: " + AnalyticsSupport.durationText(
                    approvalDurations.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(approvalDurations.size())));
        }
        if (!rejectReasons.isEmpty()) {
            facts.add("驳回原因共 " + rejectReasons.size() + " 类：");
            rejectReasons.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(8)
                    .forEach(e -> facts.add("  驳回原因【" + e.getKey() + "】共 " + e.getValue() + " 条"));
        }
        // 登记关键数字给综合简报的规则建议消费（不解析 facts 文本，改措辞不弄坏规则）
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("pendingApplies", String.valueOf(pending));
        metrics.put("rejectedApplies", String.valueOf(rejected));
        pendingAges.stream().max(Duration::compareTo)
                .ifPresent(d -> metrics.put("maxPendingWait", AnalyticsSupport.durationText(d)));
        return new ToolResult(name(), label(), note.toString(), facts, metrics);
    }
}
