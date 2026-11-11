package com.pork.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pork.agent.support.AgentContext;
import com.pork.agent.support.JsonFlatten;
import com.pork.agent.support.ReadOnlyRemote;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作台数据解读：把 {@code trace-service /dashboard/overview} 已经按角色聚合好的
 * 指标卡、图表块、待办清单取回来，交给模型组织成一段总结。
 * <p>
 * 设计上零 SQL、零算术：口径全部来自工作台接口本身（它已经按角色 / 机构圈过，
 * 降级服务也在 {@code degraded} 里如实标注），本工具只做「取回 → 压平 → 交话筒」。
 * 这是刻意的分层——dashboard 数据是现成的聚合结果，再在这里复算一遍，
 * 只会制造第二份口径，两处迟早说不一样的话。
 * <p>
 * 未知结构的块（以后新加的图表类型）走通用扁平化兜底，不静默丢字段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardTool implements AgentTool {

    /** 注意措辞避开「运单」「申报」等专属词，那些问题的第一优先级属于专项分析工具 */
    private static final List<String> HINTS = List.of(
            "工作台", "概况", "整体情况", "整体怎么样", "今天怎么样", "今日情况", "总结", "大盘");

    private final ReadOnlyRemote remote;

    @Value("${services.trace-url:http://localhost:8086}")
    private String traceUrl;

    @Override
    public String name() {
        return "dashboard";
    }

    @Override
    public String label() {
        return "工作台数据解读";
    }

    @Override
    public String description() {
        return "按当前用户角色汇总工作台指标卡、图表与待办，回答「今天整体怎么样」这类概况问题";
    }

    @Override
    public int priority() {
        return 18;
    }

    @Override
    public String hint() {
        return "按你的角色把今天工作台的指标与待办总结成一段话";
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
        // 接口本身按 X-User-Role / X-User-Org 出角色视图，身份头由 ReadOnlyRemote 原样透传
        JsonNode view = remote.getData(traceUrl + "/dashboard/overview", ctx);
        if (view == null || view.isMissingNode() || view.isNull()) {
            return ToolResult.empty(name(), label(), "工作台暂时没有返回数据，稍后再试。");
        }

        String roleLabel = view.path("roleLabel").asText("用户");
        String scope = view.path("scopeLabel").asText("");

        StringBuilder note = new StringBuilder();
        note.append("已取到 ").append(roleLabel)
                .append(scope.isBlank() ? "" : "（" + scope + "）")
                .append(" 的工作台数据。");

        List<String> facts = new ArrayList<>();
        facts.add("视角: " + roleLabel + (scope.isBlank() ? "" : "（" + scope + "）"));

        JsonNode degraded = view.get("degraded");
        if (degraded != null && degraded.isArray() && !degraded.isEmpty()) {
            List<String> names = new ArrayList<>();
            degraded.forEach(d -> names.add(d.asText()));
            facts.add("注意，以下数据源当前不可用，对应数字可能偏少: " + String.join("、", names));
        }

        JsonNode cards = view.get("statCards");
        if (cards != null && cards.isArray() && !cards.isEmpty()) {
            facts.add("核心指标共 " + cards.size() + " 项：");
            for (JsonNode c : cards) {
                facts.add("  " + c.path("label").asText(c.path("key").asText("?")) + ": "
                        + c.path("value").asText("?") + c.path("unit").asText(""));
            }
        }

        JsonNode todo = view.get("todoList");
        if (todo != null && todo.isArray() && !todo.isEmpty()) {
            facts.add("待办清单共 " + todo.size() + " 项：");
            facts.addAll(JsonFlatten.lines(todo, 12));
        }

        JsonNode blocks = view.get("blocks");
        int blockCount = blocks != null && blocks.isArray() ? blocks.size() : 0;
        if (blockCount > 0) {
            facts.add("图表共 " + blockCount + " 个：");
            for (JsonNode b : blocks) {
                appendBlock(b, facts);
            }
        }

        if (cards == null || cards.isEmpty()) {
            note.append("没有配置指标卡。");
        }

        // 指标卡键值登记给综合简报的规则建议消费（key 是稳定口径名，label 是展示文案）
        Map<String, String> metrics = new java.util.LinkedHashMap<>();
        if (cards != null && cards.isArray()) {
            for (JsonNode c : cards) {
                String key = c.path("key").asText("");
                if (!key.isBlank()) metrics.put(key, c.path("value").asText("0"));
            }
        }
        return new ToolResult(name(), label(), note.toString(), facts, metrics);
    }

    /** 单个图表块：饼图/柱图按「名: 值」逐项列出，表格列出行数与样例，未知结构走通用扁平化 */
    private void appendBlock(JsonNode b, List<String> facts) {
        String title = b.path("title").asText(b.path("key").asText("图表"));
        String type = b.path("type").asText("");
        String unit = b.path("unit").asText("");

        JsonNode data = b.get("data");
        if (data != null && data.isArray() && !data.isEmpty()) {
            facts.add("「" + title + "」(" + type + "，" + data.size() + " 项" + (unit.isBlank() ? "" : "，单位 " + unit) + ")：");
            for (JsonNode d : data) {
                JsonNode name = d.get("name");
                if (name != null && !name.isNull()) {
                    facts.add("  " + name.asText() + ": " + d.path("value").asText("?") + unit);
                } else {
                    facts.addAll(JsonFlatten.lines(d, 6));
                }
            }
            return;
        }
        if (data != null && data.isObject()) {
            // sankey 类：nodes / links
            JsonNode nodes = data.get("nodes");
            if (nodes != null && nodes.isArray()) {
                facts.add("「" + title + "」各环节数量：");
                for (JsonNode n : nodes) {
                    facts.add("  " + n.path("name").asText("?") + ": " + n.path("value").asText("?"));
                }
            }
            return;
        }
        JsonNode rows = b.get("rows");
        if (rows != null && rows.isArray()) {
            if (rows.isEmpty()) {
                facts.add("「" + title + "」：" + b.path("emptyText").asText("暂无数据"));
            } else {
                facts.add("「" + title + "」表格共 " + rows.size() + " 行，前几行：");
                facts.addAll(JsonFlatten.lines(rows, 12));
            }
            return;
        }
        // 以后新加的块类型：整体扁平化兜底，宁可多念也不静默丢
        List<String> lines = JsonFlatten.lines(b, 10);
        if (lines.isEmpty()) {
            facts.add("「" + title + "」：" + b.path("emptyText").asText("暂无数据"));
        } else {
            facts.add("「" + title + "」：");
            facts.addAll(lines);
        }
    }
}
