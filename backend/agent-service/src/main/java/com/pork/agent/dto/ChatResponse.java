package com.pork.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 回答。
 * <p>
 * {@code source} 必须如实告诉用户这条答案是「模型写的」还是「直接取数拼的」——
 * 前者措辞顺但可能理解偏，后者生硬但一字不改。把来源藏起来才是真正的不负责。
 */
@Data
@Builder
public class ChatResponse {
    private Long threadId;
    private String answer;
    /** llm / local / none */
    private String source;
    private String tool;
    private String toolLabel;
    /** 依据清单（页面折叠展示，可逐行核对） */
    private List<String> facts;
    private Integer costMs;
}
