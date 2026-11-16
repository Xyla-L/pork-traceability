-- 55 存证报文快照（2026-11-15）
-- 背景：blockchain_record 只存 contentHash，不存上链时的原始报文。
--       结果 retry / retryPending / migrateLegacy 三处重放只能传空 Map，
--       链上留下「有哈希、无内容」的存证（962 条迁移记录 + 所有重试成功记录），
--       既没法给第三方看原文，也没法在验真时重算业务数据。
-- 处理：上链前把报文快照落库，重试/迁移原样重放。
--       存量记录没有快照可补（当初就没留），只能凭哈希补写，如实计入 payloadMissing。
ALTER TABLE db_common.blockchain_record
  ADD COLUMN payload_json MEDIUMTEXT NULL
    COMMENT '上链报文快照（JSON）：重试/迁移时原样重放；历史记录为空表示当初未留存' AFTER content_hash;
