-- =====================================================================
-- 20-sync-org-from-business.sql
-- 目的：把业务机构表（养殖场 db_breeding.farm、屠宰场 db_slaughter.slaughterhouse）
--       中「尚未登记到机构表 db_common.sys_org」的机构补进去，
--       使「系统管理 → 机构管理」与各业务页面的机构下拉保持一致。
--
-- 背景（本次修复的根因）：
--   机构有两套登记、彼此不联动——
--     · db_common.sys_org        : 「机构管理」、用户所属机构、出栏审批屠宰场下拉、冷链门店下拉 的数据源
--     · db_breeding.farm         : 「生猪档案 → 新建生猪 / 产地检疫」的养殖场下拉的数据源
--     · db_slaughter.slaughterhouse : 屠宰场业务档案
--   历史种子只在 sys_org 里放了 1 个养殖场、1 个屠宰场，
--   而 farm 表有 14 条、slaughterhouse 表有 3 条 —— 于是机构管理里「缺机构」。
--   具体来源：
--     · 07-demo-data.sql      → farm 1 条 + sys_org 1 条养殖场/1 条屠宰场
--     · 08-test-data.sql      → farm 再补 13 条，但没同步 sys_org
--     · 04-slaughter.sql      → slaughterhouse 3 条，也没同步 sys_org
--
-- 幂等：按 (type, name) 判重，可重复执行（已存在则跳过）。
-- 命名 20- 保证在全新初始化时排在 19- 之后最后执行。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- A. 养殖场：db_breeding.farm → sys_org(type='farm')
--    挂在顶层监管机构之下（与既有 07 号种子的层级保持一致）
-- ---------------------------------------------------------------------
INSERT INTO db_common.sys_org (parent_id, type, name, manager, phone, address, remark, create_time, update_time)
SELECT COALESCE((SELECT MIN(id) FROM db_common.sys_org WHERE type = 'supervisor'), 0),
       'farm',
       f.farm_name,
       f.contact_person,
       f.contact_phone,
       f.address,
       CONCAT('由养殖场档案同步（许可证 ', COALESCE(f.license_no, '-'), '）'),
       NOW(), NOW()
FROM db_breeding.farm f
WHERE f.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM db_common.sys_org o
      WHERE o.type = 'farm' AND o.name = f.farm_name
  );

-- ---------------------------------------------------------------------
-- B. 屠宰场：db_slaughter.slaughterhouse → sys_org(type='slaughter')
-- ---------------------------------------------------------------------
INSERT INTO db_common.sys_org (parent_id, type, name, manager, phone, address, remark, create_time, update_time)
SELECT COALESCE((SELECT MIN(id) FROM db_common.sys_org WHERE type = 'supervisor'), 0),
       'slaughter',
       s.name,
       s.contact_person,
       s.contact_phone,
       s.address,
       CONCAT('由屠宰场档案同步（许可证 ', COALESCE(s.license_no, '-'), '）'),
       NOW(), NOW()
FROM db_slaughter.slaughterhouse s
WHERE s.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM db_common.sys_org o
      WHERE o.type = 'slaughter' AND o.name = s.name
  );

-- ---------------------------------------------------------------------
-- C. 一致性自检：业务表机构数 vs 机构表机构数，应逐行相等
-- ---------------------------------------------------------------------
SELECT '养殖场' AS 机构类型,
       (SELECT COUNT(*) FROM db_breeding.farm WHERE status = 1)                       AS 业务档案数,
       (SELECT COUNT(*) FROM db_common.sys_org WHERE type = 'farm')                   AS 机构表数,
       (SELECT COUNT(*) FROM db_common.sys_org WHERE type = 'farm') -
       (SELECT COUNT(*) FROM db_breeding.farm WHERE status = 1)                       AS 差额
UNION ALL
SELECT '屠宰场',
       (SELECT COUNT(*) FROM db_slaughter.slaughterhouse WHERE status = 1),
       (SELECT COUNT(*) FROM db_common.sys_org WHERE type = 'slaughter'),
       (SELECT COUNT(*) FROM db_common.sys_org WHERE type = 'slaughter') -
       (SELECT COUNT(*) FROM db_slaughter.slaughterhouse WHERE status = 1);

-- ---------------------------------------------------------------------
-- D. 明细自检：业务表里存在、但机构表里按名称找不到的机构（应为 0 行）
-- ---------------------------------------------------------------------
SELECT '养殖场缺失' AS 问题, f.farm_name AS 名称
FROM db_breeding.farm f
WHERE f.status = 1
  AND NOT EXISTS (SELECT 1 FROM db_common.sys_org o WHERE o.type = 'farm' AND o.name = f.farm_name)
UNION ALL
SELECT '屠宰场缺失', s.name
FROM db_slaughter.slaughterhouse s
WHERE s.status = 1
  AND NOT EXISTS (SELECT 1 FROM db_common.sys_org o WHERE o.type = 'slaughter' AND o.name = s.name);
