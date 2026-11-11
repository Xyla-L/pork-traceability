package com.pork.agent.llm;

/**
 * 模型请求的一次工具调用。
 * <p>
 * {@code arguments} 保留原始 JSON 串，由工具自己解析——本层不做语义加工，
 * 免得「模型给错参数」被中间层悄悄修正成另一件事。
 */
public record ToolCall(String id, String name, String arguments) {
}
