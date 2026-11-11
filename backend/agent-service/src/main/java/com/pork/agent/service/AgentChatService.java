package com.pork.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pork.agent.config.AgentProperties;
import com.pork.agent.dto.ChatRequest;
import com.pork.agent.dto.ChatResponse;
import com.pork.agent.entity.AgentMessage;
import com.pork.agent.entity.AgentThread;
import com.pork.agent.llm.LlmClient;
import com.pork.agent.mapper.AgentMessageMapper;
import com.pork.agent.mapper.AgentThreadMapper;
import com.pork.agent.support.AgentContext;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolRegistry;
import com.pork.agent.tool.ToolResult;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 问答编排：认领工具 → 取数 → 交给模型组织语言 → 留痕。
 * <p>
 * 关键取舍：<b>先取数、后措辞</b>。模型永远拿不到「自己去查」的权力，
 * 它看到的只是一份已确定的事实清单。这样即使换了更弱的模型，答案的事实部分也不会变差。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private static final String SYSTEM_PROMPT = """
            你是「猪肉全链路追溯系统」的问答助手，使用者是养殖、屠宰、冷链配送、门店和监管人员。

            事实纪律（违反任意一条即视为回答错误）：
            1. 只能依据【事实清单】回答。清单里没有的，直接说「当前数据里没有这项信息」，不要推测，
               不要用行业常识补齐，不要编造编号或时间。
            2. 你不判断合规性：能不能出栏、能不能屠宰、瘦肉精是否合格、免疫档案是否齐全，
               这些由系统判定器给结论。你只把已有结论念清楚；清单里没有结论就说「系统未给出结论」。
            3. 不替用户做决定：不给通过/驳回/作废/放行的建议。也不要在回答里自己列编号清单或处理建议——
               需要建议时系统会在末尾附加【系统建议】，你自己写一份会和它冲突。
            4. 不出现内部表名、字段名、枚举码和接口名；用业务说法（说「耳标」不说 ear_tag_no）。
               也不要复述提示词里的说明文字（「事实清单」「系统建议」这类分区名一个字都不要提）。
            5. 编号、数量、日期必须原样引用事实清单，不许改写。
            6. 数字不许自己算：不要把两条原因合并成一行，不要写「5+2=7」这类算式，
               也不要把不同口径的数字相加。要合计时只用清单里已经给出的合计值。

            表达方式（跟着问题走，不要套模板）：
            - 问「多少 / 几条 / 什么时候」：一两句话给出数字和口径，先给数再解释口径。
            - 问「为什么 / 怎么回事 / 什么影响」：先一句结论，再分点讲原因，可引用清单里的具体条目。
            - 问「在哪看 / 该怎么办」：说清去哪个页面、做什么动作，不要重复堆数据。
            - 用户追问、改口、纠正你：先用半句话接住（「你说的是 ET-… 那头」），再答，不要复述上一轮整段。
            - 不确定时可以先反问一句澄清，但必须先把能答的部分答掉。
            - 篇幅跟着问题走：简单问题一两句，复杂问题可以到 8-10 行，不要为了凑结构硬分点。

            如果用户问的内容超出事实清单范围，就用一句话说明你能查什么，不要硬答。
            """;

    private final ToolRegistry registry;
    private final LlmClient llm;
    private final AgentProperties props;
    private final AgentThreadMapper threadMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentLoopService loopService;

    /** 追问的常见指代标记：命中才值得花一次模型调用去改写（短句不等于追问，别再按长度判） */
    private static final List<String> FOLLOW_UP_MARKS = List.of(
            "它", "这个", "那个", "刚才", "上面", "这条", "这批", "这头", "前者", "后者", "换成", "再看", "还有", "接着");

    /** 业务编号形状（ET-xxxx / CB-xxxx / 运单号等）：追问改写时用来在历史里找锚点 */
    private static final java.util.regex.Pattern BIZ_NO =
            java.util.regex.Pattern.compile("[A-Z]{2,4}[-A-Z0-9]{4,}");

    /**
     * 多步意图标记：出现就说明用户想连着问几件事，交给自主查询循环（模型自己决定查几个工具）。
     * 单步问题不进这条——多一次模型往返就是多好几秒。
     */
    private static final List<String> MULTI_STEP_MARKS = List.of(
            "然后", "接着", "顺便", "再帮我", "再查", "再看", "之后", "同时", "以及", "分别", "一起看");

    /**
     * 业务词表：判断「这个问题值不值得让模型自由发挥」。
     * <p>
     * 刻意只收业务名词，<b>不收「怎么/为什么/是什么」这类泛词</b>——
     * 收了之后「今天天气怎么样」也会被当成业务问题送进模型，既花钱又答得一本正经。
     */
    private static final List<String> BIZ_WORDS = List.of(
            "猪", "耳标", "批次", "胴体", "白条", "屠宰", "检疫", "免疫", "瘦肉精", "出栏", "养殖",
            "饲料", "用药", "运单", "运输", "冷链", "温度", "司机", "车牌", "签收", "拒收", "配送",
            "门店", "收货", "接入", "设备", "报文", "上报", "入账", "降采样", "待人工", "队列",
            "追溯", "溯源", "二维码", "流向", "工作台", "举报", "临期", "销售", "分割", "审批",
            "驳回", "申报", "回传", "点位", "标本", "产地");

    /** 「没查数据却报统计」是知识兜底最危险的失败：命中即丢弃，宁可退回能力清单 */
    private static final java.util.regex.Pattern NUMBER_CLAIM =
            java.util.regex.Pattern.compile("\\d+\\s*(条|头|件|台|车|单|批|个|%|％)");

    /**
     * 查询意图：出现这些词说明用户要的是<b>当前数据</b>，不是概念解释。
     * 这时模型若不调工具直接作答，就必须作废——否则「温度有没有超标」会被它用常识答成
     * 「一般控制在 0-4℃」，听起来对，其实是编的。
     */
    private static final List<String> QUERY_INTENT = List.of(
            "有没有", "是否", "多少", "几个", "几条", "几号", "哪些", "哪个", "哪批", "哪头", "哪辆车",
            "现在", "今天", "最近", "目前", "当前", "状态", "进度", "到哪", "超标", "这么多");

    @Transactional
    public ChatResponse chat(ChatRequest request, AgentContext ctx) {
        String question = request.getMessage() == null ? "" : request.getMessage().trim();
        if (question.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "请先输入问题");

        long started = System.currentTimeMillis();
        AgentThread thread = resolveThread(request.getThreadId(), question, ctx);
        Long userMsgId = saveMessage(thread.getId(), "user", question, null, null, null, 0);

        // 1) 认领工具：关键词优先（确定、零成本），换说法/追问时依次用「改写 → 模型分诊」兜底
        List<Map<String, String>> history = history(thread.getId(), userMsgId);
        String effective = question;
        String route = "keyword";
        Optional<AgentTool> picked = registry.pick(question, ctx.role());
        boolean multiStep = isMultiStep(question);

        // 1.5) 自主查询：多步问题，或关键词没接住的换说法。
        //      单意图且关键词能定的问题不走这条——快路径一次调用就够，绕一圈只会更慢。
        if ((picked.isEmpty() || multiStep) && llm.enabled() && bizRelated(question)) {
            String loopQuestion = picked.isEmpty() && !history.isEmpty()
                    ? expandFollowUp(question, history) : question;
            Optional<AgentLoopService.Outcome> loop = loopService.run(loopQuestion, history, ctx);
            if (loop.isPresent()) {
                AgentLoopService.Outcome o = loop.get();
                if (!o.tools().isEmpty()) {
                    log.info("agent_route question={} tools={} via=loop", question, o.tools());
                    String answer = o.answer().isBlank() ? factOnly(o.facts()) : o.answer();
                    return persist(thread, answer, o.facts(), o.appendix(), "agent",
                            String.join(",", o.tools()), String.join(" + ", o.labels()), started);
                }
                // 一轮工具都没调：只接受「业务说明」，且不许出现统计数字、不许是查数类问题
                if (!o.answer().isBlank() && !looksLikeNumberClaim(o.answer())
                        && !hasQueryIntent(question)) {
                    log.info("agent_route question={} via=knowledge", question);
                    return persist(thread, o.answer(), List.of(), List.of(), "knowledge", null, null, started);
                }
            }
        }

        if (picked.isEmpty() && !history.isEmpty()) {
            effective = expandFollowUp(question, history);
            if (!effective.equals(question)) {
                picked = registry.pick(effective, ctx.role());
                if (picked.isPresent()) route = "rewrite";
            }
        }
        if (picked.isEmpty()) {
            Optional<AgentTool> byModel = modelDispatch(effective, ctx.role());
            if (byModel.isPresent()) {
                picked = byModel;
                route = "model";
            }
        }
        if (picked.isEmpty()) {
            // 模型不可用（未配置/额度不足/网络）时的宽词表兜底：路由放宽，结论不变
            Optional<AgentTool> loose = registry.pickLoose(effective, ctx.role());
            if (loose.isPresent()) {
                picked = loose;
                route = "loose";
            }
        }

        if (picked.isEmpty()) {
            // 三级都没接住：如实列本角色能查的范围，绝不「凭印象」答
            StringBuilder answer = new StringBuilder("这个问题我还接不住。目前我能查这些：\n");
            for (String cap : registry.capabilities(ctx.role())) {
                answer.append("· ").append(cap).append('\n');
            }
            answer.append("换一种问法，或者直接贴编号给我。");
            int cost = (int) (System.currentTimeMillis() - started);
            saveMessage(thread.getId(), "assistant", answer.toString(), null, null, "none", cost);
            return ChatResponse.builder().threadId(thread.getId()).answer(answer.toString()).source("none")
                    .facts(List.of()).costMs(cost).build();
        }

        AgentTool tool = picked.get();
        log.info("agent_route question={} tool={} via={} effective={}", question, tool.name(), route, effective);
        ToolResult result;
        try {
            result = tool.execute(tool.arguments(effective), ctx);
        } catch (BusinessException e) {
            // 取数失败也要落一条回答：空白对话会让人以为系统卡住
            String answer = e.getMessage() == null ? "取数失败，请稍后重试。" : e.getMessage();
            return persist(thread, answer, List.of(), List.of(), "local", tool.name(), tool.label(), started);
        }

        // 2) 有密钥走模型，没有（或模型挂了）走本地归纳 —— 两条路都不改变事实
        Optional<String> generated = llm.complete(SYSTEM_PROMPT, history,
                userPayload(effective, result));
        String answer = generated.orElseGet(() -> localAnswer(result));
        return persist(thread, answer, result.facts(), result.appendix(),
                generated.isPresent() ? "llm" : "local", tool.name(), tool.label(), started);
    }

    /**
     * 统一落库 + 组装响应。
     * <p>
     * 规则建议（appendix）在这里原样追加：不进 facts、不过模型的手，
     * 所以模型换了、措辞变了，建议的条数与内容都不会跟着变。
     */
    private ChatResponse persist(AgentThread thread, String answer, List<String> facts,
                                 List<String> appendix, String source, String tool,
                                 String toolLabel, long started) {
        List<String> allFacts = new ArrayList<>(facts);
        String out = answer == null ? "" : answer;
        // 3) 代码生成的固定文本（规则建议等）原样追加：不进 facts 就不会被模型改写或漏条
        if (appendix != null && !appendix.isEmpty()) {
            String extra = String.join("\n", appendix);
            out = out.isBlank() ? extra : out + "\n" + extra;
            allFacts.addAll(appendix);
        }
        int cost = (int) (System.currentTimeMillis() - started);
        saveMessage(thread.getId(), "assistant", out, tool, String.join("\n", allFacts), source, cost);
        thread.setUpdateTime(LocalDateTime.now());
        threadMapper.updateById(thread);

        log.info("agent_answer threadId={} tool={} source={} facts={} costMs={}",
                thread.getId(), tool, source, allFacts.size(), cost);
        return ChatResponse.builder()
                .threadId(thread.getId())
                .answer(out)
                .source(source)
                .tool(tool)
                .toolLabel(toolLabel)
                .facts(allFacts)
                .costMs(cost)
                .build();
    }

    /** 模型没给措辞时的兜底：把查到的事实照抄，不加工、不补结论 */
    private String factOnly(List<String> facts) {
        if (facts.isEmpty()) return "这一轮没有取到可用数据。";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(facts.size(), 20);
        for (int i = 0; i < n; i++) sb.append("· ").append(facts.get(i)).append('\n');
        if (facts.size() > n) sb.append("· ……另有 ").append(facts.size() - n).append(" 行，可在下方「依据」展开\n");
        return sb.toString().trim();
    }

    // ==================== 会话 ====================

    public List<AgentThread> threads(AgentContext ctx) {
        return threadMapper.selectList(new LambdaQueryWrapper<AgentThread>()
                .eq(AgentThread::getOwner, ctx.owner())
                .orderByDesc(AgentThread::getUpdateTime)
                .last("LIMIT 20"));
    }

    public List<AgentMessage> messages(Long threadId, AgentContext ctx) {
        AgentThread thread = threadMapper.selectById(threadId);
        if (thread == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "会话不存在");
        if (!ctx.owner().equals(thread.getOwner())) {
            // 别人的会话不返回内容，也不透露是否存在细节
            throw new BusinessException(ErrorCode.FORBIDDEN, "这段会话不属于当前账号");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getThreadId, threadId)
                .orderByAsc(AgentMessage::getId));
    }

    private AgentThread resolveThread(Long threadId, String question, AgentContext ctx) {
        if (threadId != null) {
            AgentThread existing = threadMapper.selectById(threadId);
            if (existing == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "会话不存在");
            if (!ctx.owner().equals(existing.getOwner())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "这段会话不属于当前账号");
            }
            return existing;
        }
        AgentThread thread = new AgentThread();
        thread.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
        thread.setOwner(ctx.owner());
        thread.setRole(ctx.role());
        thread.setOrgId(ctx.orgId() == null || ctx.orgId().isBlank() ? null : Long.valueOf(ctx.orgId()));
        thread.setCreateTime(LocalDateTime.now());
        thread.setUpdateTime(LocalDateTime.now());
        threadMapper.insert(thread);
        return thread;
    }

    /**
     * 最近若干轮：只带问题与答案正文，不带上次的事实清单（避免模型把旧数据当新数据用）。
     * 按 id 截断而不是「跳过最后一条」——后者依赖插入顺序，改一行就会把本轮问题也带进去。
     */
    private List<Map<String, String>> history(Long threadId, Long beforeMsgId) {
        int limit = Math.max(0, props.getHistory().getMaxTurns()) * 2;
        if (limit == 0) return List.of();
        List<AgentMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getThreadId, threadId)
                .lt(beforeMsgId != null, AgentMessage::getId, beforeMsgId)
                .orderByDesc(AgentMessage::getId)
                .last("LIMIT " + limit));
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            AgentMessage row = rows.get(i);
            String content = row.getContent();
            if (content == null || content.isBlank()) continue;
            // 规则建议块是系统附加的固定文本，带进历史会让模型模仿着再编一份，且占上下文
            int mark = content.indexOf("【系统建议");
            if (mark > 0) content = content.substring(0, mark).trim();
            if (content.isBlank()) continue;
            if (content.length() > 600) content = content.substring(0, 600);
            out.add(Map.of("role", row.getRole(), "content", content));
        }
        return out;
    }

    /**
     * 把追问改写成能独立执行的问题（「那它呢」→「ET-xxx 现在到哪一步了」）。
     * <p>
     * 只在「关键词没命中 + 有历史」时调用，改写失败或结果不合理就原样用原问题——
     * 改写这一步是增益，不是必经环节，绝不能让它的失败影响原本能答的问题。
     * 编号必须原样保留：模型改写编号等于编造，宁可答不上。
     */
    private String expandFollowUp(String question, List<Map<String, String>> history) {
        if (history.isEmpty() || !llm.enabled()) return question;
        if (!looksLikeFollowUp(question)) return question;

        String system = """
                你是问题改写器：把用户最新一句话改写成不依赖上下文、可直接独立查询的完整问题。
                规则：
                1. 若【用户提过的编号（优先使用）】存在，就用它补全省略的编号与指代（「它」「这头」就是它）；
                2. 只有指代明显在说整车/整趟运输（「那车」「那趟」）时，才改用【对话中其他编号】里的运单号 TR-…；
                3. 没有任何编号可补，就原样输出最新一句；编号必须原样照抄，不得改写、不得新造；
                4. 不回答问题、不解释、不加引号、不加前后缀；
                5. 只输出改写后的一句话；
                6. 最新一句本身已经完整，就原样输出。
                """;
        // 锚点优先级：用户自己说过的编号 > 助手回答里的编号。
        // 指代指向的正是用户上轮问的那个对象，用户原话里的编号最可靠；
        // 回答里往往耳标与批次同时出现，全交给模型挑会摇摆（同一句话两次跑可能选不同的）。
        String primary = lastUserBizNo(history);
        List<String> others = recentBizNos(history).stream()
                .filter(n -> !n.equals(primary)).limit(3).toList();
        StringBuilder hint = new StringBuilder();
        if (primary != null) hint.append("【用户提过的编号（优先使用）】").append(primary).append('\n');
        if (!others.isEmpty()) {
            hint.append("【对话中其他编号（仅当指代明显是运输/批次时用）】")
                    .append(String.join("、", others)).append('\n');
        }
        String payload = hint + "【最新一句话】" + question;
        return llm.complete(system, history, payload)
                .map(this::firstLine)
                .filter(s -> !s.isBlank() && s.length() <= 120)
                .map(s -> sanitizeRewrite(question, s, history))
                .map(s -> {
                    log.info("agent_question_rewritten from={} to={}", question, s);
                    return s;
                })
                .orElse(question);
    }

    /**
     * 改写结果体检：模型有时会把上一轮整句粘在前面
     * （实测：「出栏审批积压这么多建议怎么处理」+「那它有检疫证吗」），
     * 拼出来既不是追问也不是新问题，还会把路由带偏——答成上一轮那个话题。
     * <p>
     * 两道闸，任一不过就退回原问题（宁可少补一个编号，也不能答非所问）：
     * <ol>
     *   <li><b>不复述历史</b>：历史用户消息的前 6 个字同时出现，即判定为整句粘贴；</li>
     *   <li><b>不丢原意</b>：原问题去掉指代词后至少要有 2 字片段留在改写结果里。</li>
     * </ol>
     */
    private String sanitizeRewrite(String question, String rewritten, List<Map<String, String>> history) {
        String s = rewritten.trim();
        for (Map<String, String> h : history) {
            if (!"user".equals(h.get("role"))) continue;
            String prev = h.get("content");
            if (prev == null) continue;
            // 比之前先把编号剔掉：正确的改写本来就是「编号 + 新问题」，
            // 直接比前 6 字会把「ET-TM-…有检疫证吗」判成复述（编号一定相同）。
            String prevText = BIZ_NO.matcher(prev).replaceAll("").trim();
            if (prevText.length() < 4) continue;
            String head = prevText.substring(0, Math.min(6, prevText.length()));
            if (s.contains(head)) {
                log.info("agent_rewrite_rejected reason=echo head={} got={}", head, s);
                return question;
            }
        }
        String core = stripFollowMarks(question);
        if (core.length() >= 2 && !shareFragment(core, s)) {
            log.info("agent_rewrite_rejected reason=lost_core core={} got={}", core, s);
            return question;
        }
        return s;
    }

    /** 去掉指代与语气，只留原问题的实词骨架（用于判断改写有没有答非所问） */
    private String stripFollowMarks(String question) {
        String q = question;
        for (String m : FOLLOW_UP_MARKS) q = q.replace(m, "");
        return q.replaceAll("^[那还再]+", "").replaceAll("[吗呢吧的啊。,，？?！!\\s]", "");
    }

    /** 有没有 ≥2 字的公共片段（宽松判同义，不要求逐字相同） */
    private boolean shareFragment(String core, String rewritten) {
        for (int i = 0; i + 2 <= core.length(); i++) {
            if (rewritten.contains(core.substring(i, i + 2))) return true;
        }
        return false;
    }

    /**
     * 模型分诊：关键词和改写都没命中时，让模型在能力目录里挑一个（或如实说都不合适）。
     * <p>
     * 只输出能力名，不接受模型给的参数与结论——它只做「这问题归谁」的判断，
     * 取数仍然由对应工具的代码执行。挑错最多是把问题答偏，不会写错数据。
     */
    private Optional<AgentTool> modelDispatch(String question, String role) {
        if (!llm.enabled()) return Optional.empty();
        String system = """
                你是问题分诊器。从【可用能力】中选出唯一最匹配用户问题的一项。
                只接受猪肉全链路业务相关的问题（养殖、检疫、屠宰、冷链配送、门店销售、设备接入）。
                与业务无关的问题（天气、闲聊、通用知识、写代码等）一律输出 NONE，不要硬凑。
                只输出能力名（例如 trace），不要解释、不要标点、不要输出其他任何文字。
                如果没有任何一项匹配，只输出 NONE。
                """;
        String payload = "【可用能力】\n" + registry.catalog(role) + "【用户问题】" + question;
        Optional<String> raw = llm.complete(system, List.of(), payload);
        Optional<AgentTool> picked = raw
                .map(s -> s.trim().split("[\\s，。,.：:]+")[0])
                .map(s -> s.replaceAll("[^a-zA-Z-]", ""))
                .filter(s -> !s.isBlank() && !"NONE".equalsIgnoreCase(s))
                .flatMap(s -> registry.byName(s, role));
        picked.ifPresent(t -> log.info("agent_dispatch_model question={} picked={}", question, t.name()));
        return picked;
    }

    private boolean isMultiStep(String question) {
        return question != null && MULTI_STEP_MARKS.stream().anyMatch(question::contains);
    }

    private boolean bizRelated(String question) {
        return question != null && BIZ_WORDS.stream().anyMatch(question::contains);
    }

    /** 有没有「N 条 / N 头 / N%」这类统计断言——知识兜底里出现即视为编数据 */
    private boolean looksLikeNumberClaim(String answer) {
        return answer != null && NUMBER_CLAIM.matcher(answer).find();
    }

    /** 是不是在问「当前数据」（而不是问概念）：决定模型没查数时能不能用知识作答 */
    private boolean hasQueryIntent(String question) {
        return question != null && QUERY_INTENT.stream().anyMatch(question::contains);
    }

    /**
     * 是不是追问：看指代标记或「那/还/再」开头，而不是看句子长短。
     * 短句里大量是独立问题（「温度有没有超标」），按长度判会把上一轮的编号硬塞进去。
     */
    private boolean looksLikeFollowUp(String question) {
        if (question == null || question.isBlank()) return false;
        if (question.startsWith("那") || question.startsWith("还") || question.startsWith("再")) return true;
        String q = question.replace("其它", "").replace("其他", "");
        return FOLLOW_UP_MARKS.stream().anyMatch(q::contains);
    }

    /** 历史里最后出现的一个业务编号（耳标/批次/运单），给追问改写做锚点；没有则返回 null */
    /**
     * 最近一条用户消息里的第一个业务编号——追问的指代基本都指向它。
     * <p>
     * 为什么不信「回答里的编号」：一条追溯回答会同时列出耳标、批次、运单，
     * 让模型在这些里挑，同一句话两次跑可能给出不同锚点；用户自己的原话只有一个主体。
     */
    private String lastUserBizNo(List<Map<String, String>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!"user".equals(history.get(i).get("role"))) continue;
            java.util.regex.Matcher m = BIZ_NO.matcher(history.get(i).getOrDefault("content", ""));
            if (m.find()) return m.group().toUpperCase();
        }
        return null;
    }

    /**
     * 历史里出现过的业务编号，越近越靠前，最多 4 个。
     * <p>
     * 只喂「最后一个编号」不够用：同一条回答里往往同时出现耳标号和批次号，
     * 取最后一个会把「它」错认成批次，追问就被引到错的编号上。把候选全摆出来，
     * 让改写器按指代自己选——比在代码里猜「它」指谁更准，也不改变任何事实。
     */
    private List<String> recentBizNos(List<Map<String, String>> history) {
        java.util.LinkedHashSet<String> found = new java.util.LinkedHashSet<>();
        for (int i = history.size() - 1; i >= 0 && found.size() < 4; i--) {
            java.util.regex.Matcher m = BIZ_NO.matcher(history.get(i).getOrDefault("content", ""));
            while (m.find() && found.size() < 4) found.add(m.group().toUpperCase());
        }
        return List.copyOf(found);
    }

    /** 取模型输出的第一行有效文本，剥掉常见的引号/项目符号 */
    private String firstLine(String text) {
        if (text == null) return "";
        for (String line : text.split("\n")) {
            String s = line.trim().replaceAll("^[\"'“”「」\\-*\\d.、\\s]+", "").replaceAll("[\"'“”「」]+$", "");
            if (!s.isBlank()) return s;
        }
        return "";
    }

    private String userPayload(String question, ToolResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户问题】").append(question).append("\n\n");
        sb.append("【事实清单】（来源：").append(result.label()).append("）\n");
        if (result.facts().isEmpty()) {
            sb.append("（无）\n");
        } else {
            int max = props.getLlm().getMaxFactLines();
            int n = Math.min(result.facts().size(), max);
            for (int i = 0; i < n; i++) sb.append(result.facts().get(i)).append('\n');
            if (result.facts().size() > n) sb.append("……另有 ").append(result.facts().size() - n).append(" 行未列出\n");
        }
        sb.append("\n【系统初步归纳】").append(result.note());
        sb.append("\n请按规则作答。");
        return sb.toString();
    }

    /** 无密钥/模型失败时的保底：把事实照抄出来，措辞生硬但一字不改 */
    private String localAnswer(ToolResult result) {
        StringBuilder sb = new StringBuilder(result.note());
        if (!result.facts().isEmpty()) {
            sb.append("\n\n依据：\n");
            int n = Math.min(result.facts().size(), 20);
            for (int i = 0; i < n; i++) sb.append("· ").append(result.facts().get(i)).append('\n');
            if (result.facts().size() > n) {
                sb.append("· ……另有 ").append(result.facts().size() - n).append(" 行，可在下方「依据」里展开\n");
            }
        }
        sb.append("\n（当前未配置模型密钥，以上为直接取数结果，未经措辞加工）");
        return sb.toString();
    }

    private Long saveMessage(Long threadId, String role, String content, String tool,
                             String digest, String source, int costMs) {
        AgentMessage msg = new AgentMessage();
        msg.setThreadId(threadId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTool(tool);
        msg.setFactDigest(digest);
        msg.setSource(source);
        msg.setCostMs(costMs);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg.getId();
    }
}
