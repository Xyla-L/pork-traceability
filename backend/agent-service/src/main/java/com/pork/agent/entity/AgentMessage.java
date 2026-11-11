package com.pork.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息（db_common.agent_message）。
 * <p>
 * 三个字段说清一次回答的可信度：{@code tool} 用了哪个取数工具、{@code factDigest} 模型看到的事实原文、
 * {@code source} 是模型生成还是本地归纳。出了问题能复盘「它当时到底看到了什么」，
 * 而不是只能看到一句说错的话。
 */
@Data
@TableName("agent_message")
public class AgentMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long threadId;
    /** user / assistant */
    private String role;
    private String content;
    /** 命中的工具名，未命中为空 */
    private String tool;
    /** 送给模型的事实摘要（只读快照，便于复盘） */
    private String factDigest;
    /** llm=模型生成，local=无密钥时本地归纳，none=未命中工具 */
    private String source;
    private Integer costMs;
    private LocalDateTime createTime;
}
