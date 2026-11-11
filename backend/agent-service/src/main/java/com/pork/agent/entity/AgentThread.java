package com.pork.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（db_common.agent_thread）。
 * <p>
 * 只记「谁在什么时候问了什么」，不含任何业务数据副本——业务数据永远现查现答，
 * 存一份就会过期，过期的答案比没有答案更危险。
 */
@Data
@TableName("agent_thread")
public class AgentThread {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    /** 归属人（令牌里的用户名），用于「我的会话」隔离 */
    private String owner;
    private String role;
    private Long orgId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
