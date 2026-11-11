package com.pork.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pork.agent.support.AgentContext;
import com.pork.agent.support.ReadOnlyRemote;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检疫与免疫档案：出证多少张、覆盖多少头、作废几张，以及「有证没免疫」「先出证后免疫」的批次。
 * <p>
 * 数据来自养殖侧的按批次分组视图（{@code /records/certs}、{@code /records/vaccines}），
 * 已经是「一次操作一行」，本工具只做计数与批次对齐，不重算任何判定。
 *
 * <h3>为什么这个工具重要</h3>
 * 免疫档案是产地检疫的<b>法定前置</b>（{@code ImmunityGate} 的两条拦截理由就是「一条免疫记录都没有」
 * 和「免疫全部晚于出证」）。这两件事在系统里只能靠出证那一刻被拦下来，事后没人能一眼看出来——
 * 所以问「哪些批次的免疫档案有问题」时，必须能答。
 *
 * <h3>边界：只陈列，不判定</h3>
 * 分组视图只到<b>日期</b>粒度（{@code happenedOn} 是 yyyy-MM-dd），而闸门判的是<b>分钟</b>级先后
 * （5 分钟容差）。所以这里只能说「出证日期早于该批最早免疫日期」，不能说「违规」——
 * 同一天内的先后顺序，这个数据看不出来。最终结论以屠宰入场时的免疫闸门与监管认定为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuarantineTool implements AgentTool {

    private static final List<String> HINTS = List.of(
            "检疫", "免疫", "疫苗", "接种", "出证", "检疫证", "免疫档案", "打针");

    /** 问「这头猪走到哪一步」时让位全链路追溯：这里只答档案齐不齐，不答流向 */
    private static final List<String> TRACE_HINTS = List.of("到哪", "追溯", "流向", "全链路", "走到哪", "什么时候宰");

    /** 耳标 / 批次号：给了就只看这一个对象 */
    private static final Pattern BIZ_NO = Pattern.compile("[A-Z]{2,4}[-A-Z0-9]{4,}");

    private final ReadOnlyRemote remote;

    @Value("${services.breeding-url:http://localhost:8082}")
    private String breedingUrl;

    @Override
    public String name() {
        return "quarantine";
    }

    @Override
    public String label() {
        return "检疫与免疫档案";
    }

    @Override
    public String description() {
        return "产地检疫证与免疫档案：出了多少张证、覆盖多少头、作废几张，哪些批次有证却查不到免疫记录、哪些批次出证日期早于免疫；问「检疫证够不够」「哪些批次免疫不全」时用";
    }

    @Override
    public int priority() {
        return 19;
    }

    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR", "FARMER", "SLAUGHTER_OP");
    }

    @Override
    public String hint() {
        return "检疫与免疫档案：出证多少张、哪些批次免疫不全";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        if (TRACE_HINTS.stream().anyMatch(question::contains)) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        if (question == null) return Map.of();
        Matcher m = BIZ_NO.matcher(question);
        return m.find() ? Map.of("keyword", m.group()) : Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        String keyword = args == null ? null : args.get("keyword");
        // 分组视图分页参数是 current/size（与 /applies 一致），不是 pageNum/pageSize
        JsonNode certPage = remote.getData(breedingUrl + "/records/certs?current=1&size=200", ctx);
        JsonNode vacPage = remote.getData(breedingUrl + "/records/vaccines?current=1&size=200", ctx);

        JsonNode certs = filter(certPage == null ? null : certPage.get("records"), keyword);
        JsonNode vaccines = filter(vacPage == null ? null : vacPage.get("records"), keyword);

        if (certs.size() == 0 && vaccines.size() == 0) {
            return ToolResult.empty(name(), label(), keyword == null
                    ? "当前没有检疫证与免疫记录。"
                    : "没有查到 " + keyword + " 相关的检疫证或免疫记录。");
        }

        List<String> facts = new ArrayList<>();
        Map<String, String> metrics = new LinkedHashMap<>();

        // 1) 检疫证概况
        int certHeads = 0, certVoid = 0;
        Map<String, Integer> byOrg = new LinkedHashMap<>();
        Map<String, String> certDateByBatch = new HashMap<>();
        Set<String> certBatches = new LinkedHashSet<>();
        for (JsonNode row : certs) {
            certHeads += row.path("headCount").asInt(0);
            certVoid += row.path("voidCount").asInt(0);
            String org = row.path("subTitle").asText("");
            if (!org.isBlank()) byOrg.merge(org, 1, Integer::sum);
            String batch = row.path("batchNo").asText("");
            String day = row.path("happenedOn").asText("");
            if (!batch.isBlank()) {
                certBatches.add(batch);
                // 同一批可能多次出证，取最早一次作为「这批最早出证日期」
                certDateByBatch.merge(batch, day, (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
        }
        facts.add("检疫证 " + certs.size() + " 张（覆盖 " + certHeads + " 头，涉及 " + certBatches.size()
                + " 个批次），其中作废 " + certVoid + " 头份。");

        // 2) 免疫概况
        int vacHeads = 0, vacVoid = 0;
        Map<String, Integer> byVaccine = new LinkedHashMap<>();
        Map<String, String> earliestVacByBatch = new HashMap<>();
        for (JsonNode row : vaccines) {
            vacHeads += row.path("headCount").asInt(0);
            vacVoid += row.path("voidCount").asInt(0);
            String name = row.path("title").asText("");
            if (!name.isBlank()) byVaccine.merge(name, 1, Integer::sum);
            String batch = row.path("batchNo").asText("");
            String day = row.path("happenedOn").asText("");
            if (!batch.isBlank() && !day.isBlank()) {
                earliestVacByBatch.merge(batch, day, (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
        }
        facts.add("免疫记录 " + vaccines.size() + " 次（覆盖 " + vacHeads + " 头份，作废 " + vacVoid + " 头份），涉及 "
                + byVaccine.size() + " 种疫苗。");

        // 3) 交叉检查：只对有检疫证的批次做，因为这些批次才是「要出栏调运」的
        List<String> noVaccine = new ArrayList<>();
        List<String> certBeforeVaccine = new ArrayList<>();
        for (String batch : certBatches) {
            String vacDay = earliestVacByBatch.get(batch);
            String certDay = certDateByBatch.get(batch);
            if (vacDay == null) {
                noVaccine.add(batch);
            } else if (certDay != null && !certDay.isBlank() && certDay.compareTo(vacDay) < 0) {
                certBeforeVaccine.add(batch + "（出证 " + certDay + "，最早免疫 " + vacDay + "）");
            }
        }
        if (noVaccine.isEmpty() && certBeforeVaccine.isEmpty()) {
            facts.add("档案交叉检查: 已出证的 " + certBatches.size() + " 个批次都能查到免疫记录，且免疫日期不晚于出证日期。");
        } else {
            if (!noVaccine.isEmpty()) {
                facts.add("⚠ 已出证但查不到任何免疫记录的批次: " + noVaccine.size() + " 个 —— "
                        + String.join("、", noVaccine.stream().limit(6).toList())
                        + (noVaccine.size() > 6 ? " 等" : ""));
            }
            if (!certBeforeVaccine.isEmpty()) {
                facts.add("⚠ 出证日期早于该批最早免疫日期的批次: " + certBeforeVaccine.size() + " 个 —— "
                        + String.join("、", certBeforeVaccine.stream().limit(6).toList())
                        + (certBeforeVaccine.size() > 6 ? " 等" : ""));
            }
        }

        if (!byOrg.isEmpty()) {
            facts.add("出证机构（按张数前 5）: " + String.join("、",
                    byOrg.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(5)
                            .map(e -> e.getKey() + " " + e.getValue() + " 张")
                            .toList()) + "。");
        }
        if (!byVaccine.isEmpty()) {
            facts.add("疫苗种类（按次数前 5）: " + String.join("、",
                    byVaccine.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(5)
                            .map(e -> e.getKey() + " " + e.getValue() + " 次")
                            .toList()) + "。");
        }

        facts.add("说明: 免疫是产地检疫的法定前置，闸门判的是分钟级先后（5 分钟容差），"
                + "而本表只有日期粒度，所以上面两条是「看起来对不上」的批次，不是判定结论；"
                + "最终以屠宰入场时的免疫闸门与监管认定为准。");

        metrics.put("certTotal", String.valueOf(certs.size()));
        metrics.put("certHeads", String.valueOf(certHeads));
        metrics.put("noVaccineBatches", String.valueOf(noVaccine.size()));
        metrics.put("certBeforeVaccineBatches", String.valueOf(certBeforeVaccine.size()));

        String note = "检疫证 " + certs.size() + " 张覆盖 " + certHeads + " 头，免疫记录 " + vaccines.size() + " 次"
                + (noVaccine.isEmpty() && certBeforeVaccine.isEmpty()
                ? "，已出证批次都能对上免疫档案。"
                : "；有 " + noVaccine.size() + " 个批次查不到免疫记录、"
                + certBeforeVaccine.size() + " 个批次出证早于免疫。");
        return new ToolResult(name(), label(), note, facts, metrics);
    }

    /** 给了编号就只留下这一头/这一批；没给就原样返回（200 条以内，内存过滤比再打一次接口省事） */
    private JsonNode filter(JsonNode records, String keyword) {
        if (records == null || !records.isArray()) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        if (keyword == null || keyword.isBlank()) return records;
        com.fasterxml.jackson.databind.node.ArrayNode out =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (JsonNode row : records) {
            String batch = row.path("batchNo").asText("");
            String start = row.path("tagStart").asText("");
            String end = row.path("tagEnd").asText("");
            if (batch.equalsIgnoreCase(keyword)
                    || start.equalsIgnoreCase(keyword)
                    || end.equalsIgnoreCase(keyword)
                    || (start.compareTo(keyword) <= 0 && end.compareTo(keyword) >= 0 && !start.isBlank())) {
                out.add(row);
            }
        }
        return out;
    }
}
