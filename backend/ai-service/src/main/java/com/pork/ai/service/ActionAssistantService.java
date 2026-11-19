package com.pork.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.ActionProperties;
import com.pork.ai.config.GraphProperties;
import com.pork.ai.dto.ActionChatRequest;
import com.pork.ai.dto.ActionChatResponse;
import com.pork.ai.dto.ActionChatResponse.Candidate;
import com.pork.ai.dto.ActionChatResponse.ExecutionBrief;
import com.pork.ai.dto.ActionChatResponse.MissingSlot;
import com.pork.ai.dto.ActionChatResponse.OperationBrief;
import com.pork.ai.graph.ActionExecutor;
import com.pork.ai.graph.ArchGraphService;
import com.pork.ai.graph.SlotFiller;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.ApiParam;
import com.pork.ai.graph.model.OperationMatch;
import com.pork.ai.graph.session.ActionSession;
import com.pork.ai.graph.session.ActionSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 自然语言操作的总编排。
 *
 * <p>四步固定顺序，每一步都不允许跳过：</p>
 * <pre>
 *   一句话
 *     ↓ ① 图谱检索（倒排 + 模块扩散）         → 候选 8 个
 *     ↓ ② 千问在候选里选一个 + 抽参数          → operationId + slots
 *     ↓ ③ 后端对比 schema 算缺什么             → missing（模型说了不算）
 *     ↓ ④ 写操作要用户点确认 / 读操作直接执行   → 经网关带用户令牌
 * </pre>
 *
 * <p>为什么不让模型一步到位输出「调哪个接口 + 参数 + 直接执行」：
 * 那样一次幻觉就是一次真实写库。现在模型只影响①和②，
 * ③④是纯代码判断，最坏结果是「选错接口但被策略拦下」或者「多问一句」。</p>
 */
@Slf4j
@Service
public class ActionAssistantService {

    /** 用户一句话的长度上限，和消费者端追问保持一致 */
    private static final int MAX_TEXT_LEN = 500;

    private static final Pattern NUMERIC = Pattern.compile(
            "^(int|long|short|byte|Integer|Long|Short|Byte|BigDecimal|BigInteger|Double|double|Float|float)$");
    private static final Pattern DATE = Pattern.compile("^(LocalDate|Date|LocalDateTime)$");

    /**
     * 降级（模型不可用）时挂在回答前面的提示。
     *
     * <p>降级路径按检索分取 Top1，同分时挑到哪个几乎是随机的——实测
     * 「今天卖了多少猪肉」会落到召回相关的只读接口上，接口本身没错，
     * 但答的不是用户问的问题。这种「看起来成功、其实是另一件事」的结果
     * 比明确报错危险，所以必须让用户知道这次不是模型理解的。</p>
     */
    private static final String DEGRADED_NOTE =
            "（当前连不上大模型，这是按关键词匹配选出来的接口，可能不是你想要的；"
                    + "换个说法或说得更具体些可以提准。）\n";

    private final ArchGraphService graph;
    private final SlotFiller slotFiller;
    private final ActionExecutor executor;
    private final ActionSessionStore sessions;
    private final ActionProperties actionProps;
    private final GraphProperties graphProps;
    private final ObjectMapper objectMapper;

    public ActionAssistantService(ArchGraphService graph,
                                  SlotFiller slotFiller,
                                  ActionExecutor executor,
                                  ActionSessionStore sessions,
                                  ActionProperties actionProps,
                                  GraphProperties graphProps,
                                  ObjectMapper objectMapper) {
        this.graph = graph;
        this.slotFiller = slotFiller;
        this.executor = executor;
        this.sessions = sessions;
        this.actionProps = actionProps;
        this.graphProps = graphProps;
        this.objectMapper = objectMapper;
    }

    // ==================== 主流程 ====================

    public ActionChatResponse chat(ActionChatRequest req, String userId, String authorization) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return error(null, "请输入你想做的事，比如「查一下这个月各养殖场的存栏量」。");
        }
        String text = req.text().trim();
        if (text.length() > MAX_TEXT_LEN) {
            return error(req.conversationId(),
                    "这句话太长了，请控制在 " + MAX_TEXT_LEN + " 字以内。");
        }
        if (!graphProps.isEnabled() || graph.snapshot().operations().isEmpty()) {
            return error(req.conversationId(), "架构图谱还没建好，暂时无法执行操作。");
        }

        // reset 或换人：直接开新会话，避免上一个用户的上下文串进来
        String convId = Boolean.TRUE.equals(req.reset()) ? null : req.conversationId();
        ActionSession session = sessions.createOrGet(convId, userId);
        if (session.getUserId() != null && !session.getUserId().equals(userId)) {
            log.warn("会话归属不一致 conv={} owner={} current={}",
                    session.getConversationId(), session.getUserId(), userId);
            return error(null, "会话已失效，请重新开始。");
        }
        session.touch();

        // ① 检索
        List<OperationMatch> candidates = buildCandidates(text, session);
        if (candidates.isEmpty()) {
            return unsupported(session, text,
                    "我在系统里没找到能对应这句话的功能。可以换个说法，或者说得更具体，"
                            + "比如「查询某养殖场的批次列表」。");
        }

        // ② 模型选择 + 抽槽
        SlotFiller.Decision decision = slotFiller.decide(
                text, candidates, session.getSlots(), "action:" + userId);

        if (decision.operationId() == null) {
            return unsupported(session, text,
                    decision.question().isBlank()
                            ? "这句话我没法对应到具体功能，能说得再具体一点吗？"
                            : decision.question());
        }

        ApiOperation op = graph.findById(decision.operationId());
        if (op == null) {
            return error(session.getConversationId(), "选中的接口已经不在图谱里了，请重新发起。");
        }

        // ③ 合并并校验参数，缺什么由后端算
        session.setOperationId(op.id());
        session.setSlots(mergeSlots(session.getSlots(), decision.slots(), op));

        List<MissingSlot> missing = missingSlots(op, session.getSlots());
        String note = decision.fromModel() ? "" : DEGRADED_NOTE;
        if (!missing.isEmpty()) {
            session.setPendingConfirm(false);
            String question = decision.question() == null || decision.question().isBlank()
                    ? defaultQuestion(op, missing)
                    : decision.question();
            return new ActionChatResponse("NEED_SLOT", session.getConversationId(),
                    brief(op), session.getSlots(), missing, note + question, note + question, null,
                    candidateBriefs(candidates));
        }

        // ④ 执行：读操作直接跑，写操作先要一次确认
        if (!op.isReadOnly()) {
            session.setPendingConfirm(true);
            String q = note + "即将执行「" + op.summary() + "」（" + op.method() + " " + op.path()
                    + "），确认继续吗？";
            return new ActionChatResponse("READY", session.getConversationId(),
                    brief(op), session.getSlots(), List.of(), q, q, null,
                    candidateBriefs(candidates));
        }

        return run(session, op, authorization, candidates, note);
    }

    /** 用户在卡片上点了「确认执行」 */
    public ActionChatResponse confirm(String conversationId, String userId, String authorization) {
        ActionSession session = sessions.get(conversationId);
        if (session == null) {
            return error(conversationId, "会话已过期，请重新描述一次要做什么。");
        }
        if (session.getUserId() != null && !session.getUserId().equals(userId)) {
            return error(conversationId, "会话已失效，请重新开始。");
        }
        if (!session.isPendingConfirm() || session.getOperationId() == null) {
            return error(conversationId, "当前没有等待确认的操作。");
        }
        ApiOperation op = graph.findById(session.getOperationId());
        if (op == null) {
            return error(conversationId, "选中的接口已经不在图谱里了，请重新发起。");
        }
        List<MissingSlot> missing = missingSlots(op, session.getSlots());
        if (!missing.isEmpty()) {
            // 期间参数被清掉了（理论上不会），退回收参数的状态而不是硬执行
            session.setPendingConfirm(false);
            String q = defaultQuestion(op, missing);
            return new ActionChatResponse("NEED_SLOT", conversationId, brief(op),
                    session.getSlots(), missing, q, q, null, List.of());
        }
        session.setPendingConfirm(false);
        session.touch();
        return run(session, op, authorization, List.of(), "");
    }

    // ==================== 执行 ====================

    private ActionChatResponse run(ActionSession session, ApiOperation op,
                                   String authorization, List<OperationMatch> candidates,
                                   String note) {
        ActionExecutor.ExecutionResult r =
                executor.execute(op, session.getSlots(), authorization, session.getUserId());

        // 执行过就清空上下文：同一个接口连着跑两次多半不是用户想要的
        session.setOperationId(null);
        session.setSlots(new LinkedHashMap<>());
        session.setPendingConfirm(false);

        ExecutionBrief brief = new ExecutionBrief(
                r.success(), r.status(), r.elapsedMs(), pretty(r.body()), r.error());

        String answer;
        if (r.success()) {
            answer = "已执行「" + op.summary() + "」。" + countHint(r.body());
        } else if (!r.executed()) {
            answer = r.error();
        } else {
            answer = "执行「" + op.summary() + "」失败：" + r.error();
        }

        log.info("ai_action_done user={} op={} success={} status={} ms={}",
                session.getUserId(), op.id(), r.success(), r.status(), r.elapsedMs());

        return new ActionChatResponse(r.success() ? "EXECUTED" : "FAILED",
                session.getConversationId(), brief(op), Map.of(), List.of(),
                note + answer, note + answer, brief, candidateBriefs(candidates));
    }

    // ==================== 候选 ====================

    /**
     * 候选 = 会话里锁定的接口（如果有） + 检索结果。
     *
     * <p>把上一轮的接口放第一位，是为了「用户正在补参数」这种情况：
     * 他说「用 Excel」时，重新检索很可能命中别的导出接口，
     * 模型就会被带跑。已经有上下文时，先让它在原接口上补参数。</p>
     */
    private List<OperationMatch> buildCandidates(String text, ActionSession session) {
        List<OperationMatch> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (session.getOperationId() != null) {
            ApiOperation prev = graph.findById(session.getOperationId());
            if (prev != null && executor.isExecutable(prev)) {
                out.add(new OperationMatch(prev, 1.0, List.of("会话上下文")));
                seen.add(prev.id());
            }
        }

        int limit = Math.max(1, graphProps.getMaxCandidates());
        for (OperationMatch m : graph.search(text, limit * 2)) {
            if (out.size() >= limit) {
                break;
            }
            if (seen.contains(m.operation().id())) {
                continue;
            }
            if (!executor.isExecutable(m.operation())) {
                continue;
            }
            out.add(m);
            seen.add(m.operation().id());
        }
        return out;
    }

    // ==================== 参数 ====================

    /**
     * 合并参数。
     *
     * <p>三条规则：</p>
     * <ul>
     *   <li>schema 里没有的键直接丢 —— 模型偶尔会自己加个 userId 之类的字段；</li>
     *   <li>body 做浅合并而不是整体覆盖，否则第二轮回一句「格式用 Excel」会把之前填的字段清掉；</li>
     *   <li>空字符串视为没填，允许用户用「清空」的方式反悔。</li>
     * </ul>
     */
    private Map<String, Object> mergeSlots(Map<String, Object> existing,
                                           Map<String, Object> incoming, ApiOperation op) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        Set<String> allowed = op.slots().stream()
                .map(ApiParam::name).collect(Collectors.toCollection(LinkedHashSet::new));
        allowed.add("body");

        if (incoming != null) {
            for (Map.Entry<String, Object> e : incoming.entrySet()) {
                String key = e.getKey();
                if (!allowed.contains(key)) {
                    continue;
                }
                Object value = e.getValue();
                if (value == null || (value instanceof String s && s.isBlank())) {
                    continue;
                }
                if ("body".equals(key) && value instanceof Map<?, ?>) {
                    Map<String, Object> prev = asMap(merged.get("body"));
                    prev.putAll(asMap(value));
                    merged.put("body", prev);
                } else {
                    merged.put(key, value);
                }
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private List<MissingSlot> missingSlots(ApiOperation op, Map<String, Object> slots) {
        List<MissingSlot> missing = new ArrayList<>();
        for (ApiParam p : op.requiredSlots()) {
            if (!filled(p, slots)) {
                missing.add(new MissingSlot(p.name(), p.type(), hintOf(p)));
            }
        }
        return missing;
    }

    private boolean filled(ApiParam p, Map<String, Object> slots) {
        if (slots == null) {
            return false;
        }
        if (p.isBody()) {
            Map<String, Object> body = asMap(slots.get("body"));
            return !body.isEmpty();
        }
        Object v = slots.get(p.name());
        return v != null && !String.valueOf(v).isBlank();
    }

    private String hintOf(ApiParam p) {
        String type = p.type() == null ? "" : p.type();
        if (DATE.matcher(type).matches()) {
            return "日期，格式 YYYY-MM-DD";
        }
        if (NUMERIC.matcher(type).matches()) {
            return "数字";
        }
        if (p.isBody()) {
            String fields = p.bodyFields().stream()
                    .map(ApiParam.BodyField::name)
                    .collect(Collectors.joining("、"));
            return fields.isEmpty() ? "请求体对象" : "需要提供：" + fields;
        }
        return "文本";
    }

    private String defaultQuestion(ApiOperation op, List<MissingSlot> missing) {
        String names = missing.stream()
                .map(m -> m.name() + "（" + m.hint() + "）")
                .collect(Collectors.joining("、"));
        return "要" + op.summary() + "，还需要补充：" + names + "。";
    }

    // ==================== 输出 ====================

    private OperationBrief brief(ApiOperation op) {
        String module = graph.snapshot().moduleById()
                .getOrDefault(op.moduleId(), null) == null
                ? op.moduleId() : graph.snapshot().moduleById().get(op.moduleId()).name();
        return new OperationBrief(op.id(), op.summary(), op.method(), op.path(),
                op.gatewayPath(), module, op.service());
    }

    private List<Candidate> candidateBriefs(List<OperationMatch> matches) {
        if (matches == null) {
            return List.of();
        }
        return matches.stream()
                .limit(3)
                .map(m -> new Candidate(m.operation().id(), m.operation().summary(),
                        m.operation().method(), m.score()))
                .collect(Collectors.toList());
    }

    private ActionChatResponse unsupported(ActionSession session, String text, String message) {
        log.info("ai_action_unsupported user={} text={}", session.getUserId(), abbreviate(text));
        return new ActionChatResponse("UNSUPPORTED", session.getConversationId(),
                null, session.getSlots(), List.of(), message, message, null, List.of());
    }

    private ActionChatResponse error(String conversationId, String message) {
        return new ActionChatResponse("FAILED", conversationId, null, Map.of(),
                List.of(), message, message, null, List.of());
    }

    /** 结果尽量格式化成缩进 JSON，前端和人都好读 */
    private String pretty(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return body;
        }
    }

    /** 从结果里数一下条数，给一句「查到 12 条」这种实在的反馈 */
    private String countHint(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (data.isArray()) {
                return "共 " + data.size() + " 条。";
            }
            if (data.has("records") && data.path("records").isArray()) {
                return "共 " + data.path("total").asLong(data.path("records").size()) + " 条，本页返回 "
                        + data.path("records").size() + " 条。";
            }
        } catch (Exception ignored) {
            // 不是 JSON，不猜
        }
        return "";
    }

    private String abbreviate(String text) {
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}
