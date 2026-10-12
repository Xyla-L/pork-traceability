-- 盖章示例值回归自造记录 20261012-152458
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
-- Table structure for table `carcass_stamp`
--

DROP TABLE IF EXISTS `carcass_stamp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carcass_stamp` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pig_id` bigint NOT NULL,
  `batch_no` varchar(32) NOT NULL,
  `ear_tag_no` varchar(32) DEFAULT NULL,
  `carcass_no` varchar(32) NOT NULL,
  `stamp_no` varchar(32) NOT NULL,
  `stamp_type` varchar(32) DEFAULT 'æ£€ç–«åˆæ ¼ç« ',
  `stamp_time` datetime NOT NULL,
  `stamp_position` varchar(64) DEFAULT 'èƒ´ä½“ä¸¤ä¾§è‡€éƒ¨',
  `veterinary` varchar(32) NOT NULL,
  `e_signature` text,
  `status` tinyint DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
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
  UNIQUE KEY `uk_pig` (`pig_id`),
  UNIQUE KEY `uk_stamp_no` (`stamp_no`),
  UNIQUE KEY `uk_carcass_no` (`carcass_no`),
  KEY `idx_batch` (`batch_no`),
  KEY `idx_status_time` (`status`,`stamp_time`),
  KEY `idx_void_flag` (`void_flag`)
) ENGINE=InnoDB AUTO_INCREMENT=173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='èƒ´ä½“æ£€ç–«åˆæ ¼å°ç« ';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `carcass_stamp`
--
-- WHERE:  id=172

LOCK TABLES `carcass_stamp` WRITE;
/*!40000 ALTER TABLE `carcass_stamp` DISABLE KEYS */;
INSERT INTO `carcass_stamp` VALUES (172,306,'B-20261012-01','ET-DEMO-1791787264150','CT-20261012-152425','ST20261012196568','检疫合格章','2026-10-12 15:24:29','胴体两侧臀部','李官方兽医',NULL,1,NULL,'64417240e75396b9d834ed51afa5d9e64778392c010ef1bee688d96c048d9392','DEVICE','STAMPER-001','{\"batchNo\": \"B-20261012-01\", \"earTagNo\": \"ET-DEMO-1791787264150\", \"carcassNo\": \"CT-20261012-152425\", \"veterinary\": \"李官方兽医\"}','2026-10-12 15:24:29',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `carcass_stamp` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12 15:24:59
-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_ingest
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
-- Table structure for table `ingest_staging`
--

DROP TABLE IF EXISTS `ingest_staging`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ingest_staging` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `channel` varchar(32) NOT NULL COMMENT 'æŽ¥å…¥é€šé“',
  `device_no` varchar(64) NOT NULL COMMENT 'æ¥æºè®¾å¤‡ç¼–å·',
  `biz_key` varchar(96) NOT NULL COMMENT 'è®¾å¤‡ä¾§ä¸šåŠ¡å”¯ä¸€å·ï¼ˆå¹‚ç­‰é”®ï¼Œä¸Ž device_no ç»„æˆå”¯ä¸€çº¦æŸï¼‰',
  `report_time` datetime NOT NULL COMMENT 'è®¾å¤‡ä¾§æ•°æ®æ—¶é—´',
  `receive_time` datetime NOT NULL COMMENT 'æŽ¥å…¥å±‚æŽ¥æ”¶æ—¶é—´',
  `payload` json NOT NULL COMMENT 'åŽŸå§‹æŠ¥æ–‡ï¼ˆå…¨é‡ç•™æ¡£ï¼Œä¸å¯å˜ï¼‰',
  `source_ref` varchar(128) DEFAULT NULL COMMENT 'æ¥æºå•æ®å·/è®¾å¤‡æµæ°´å·',
  `status` tinyint DEFAULT '0' COMMENT '0å¾…å¤„ç† 1å¾…äººå·¥å¤„ç† 2å·²å…¥è´¦ 3å·²æ‹’ç» 4å·²é™é‡‡æ ·ä¸¢å¼ƒ',
  `target_table` varchar(64) DEFAULT NULL COMMENT 'å…¥è´¦ç›®æ ‡è¡¨',
  `target_id` bigint DEFAULT NULL COMMENT 'å…¥è´¦ç›®æ ‡ä¸»é”®',
  `error_msg` varchar(1024) DEFAULT NULL COMMENT 'æ ¡éªŒå¤±è´¥åŽŸå› ï¼ˆå¤šæ¡ä»¥ ; åˆ†éš”ï¼‰',
  `handle_time` datetime DEFAULT NULL COMMENT 'äººå·¥å¤„ç†æ—¶é—´',
  `handler` varchar(32) DEFAULT NULL COMMENT 'äººå·¥å¤„ç†äºº',
  `remark` varchar(512) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_biz` (`device_no`,`biz_key`),
  KEY `idx_status_time` (`status`,`receive_time`),
  KEY `idx_channel_time` (`channel`,`receive_time`),
  KEY `idx_report_time` (`report_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4267 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è®¾å¤‡ä¸ŠæŠ¥å¾…ç¡®è®¤é˜Ÿåˆ—';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ingest_staging`
--
-- WHERE:  channel='STAMP' AND device_no='STAMPER-001' AND receive_time>='2026-10-12 15:24:07'

LOCK TABLES `ingest_staging` WRITE;
/*!40000 ALTER TABLE `ingest_staging` DISABLE KEYS */;
INSERT INTO `ingest_staging` VALUES (4265,'STAMP','STAMPER-001','DEMO-1791789868307','2026-10-12 15:24:28','2026-10-12 15:24:28','{\"batchNo\": \"B-20261012-01\", \"earTagNo\": \"ET-DEMO-1791787264150\", \"carcassNo\": \"CT-20261012-152425\", \"veterinary\": \"李官方兽医\"}','STAMPER-001',2,'carcass_stamp',172,NULL,NULL,NULL,NULL,'2026-10-12 15:24:28'),(4266,'STAMP','STAMPER-001','STAMP-CONFLICT-1791789847518','2026-10-12 15:24:33','2026-10-12 15:24:33','{\"batchNo\": \"B-20261012-01\", \"earTagNo\": \"ET-DEMO-1791787264150\", \"carcassNo\": \"CT-20261012-999999\", \"veterinary\": \"李官方兽医\"}','STAMPER-001',1,'carcass_stamp',NULL,'该白条（耳标 ET-DEMO-1791787264150）已有盖章记录（胴体编号 CT-20261012-152425），本次上报的胴体编号是 CT-20261012-999999，两者不一致：请人工核对是重复盖章还是编号录错',NULL,NULL,NULL,'2026-10-12 15:24:33');
/*!40000 ALTER TABLE `ingest_staging` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12 15:25:01
-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_common
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
-- Table structure for table `blockchain_record`
--

DROP TABLE IF EXISTS `blockchain_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blockchain_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `biz_type` varchar(32) NOT NULL,
  `biz_id` bigint NOT NULL,
  `biz_key` varchar(64) NOT NULL,
  `content_hash` char(64) NOT NULL,
  `tx_hash` varchar(128) DEFAULT NULL,
  `block_number` bigint DEFAULT NULL,
  `chain_time` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `retry_count` int DEFAULT '0',
  `error_msg` varchar(512) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  UNIQUE KEY `uk_tx_hash` (`tx_hash`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_biz_key` (`biz_key`),
  KEY `idx_status_time` (`status`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2383 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='区块链存证记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `blockchain_record`
--
-- WHERE:  biz_type='SLAUGHTER_INSPECT' AND biz_id=172

LOCK TABLES `blockchain_record` WRITE;
/*!40000 ALTER TABLE `blockchain_record` DISABLE KEYS */;
INSERT INTO `blockchain_record` VALUES (2382,'b54501e86e08d0c964f91e6d076d3e7c3d04e56141228f09615cfa7915028aa5','SLAUGHTER_INSPECT',172,'ST20261012196568','64417240e75396b9d834ed51afa5d9e64778392c010ef1bee688d96c048d9392','0xedfa4e76da41813d622c0bae2312603fc8eb7fc1d21299f5ab9ed15ea3754413',1869,'2026-10-12 15:24:29',1,0,NULL,'2026-10-12 15:24:29','2026-10-12 15:24:29');
/*!40000 ALTER TABLE `blockchain_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12 15:25:02
