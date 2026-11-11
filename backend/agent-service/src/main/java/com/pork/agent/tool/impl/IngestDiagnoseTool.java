package com.pork.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pork.agent.support.AnalyticsSupport;
import com.pork.agent.support.AgentContext;
import com.pork.agent.support.ReadOnlyRemote;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入异常诊断：把「待人工处理」队列里的失败原因归类，告诉人先去哪儿、先处理哪一类。
 * <p>
 * <b>归类在这里算，不交给模型。</b>让模型对着一堆 errorMsg 数数是最容易出错的做法
 * （合并错、漏计、把两条相似消息说成一条）。这里按「去掉编号后的原因」分组，
 * 模型只负责把分组结果讲顺，事实与措辞分离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestDiagnoseTool implements AgentTool {

    private static final List<String> HINTS = List.of(
            "接入", "待确认", "待处理", "没入账", "没入库", "入账失败", "上报失败", "设备", "诊断", "异常", "失败原因");

    private final ReadOnlyRemote remote;

    @Value("${services.ingest-url:http://localhost:8089}")
    private String ingestUrl;

    @Override
    public String name() {
        return "ingest-diagnose";
    }

    @Override
    public String label() {
        return "接入异常诊断";
    }

    @Override
    public String description() {
        return "设备上报没入账、待确认、入账失败、诊断异常原因；把待人工队列按原因归类，告诉先处理哪一类";
    }

    @Override
    public int priority() {
        return 10;
    }

    /** 接入队列是设备归口的事：养殖户看不到全平台设备台账，问也只会得到「这事不归你看」 */
    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR", "SLAUGHTER_OP", "DISTRIBUTOR");
    }

    @Override
    public String hint() {
        return "把待人工处理的设备上报按失败原因归类，告诉你先处理哪一类";
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
        JsonNode data = remote.getData(ingestUrl + "/ingest/staging?status=1&pageNum=1&pageSize=50", ctx);
        JsonNode records = data == null ? null : data.get("records");
        long total = data == null ? 0 : data.path("total").asLong(0);

        if (records == null || !records.isArray() || records.isEmpty()) {
            return ToolResult.empty(name(), label(), "当前没有待人工处理的接入记录，设备上报的数据都已自动入账。");
        }

        // 分组键＝去掉具体编号后的原因文本，避免「同一原因 20 条」被当成 20 个问题
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        for (JsonNode row : records) {
            groups.computeIfAbsent(AnalyticsSupport.normalizeReason(row.path("errorMsg").asText("")),
                    k -> new ArrayList<>()).add(row);
        }
        List<Map.Entry<String, List<JsonNode>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, List<JsonNode>> e) -> e.getValue().size()).reversed());

        StringBuilder note = new StringBuilder();
        note.append("待人工处理共 ").append(total).append(" 条");
        if (records.size() < total) note.append("（本次按最近 ").append(records.size()).append(" 条归纳）");
        note.append("，归为 ").append(sorted.size()).append(" 类原因");
        note.append("；最多的一类是「").append(sorted.get(0).getKey()).append("」，共 ")
                .append(sorted.get(0).getValue().size()).append(" 条。");
        note.append("处理入口：设备接入监控页的待确认队列，可逐条补条件入账或拒绝。");

        List<String> facts = new ArrayList<>();
        facts.add("队列总数: " + total);
        facts.add("本次归纳条数: " + records.size());
        facts.add("原因分类数: " + sorted.size());
        for (Map.Entry<String, List<JsonNode>> e : sorted) {
            List<JsonNode> rows = e.getValue();
            JsonNode first = rows.get(0);
            facts.add("原因【" + e.getKey() + "】共 " + rows.size() + " 条；通道="
                    + first.path("channelLabel").asText("")
                    + "；设备=" + first.path("deviceName").asText(first.path("deviceNo").asText(""))
                    + "；样例编号=" + first.path("bizKey").asText("")
                    + "；最近时间=" + first.path("receiveTime").asText(""));
            for (int i = 1; i < Math.min(rows.size(), 3); i++) {
                JsonNode r = rows.get(i);
                facts.add("  同原因样例: " + r.path("bizKey").asText("")
                        + " / " + r.path("deviceName").asText(r.path("deviceNo").asText(""))
                        + " / " + r.path("receiveTime").asText(""));
            }
        }
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("queueTotal", String.valueOf(total));
        metrics.put("topReason", sorted.get(0).getKey());
        metrics.put("topReasonCount", String.valueOf(sorted.get(0).getValue().size()));
        return new ToolResult(name(), label(), note.toString(), facts, metrics);
    }
}
