package com.pork.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pork.agent.support.AgentContext;
import com.pork.agent.support.JsonFlatten;
import com.pork.agent.support.ReadOnlyRemote;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全链路追溯问答。
 * <p>
 * 取数分两级，都是只读：
 * <ul>
 *   <li>耳标（ET-…）：先查养殖档案（猪现在什么状态），再查屠宰入场记录，最后尝试查链路。
 *       大量活猪根本还没有链路——「查不到链路」是正常业务状态，不是故障，不能报错了事；</li>
 *   <li>批次 / 运单 / 二维码：直接走 trace-service 的 {@code /trace/search}，
 *       与「应急追溯」页同一个接口，口径天然一致。</li>
 * </ul>
 * agent 不重算、不猜「应该到哪一步了」，只把已有记录翻译成人话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceTool implements AgentTool {

    /** 业务编号形态：耳标 ET-xxx、胴体批次 CBxxx、二维码 QR-xxx、运单 TRxxx 等 */
    private static final Pattern BIZ_NO = Pattern.compile(
            "\\b(?:ET|CB|QR|TR|PB|SP|SL)[-A-Z0-9]{2,}", Pattern.CASE_INSENSITIVE);
    /**
     * 没带编号也要接住：用户直接问「这头猪到哪了」时，回一句「请给编号」比回「我不懂」有用。
     * 「查一下」这类泛词刻意不收，否则会把别的问题抢过来。
     */
    private static final List<String> HINTS = List.of(
            "追溯", "全链路", "到哪", "流向", "这头猪", "这批", "耳标", "二维码", "溯源");

    /** 运输语义词：出现且没有生猪特征词时，本工具让位给运单分析 */
    private static final List<String> VEHICLE_HINTS = List.of(
            "车", "运单", "司机", "在途", "冷链", "送货", "发货", "配送");

    /** 生猪语义词：与运输词同现时（「这车上的猪」），追溯仍然优先 */
    private static final List<String> PIG_HINTS = List.of(
            "猪", "耳标", "批次", "胴体", "白条", "屠宰");

    private final ReadOnlyRemote remote;

    @Value("${services.trace-url:http://localhost:8086}")
    private String traceUrl;

    @Value("${services.breeding-url:http://localhost:8082}")
    private String breedingUrl;

    @Value("${services.slaughter-url:http://localhost:8083}")
    private String slaughterUrl;

    @Value("${agent.llm.max-fact-lines:120}")
    private int maxFacts;

    @Override
    public String name() {
        return "trace";
    }

    @Override
    public String label() {
        return "全链路追溯";
    }

    @Override
    public String description() {
        return "按耳标号、胴体批次号或运单号查这头猪走到了哪一步（养殖、检疫、屠宰、分割、配送、销售）";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public String hint() {
        return "给一个耳标号 / 胴体批次号 / 运单号，把全链路走到哪一步讲清楚";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        // 「车/运单/司机 到哪了」问的是运输，不是这头猪的链路：让运单分析接。
        // 但只要同一句里提到了猪/耳标/批次，追溯仍然优先——「这车上的 ET-xxx」问的还是猪。
        boolean vehicle = VEHICLE_HINTS.stream().anyMatch(question::contains);
        boolean pig = PIG_HINTS.stream().anyMatch(question::contains);
        if (vehicle && !pig) return false;
        // 「这车上的猪」主语是猪：没带编号也归追溯，由 execute 回一句「请给编号」，
        // 比让运单分析拿车号解释一遍拒收更有用。
        if (vehicle && pig) return true;
        // 带业务编号，或明确在问追溯链路。接入异常类问题由诊断工具先认领（它优先级更高）。
        return BIZ_NO.matcher(question).find() || HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        Map<String, String> args = new LinkedHashMap<>();
        Matcher m = BIZ_NO.matcher(question == null ? "" : question);
        if (m.find()) args.put("keyword", m.group().toUpperCase());
        return args;
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        String keyword = args.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return ToolResult.empty(name(), label(),
                    "没听出要查哪个编号。请给一个耳标号（ET-…）、胴体批次号（CB…）或运单号（TR…）。");
        }
        List<String> facts = new ArrayList<>();
        StringBuilder note = new StringBuilder();
        boolean isEarTag = keyword.startsWith("ET");
        int pigCount = 0;

        // 第一级：耳标 → 养殖档案 + 屠宰入场。这两步查不到时，后面的链路查询不再有意义
        if (isEarTag) {
            // 养殖侧公开列表挂在 /pigs（网关 StripPrefix=3 后无服务前缀），别写成 /breeding/pigs
            JsonNode pigPage = remote.getData(breedingUrl
                    + "/pigs?current=1&size=5&earTagNo=" + enc(keyword), ctx);
            JsonNode pigs = pigPage == null ? null : pigPage.get("records");
            if (pigs == null || !pigs.isArray() || pigs.isEmpty()) {
                return ToolResult.empty(name(), label(),
                        "养殖档案里没有这枚耳标（" + keyword + "）。请核对编号，或确认这头猪是否已登记批次。");
            }
            pigCount = pigs.size();
            note.append("耳标 ").append(keyword).append(" 的养殖档案已取到");
            for (int i = 0; i < Math.min(pigs.size(), 3); i++) {
                JsonNode pig = pigs.get(i);
                facts.add("养殖档案: 耳标=" + pig.path("earTagNo").asText("")
                        + "；状态=" + pig.path("statusLabel").asText("")
                        + "；养殖场=" + pig.path("farmName").asText("")
                        + "；批次=" + pig.path("batchNo").asText("")
                        + "；品种=" + pig.path("breed").asText(""));
            }
            if (pigs.size() > 3) facts.add("养殖档案: ……另有 " + (pigs.size() - 3) + " 头同号记录");
            try {
                JsonNode entryPage = remote.getData(slaughterUrl
                        + "/slaughter/entries?earTagNo=" + enc(keyword) + "&pageNum=1&pageSize=3", ctx);
                JsonNode entries = entryPage == null ? null : entryPage.get("records");
                if (entries != null && entries.isArray() && !entries.isEmpty()) {
                    JsonNode first = entries.get(0);
                    facts.add("屠宰入场: 到达时间=" + first.path("arriveTime").asText("")
                            + "；查验状态=" + first.path("status").asText("")
                            + "；检疫证=" + first.path("quarantineCert").asText(""));
                    note.append("，已进入屠宰环节");
                } else {
                    note.append("，尚未进入屠宰环节");
                }
            } catch (BusinessException e) {
                // 入场记录查不到不算失败：活猪本来就没有这一环
                note.append("，尚未进入屠宰环节");
            }
        }

        // 第二级：链路。耳标不是链路键，查不到是常态而非故障；批次/运单/二维码查不到才是「没有记录」
        try {
            JsonNode data = remote.getData(traceUrl + "/trace/search?keyword=" + enc(keyword), ctx);
            if (data != null && !data.isNull() && !data.isEmpty()) {
                String batchNo = data.path("batchNo").asText("");
                if (!batchNo.isBlank() && !batchNo.equalsIgnoreCase(keyword)) {
                    note.append("；对应批次 ").append(batchNo);
                }
                String voided = data.path("voidedSummary").asText("");
                if (!voided.isBlank()) note.append("；注意：").append(voided);
                facts.addAll(JsonFlatten.lines(data, Math.max(0, maxFacts - facts.size())));
            }
        } catch (BusinessException e) {
            if (!(isEarTag && pigCount > 0)) {
                return ToolResult.empty(name(), label(), "系统里没有查到 " + keyword + " 的链路记录。");
            }
            note.append("；该耳标还没有形成可查的批次链路（屠宰分割完成后才能按批次看全程）");
        }
        if (note.isEmpty()) note.append("已取到 ").append(keyword).append(" 的记录");
        return new ToolResult(name(), label(), note.toString(), facts);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
