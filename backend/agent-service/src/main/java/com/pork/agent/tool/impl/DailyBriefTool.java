package com.pork.agent.tool.impl;

import com.pork.agent.support.AgentContext;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 综合简报与建议：把工作台、接入诊断、申报分析、运单分析四份口径<b>复用</b>到一起，
 * 再按固定规则生成「先做什么」的建议。
 * <p>
 * 两条设计红线：
 * <ul>
 *   <li><b>不造第二份统计</b>：直接调用四个工具的 {@code execute()} 复用它们的事实与数字
 *       （数字经 {@link ToolResult#metric} 登记，规则绝不解析 facts 文本）；</li>
 *   <li><b>建议是规则算的，不是模型编的</b>：每条建议的依据都是已查到的数字，
 *       并告诉用户去哪个页面做什么——建议只到「页面落点」为止，agent 不产生任何写操作。
 *       模型只负责把算好的建议转述通顺，已在事实清单里明确告知不得自行加建议。</li>
 * </ul>
 * 某一方面取数失败不拖垮简报：如实标注该部分今天看不了，其余照常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyBriefTool implements AgentTool {

    /** 措辞避开专项工具的领地词（运单/申报/接入/耳标打头的问题归它们），只接「要建议」类问法 */
    private static final List<String> HINTS = List.of(
            "建议", "简报", "该怎么办", "怎么处理", "优先", "重点关注", "综合分析", "需要注意", "要做什么");

    /**
     * 主题守卫：这些词说明用户在问<b>某一个具体环节</b>，即使夹了「怎么办/建议」也不该由本工具接管。
     * <p>
     * 反例（不加守卫时的真实故障）：问「这批运单温度超标怎么处理」，因为含「怎么处理」被简报抢答，
     * 答非所问，还必然附一整块与问题无关的全平台建议；用户看到的就是「每次都甩同一段建议」。
     * 守卫只回答「谁来答」，不改任何结论——让位后由专项工具照原口径作答。
     */
    private static final List<String> TOPIC_GUARD = List.of(
            "运单", "运输", "冷链", "签收", "在途", "发车", "配送", "车号", "车牌", "司机", "温度", "拒收",
            "申报", "审批", "驳回", "出栏", "通过率", "排队", "积压",
            "接入", "队列", "待人工", "报文", "设备", "上报", "入账",
            "临期", "过期", "召回", "效期", "疫苗", "接种", "免疫",
            "耳标", "批次", "追溯", "溯源", "胴体", "白条", "屠宰", "检疫", "举报");

    /**
     * 建议条数上限：一屏能扫完、且每条都指向一个真实待办。
     * <p>
     * 超出的不静默丢弃：同类项先在 {@link #rules} 里合并，仍超出时由 {@link #execute}
     * 在归纳里如实说明还有几项。<b>「少列」可以是取舍，「谎称只有这些」不行。</b>
     */
    private static final int MAX_SUGGESTIONS = 6;

    private final DashboardTool dashboardTool;
    private final IngestDiagnoseTool diagnoseTool;
    private final ApplyAnalysisTool applyTool;
    private final TransportAnalysisTool transportTool;

    @Override
    public String name() {
        return "daily-brief";
    }

    @Override
    public String label() {
        return "综合简报与建议";
    }

    @Override
    public String description() {
        return "汇总工作台、接入队列、申报、运单四方面数据并按规则给出优先处理建议；问「今天该先做什么」「有什么建议」时用";
    }

    @Override
    public int priority() {
        return 12;
    }

    @Override
    public String hint() {
        return "汇总各方面数据，按固定规则告诉你先处理什么、去哪个页面";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        // 具体的某一环节归专项工具：有主题词就不认领，避免「问运单却答全平台待办」
        if (TOPIC_GUARD.stream().anyMatch(question::contains)) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        return Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        List<String> facts = new ArrayList<>();
        Map<String, String> all = new LinkedHashMap<>();
        // 这里刻意不写「建议由系统附在末尾」之类的说明：那是给模型的元指令，
        // 写进 facts 会被它当内容念出来（实测答过「系统会在末尾附加【系统建议】」）。
        // 不要自己列建议这条约束放在系统提示词里，facts 只放数据。

        // 1) 复用四个工具的取数结果；单方面失败不拖垮简报。
        //    角色看不见的段落直接不取——简报内部调用也必须过角色，否则等于绕过工具白名单。
        merge("工作台指标", dashboardTool, ctx, facts, all);
        merge("接入待人工队列", diagnoseTool, ctx, facts, all);
        merge("出栏申报", applyTool, ctx, facts, all);
        merge("冷链运单", transportTool, ctx, facts, all);

        // 2) 规则建议：每条都有数字依据和页面落点，缺数的规则自动跳过，不猜。
        //    建议走 appendix（服务层原样追加在模型回答之后），不进 facts——
        //    免费小模型复述列表会漏条、并条甚至编数字，规则结论不过模型的手。
        List<String> triggered = rules(all, ctx == null ? null : ctx.role());
        // 条数上限：一次列 7 条以上等于没有优先级，管理员不会逐条做。
        // 同类项已在 rules() 里合并；仍超出时只截断、<b>不静默</b>——归纳里明说还有几项，想看可单独问。
        List<String> suggestions = triggered;
        String overflow = "";
        if (triggered.size() > MAX_SUGGESTIONS) {
            suggestions = new ArrayList<>(triggered.subList(0, MAX_SUGGESTIONS));
            overflow = "另有 " + (triggered.size() - MAX_SUGGESTIONS) + " 项次要提醒未列出，需要时可单独问。";
        }

        String note = "已汇总本角色可见的各方面数据，"
                + (suggestions.isEmpty() ? "当前没有触发任何待办规则。"
                        : "规则建议见文末【系统建议】共 " + suggestions.size() + " 条。")
                + overflow;
        return new ToolResult(name(), label(), note, facts, all, appendix(suggestions));
    }

    /** 建议块格式：标题 + 逐条编号，由服务层原样拼在模型回答后面 */
    private List<String> appendix(List<String> suggestions) {
        if (suggestions.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        out.add("【系统建议 · 按固定规则生成】");
        for (int i = 0; i < suggestions.size(); i++) {
            out.add((i + 1) + ". " + suggestions.get(i));
        }
        return out;
    }

    private void merge(String title, AgentTool tool, AgentContext ctx, List<String> facts, Map<String, String> all) {
        // 这个角色用不了这个工具，就不取这一段：否则养殖户的简报里会混进举报台账和全平台接入队列
        if (ctx != null && !tool.allows(ctx.role())) return;
        facts.add("—— " + title + " ——");
        try {
            ToolResult r = tool.execute(Map.of(), ctx);
            facts.add(r.note());
            facts.addAll(r.facts());
            all.putAll(r.metrics());
        } catch (Exception e) {
            log.warn("agent_brief_section_failed tool={} err={}", tool.name(), e.toString());
            facts.add("「" + tool.label() + "」取数失败，这部分今天看不了，建议稍后单独再问。");
        }
    }

    /**
     * 规则建议：每条 = 数字依据 + 具体动作 + 页面入口。
     * 阈值就是「出现即待办」——这个系统里不存在需要调优的概率型阈值，出现即异常。
     * <p>
     * 同一份数字对不同角色意味着不同的事：养殖户不需要被告知「处理举报」，监管不需要被告知
     * 「驳回后重新过磅」。所以每条规则都带角色白名单，而不是给所有人念同一张清单。
     * <p>
     * 空角色（内部调用/拿不到身份）按最宽处理：宁可多给一条，也不要让简报变成空壳。
     */
    private List<String> rules(Map<String, String> m, String role) {
        List<String> out = new ArrayList<>();
        // 优先用工作台指标卡的数字：它已经按角色和机构圈过（养殖户看到的就是本场）。
        // 专项工具的同名口径是全平台量，只在工作台没有这张卡时才用（如管理员视图没有待审批卡）。
        String pending = first(m, "pendingApply", "pendingApplies");
        if (pending != null && num(pending) > 0 && forRole(role, "ADMIN", "SUPERVISOR", "FARMER", "SLAUGHTER_OP")) {
            String wait = m.get("maxPendingWait");
            out.add("先看出栏审批：有 " + pending + " 条待审批"
                    + (wait != null ? "，最早一条已等 " + wait : "")
                    + "。入口：养殖管理-出栏审批，逐条通过或驳回。");
        }
        String queue = m.get("queueTotal");
        if (queue != null && num(queue) > 0 && forRole(role, "ADMIN", "SUPERVISOR", "SLAUGHTER_OP", "DISTRIBUTOR")) {
            String top = m.get("topReason");
            String topCount = m.get("topReasonCount");
            out.add("再清接入队列：还有 " + queue + " 条待人工处理"
                    + (top != null && topCount != null
                            ? "，先处理最多的一类「" + briefReason(top) + "」共 " + topCount + " 条，收益最大" : "")
                    + "。入口：设备接入监控-待确认队列。");
        }
        String complaint = m.get("complaintCount");
        if (complaint != null && num(complaint) > 0 && forRole(role, "ADMIN", "SUPERVISOR")) {
            out.add("处理举报：有 " + complaint + " 条待处理举报，结案需给出结论并留痕。入口：追溯监管-举报管理。");
        }
        String expire = first(m, "expireCount", "expiringCount");
        if (expire != null && num(expire) > 0 && forRole(role, "ADMIN", "SUPERVISOR", "RETAILER")) {
            out.add("盯临期产品：有 " + expire + " 件临期，过期未处置会被监管视角计入合规问题。入口：销售管理-临期预警。");
        }
        String rejected = m.get("rejectedApplies");
        // 运单侧两件事（拒收站点 / 在途未签收）指向同一个页面、同一般人，合成一条：
        // 条数落回可读范围，数字一项不少——只是不再刷成两条。
        String stops = m.get("rejectedStops");
        String inTransit = m.get("inTransit");
        boolean hasStops = stops != null && num(stops) > 0;
        boolean hasTransit = inTransit != null && num(inTransit) > 0;
        if ((hasStops || hasTransit) && forRole(role, "ADMIN", "SUPERVISOR", "DISTRIBUTOR")) {
            List<String> parts = new ArrayList<>(2);
            if (hasStops) parts.add(stops + " 个拒收站点待核对温度记录与签收状态");
            if (hasTransit) parts.add(inTransit + " 张运单在途未签收，到站后由门店确认收货，别让单子挂在待签收");
            out.add("看运单：" + String.join("；", parts) + "。入口：冷链配送-运输监控。");
        }
        // 驳回是提示规则、不是待办动作，排在最后：它不产生任何点击，不该占前面的位置
        if (rejected != null && num(rejected) > 0 && forRole(role, "ADMIN", "SUPERVISOR", "FARMER", "SLAUGHTER_OP")) {
            out.add("驳回的 " + rejected + " 条申报不用改原单：驳回是终态，整改后重新过磅申报即可。");
        }
        return out;
    }

    /** 这条建议该不该给这个角色看 */
    private boolean forRole(String role, String... allowed) {
        if (role == null || role.isBlank()) return true;
        return List.of(allowed).contains(role.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 接入层的原因文案往往带一长串给运维看的解释（「车牌 京A·* 名下没有在途的运单（车可能还没刷卡发车…）」），
     * 直接嵌进建议会把一条待办撑成一整段，反而看不出「先做什么」。
     * <p>
     * 只取括号前的主句，超长再硬截：<b>丢掉的只是解释，不是数字</b>，完整原因在待确认队列里原样可查。
     */
    private String briefReason(String reason) {
        if (reason == null || reason.isBlank()) return "未标注原因";
        int cut = reason.indexOf('（');
        String s = cut > 0 ? reason.substring(0, cut) : reason;
        return s.length() > 24 ? s.substring(0, 24) + "…" : s;
    }

    /**
     * 多个候选口径名取第一个有值的。
     * <p>
     * 工作台各视图的指标卡 key 不完全统一（养殖户是 pendingApply、管理员侧专项工具是 pendingApplies），
     * 只认一个名字就会出现「换个角色建议全空」——少一个 s 的坑，不该由用户来踩。
     */
    private String first(Map<String, String> m, String... keys) {
        for (String k : keys) {
            String v = m.get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private long num(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
