-- ============================================================
-- 47 智能助手：会话与消息留痕
-- 说明：
--   1. agent 不建任何业务数据副本。业务数据一律现查现答——存一份就会过期，
--      过期的答案比没有答案更危险。这两张表只解决「问过什么、依据是什么」的可复盘性。
--   2. 不留 API Key、不留模型原始响应体：密钥只在环境变量里，报文可能含大段原文，收益低风险高。
-- ============================================================
USE db_common;

CREATE TABLE IF NOT EXISTS agent_thread (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL DEFAULT '',
    -- 归属人用用户名而非自增ID：换账号体系也不会串会话
    owner VARCHAR(64) NOT NULL,
    role VARCHAR(32),
    org_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_owner_time (owner, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助手会话';

CREATE TABLE IF NOT EXISTS agent_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL,
    -- 命中的只读取数工具；未命中为空。自主查询一次可能查多个，用逗号连接
    tool VARCHAR(128),
    -- 送给模型的事实摘要快照：答错了能复盘「它当时看到了什么」
    fact_digest MEDIUMTEXT,
    -- llm=模型整理 local=无密钥本地归纳 agent=模型自主查询 knowledge=业务说明（无实时数据） none=未命中
    source VARCHAR(16),
    cost_ms INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_thread (thread_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能助手消息留痕';
