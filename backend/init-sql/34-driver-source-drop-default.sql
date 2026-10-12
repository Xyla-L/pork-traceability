-- ============================================================================
-- 34. 司机来源列去掉「默认值 MANUAL」
--
-- 背景（2026-10-10，真实踩坑）：
--   现场只在上车那一刻刷一次卡，装车口不采集司机 —— 司机留空是**正常中间态**。
--   但 cold_chain_transport.driver_source 建列时给了 DEFAULT 'MANUAL'（见 29 号脚本），
--   而 MyBatis-Plus 的 insert 默认跳过 null 字段（FieldStrategy.NOT_NULL）：
--   应用明明把 driver_source 置为 null 表示「没有司机」，数据库却按默认值填成 'MANUAL'，
--   于是落出「driver_name 为空、driver_source=MANUAL」的自相矛盾记录 ——
--   追责时比「两个都空」更糟：它假装调度曾经录过一个名字。
--
-- 口径（与后端 support/DriverSource 一致）：
--   **没有司机就不该有来源**。所以这一列的默认值必须是 NULL，而不是替应用做决定。
--   （应用侧同时把该字段标为 insertStrategy=ALWAYS，双重保障。）
--
-- 本脚本：① 去掉列默认值；② 清掉存量里「有来源、没名字」的矛盾行；③ 核对。
-- ============================================================================

ALTER TABLE db_distribution.cold_chain_transport
    ALTER COLUMN driver_source DROP DEFAULT;

-- 存量矛盾行：名字为空却写着来源 —— 一律清成 NULL（这是修数据，不是改历史事实：
-- 原来那一行本来就没有任何证据说明司机是谁）
UPDATE db_distribution.cold_chain_transport
SET driver_source = NULL, driver_confirmed = 0
WHERE (driver_name IS NULL OR driver_name = '')
  AND driver_source IS NOT NULL
  AND create_time < NOW();

-- 核对 ①：默认值必须不再是 MANUAL
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution'
  AND TABLE_NAME = 'cold_chain_transport'
  AND COLUMN_NAME = 'driver_source';

-- 核对 ②：不得再有「空名字配来源」的矛盾行
SELECT COUNT(*) AS empty_name_with_source
FROM db_distribution.cold_chain_transport
WHERE (driver_name IS NULL OR driver_name = '') AND driver_source IS NOT NULL;

-- 核对 ③：不得再有「有名字却没来源」的行（历史回填的除外，应为 0）
SELECT COUNT(*) AS name_without_source
FROM db_distribution.cold_chain_transport
WHERE driver_name IS NOT NULL AND driver_name <> ''
  AND (driver_source IS NULL OR driver_source = '');
