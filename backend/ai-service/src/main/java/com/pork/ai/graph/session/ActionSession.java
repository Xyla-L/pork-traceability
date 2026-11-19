package com.pork.ai.graph.session;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次「自然语言操作」的多轮上下文。
 *
 * <p>只存三样东西：选中的接口、已收集的参数、待确认标记。
 * 不存对话历史 —— 历史留在 Dify 那边就够了，这里存一份只会两边不一致。</p>
 */
@Data
public class ActionSession {

    private final String conversationId;
    private final String userId;

    /** 当前锁定的接口 id；用户换了话题时要能改掉，所以不是 final */
    private String operationId;

    /** 已经确认过的参数 */
    private Map<String, Object> slots = new LinkedHashMap<>();

    /** 写操作等待用户点确认 */
    private boolean pendingConfirm;

    /** 轮次，用来在追问里说「你已经补充了 2 次」这类话，也方便排查 */
    private int turns;

    private volatile long updatedAt = System.currentTimeMillis();

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
        this.turns++;
    }
}
