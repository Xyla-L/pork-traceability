package com.pork.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * 一回合模型输出：可能是最终答复，也可能是「再帮我查一次」，还可能两者都有。
 *
 * @param content          文本答复（请求工具时通常为空）
 * @param toolCalls        请求调用的工具，按模型给的顺序执行
 * @param assistantMessage 要原样回传给模型的 assistant 消息（含 tool_calls）。
 *                         不自己拼一份——漏字段会被服务端判为非法消息序列。
 */
public record LlmTurn(String content, List<ToolCall> toolCalls, Map<String, Object> assistantMessage) {

    public boolean wantsTools() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
