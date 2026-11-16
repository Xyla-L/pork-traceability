package com.pork.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 调用 Dify /v1/chat-messages 的入参。
 */
@Data
@Builder
public class DifyChatCommand {

    /** 用户输入。溯源解读场景下，这里由后端拼好「数据 + 任务说明」 */
    private String query;

    /** 终端用户标识，Dify 用它做会话隔离和多轮上下文归属 */
    private String user;

    /** 多轮对话 id。为空表示开新会话；传上次返回的值可续聊 */
    private String conversationId;

    /** Dify 应用的自定义输入变量。当前 Chatflow 没定义变量，留空即可 */
    @Builder.Default
    private Map<String, Object> inputs = new HashMap<>();
}
