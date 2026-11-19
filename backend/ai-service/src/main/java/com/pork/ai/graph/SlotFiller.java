package com.pork.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.client.DifyClient;
import com.pork.ai.config.ActionProperties;
import com.pork.ai.dto.DifyChatCommand;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.ApiParam;
import com.pork.ai.graph.model.OperationMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 让千问做两件它擅长的事：<b>在候选项里做选择</b> + <b>把自然语言拆成参数</b>。
 *
 * <p>它不擅长的事一件都不给它做：</p>
 * <ul>
 *   <li>不负责找接口 —— 那是 {@link ArchGraphService} 的倒排打分，可解释、可复现；</li>
 *   <li>不负责判断参数够不够 —— 缺哪些必填由后端对比 schema 算，
 *       模型说「参数齐了」不算数；</li>
 *   <li>不负责拼 URL —— 它只回一个 operationId，路径由执行器按图谱取。</li>
 * </ul>
 *
 * <p>为什么这样切：模型输出是不可信输入。把「选接口 / 算缺参 / 拼 URL」交给它，
 * 等于允许一次幻觉直接变成一次真实请求；只让它输出 operationId + 参数值，
 * 后端每一层都能校验，最坏情况是「它选错了接口」，而不是「它调了别的接口」。</p>
 */
@Slf4j
@Component
public class SlotFiller {

    /** 抽取参数是短任务，44 秒还没出结果基本就是模型卡住了，早断早重试 */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(45);

    private final DifyClient difyClient;
    private final ObjectMapper objectMapper;
    private final ActionProperties actionProperties;

    public SlotFiller(DifyClient difyClient, ObjectMapper objectMapper,
                      ActionProperties actionProperties) {
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.actionProperties = actionProperties;
    }

    /**
     * @param query      用户这一轮说的话
     * @param candidates 图谱检索出来的候选接口（已排序）
     * @param knownSlots 前几轮已经收集到的参数
     * @param userKey    给 Dify 的终端用户标识，用于会话隔离
     */
    public Decision decide(String query, List<OperationMatch> candidates,
                           Map<String, Object> knownSlots, String userKey) {
        if (candidates.isEmpty()) {
            return Decision.unmatched("我在系统里没找到能对应这句话的功能。可以换个说法，或者说得更具体一点，比如「查一下某批次的分娩记录」。");
        }

        String prompt = buildPrompt(query, candidates, knownSlots);
        String answer;
        try {
            answer = difyClient.chatBlocking(DifyChatCommand.builder()
                            .query(prompt)
                            .user(userKey)
                            .build())
                    .block(CALL_TIMEOUT);
        } catch (Exception e) {
            log.warn("调用千问抽取参数失败，降级为规则选择: {}", e.toString());
            return fallback(candidates, knownSlots, e.getMessage());
        }

        Decision parsed = parse(answer, candidates);
        if (parsed == null) {
            log.warn("模型输出无法解析成 JSON，降级为规则选择。原始输出: {}",
                    answer == null ? "null" : truncate(answer, 300));
            return fallback(candidates, knownSlots, "模型输出格式不正确");
        }
        return parsed;
    }

    // ==================== Prompt ====================

    private String buildPrompt(String query, List<OperationMatch> candidates,
                               Map<String, Object> knownSlots) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("""
                你是猪肉溯源平台的接口调度器。把用户的一句话翻译成「一次」接口调用。
                """);
        sb.append("\n【可选接口】只能从下面这些 id 里选，禁止编造：\n");
        int i = 1;
        for (OperationMatch m : candidates) {
            sb.append(i++).append(". ").append(m.operation().promptLine()).append('\n');
        }

        sb.append("\n【已经收集到的参数】\n");
        if (knownSlots == null || knownSlots.isEmpty()) {
            sb.append("（无）\n");
        } else {
            try {
                sb.append(objectMapper.writeValueAsString(knownSlots)).append('\n');
            } catch (Exception e) {
                sb.append("（无）\n");
            }
        }

        sb.append("\n【用户这句话】\n").append(query).append('\n');

        sb.append("""

                【输出要求】只输出一个 JSON，不要解释，不要 markdown 代码块：
                {"operation":"接口 id 或 null","slots":{"参数名":"值"},"question":"需要向用户追问的话","reason":"一句话说明为什么选这个接口"}

                【硬性规则】
                1. 用户没提到的参数，不要填。宁可留空让系统再问，也绝对不许编一个看起来合理的值。
                2. 日期一律 YYYY-MM-DD。"上个月""上周""昨天"按今天 %s 换算成具体区间。
                3. 请求体参数放在 slots.body 对象里，键名照抄接口说明里 {} 内的字段名。
                4. 一个接口就能做完的事不要拆成多个。确实需要多个接口时，选最接近的那个，
                   并在 question 里说明"这一步只做了 X，剩下的需要再发起一次"。
                5. 找不到能对应的接口时，operation 输出 null，question 里告诉用户你能做什么。
                """.formatted(LocalDate.now()));
        return sb.toString();
    }

    // ==================== 解析 ====================

    private Decision parse(String answer, List<OperationMatch> candidates) {
        String json = extractJson(answer);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String opId = root.path("operation").asText(null);
            if (opId == null || "null".equals(opId) || opId.isBlank()) {
                String q = root.path("question").asText("");
                return Decision.unmatched(q.isBlank()
                        ? "这个问题我暂时没有办法通过系统操作来完成。" : q);
            }
            // 模型可能返回序号而不是 id，做一次宽容映射，但只在候选范围内
            ApiOperation op = resolveOperation(opId, candidates);
            if (op == null) {
                log.warn("模型返回了候选之外的 operation: {}", opId);
                return null;
            }
            Map<String, Object> slots = readSlots(root.path("slots"));
            return new Decision(op.id(), slots, ensureQuestion(root.path("question").asText("")),
                    root.path("reason").asText(""), true);
        } catch (Exception e) {
            log.debug("解析模型 JSON 失败: {}", e.toString());
            return null;
        }
    }

    private ApiOperation resolveOperation(String raw, List<OperationMatch> candidates) {
        String id = raw.trim();
        for (OperationMatch m : candidates) {
            if (m.operation().id().equals(id)) {
                return m.operation();
            }
        }
        // 允许模型只回序号（"1" / "第2个"）
        String digits = id.replaceAll("\\D+", "");
        if (!digits.isEmpty()) {
            try {
                int idx = Integer.parseInt(digits);
                if (idx >= 1 && idx <= candidates.size()) {
                    return candidates.get(idx - 1).operation();
                }
            } catch (NumberFormatException ignored) {
                // 不是序号，正常情况
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSlots(JsonNode node) {
        if (!node.isObject()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 从模型输出里截出 JSON。
     *
     * <p>模型就算被要求「只输出 JSON」，也很常见会加一句「好的，这是结果：」或包一层 ```json。
     * 用括号配平找第一个完整对象，比正则贪婪匹配可靠（JSON 里可能嵌套 {}）。</p>
     */
    static String extractJson(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        int start = s.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    // ==================== 降级 ====================

    /**
     * 模型不可用时的兜底：选图谱评分第一的接口，参数全部留给追问。
     *
     * <p>故意不尝试「规则抽参数」：自然语言里的时间和人员关系（"上个月张三负责的"）
     * 靠正则抽出来的东西十次有三次是错的，而错参数比没参数糟糕得多 ——
     * 没参数用户看得见要补什么，错参数会直接查到别人的数据或查空。</p>
     *
     * <p>question 在这里刻意留空，交给上层根据真实缺失生成。
     * 之前把异常 message 拼进 question，结果前端显示了
     * 「Connection refused: getsockopt: /127.0.0.1:9090」——
     * 用户看不懂，还把内网地址泄露出去了。原始原因只进日志。</p>
     */
    private Decision fallback(List<OperationMatch> candidates, Map<String, Object> knownSlots,
                              String reason) {
        ApiOperation top = candidates.get(0).operation();
        return new Decision(top.id(), new LinkedHashMap<>(knownSlots),
                "", "降级选择: " + reason, false);
    }

    // ==================== 结果 ====================

    /**
     * @param operationId 选中的接口；空表示没匹配上
     * @param slots       模型抽出来的参数（未校验，执行前还要过一遍白名单和类型）
     * @param question    需要向用户追问的话
     * @param reason      模型给的选择理由，只进日志
     * @param fromModel   false 表示走了降级
     */
    public record Decision(String operationId, Map<String, Object> slots, String question,
                           String reason, boolean fromModel) {

        static Decision unmatched(String question) {
            return new Decision(null, Map.of(), question, "", true);
        }
    }

    private String ensureQuestion(String q) {
        return q == null ? "" : q.trim();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
