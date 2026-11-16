-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_slaughter
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
-- Table structure for table `entry_inspection`
--

DROP TABLE IF EXISTS `entry_inspection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entry_inspection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pig_id` bigint NOT NULL,
  `batch_no` varchar(32) NOT NULL,
  `ear_tag_no` varchar(32) DEFAULT NULL,
  `source_farm` varchar(128) DEFAULT NULL,
  `arrive_time` datetime NOT NULL,
  `weight` decimal(7,2) DEFAULT NULL,
  `quarantine_cert` varchar(32) DEFAULT NULL,
  `vehicle_no` varchar(16) DEFAULT NULL,
  `health_check` tinyint DEFAULT NULL,
  `cert_verified` tinyint DEFAULT NULL,
  `abnormal_note` varchar(512) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  `content_hash` char(64) DEFAULT NULL,
  `inspector` varchar(32) NOT NULL,
  `source` varchar(16) DEFAULT 'MANUAL' COMMENT 'æ•°æ®æ¥æº MANUAL/DEVICE/API/IMPORT',
  `source_ref` varchar(64) DEFAULT NULL COMMENT 'æ¥æºè®¾å¤‡å·/å•æ®å·',
  `raw_payload` json DEFAULT NULL COMMENT 'è®¾å¤‡åŽŸå§‹æŠ¥æ–‡',
  `file_ids` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `void_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0=有效 1=已作废',
  `void_reason` varchar(500) DEFAULT NULL COMMENT '作废理由（必填）',
  `void_operator` varchar(128) DEFAULT NULL COMMENT '作废操作人（实名）',
  `void_time` datetime DEFAULT NULL COMMENT '作废时间',
  `org_id` bigint DEFAULT NULL COMMENT '操作机构（sys_org.id）：屠宰场归属，NULL=平台/未知',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_pig` (`batch_no`,`pig_id`),
  KEY `idx_pig` (`pig_id`),
  KEY `idx_ear_tag` (`ear_tag_no`),
  KEY `idx_status_time` (`status`,`arrive_time`),
  KEY `idx_void_flag` (`void_flag`),
  KEY `idx_entry_org` (`org_id`)
) ENGINE=InnoDB AUTO_INCREMENT=430 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å…¥åœºæŸ¥éªŒè®°å½•';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `entry_inspection`
--
-- WHERE:  pig_id=1397

LOCK TABLES `entry_inspection` WRITE;
/*!40000 ALTER TABLE `entry_inspection` DISABLE KEYS */;
INSERT INTO `entry_inspection` VALUES (429,1397,'B-GATE-1794733764242-D','ET-GATE-1794733764242','宏图养殖公司','2026-11-15 17:09:25',116.50,'QC-GATE-1794733764242',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 116.5, \"batchNo\": \"B-GATE-1794733764242-D\", \"earTagNo\": \"ET-GATE-1794733764242\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-GATE-1794733764242\"}',NULL,'2026-11-15 17:09:26',0,NULL,NULL,NULL,3);
/*!40000 ALTER TABLE `entry_inspection` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-15 17:16:54
-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_breeding
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
-- Table structure for table `pig_individual`
--

DROP TABLE IF EXISTS `pig_individual`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pig_individual` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ear_tag_no` varchar(32) NOT NULL,
  `farm_id` bigint NOT NULL,
  `breed` varchar(32) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `gender` tinyint DEFAULT '1',
  `pen_no` varchar(16) DEFAULT NULL,
  `source` varchar(64) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `data_source` varchar(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL/DEVICE/API/IMPORT（区别于业务字段 source=自繁/外购）',
  `source_ref` varchar(64) DEFAULT NULL COMMENT '来源设备号/单据号',
  `raw_payload` json DEFAULT NULL COMMENT '设备原始报文',
  `deleted` tinyint DEFAULT '0',
  `content_hash` char(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `void_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0=有效 1=已作废',
  `void_reason` varchar(500) DEFAULT NULL COMMENT '作废理由（必填）',
  `void_operator` varchar(128) DEFAULT NULL COMMENT '作废操作人（实名）',
  `void_time` datetime DEFAULT NULL COMMENT '作废时间',
  `batch_id` bigint DEFAULT NULL COMMENT '所属生猪批次 pig_batch.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ear_tag` (`ear_tag_no`),
  KEY `idx_farm_status` (`farm_id`,`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_void_flag` (`void_flag`),
  KEY `idx_batch` (`batch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1520 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”ŸçŒªä¸ªä½“æ¡£æ¡ˆ';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pig_individual`
--
-- WHERE:  id=1397

LOCK TABLES `pig_individual` WRITE;
/*!40000 ALTER TABLE `pig_individual` DISABLE KEYS */;
INSERT INTO `pig_individual` VALUES (1397,'ET-GATE-1794733764242',14,'三元杂',NULL,1,NULL,'自繁',2,'MANUAL','生猪批次 PB-GATE-1794733764242（按耳标区间自动生成）',NULL,0,NULL,'2026-11-15 17:09:24','2026-11-15 17:09:26',0,NULL,NULL,NULL,175);
/*!40000 ALTER TABLE `pig_individual` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-15 17:16:54
-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_breeding
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
-- Table structure for table `pig_batch`
--

DROP TABLE IF EXISTS `pig_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pig_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) NOT NULL,
  `farm_id` bigint NOT NULL,
  `breed` varchar(32) DEFAULT NULL,
  `entry_date` date DEFAULT NULL,
  `birth_date` date DEFAULT NULL COMMENT '批次出生日期（养殖 ERP 提供，同批共用；日龄由它现算，不存日龄）',
  `ear_tag_start` varchar(32) NOT NULL,
  `ear_tag_end` varchar(32) NOT NULL,
  `quantity` int NOT NULL,
  `source` varchar(64) DEFAULT NULL,
  `external_system` varchar(32) DEFAULT NULL COMMENT '外部系统标识（如 FARM_ERP / 具体的养殖 SaaS 代号）；NULL=人工登记',
  `external_id` varchar(64) DEFAULT NULL COMMENT '外部系统内的批次主键；与 external_system 组成幂等键',
  `data_source` varchar(16) NOT NULL DEFAULT 'MANUAL',
  `source_ref` varchar(128) DEFAULT NULL,
  `raw_payload` text,
  `void_flag` tinyint DEFAULT '0',
  `void_reason` varchar(256) DEFAULT NULL,
  `void_operator` varchar(32) DEFAULT NULL,
  `void_source` varchar(16) DEFAULT NULL COMMENT '作废来源 MANUAL=人工作废 / EXTERNAL=源系统撤销（两者都上链，责任方不同）',
  `void_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch` (`farm_id`,`batch_no`),
  UNIQUE KEY `uk_external` (`external_system`,`external_id`),
  KEY `idx_farm` (`farm_id`),
  KEY `idx_void` (`void_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=181 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生猪批次档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pig_batch`
--
-- WHERE:  id=175

LOCK TABLES `pig_batch` WRITE;
/*!40000 ALTER TABLE `pig_batch` DISABLE KEYS */;
INSERT INTO `pig_batch` VALUES (175,'PB-GATE-1794733764242',14,'三元杂','2026-08-17',NULL,'ET-GATE-1794733764242','ET-GATE-1794733764242',1,'自繁',NULL,NULL,'MANUAL','人工登记批次',NULL,0,NULL,NULL,NULL,NULL,'2026-11-15 17:09:24','2026-11-15 17:09:24');
/*!40000 ALTER TABLE `pig_batch` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-15 17:16:54
