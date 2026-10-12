SET NAMES utf8mb4;

SELECT '=== F. 屠宰三件套完整性矩阵（有效猪）===' AS section;
SELECT p.id, p.ear_tag_no, p.status,
       (SELECT COUNT(*) FROM db_slaughter.entry_inspection e WHERE e.pig_id=p.id AND e.void_flag=0) AS entry,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1) AS pre,
       (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2) AS post,
       (SELECT COUNT(*) FROM db_slaughter.ractopamine_test rt WHERE rt.pig_id=p.id AND rt.void_flag=0) AS racto,
       (SELECT COUNT(*) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0) AS stamp,
       (SELECT COALESCE(MAX(cs.status),-1) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0) AS stamp_status
FROM db_breeding.pig_individual p
WHERE p.void_flag = 0
ORDER BY p.id;

SELECT '=== G. 违规清单：有章但缺宰前或宰后 ===' AS section;
SELECT p.id, p.ear_tag_no,
       CASE WHEN (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1)=0 THEN '缺宰前' ELSE '' END AS miss_pre,
       CASE WHEN (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2)=0 THEN '缺宰后' ELSE '' END AS miss_post,
       (SELECT COALESCE(MAX(cs.status),-1) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0) AS stamp_status
FROM db_breeding.pig_individual p
WHERE p.void_flag = 0
  AND EXISTS (SELECT 1 FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id AND cs.void_flag=0)
  AND (
    (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=1)=0
    OR
    (SELECT COUNT(*) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id AND si.void_flag=0 AND si.inspect_type=2)=0
  )
ORDER BY p.id;

SELECT '=== H. 采样：检验批次号与猪的入场批次对应关系 ===' AS section;
SELECT p.id, p.ear_tag_no,
  (SELECT GROUP_CONCAT(e.batch_no) FROM db_slaughter.entry_inspection e WHERE e.pig_id=p.id) AS entry_batch,
  (SELECT GROUP_CONCAT(cs.batch_no) FROM db_slaughter.carcass_stamp cs WHERE cs.pig_id=p.id) AS stamp_batch,
  (SELECT GROUP_CONCAT(si.batch_no) FROM db_slaughter.slaughter_inspection si WHERE si.pig_id=p.id) AS inspect_batch
FROM db_breeding.pig_individual p WHERE p.id IN (4,10,11,12,13,14,15,16);
