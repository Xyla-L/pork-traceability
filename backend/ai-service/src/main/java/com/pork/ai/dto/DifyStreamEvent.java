package com.pork.ai.dto;

import lombok.Data;

/**
 * Dify SSE 流中的一个事件（只保留业务需要的字段）。
 *
 * Dify 会推送很多中间事件：workflow_started / node_started / node_finished /
 * message / message_end / workflow_finished / ping / error。
 * 对接层只关心两类：
 *   - message      每来一条就是一小段增量文本（answer 字段）
 *   - message_end  整轮结束，可以关闭连接
 */
@Data
public class DifyStreamEvent {

    /** 事件类型 */
    private String event;

    /** 增量文本（仅 message 事件有值） */
    private String answer;

    /** 会话 id，前端存下来可以续聊 */
    private String conversationId;

    /** 消息 id */
    private String messageId;

    /** 出错时的信息 */
    private String message;

    /** error 事件的错误码 */
    private String code;

    public boolean isMessage() {
        return "message".equals(event);
    }

    public boolean isEnd() {
        return "message_end".equals(event) || "error".equals(event);
    }
}
