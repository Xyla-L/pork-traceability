-- ============================================================================
-- 门店拒收：冷链异常货物的正式出口（2026-10-25）
--
-- 背景：冷链运单此前只有「到达 → 签收」一条正向路，货有异常时门店无处可去 ——
-- 单悬着车被锁，删运单又毁了追溯链。拒收是真实商业事实，给它正式的状态通道：
--   一、拒收是站点终态（STOP_REJECTED=3）：只有门店人工发起，理由必填、实名、上链；
--       运单状态推导把拒收视同「已终结」—— 单店拒收不卡别的店签收，车也照常放行。
--   二、处置是另一次登记：退回供应商 / 就地销毁 / 折价接收，人工登记事实、一经登记不可改。
--   三、协商成功可「拒收改签收」：复用正常签收录入凭证，站点标记「曾拒收后改签」。
--
-- 幂等：全部 ADD COLUMN 用 information_schema 守卫；status 列注释 MODIFY 可重复执行。
-- 执行：mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 < 本文件
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1) transport_stop 新增拒收与处置列（幂等，一次 ALTER 全加）
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'db_distribution'
                   AND TABLE_NAME = 'transport_stop'
                   AND COLUMN_NAME = 'reject_reason');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE db_distribution.transport_stop
     ADD COLUMN reject_reason VARCHAR(500) DEFAULT NULL
       COMMENT ''拒收理由（必填，随拒收事件上链留档）'' AFTER receipt_id,
     ADD COLUMN reject_by VARCHAR(64) DEFAULT NULL
       COMMENT ''拒收操作人（门店登录用户实名）'' AFTER reject_reason,
     ADD COLUMN reject_time DATETIME DEFAULT NULL
       COMMENT ''拒收时刻'' AFTER reject_by,
     ADD COLUMN reject_source VARCHAR(24) DEFAULT NULL
       COMMENT ''拒收来源 MANUAL=门店页面人工拒收（拒收不允许设备代劳）'' AFTER reject_time,
     ADD COLUMN reject_source_ref VARCHAR(64) DEFAULT NULL
       COMMENT ''拒收操作留痕（操作人 id）'' AFTER reject_source,
     ADD COLUMN signed_after_reject TINYINT DEFAULT NULL
       COMMENT ''拒收后协商改签收标记 1=本站曾拒收、协商后已改签收'' AFTER reject_source_ref,
     ADD COLUMN disposal_type VARCHAR(24) DEFAULT NULL
       COMMENT ''处置方式 RETURN_SUPPLIER=退回供应商 / DESTROY=就地销毁 / DISCOUNT=折价接收'' AFTER signed_after_reject,
     ADD COLUMN disposal_note VARCHAR(500) DEFAULT NULL
       COMMENT ''处置说明'' AFTER disposal_type,
     ADD COLUMN disposal_by VARCHAR(64) DEFAULT NULL
       COMMENT ''处置经办人（登记时的登录用户实名）'' AFTER disposal_note,
     ADD COLUMN disposal_time DATETIME DEFAULT NULL
       COMMENT ''处置登记时刻'' AFTER disposal_by',
  'SELECT ''reject_reason 已存在，跳过 ADD COLUMN'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2) status 列注释跟上新状态码（3=已拒收）
-- ---------------------------------------------------------------------------
ALTER TABLE db_distribution.transport_stop
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '行程状态 0 未到达 / 1 已到达 / 2 已签收 / 3 已拒收';


-- ---------------------------------------------------------------------------
-- 3) 核验
-- ---------------------------------------------------------------------------
SELECT 'transport_stop 列' AS item,
       GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) AS v
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'transport_stop'
UNION ALL
SELECT '列注释 status', COLUMN_COMMENT FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'transport_stop' AND COLUMN_NAME = 'status'
UNION ALL
SELECT '站点状态分布',
       CONCAT('未到达=', SUM(status = 0), ' 已到达=', SUM(status = 1),
              ' 已签收=', SUM(status = 2), ' 已拒收=', SUM(status = 3))
FROM db_distribution.transport_stop;
