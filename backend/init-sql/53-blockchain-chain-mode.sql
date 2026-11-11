-- 53 存证载体标注（2026-11-08）
-- 背景：区块链服务默认 local（数据库账本），历史 908 条记录没有真实交易哈希。
--       切到 evm 后必须能区分「迁移前的本地存证」与「真实上链」，否则页面会把
--       本地哈希当成链上交易展示（虚假陈述）。迁移完成后 chain_mode 全部转 evm。
ALTER TABLE db_common.blockchain_record
  ADD COLUMN chain_mode VARCHAR(8) NOT NULL DEFAULT 'local'
    COMMENT '存证载体：local=平台本地账本（未上链） evm=区块链' AFTER status,
  ADD COLUMN migrated_at DATETIME NULL
    COMMENT '历史本地存证补写链上的时间（实时写入的记录为 NULL）' AFTER chain_mode;

-- 存量一律按本地存证标注：这批记录的 tx_hash 是内容指纹，链上查不到
UPDATE db_common.blockchain_record SET chain_mode = 'local' WHERE chain_mode IS NULL;

CREATE INDEX idx_chain_mode ON db_common.blockchain_record (chain_mode, status);
