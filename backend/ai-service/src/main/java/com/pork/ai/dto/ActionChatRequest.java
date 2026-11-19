package com.pork.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 自然语言操作请求。
 *
 * @param text           用户这句话
 * @param conversationId 上一轮返回的会话 id；为空表示新会话
 * @param reset          true 表示丢掉上下文重新开始（用户说「算了，换个事」时前端发这个）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionChatRequest(String text, String conversationId, Boolean reset) {
}
