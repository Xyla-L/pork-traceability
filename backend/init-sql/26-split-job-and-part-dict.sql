-- ============================================================================
-- 分割操作：把「一条上报 = 一条部位记录」改成「一次上报 = 一整头猪的产出清单」
--
-- 背景（2026-10-09 设计评审）：分割在行业里是**固定部位**切割（二分体 → 六分体 →
-- 一号到五号肉等标准部位），出肉率也是刚性的（白条约 75~80kg，红肉占 65~70%，
-- 其余是骨、脂、皮和副产）。但原实现的 ingestSplit 一次上报只建一条 split_batch，
-- 一头猪切 8 个部位就要报 8 次、建 8 条互不关联的记录，于是：
--   ① 系统不知道这 8 条同属一头猪的一次作业 → 「这头猪切了什么」查不出来；
--   ② product_name 是自由文本（「二分体」「带皮白条」「猪前腿肉」混写），
--      没有部位编码 → 没法统计、没法按部位召回；
--   ③ 各部位重量合计算不对得上白条，没人管 → 少了一块肉系统无感。
--
-- 本脚本做四件事：
--   1) 新建 split_job（分割作业主表）：一条作业 = 一头白条的整份产出清单，
--      同时存对账结果（基准重量、总出肉率、红肉出肉率、校验状态与说明）。
--   2) split_batch 加 job_id（挂在哪次作业下）与 part_code（标准部位编码）。
--   3) 新建 meat_part 部位字典：设备只能报字典里的部位，否则转人工补录。
--   4) 按 product_name 反查字典，回填存量 split_batch.part_code。
--
-- 兼容：存量历史记录 job_id 一律留空（表示「未经作业归集」，前端按平铺展示），
-- 不回填、不造假；只做能确凿命中的 part_code 回填。
--
-- 幂等：本文件可重复执行——ALTER / CREATE 前先探测 information_schema；
-- 字典用 ON DUPLICATE KEY UPDATE（脚本是字典的唯一来源，可重放修正文案）。
-- 执行：docker cp 进 mysql 容器后 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 本文件`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 分割作业主表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.split_job (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    job_no           VARCHAR(32)  NOT NULL COMMENT '分割作业号 SV+yyyyMMdd+3位当日流水；一条作业=一头白条的整份产出清单',
    parent_batch_id  BIGINT       NOT NULL COMMENT '所属胴体批次 carcass_batch.id',
    parent_batch_no  VARCHAR(32)  NOT NULL COMMENT '所属胴体批次号（冗余，追溯查询免联表）',
    pig_id           BIGINT       NULL COMMENT '被分割的那头生猪 id；报文带耳标时精确定位，否则为空（按批次均重对账）',
    ear_tag_no       VARCHAR(32)  NULL COMMENT '耳标号（白条挂钩标签识读结果）',
    operator         VARCHAR(32)  NULL COMMENT '分割操作员（实名）。设备不得代签，缺人转人工',
    workshop         VARCHAR(64)  NULL COMMENT '分割车间',
    line_no          VARCHAR(32)  NULL COMMENT '分割产线/班次',
    workshop_temp    DECIMAL(4,1) NULL COMMENT '车间温度(℃)',
    item_count       INT          NOT NULL DEFAULT 0 COMMENT '本次作业切出的部位条数',
    total_weight_kg  DECIMAL(8,2) NULL COMMENT '各部位重量合计(kg)',
    parent_weight_kg DECIMAL(8,2) NULL COMMENT '对账基准：该头白条重量(kg)',
    weight_basis     VARCHAR(16)  NULL COMMENT '基准来源：PIG=按耳标精确 / BATCH_AVG=批次单头均值 / NONE=无基准（不做对账）',
    total_yield_rate DECIMAL(6,2) NULL COMMENT '总出肉率(%) = 各部位合计 / 白条重量 × 100',
    lean_yield_rate  DECIMAL(6,2) NULL COMMENT '红肉出肉率(%) = 红肉类部位合计 / 白条重量 × 100',
    check_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '对账结论：0=通过 1=待人工复核（缺必出部位/总重超上限/总重低于下限）',
    check_note       VARCHAR(500) NULL COMMENT '对账说明（缺哪些部位、超了多少）；通过时为空',
    split_time       DATETIME     NOT NULL COMMENT '分割作业时刻',
    source           VARCHAR(16)  NULL DEFAULT 'DEVICE' COMMENT '数据来源：MANUAL/DEVICE',
    source_ref       VARCHAR(64)  NULL COMMENT '来源设备号',
    raw_payload      JSON         NULL COMMENT '设备原始报文留档',
    content_hash     CHAR(64)     NULL COMMENT '原文内容哈希(SHA-256)',
    create_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    void_flag        TINYINT      NOT NULL DEFAULT 0 COMMENT '作废标记：0=有效 1=已作废（MyBatis-Plus 逻辑删除列）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_no (job_no),
    KEY idx_parent (parent_batch_id),
    KEY idx_split_time (split_time),
    KEY idx_check (check_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '分割作业：一头白条的整份产出清单（一次上报产生一条，展开为多条 split_batch）';

-- ---------------------------------------------------------------------------
-- 2) split_batch 加「所属作业」与「部位编码」
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'split_batch' AND COLUMN_NAME = 'job_id');
SET @s1 := IF(@c1 = 0,
    'ALTER TABLE db_distribution.split_batch ADD COLUMN job_id BIGINT NULL COMMENT ''所属分割作业 split_job.id；空=未经作业归集的历史记录'' AFTER parent_batch_id',
    'SELECT ''split_batch.job_id 已存在，跳过'' AS skip_note');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'split_batch' AND COLUMN_NAME = 'part_code');
SET @s2 := IF(@c2 = 0,
    'ALTER TABLE db_distribution.split_batch ADD COLUMN part_code VARCHAR(16) NULL COMMENT ''标准部位编码（见 meat_part 字典）；空=字典建立前的历史记录'' AFTER product_name',
    'SELECT ''split_batch.part_code 已存在，跳过'' AS skip_note');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'split_batch' AND INDEX_NAME = 'idx_job');
SET @s3 := IF(@c3 = 0,
    'ALTER TABLE db_distribution.split_batch ADD INDEX idx_job (job_id)',
    'SELECT ''idx_job 已存在，跳过'' AS skip_note');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

-- 2.4 重量精度对齐：原 weight_kg 是 DECIMAL(6,1)（0.1kg 一档），而作业合计是 DECIMAL(8,2)，
--     两者精度不一致会让「作业合计 == 各部位合计」这条不变量永远对不上 ——
--     每个部位落库时被截掉 0.0x，合计却按未截断值算，差值是系统性的、不是偶发。
--     电子秤实际精度在 ±5g 量级，与 carcass_batch_pig.weight_kg 统一到 2 位小数。
SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'db_distribution' AND TABLE_NAME = 'split_batch'
              AND COLUMN_NAME = 'weight_kg' AND NUMERIC_SCALE < 2);
SET @s4 := IF(@c4 = 1,
    'ALTER TABLE db_distribution.split_batch MODIFY COLUMN weight_kg DECIMAL(8,2) NULL COMMENT ''该部位重量(kg)，与作业合计同精度''',
    'SELECT ''split_batch.weight_kg 精度已对齐，跳过'' AS skip_note');
PREPARE st4 FROM @s4; EXECUTE st4; DEALLOCATE PREPARE st4;

-- ---------------------------------------------------------------------------
-- 3) 部位字典
-- 设备报文里只认 part_code；只报了名称时按 part_name / aliases 反查，
-- 反查不到一律转人工 —— 部位是追溯的关键字段，宁缺勿猜。
-- category 决定对账口径：只有 LEAN 时按红肉出肉率判，出现 WHOLE（整条/半片出货）
-- 则不做下限判定（整条卖本来就没有部位比例可言）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS db_distribution.meat_part (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    part_code VARCHAR(16)  NOT NULL COMMENT '部位编码，设备上报只认这个',
    part_name VARCHAR(32)  NOT NULL COMMENT '标准部位名（展示用）',
    aliases   VARCHAR(200) NULL COMMENT '兼容叫法，逗号分隔；设备只报名称时按此反查',
    segment   VARCHAR(16)  NULL COMMENT '所属段：FRONT前段 / MIDDLE中段 / REAR后段 / WHOLE整条',
    category  VARCHAR(16)  NOT NULL COMMENT '品类：LEAN红肉 / BONE骨 / FAT脂 / SKIN皮 / BYPRODUCT副产 / WHOLE整条未细分',
    required  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必出部位：1=该出的没报就对账告警',
    sort_no   INT          NULL,
    enabled   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用（历史记录仍可展示其名称）',
    note      VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_part_code (part_code),
    KEY idx_part_name (part_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '分割部位字典：行业标准部位清单，设备只能报这里的编码';

INSERT INTO db_distribution.meat_part (part_code, part_name, aliases, segment, category, required, sort_no, note) VALUES
    ('P01', '颈背肌肉',   '1号肉,一号肉,颈背肉,梅花肉',       'FRONT',  'LEAN',      0, 10, '行业一号肉'),
    ('P02', '前腿肉',     '2号肉,二号肉,猪前腿肉,前腿',       'FRONT',  'LEAN',      1, 20, '行业二号肉，前段主产'),
    ('P03', '大排肌肉',   '3号肉,三号肉,大排,大里脊',         'MIDDLE', 'LEAN',      1, 30, '行业三号肉，中段主产'),
    ('P04', '后腿肉',     '4号肉,四号肉,猪后腿肉,后腿',       'REAR',   'LEAN',      1, 40, '行业四号肉，后段主产'),
    ('P05', '里脊',       '5号肉,五号肉,腰花肉,猪里脊,小里脊', 'MIDDLE', 'LEAN',      0, 50, '行业五号肉'),
    ('P06', '五花肉',     '培根,腹部五花肉,猪五花肉,五花',     'MIDDLE', 'FAT',       0, 60, '腹部五花，培根原料'),
    ('P07', '肋排',       '猪排骨,排骨,小排,肋排骨',           'MIDDLE', 'BONE',      0, 70, '中段肋骨'),
    ('P08', '前蹄',       '前脚,猪前蹄,前猪蹄',                'FRONT',  'BYPRODUCT', 0, 80, NULL),
    ('P09', '后蹄',       '后脚,猪后蹄,后猪蹄',                'REAR',   'BYPRODUCT', 0, 90, NULL),
    ('P10', '颈骨',       '颈骨块,颈椎骨',                     'FRONT',  'BONE',      0, 100, NULL),
    ('P11', '脊骨',       '龙骨,脊骨块,脊椎骨',                'MIDDLE', 'BONE',      0, 110, NULL),
    ('P12', '板油',       '脊膘,板油块,猪板油',                'MIDDLE', 'FAT',       0, 120, NULL),
    ('P13', '猪皮',       '皮,带皮,猪皮张',                    'WHOLE',  'SKIN',      0, 130, NULL),
    ('P14', '碎肉',       '修整碎肉,边角料,碎瘦肉',            'WHOLE',  'LEAN',      0, 140, '精修环节的修整产出'),
    ('P15', '前排',       '前肋排,前排骨',                     'FRONT',  'BONE',      0, 150, '要求带 5 根肋骨'),
    ('P20', '二分体',     '带皮白条,白条,无皮白条,红条,整条白条,半片', 'WHOLE', 'WHOLE', 0, 200, '整条/半片出货，未细分部位'),
    ('P99', '其他副产品', '内脏,猪头,猪尾,下水,三腺',          'WHOLE',  'BYPRODUCT', 0, 900, '兜底项，明细见备注')
ON DUPLICATE KEY UPDATE
    part_name = VALUES(part_name), aliases = VALUES(aliases), segment = VALUES(segment),
    category = VALUES(category), required = VALUES(required), sort_no = VALUES(sort_no), note = VALUES(note);

-- ---------------------------------------------------------------------------
-- 4) 存量回填：按品名反查字典（只回填能确凿命中的，绝不猜）
-- FIND_IN_SET 用于匹配逗号分隔的 aliases
-- ---------------------------------------------------------------------------
UPDATE db_distribution.split_batch b
JOIN db_distribution.meat_part p
  ON (b.product_name = p.part_name OR FIND_IN_SET(b.product_name, p.aliases) > 0)
SET b.part_code = p.part_code
WHERE b.part_code IS NULL;

-- ---------------------------------------------------------------------------
-- 5) 核验
-- ---------------------------------------------------------------------------
SELECT '部位字典条目' AS item, COUNT(*) AS v FROM db_distribution.meat_part
UNION ALL SELECT '其中必出部位', COUNT(*) FROM db_distribution.meat_part WHERE required = 1
UNION ALL SELECT '分割作业数', COUNT(*) FROM db_distribution.split_job
UNION ALL SELECT 'split_batch 总数', COUNT(*) FROM db_distribution.split_batch
UNION ALL SELECT '已回填 part_code', COUNT(*) FROM db_distribution.split_batch WHERE part_code IS NOT NULL
UNION ALL SELECT '未匹配到字典的品名', COUNT(DISTINCT product_name) FROM db_distribution.split_batch WHERE part_code IS NULL
UNION ALL SELECT '已挂作业(job_id)的记录', COUNT(*) FROM db_distribution.split_batch WHERE job_id IS NOT NULL;

SELECT DISTINCT product_name, part_code FROM db_distribution.split_batch ORDER BY part_code, product_name;
