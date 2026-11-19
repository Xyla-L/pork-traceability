package com.pork.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 自然语言操作的回复。
 *
 * <p>用结构化 JSON 而不是 SSE 文本流，理由：这个场景的输出不是「一段话」，
 * 而是「我理解成了哪个功能 + 还缺什么 + 执行结果是什么」。
 * 前端要拿 {@code missing} 渲染输入框、拿 {@code execution.data} 渲染表格卡片，
 * 这些都必须是字段，不能藏在字符串里让前端去正则抠。</p>
 *
 * @param phase        UNSUPPORTED 没找到 / NEED_SLOT 缺参数 / READY 待确认 /
 *                     EXECUTED 已执行 / FAILED 执行失败
 * @param operation    这次选中的接口（UNSUPPORTED 时为 null）
 * @param slots        目前已收集的参数
 * @param missing      还缺的必填参数
 * @param question     需要向用户说的话（追问或提示）
 * @param answer       给用户看的最终答复（模板生成，不含模型幻觉风险）
 * @param execution    执行结果
 * @param candidates   图谱检索出的候选，供排查「为什么选了这个」
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionChatResponse(
        String phase,
        String conversationId,
        OperationBrief operation,
        Map<String, Object> slots,
        List<MissingSlot> missing,
        String question,
        String answer,
        ExecutionBrief execution,
        List<Candidate> candidates
) {

    public record OperationBrief(String id, String summary, String method,
                                 String path, String gatewayPath,
                                 String module, String service) {
    }

    /**
     * @param name 参数名
     * @param type 类型
     * @param hint 给用户的提示，例如「日期，格式 YYYY-MM-DD」
     */
    public record MissingSlot(String name, String type, String hint) {
    }

    /**
     * @param data    下游返回的原始数据（可能是 JSON 字符串，前端按需解析）
     * @param summary 一句话结论
     */
    public record ExecutionBrief(boolean success, int status, long elapsedMs,
                                 String data, String error) {
    }

    public record Candidate(String id, String summary, String method, double score) {
    }
}
