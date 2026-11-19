package com.pork.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 用户点「确认执行」时提交的内容。
 *
 * <p>只带 conversationId 不带参数：参数留在服务端会话里。
 * 如果让前端把参数回传，前端就成了可信输入，那条「参数白名单」的路就绕过去了。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionConfirmRequest(String conversationId) {
}
