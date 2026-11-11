package com.pork.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.agent.llm.LlmClient;
import com.pork.agent.llm.LlmTurn;
import com.pork.agent.llm.ToolCall;
import com.pork.agent.support.AgentContext;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolRegistry;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 让模型自己决定查什么：一个问题上可以连续调多个工具，再统一作答。
 * <p>
 * 与单工具路径的分工：<b>能靠关键词确定意图的问题仍然走单工具</b>（一次模型调用、3 秒、结论确定）；
 * 这条循环只接两种问题——换了说法的长尾，和多步問題（先看运单，再查这车上的猪，再看检疫证）。
 * 这样「像对话」是增量能力，不是把所有问题都变慢。
 * <p>
 * 三条硬约束：
 * <ul>
 *   <li><b>只能读</b>：工具本身没有写方法，{@code ReadOnlyRemote} 只有 get，模型要不到写权限；</li>
 *   <li><b>参数不加工</b>：模型给的 arguments 原样交给工具校验，错就回「没听出要查哪个编号」，
 *       绝不替它猜一个编号；</li>
 *   <li><b>有预算</b>：最多 4 轮、总时长 30 秒，超了就用已查到的事实作答，不让用户干等。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoopService {

    /** 一次问答最多让模型调几轮工具：日常多步问题 2-3 轮够用，再多人也看不懂 */
    private static final int MAX_ROUNDS = 4;
    /** 总预算：前端请求超时 30 秒，这里留 10 秒给最后一轮措辞，别把用户卡在转圈上 */
    private static final long BUDGET_MS = 20_000;
    /** 单次工具结果送进上下文的字数上限：整表塞进去既贵又容易答偏 */
    private static final int TOOL_PAYLOAD_CHARS = 2500;

    private final ToolRegistry registry;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    /** 一次自主查询的结果；answer 可能为空（模型没给出措辞），由调用方兜底成本地归纳 */
    public record Outcome(String answer, List<String> tools, List<String> labels,
                          List<String> facts, List<String> appendix) {
    }

    private static final String SYSTEM_PROMPT = """
            你是「猪肉全链路追溯系统」的只读助手。你可以调用工具查真实数据，也可以直接回答。

            查数纪律：
            1. 先想清楚需要哪几块数据。一个工具不够就继续调下一个——比如先查运单，再查这车上的猪，
               再看那头猪的检疫证。查到够回答就停，不要为了「完整」再查一遍。
            2. 不要在没查数据的情况下猜数字、编号、状态、时间。工具没返回的，就说「系统里没有这项数据」。
            3. 工具返回「取数失败」时如实说这部分查不到，不要拿别的工具的结果顶替，也不要编。
            4. 你只能查，不能改：用户要求审批、驳回、作废、签收、放行时，告诉他去哪个页面自己操作。
            5. 系统判定器给出的结论（能不能出栏、瘦肉精是否合格、免疫档案是否齐全）原样转述，
               不要自己重新判断，也不要替用户做决定。
            6. 数字不许自己算：不要把两条原因合并成一行，不要写「5+2=7」这类算式，
               也不要把不同口径的数字相加。要合计时只用工具返回的合计值。

            表达方式（跟着问题走，不要套模板）：
            - 问「多少 / 几条 / 什么时候」：一两句话给出数字和口径。
            - 问「为什么 / 怎么回事 / 什么影响」：先一句结论，再分点讲原因，可以引用查到的具体条目。
            - 问「这头猪 / 这车货的来龙去脉」：按时间顺序讲它经过了哪些环节、现在在哪一步。
            - 用户追问、改口、纠正你时，先用半句话接住（「你说的 ET-… 那头」），再答，别重复上一轮整段。
            - 篇幅跟着问题走：简单问题一两句，复杂问题可以到 8-10 行，不要为了凑结构硬分点。

            不许出现表名、字段名、枚举码、接口名；用业务说法（说「耳标」不说 ear_tag_no）。
            不要自己列「建议 / 待办清单」——系统另有【系统建议】区，你写了会冲突。

            本系统常见术语（问到时按这里的口径讲，不要拿通用定义顶替）：
            - 降采样：温度探头通常几秒一条，非异常值按最小间隔（一般 30 秒）只入账一条，
              其余标为「降采样丢弃」；越界的异常值永远保留。被丢弃的记录仍留在待确认队列里可查。
            - 入账：设备报文通过校验后写进业务表，成为业务可用的数据（不是「上传成功」就算入账）。
            - 转人工：自动校验没通过，停在待确认队列等人判断，不是拒绝。
            - 驳回：审批的终态，原单不能改，整改后重新申报。
            - 拒收：门店收货时整站不收，需走后续处置。
            """;

    /**
     * 跑一次自主查询。模型不可用、或一轮都没调通时返回 empty，由调用方回退到单工具路径。
     */
    public Optional<Outcome> run(String question, List<Map<String, String>> history, AgentContext ctx) {
        if (!llm.enabled()) return Optional.empty();
        long started = System.currentTimeMillis();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (Map<String, String> h : history) messages.add(new LinkedHashMap<>(h));
        messages.add(Map.of("role", "user", "content", question));

        String toolsJson = registry.toolsJson(mapper, ctx.role());
        List<String> tools = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        List<String> appendix = new ArrayList<>();
        // 同参数重复调用直接命中缓存：模型偶尔会把同一个查询要两遍，不该为此再打一次业务接口
        Map<String, String> cache = new LinkedHashMap<>();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (System.currentTimeMillis() - started > BUDGET_MS) {
                log.info("agent_loop_budget_exceeded round={} costMs={}", round, System.currentTimeMillis() - started);
                break;
            }
            Optional<LlmTurn> turn = llm.chatWithTools(messages, toolsJson);
            if (turn.isEmpty()) return Optional.empty();
            LlmTurn t = turn.get();

            if (!t.wantsTools()) {
                String content = t.content() == null ? "" : t.content().trim();
                if (content.isBlank() && facts.isEmpty()) return Optional.empty();
                log.info("agent_loop_done rounds={} tools={} costMs={}",
                        round + 1, tools, System.currentTimeMillis() - started);
                return Optional.of(new Outcome(content, tools, labels, facts, appendix));
            }

            messages.add(t.assistantMessage());
            for (ToolCall call : t.toolCalls()) {
                String key = call.name() + "|" + call.arguments();
                String payload = cache.get(key);
                if (payload == null) {
                    payload = exec(call, ctx, tools, labels, facts, appendix);
                    cache.put(key, payload);
                }
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", call.id());
                toolMsg.put("content", payload);
                messages.add(toolMsg);
            }
        }

        // 轮数或时间用尽：不再调工具，用已查到的事实写一个答案
        if (facts.isEmpty()) return Optional.empty();
        String digest = clip(String.join("\n", facts), TOOL_PAYLOAD_CHARS);
        String answer = llm.complete("""
                你是「猪肉全链路追溯系统」的助手。下面是已经查好的事实清单，请据此回答用户刚才的问题。
                只依据清单，不补充清单外的数字或编号；清单里没有的就说「系统里没有这项数据」。
                不要列建议清单。篇幅跟着问题走，简单问题一两句。
                """, List.of(), "【事实清单】\n" + digest + "\n【用户问题】" + question).orElse("");
        log.info("agent_loop_partial rounds={} tools={} costMs={}", MAX_ROUNDS, tools, System.currentTimeMillis() - started);
        return Optional.of(new Outcome(answer, tools, labels, facts, appendix));
    }

    /** 执行一次工具调用，把结果压成一条 tool 消息；异常也变成文字，让模型如实说明而不是崩掉 */
    private String exec(ToolCall call, AgentContext ctx, List<String> tools, List<String> labels,
                        List<String> facts, List<String> appendix) {
        Optional<AgentTool> found = registry.byName(call.name(), ctx.role());
        if (found.isEmpty()) {
            log.warn("agent_loop_unknown_tool name={} role={}", call.name(), ctx.role());
            return "没有名为「" + call.name() + "」的能力，请从可用工具里选。";
        }
        AgentTool tool = found.get();
        try {
            ToolResult r = tool.execute(parseArgs(call.arguments()), ctx);
            if (!tools.contains(r.tool())) tools.add(r.tool());
            if (!labels.contains(r.label())) labels.add(r.label());
            facts.addAll(r.facts());
            appendix.addAll(r.appendix());
            StringBuilder sb = new StringBuilder(r.note() == null ? "" : r.note()).append('\n');
            int lines = 0;
            for (String f : r.facts()) {
                if (lines++ >= 60) {
                    sb.append("…（其余 ").append(r.facts().size() - 60).append(" 行略）\n");
                    break;
                }
                sb.append(f).append('\n');
            }
            return clip(sb.toString(), TOOL_PAYLOAD_CHARS);
        } catch (Exception e) {
            log.warn("agent_loop_tool_failed tool={} err={}", call.name(), e.toString());
            return "取数失败：" + (e.getMessage() == null ? "服务暂时没有响应" : e.getMessage());
        }
    }

    /** 模型的 arguments 是 JSON 串；只取字符串值，其余类型一律转文本，不在这里做语义修正 */
    private Map<String, String> parseArgs(String json) {
        Map<String, String> args = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return args;
        try {
            JsonNode root = mapper.readTree(json);
            root.fields().forEachRemaining(e -> {
                String v = e.getValue().asText("");
                if (!v.isBlank()) args.put(e.getKey(), v);
            });
        } catch (Exception e) {
            log.warn("agent_loop_args_invalid json={}", json);
        }
        return args;
    }

    private String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
