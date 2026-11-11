-- =============================================================
-- 43 一辆车同一时间只跑一趟：清理同车牌下的多余未完成运单
--
-- 背景：运单是「这趟车装了哪些货」的载体。早期只有装车口在 120 分钟窗口内
--       才会把同车牌的货并进那张待发车运单，窗口之外再扫就直接新建一张 ——
--       于是同一台车裂出两张「未完成」运单（本次存量：京A·12345 名下
--       TR20261012019402 待签收 / TR20261012892184 运输中）。
--       两张单各自记站点与温度，发车（DEPART）按车牌倒查时还会撞上
--       「多张待发车 → 不敢猜是哪一趟」而转人工：一份现场作业变成两条待办。
--
-- 代码侧已经堵住新建（ActiveTransportGuard，三条建单路径都过它）；
-- 本脚本处理存量：每个车牌只留一张未完成的运单。
--
-- 留哪一张（确定性规则，不猜）：
--   status 大者优先 → create_time 新者优先 → id 大者优先。
--   即「待签收(3) > 运输中(2) > 待发车(1)」：留离完成最近的那张，
--   其余的是同一台车在旧作业没结束时就又开了一趟，属于应当消失的那张。
--
-- 不做的事：
--   * 已经生成门店签收单的运单不删 —— 那是溯源链下游凭证，
--     deleteTransport 对这类运单也是直接拒绝，这里同样交给人工；
--   * 已取消的计划不复活（status=3 不动）。
--
-- 连带清理与 deleteTransport 同一口径：
--   温度日志 → 计划明细退回「待排车」→ 按剩余量重算计划状态
--   → 装载明细 → 站点台账 → 运单本身。
--
-- 幂等：清理完每个车牌的未完成运单 ≤ 1，临时表为空，重复执行无副作用。
-- 跑法：docker exec -i pork-traceability-mysql-1 \
--         mysql -uroot -p*** --default-character-set=utf8mb4 < backend/init-sql/43-single-active-transport.sql
-- =============================================================
SET NAMES utf8mb4;
-- 临时表（tmp_dup_transport / tmp_affected_plan）不带库名，必须先选库
USE db_distribution;

-- 1) 找出要清掉的运单（同车牌未完成运单里，除第一名之外的）
DROP TEMPORARY TABLE IF EXISTS tmp_dup_transport;
CREATE TEMPORARY TABLE tmp_dup_transport (id BIGINT PRIMARY KEY);

INSERT INTO tmp_dup_transport (id)
SELECT id FROM (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY vehicle_no
                            ORDER BY status DESC, create_time DESC, id DESC) AS rn
    FROM db_distribution.cold_chain_transport
   WHERE status IN (1, 2, 3)
     AND vehicle_no IS NOT NULL
     AND vehicle_no <> ''
) ranked
WHERE rn > 1;

-- 2) 已生成门店签收单的运单不动（下游凭证，删除会让签收单失去归属）
DELETE d FROM tmp_dup_transport d
  JOIN db_distribution.store_receipt r ON r.transport_id = d.id;

-- 3) 记下受影响的计划（明细退回后要按剩余量重算状态）
DROP TEMPORARY TABLE IF EXISTS tmp_affected_plan;
CREATE TEMPORARY TABLE tmp_affected_plan (id BIGINT PRIMARY KEY);

INSERT INTO tmp_affected_plan (id)
SELECT DISTINCT i.plan_id
  FROM db_distribution.delivery_plan_item i
  JOIN tmp_dup_transport d ON i.transport_id = d.id;

-- 4) 温度日志随运单一起消失（这张运单的这趟行程不存在了）
DELETE l FROM db_distribution.temperature_log l
  JOIN tmp_dup_transport d ON l.transport_id = d.id;

-- 5) 计划明细退回「待排车」：必须显式写 NULL，
--    MyBatis-Plus 那套「忽略 null」在这里不适用，但 SQL 直写要清干净，
--    否则计划会一直显示一张已经删掉的运单号。
UPDATE db_distribution.delivery_plan_item i
  JOIN tmp_dup_transport d ON i.transport_id = d.id
   SET i.shipped = 0,
       i.transport_id = NULL,
       i.shipped_time = NULL;

-- 6) 重算计划状态：全未发→1 待发货；全发走→2 已排车；发走一部分→4 部分排车
UPDATE db_distribution.delivery_plan p
   SET p.status = CASE
         WHEN NOT EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i
                           WHERE i.plan_id = p.id AND i.shipped = 1) THEN 1
         WHEN NOT EXISTS (SELECT 1 FROM db_distribution.delivery_plan_item i
                           WHERE i.plan_id = p.id AND i.shipped = 0) THEN 2
         ELSE 4
       END,
       p.transport_count = (SELECT COUNT(DISTINCT i.transport_id)
                              FROM db_distribution.delivery_plan_item i
                             WHERE i.plan_id = p.id AND i.transport_id IS NOT NULL)
 WHERE p.id IN (SELECT id FROM tmp_affected_plan)
   AND p.status <> 3;

-- 7) 一张运单都不剩的计划：把「最近一次运单」的展示字段一起清空
UPDATE db_distribution.delivery_plan p
   SET p.transport_id = NULL,
       p.transport_no = NULL,
       p.generated_time = NULL
 WHERE p.id IN (SELECT id FROM tmp_affected_plan)
   AND p.transport_count = 0
   AND p.status <> 3;

-- 8) 装载明细与站点台账是运单的从属数据，运单没了它们就是孤儿行
DELETE ti FROM db_distribution.transport_item ti
  JOIN tmp_dup_transport d ON ti.transport_id = d.id;

DELETE ts FROM db_distribution.transport_stop ts
  JOIN tmp_dup_transport d ON ts.transport_id = d.id;

-- 9) 运单本身
DELETE t FROM db_distribution.cold_chain_transport t
  JOIN tmp_dup_transport d ON t.id = d.id;

-- 10) 核对：同车牌未完成运单必须 ≤ 1（无输出即通过）
SELECT '仍有同车多张未完成运单' AS warn, vehicle_no, COUNT(*) AS cnt
  FROM db_distribution.cold_chain_transport
 WHERE status IN (1, 2, 3) AND vehicle_no IS NOT NULL AND vehicle_no <> ''
 GROUP BY vehicle_no
HAVING cnt > 1;

-- 11) 核对：运单子表不留孤儿行（无输出即通过）
SELECT '孤儿装载明细' AS warn, ti.id
  FROM db_distribution.transport_item ti
  LEFT JOIN db_distribution.cold_chain_transport t ON t.id = ti.transport_id
 WHERE t.id IS NULL;

SELECT '孤儿站点' AS warn, ts.id
  FROM db_distribution.transport_stop ts
  LEFT JOIN db_distribution.cold_chain_transport t ON t.id = ts.transport_id
 WHERE t.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_dup_transport;
DROP TEMPORARY TABLE IF EXISTS tmp_affected_plan;
