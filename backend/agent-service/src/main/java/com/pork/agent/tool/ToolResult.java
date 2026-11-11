package com.pork.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 一次取数的结果。
 *
 * @param tool    工具名（留痕用）
 * @param label   中文来源名（页面标注「依据：全链路追溯」）
 * @param note    一句话结论，本地归纳直接用它打头
 * @param facts   送给模型的事实清单（已压平、限长），同时作为页面「依据」折叠内容
 * @param metrics  关键数字的登记表（键=口径名，值=数字文本），给综合简报的规则建议消费。
 *                 只有「算术在代码里算出的」数字才允许进来——简报的规则不解析 facts 文本，
 *                 解析文本等于造第二份口径，改措辞就会把规则悄悄弄坏。
 * @param appendix 代码生成的固定文本（如规则建议），由服务层<b>原样追加</b>在模型回答之后。
 *                 不放进 facts 让模型转述——免费小模型复述列表会漏条、并条甚至编数字，
 *                 规则结论必须一字不过模型的手。
 */
public record ToolResult(String tool, String label, String note, List<String> facts,
                         Map<String, String> metrics, List<String> appendix) {

    /** 绝大多数工具不参与规则建议，走这个构造：metrics/appendix 视为空 */
    public ToolResult(String tool, String label, String note, List<String> facts) {
        this(tool, label, note, facts, Map.of(), List.of());
    }

    public ToolResult(String tool, String label, String note, List<String> facts, Map<String, String> metrics) {
        this(tool, label, note, facts, metrics, List.of());
    }

    public static ToolResult empty(String tool, String label, String note) {
        return new ToolResult(tool, label, note, List.of());
    }

    /** 缺失的指标按「无此数」处理，调用方跳过对应规则，不猜 0 还是有值 */
    public String metric(String key) {
        return metrics.get(key);
    }
}
