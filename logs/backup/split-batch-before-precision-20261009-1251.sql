-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_distribution
-- ------------------------------------------------------
-- Server version	8.4.11

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `split_batch`
--

DROP TABLE IF EXISTS `split_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `split_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) NOT NULL,
  `parent_batch_id` bigint NOT NULL,
  `job_id` bigint DEFAULT NULL COMMENT '所属分割作业 split_job.id；空=未经作业归集的历史记录',
  `split_level` tinyint DEFAULT '1',
  `product_name` varchar(64) NOT NULL,
  `part_code` varchar(16) DEFAULT NULL COMMENT '标准部位编码（见 meat_part 字典）；空=字典建立前的历史记录',
  `weight_kg` decimal(6,1) DEFAULT NULL,
  `package_count` int DEFAULT '1',
  `package_type` varchar(32) DEFAULT NULL,
  `split_time` datetime NOT NULL,
  `workshop` varchar(64) DEFAULT NULL,
  `workshop_temp` decimal(4,1) DEFAULT NULL,
  `operator` varchar(32) DEFAULT NULL,
  `file_ids` text,
  `note` varchar(500) DEFAULT NULL,
  `content_hash` char(64) DEFAULT NULL,
  `source` varchar(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT',
  `source_ref` varchar(64) DEFAULT NULL COMMENT '来源设备号/单据号',
  `raw_payload` json DEFAULT NULL COMMENT '设备原始报文',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `void_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0=有效 1=已作废',
  `void_reason` varchar(500) DEFAULT NULL COMMENT '作废理由（必填）',
  `void_operator` varchar(128) DEFAULT NULL COMMENT '作废操作人（实名）',
  `void_time` datetime DEFAULT NULL COMMENT '作废时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_parent` (`parent_batch_id`),
  KEY `idx_level` (`split_level`),
  KEY `idx_void_flag` (`void_flag`),
  KEY `idx_job` (`job_id`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='åˆ†å‰²æ‰¹æ¬¡';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `split_batch`
--

/*!40000 ALTER TABLE `split_batch` DISABLE KEYS */;
INSERT INTO `split_batch` VALUES (1,'SP-DEMO-0001',1,NULL,1,'二分体','P20',41.2,1,'白条','2026-09-11 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'44f8f80cd92d3905518165b5a3ad5be1b02346b4a6a052e5dfc43024182b39cb','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(2,'SP-DEMO-0002',1,NULL,2,'猪前腿肉','P02',10.5,20,'真空包装','2026-09-12 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'4893a726c53fad431d6fa5924a1682c49f5b20a3dba4fcd7aecb29f814ab4eaa','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(3,'SP-TEST-0001',2,NULL,1,'二分体','P20',41.2,1,'白条','2026-08-31 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'97cae8e31e45dda95b5a5772253b01828e47c2c5574a6911be9a4fc27829fbb0','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(4,'SP-TEST-0002',3,NULL,2,'猪前腿肉','P02',10.5,20,'真空包装','2026-09-01 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'6d9ad524b1310c7244581cf511ff5508abf1721a9c6d71bf48956f94c2d24a05','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(5,'SP-TEST-0003',3,NULL,1,'二分体','P20',42.5,1,'白条','2026-09-02 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'7cf9ca104950ef8d57f81dd4ebb448f5cf9d19ea7476be8f488ab1a4f89db9b6','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(6,'SP-TEST-0004',5,NULL,2,'猪后腿肉','P04',12.0,15,'真空包装','2026-09-03 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'410edc5b9e5806d3cf9709f000d5ed8ebeae2aae4b10393822c327e671fccb91','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(7,'SP-TEST-0005',4,NULL,1,'二分体','P20',39.0,1,'白条','2026-09-04 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'c53af3bfe14409c598b0e1ab9c71f0f8527fce814c49a8e9a50263f895d55b8b','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(8,'SP-TEST-0006',7,NULL,2,'猪排骨','P07',8.5,10,'真空包装','2026-09-05 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'4b902da0abfa9faa166afc531219ff2bc92d228dc720b2ed3fbfdb23e1f44ee1','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(9,'SP-TEST-0007',5,NULL,1,'二分体','P20',44.0,1,'白条','2026-09-06 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'2b32e8893b08aca5528a4cce5a897feece1d6bd15ffc70e2bf3678819297dbda','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(10,'SP-TEST-0008',9,NULL,2,'猪五花肉','P06',15.0,25,'真空包装','2026-09-07 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'5c57a5fa66d333828d1dfe2a90fea7ff8025e24d27c58ee2e5265a4b48862cca','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(11,'SP-TEST-0009',6,NULL,1,'二分体','P20',37.5,1,'白条','2026-09-08 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'15bcf67f198eda14dc9bbc865d44c83531f35e70b3fc2ed8346cd0901c1ba970','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(12,'SP-TEST-0010',11,NULL,2,'猪里脊','P05',5.5,8,'真空包装','2026-09-09 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'fd12317b1b70a17728ea71a2a4aebfa7a8d1ab2287555b4221d8a84a5974ab49','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(13,'SP-TEST-0011',7,NULL,1,'二分体','P20',40.0,1,'白条','2026-09-10 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'c7e76f136b9b2e3a9b6490f1299406f83eaf2621ed45ddab10246d1bcd63945f','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(14,'SP-TEST-0012',13,NULL,2,'猪前腿肉','P02',11.0,22,'真空包装','2026-09-11 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'775bb7b346dcf5f0fd84e89ac679e575e2d91d9bed95a1a0d8953fec5ffb05a4','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(15,'SP-TEST-0013',8,NULL,1,'二分体','P20',45.0,1,'白条','2026-09-12 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'3b1998ba844272e856776b1592818a9a4045d95983e3e99418a430940a92dd9d','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(16,'SP-TEST-0014',15,NULL,2,'猪排骨','P07',9.0,12,'真空包装','2026-09-13 19:55:18','示范分割车间',6.5,'陈分割','[]',NULL,'49f9728f4ba44aa7af1573ff06ba2e895d8629a41ebba9442a208d67d7d10545','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(17,'SP-TEST-0015',9,NULL,1,'二分体','P20',41.5,1,'白条','2026-09-14 19:55:18','示范分割车间',8.0,'陈分割','[]',NULL,'eb0fb17fc9f517705bf490591f9d1ee026534a3270940df355f4386f70c7ba5a','MANUAL','历史人工录入-陈分割',NULL,'2026-09-16 19:55:18',0,NULL,NULL,NULL),(18,'SP20260916859403',17,NULL,1,'猪前腿肉','P02',10.0,5,NULL,'2026-09-16 16:32:16','分割车间A组',NULL,NULL,NULL,'','4debd134eefbf6d57f435e0466a1432984c6536eb403b71f5eec6f22c929665b','MANUAL','历史人工录入-未记录',NULL,'2026-09-16 16:32:16',1,'一致性修复：上游胴体批次已作废（其关联生猪档案已作废），分割批次随之作废','system-reconcile','2026-10-04 16:36:59'),(19,'SP20260917959868',18,NULL,1,'猪前腿肉','P02',10.0,5,NULL,'2026-09-17 05:18:11','分割车间A组',NULL,NULL,NULL,'','b94d2a56b7de54677fb7a664bb89b4baf756d03a3c1d05b263fd3f2039a1e8d6','MANUAL','历史人工录入-未记录',NULL,'2026-09-17 05:18:11',0,NULL,NULL,NULL),(20,'SP20260917295195',19,NULL,1,'猪前腿肉','P02',10.0,5,NULL,'2026-09-17 23:19:23','分割车间A组',NULL,NULL,NULL,'','ab27c400fee155bd7066b3479b1dbc9e8d3023b42896e4ac91af0f76455c8379','MANUAL','历史人工录入-未记录',NULL,'2026-09-17 23:19:23',0,NULL,NULL,NULL),(21,'SP20260918692191',20,NULL,1,'猪前腿肉','P02',10.0,5,NULL,'2026-09-18 02:45:05','分割车间A组',NULL,NULL,NULL,'','9c5eaf44e295f929b94e3c63a28c91a3c7eb7736f44ada4b9e6b1e3030346279','MANUAL','历史人工录入-未记录',NULL,'2026-09-18 02:45:05',0,NULL,NULL,NULL),(22,'SP20260918244095',21,NULL,1,'猪五花肉','P06',10.0,5,NULL,'2026-09-18 10:09:55','分割车间A组',NULL,NULL,NULL,'','a4f80012a4be6b2ebd8cff5adac0a64896549b2fd8fec32e2cacd310bc1f27f2','MANUAL','历史人工录入-未记录',NULL,'2026-09-18 10:09:55',0,NULL,NULL,NULL),(23,'SP20260918170247',22,NULL,1,'猪里脊','P05',10.0,5,NULL,'2026-09-18 10:50:25','分割车间A组',NULL,NULL,NULL,'','322e9e1212d6168313657aca86841083c9a85cae92fd295d97188e36db301e7b','MANUAL','历史人工录入-未记录',NULL,'2026-09-18 10:50:25',0,NULL,NULL,NULL),(24,'SP20260918243893',23,NULL,1,'猪排骨','P07',10.0,5,NULL,'2026-09-18 11:47:46','分割车间A组',NULL,NULL,NULL,'','f30b5f36aca11159ba26a789c495a5134f15e8499dad234dd57b73ccf6f8dd3a','MANUAL','历史人工录入-未记录',NULL,'2026-09-18 11:47:46',0,NULL,NULL,NULL),(25,'SP20260922072778',23,NULL,1,'带皮白条','P20',78.5,1,'真空袋','2026-09-22 18:44:06','分割车间一号线',NULL,'未记录',NULL,NULL,'e648c5849a6948c398949f448a49970fd7ea1164ac2289841afa39593630573e','DEVICE','SPLIT-001','{\"weightKg\": 78.5, \"workshop\": \"分割车间一号线\", \"packageType\": \"真空袋\", \"productName\": \"带皮白条\", \"packageCount\": 1, \"parentBatchNo\": \"CB20260918732081\"}','2026-09-22 18:44:06',0,NULL,NULL,NULL),(26,'SP20260922575156',23,NULL,1,'带皮白条','P20',78.5,1,'真空袋','2026-09-22 18:48:55','分割车间一号线',NULL,'未记录',NULL,NULL,'e648c5849a6948c398949f448a49970fd7ea1164ac2289841afa39593630573e','DEVICE','SPLIT-001','{\"weightKg\": 78.5, \"workshop\": \"分割车间一号线\", \"packageType\": \"真空袋\", \"productName\": \"带皮白条\", \"packageCount\": 1, \"parentBatchNo\": \"CB20260918732081\"}','2026-09-22 18:48:55',0,NULL,NULL,NULL),(27,'SP20260922476934',23,NULL,1,'带皮白条','P20',78.5,1,'真空袋','2026-09-22 18:49:39','分割车间一号线',NULL,'未记录',NULL,NULL,'e648c5849a6948c398949f448a49970fd7ea1164ac2289841afa39593630573e','DEVICE','SPLIT-001','{\"weightKg\": 78.5, \"workshop\": \"分割车间一号线\", \"packageType\": \"真空袋\", \"productName\": \"带皮白条\", \"packageCount\": 1, \"parentBatchNo\": \"CB20260918732081\"}','2026-09-22 18:49:39',0,NULL,NULL,NULL),(28,'SP20260922139266',23,NULL,1,'带皮白条','P20',78.5,1,'真空袋','2026-09-22 18:53:43','分割车间一号线',NULL,'未记录',NULL,NULL,'e648c5849a6948c398949f448a49970fd7ea1164ac2289841afa39593630573e','DEVICE','SPLIT-001','{\"weightKg\": 78.5, \"workshop\": \"分割车间一号线\", \"packageType\": \"真空袋\", \"productName\": \"带皮白条\", \"packageCount\": 1, \"parentBatchNo\": \"CB20260918732081\"}','2026-09-22 18:53:43',0,NULL,NULL,NULL),(29,'SP20261003561802',23,NULL,1,'带皮白条','P20',78.5,1,'真空袋','2026-10-03 16:09:25','分割车间一号线',NULL,'未记录',NULL,NULL,'e648c5849a6948c398949f448a49970fd7ea1164ac2289841afa39593630573e','DEVICE','SPLIT-001','{\"weightKg\": 78.5, \"workshop\": \"分割车间一号线\", \"packageType\": \"真空袋\", \"productName\": \"带皮白条\", \"packageCount\": 1, \"parentBatchNo\": \"CB20260918732081\"}','2026-10-03 16:09:25',0,NULL,NULL,NULL),(30,'SP20261003286878',23,NULL,1,'带皮白条','P20',78.5,1,'真空袋','2026-10-03 21:04:27','分割车间一号线',NULL,'未记录',NULL,NULL,'e648c5849a6948c398949f448a49970fd7ea1164ac2289841afa39593630573e','DEVICE','SPLIT-001','{\"weightKg\": 78.5, \"workshop\": \"分割车间一号线\", \"packageType\": \"真空袋\", \"productName\": \"带皮白条\", \"packageCount\": 1, \"parentBatchNo\": \"CB20260918732081\"}','2026-10-03 21:04:27',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `split_batch` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-09 12:51:44
