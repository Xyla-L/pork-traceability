package com.pork.agent.dto;

import lombok.Data;

/** 提问请求；不传 threadId 时新建一个会话 */
@Data
public class ChatRequest {
    private Long threadId;
    private String message;
}
