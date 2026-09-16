-- ========================================================================
-- 修复 sys_user 联系信息（电话/邮箱）并为每个已存在机构补充至少 1 个用户
-- 适用场景：数据库中已有 admin / farm_user / slaughter_user / distribution_user
-- / retail_user 5 个用户但 phone/email 为 NULL，且 org_id=1002..1014 的 13 个
-- 零售门店尚无关联用户。可独立执行，无需重启 auth-service。
-- 幂等：所有 UPDATE 仅在 phone/email 为 NULL 时回填；INSERT 使用 INSERT IGNORE
--       利用 username 唯一约束避免重复插入。
-- ========================================================================
SET NAMES utf8mb4;
USE db_common;

-- ========== 1. 回填已有 5 个 bootstrap 用户的 phone / email ==========
UPDATE sys_user
SET phone = COALESCE(phone, '010-12345678'),
    email = COALESCE(email, 'admin@porktrace.com'),
    update_time = NOW()
WHERE username = 'admin' AND (phone IS NULL OR email IS NULL);

UPDATE sys_user
SET phone = COALESCE(phone, '13800000001'),
    email = COALESCE(email, 'farmer@porktrace.com'),
    update_time = NOW()
WHERE username = 'farm_user' AND (phone IS NULL OR email IS NULL);

UPDATE sys_user
SET phone = COALESCE(phone, '010-87654321'),
    email = COALESCE(email, 'slaughter@porktrace.com'),
    update_time = NOW()
WHERE username = 'slaughter_user' AND (phone IS NULL OR email IS NULL);

UPDATE sys_user
SET phone = COALESCE(phone, '010-55667788'),
    email = COALESCE(email, 'distributor@porktrace.com'),
    update_time = NOW()
WHERE username = 'distribution_user' AND (phone IS NULL OR email IS NULL);

UPDATE sys_user
SET phone = COALESCE(phone, '13800000003'),
    email = COALESCE(email, 'retailer@porktrace.com'),
    update_time = NOW()
WHERE username = 'retail_user' AND (phone IS NULL OR email IS NULL);

-- ========== 2. 为 org_id=1002..1014 的 13 个零售门店各创建 1 个 RETAILER 用户 ==========
-- 复用已有用户的 password_hash / password_salt（默认密码 ChangeMe123!）
-- 优先取 retail_user，其次 farm_user，最后 admin；保证 @ph/@ps 不为 NULL
SET @ph := (SELECT password_hash FROM sys_user WHERE username = 'retail_user' LIMIT 1);
SET @ps := (SELECT password_salt FROM sys_user WHERE username = 'retail_user' LIMIT 1);
SET @ph := COALESCE(@ph, (SELECT password_hash FROM sys_user WHERE username = 'farm_user' LIMIT 1));
SET @ps := COALESCE(@ps, (SELECT password_salt FROM sys_user WHERE username = 'farm_user' LIMIT 1));
SET @ph := COALESCE(@ph, (SELECT password_hash FROM sys_user WHERE role = 'ADMIN' LIMIT 1));
SET @ps := COALESCE(@ps, (SELECT password_salt FROM sys_user WHERE role = 'ADMIN' LIMIT 1));

INSERT IGNORE INTO sys_user
    (username, password_hash, password_salt, nickname, real_name, role, org_id, status,
     phone, email, create_time, update_time)
VALUES
    ('retail_user_1002', @ph, @ps, '周店长', '周店长', 'RETAILER', 1002, 1,
     '13800000012', 'retailer1002@porktrace.com', NOW(), NOW()),
    ('retail_user_1003', @ph, @ps, '吴店长', '吴店长', 'RETAILER', 1003, 1,
     '13800000013', 'retailer1003@porktrace.com', NOW(), NOW()),
    ('retail_user_1004', @ph, @ps, '郑店长', '郑店长', 'RETAILER', 1004, 1,
     '13800000014', 'retailer1004@porktrace.com', NOW(), NOW()),
    ('retail_user_1005', @ph, @ps, '王店长', '王店长', 'RETAILER', 1005, 1,
     '13800000015', 'retailer1005@porktrace.com', NOW(), NOW()),
    ('retail_user_1006', @ph, @ps, '冯店长', '冯店长', 'RETAILER', 1006, 1,
     '13800000016', 'retailer1006@porktrace.com', NOW(), NOW()),
    ('retail_user_1007', @ph, @ps, '蒋店长', '蒋店长', 'RETAILER', 1007, 1,
     '13800000017', 'retailer1007@porktrace.com', NOW(), NOW()),
    ('retail_user_1008', @ph, @ps, '韩店长', '韩店长', 'RETAILER', 1008, 1,
     '13800000018', 'retailer1008@porktrace.com', NOW(), NOW()),
    ('retail_user_1009', @ph, @ps, '杨店长', '杨店长', 'RETAILER', 1009, 1,
     '13800000019', 'retailer1009@porktrace.com', NOW(), NOW()),
    ('retail_user_1010', @ph, @ps, '朱店长', '朱店长', 'RETAILER', 1010, 1,
     '13800000020', 'retailer1010@porktrace.com', NOW(), NOW()),
    ('retail_user_1011', @ph, @ps, '徐店长', '徐店长', 'RETAILER', 1011, 1,
     '13800000021', 'retailer1011@porktrace.com', NOW(), NOW()),
    ('retail_user_1012', @ph, @ps, '黄店长', '黄店长', 'RETAILER', 1012, 1,
     '13800000022', 'retailer1012@porktrace.com', NOW(), NOW()),
    ('retail_user_1013', @ph, @ps, '彭店长', '彭店长', 'RETAILER', 1013, 1,
     '13800000023', 'retailer1013@porktrace.com', NOW(), NOW()),
    ('retail_user_1014', @ph, @ps, '鲁店长', '鲁店长', 'RETAILER', 1014, 1,
     '13800000024', 'retailer1014@porktrace.com', NOW(), NOW());

-- ========== 3. 校验：每个已存在机构至少 1 个用户 ==========
-- 此查询用于人工核对，无机构匹配用户的机构会列出 NULL 计数
SELECT o.id AS org_id, o.name AS org_name, o.type,
       COUNT(u.id) AS user_count
FROM sys_org o
LEFT JOIN sys_user u ON u.org_id = o.id
GROUP BY o.id, o.name, o.type
ORDER BY o.id;
