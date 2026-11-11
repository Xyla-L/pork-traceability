package com.pork.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 工具登记处：先按优先级问一遍「这个问题归不归你管」，都不认领时再让模型在目录里挑。
 * <p>
 * 两级路由的取舍：关键词路由确定、零成本、可测，覆盖日常问法；模型分诊只兜「换了说法」的
 * 长尾（这时本来也接不住，挑错不会比不答更糟）。加新能力 = 新写一个 {@link AgentTool}，这里不用动。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final List<AgentTool> tools;

    public ToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream()
                .sorted(Comparator.comparingInt(AgentTool::priority))
                .toList();
        log.info("agent_tools_registered count={} names={}", this.tools.size(),
                this.tools.stream().map(AgentTool::name).toList());
    }

    public Optional<AgentTool> pick(String question, String role) {
        if (question == null || question.isBlank()) return Optional.empty();
        return visible(role).stream().filter(t -> t.supports(question)).findFirst();
    }

    /**
     * 当前角色能用的工具。
     * <p>
     * 角色收窄放在这一层，路由/能力清单/模型分诊目录/自主查询四处自动一致——
     * 而不是各写一份「养殖户别看举报」，那种写法迟早漏一处。
     */
    private List<AgentTool> visible(String role) {
        return tools.stream().filter(t -> t.allows(role)).toList();
    }

    /**
     * 最后一道兜底：模型不可用（未配置 / 额度不足 / 网络故障）时，用一张更宽的词表再挑一次。
     * <p>
     * 为什么要这一层：模型分诊一旦失效，原本靠它兜住的换说法问题会整片变成「接不住」——
     * 用户看到的是能力消失，而实际只是模型欠费。宁可路由到相近能力、由工具自己说明数据不足，
     * 也不要把能答的问题变成拒答。<b>这张表只放宽路由，不改变任何结论</b>；
     * 刻意不放「今天」「异常」「数据」这类过泛词，否则「今天天气怎么样」也会被硬塞给某个工具。
     */
    private static final java.util.Map<String, List<String>> LOOSE_WORDS = java.util.Map.of(
            "ingest-diagnose", List.of("接入", "待人工", "队列", "报文", "设备", "上报失败", "同步失败"),
            "daily-brief", List.of("优先处理", "建议", "待办", "日报", "简报", "最要紧", "该做什么"),
            "temperature", List.of("温度", "超标", "越界", "断档", "探头", "温控", "几度"),
            "complaint", List.of("举报", "投诉", "结案", "申诉"),
            "transport-analysis", List.of("运单", "运输", "冷链", "拒收", "签收", "在途", "车", "到达", "配送"),
            "apply-analysis", List.of("申报", "审批", "驳回", "出栏", "待审", "积压"),
            "retail-risk", List.of("临期", "过期", "召回", "下架", "效期", "保质期"),
            "dashboard", List.of("工作台", "概况", "整体", "总览", "大盘"),
            "quarantine", List.of("检疫", "免疫", "疫苗", "接种", "出证"),
            "trace", List.of("追溯", "耳标", "批次", "流向", "溯源", "扫码", "到哪一步", "屠宰"));

    /** 模型不可用时的宽词表兜底；仍按 priority 顺序，先命中先得 */
    public Optional<AgentTool> pickLoose(String question, String role) {
        if (question == null || question.isBlank()) return Optional.empty();
        // 综合简报垫底：它的词表里全是「建议/待办」这类诉求词，一旦排前面会把专项问题整片抢走，
        // 于是每个问题都顶着一模一样的全平台建议。专项工具扫完没接住，才轮到它。
        Optional<AgentTool> specific = scanLoose(question, role, false);
        return specific.isPresent() ? specific : scanLoose(question, role, true);
    }

    private static final String BRIEF_TOOL = "daily-brief";

    private Optional<AgentTool> scanLoose(String question, String role, boolean brief) {
        for (AgentTool t : visible(role)) {
            if (BRIEF_TOOL.equals(t.name()) != brief) continue;
            List<String> words = LOOSE_WORDS.get(t.name());
            if (words == null) continue;
            for (String w : words) {
                if (question.contains(w)) return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** 模型自主查询按名字调工具：这里也要过角色，否则模型可以点名要一个本角色没开的能力 */
    public Optional<AgentTool> byName(String name, String role) {
        if (name == null || name.isBlank()) return Optional.empty();
        String key = name.trim().toLowerCase();
        return visible(role).stream().filter(t -> t.name().equalsIgnoreCase(key)).findFirst();
    }

    /** 给模型分诊用的能力目录：名字 + 一句话职责（只列本角色能用的） */
    public String catalog(String role) {
        StringBuilder sb = new StringBuilder();
        for (AgentTool t : visible(role)) {
            sb.append("- ").append(t.name()).append("：").append(t.description()).append('\n');
        }
        return sb.toString();
    }

    /** 给「我不认识这个问题」时用：把本角色能答的范围如实列出来，不装作什么都会 */
    public List<String> capabilities(String role) {
        return visible(role).stream().map(t -> t.label() + "（" + t.name() + "）").toList();
    }

    /** 空态用：能力名 + 这个角色能用它做什么，前端直接渲染，不再写死一份文案 */
    public List<java.util.Map<String, String>> capabilityDetails(String role) {
        return visible(role).stream().map(t -> {
            java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("name", t.name());
            m.put("label", t.label());
            m.put("hint", t.hint());
            return m;
        }).toList();
    }

    /**
     * 工具目录的 JSON 描述（OpenAI tools 格式），供模型自主调用。
     * <p>
     * 所有工具统一只声明一个可选参数 {@code keyword}：模型需要指哪查哪时就给编号，
     * 分析类工具本来就不需要参数。参数<b>只放宽不改写</b>——工具自己会校验，
     * 给错了它回「没听出要查哪个编号」，不会拿错编号去猜一个结果。
     */
    public String toolsJson(com.fasterxml.jackson.databind.ObjectMapper mapper, String role) {
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (AgentTool t : visible(role)) {
            java.util.Map<String, Object> keyword = new java.util.LinkedHashMap<>();
            keyword.put("type", "string");
            keyword.put("description", "要查的业务编号：耳标 ET-…、胴体批次 CB…、运单 TR-…。不需要编号的分析类查询可不填。");

            java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("type", "object");
            params.put("properties", java.util.Map.of("keyword", keyword));
            params.put("required", List.of());

            java.util.Map<String, Object> fn = new java.util.LinkedHashMap<>();
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.put("parameters", params);

            list.add(java.util.Map.of("type", "function", "function", fn));
        }
        try {
            return mapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("agent_tools_json_failed err={}", e.toString());
            return "[]";
        }
    }
}
