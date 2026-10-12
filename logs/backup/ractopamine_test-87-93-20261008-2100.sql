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
-- Dumping data for table `ractopamine_test`
--
-- WHERE:  id >= 87

LOCK TABLES `ractopamine_test` WRITE;
/*!40000 ALTER TABLE `ractopamine_test` DISABLE KEYS */;
INSERT INTO `ractopamine_test` (`id`, `pig_id`, `test_no`, `batch_no`, `ear_tag_no`, `sample_no`, `test_type`, `test_time`, `test_method`, `test_target`, `sample_part`, `result`, `status`, `detection_limit`, `operator`, `source`, `source_ref`, `raw_payload`, `file_ids`, `report_url`, `remark`, `content_hash`, `create_time`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (87,91,'RT20261008627475','B-RB-1791463869199-X','ET-RB-1791463869199-A','S-RB-1791463869199-A','瘦肉精快速检测','2026-10-08 20:51:10','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',1,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 1, \"batchNo\": \"B-RB-1791463869199-X\", \"earTagNo\": \"ET-RB-1791463869199-A\", \"operator\": \"赵检测\", \"sampleNo\": \"S-RB-1791463869199-A\", \"testTime\": \"2026-10-08 20:51:10\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:51:11',0,NULL,NULL,NULL),(88,92,'RT20261008468561','B-RB-1791463869199-Y','ET-RB-1791463869199-B','S-RB-1791463869199-B','瘦肉精快速检测','2026-10-08 20:51:10','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',0,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 0, \"batchNo\": \"B-RB-1791463869199-Y\", \"earTagNo\": \"ET-RB-1791463869199-B\", \"operator\": \"赵检测\", \"sampleNo\": \"S-RB-1791463869199-B\", \"testTime\": \"2026-10-08 20:51:10\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:51:11',0,NULL,NULL,NULL),(89,93,'RT20261008247623','B-RB-1791463869199-Y','ET-RB-1791463869199-C','S-RB-1791463869199-C','瘦肉精快速检测','2026-10-08 20:51:10','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',1,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 1, \"batchNo\": \"B-RB-1791463869199-Y\", \"earTagNo\": \"ET-RB-1791463869199-C\", \"operator\": \"赵检测\", \"sampleNo\": \"S-RB-1791463869199-C\", \"testTime\": \"2026-10-08 20:51:10\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:51:11',0,NULL,NULL,NULL),(90,95,'RT20261008377409','B-PSG-1791463935600-E','ET-PSG-1791463935600-E','S-PSG-1791463935600-E','瘦肉精快速检测','2026-10-08 20:52:16','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',1,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 1, \"batchNo\": \"B-PSG-1791463935600-E\", \"earTagNo\": \"ET-PSG-1791463935600-E\", \"operator\": \"赵检测\", \"sampleNo\": \"S-PSG-1791463935600-E\", \"testTime\": \"2026-10-08 20:52:16\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:52:16',0,NULL,NULL,NULL),(91,96,'RT20261008575977','B-PSG-1791463935600-F','ET-PSG-1791463935600-F','S-PSG-1791463935600-F','瘦肉精快速检测','2026-10-08 20:52:16','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',1,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 1, \"batchNo\": \"B-PSG-1791463935600-F\", \"earTagNo\": \"ET-PSG-1791463935600-F\", \"operator\": \"赵检测\", \"sampleNo\": \"S-PSG-1791463935600-F\", \"testTime\": \"2026-10-08 20:52:16\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:52:16',0,NULL,NULL,NULL),(92,97,'RT20261008431292','B-PSG-1791463935600-G','ET-PSG-1791463935600-G','S-PSG-1791463935600-G','瘦肉精快速检测','2026-10-08 20:52:16','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',1,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 1, \"batchNo\": \"B-PSG-1791463935600-G\", \"earTagNo\": \"ET-PSG-1791463935600-G\", \"operator\": \"赵检测\", \"sampleNo\": \"S-PSG-1791463935600-G\", \"testTime\": \"2026-10-08 20:52:16\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:52:17',0,NULL,NULL,NULL),(93,98,'RT20261008395411','B-PSG-1791463935600-H','ET-PSG-1791463935600-H','S-PSG-1791463935600-H','瘦肉精快速检测','2026-10-08 20:52:16','胶体金快速检测卡','ç›é…¸å…‹ä¼¦ç‰¹ç½—+èŽ±å…‹å¤šå·´èƒº+æ²™ä¸èƒºé†‡','尿液',1,2,NULL,'赵检测','DEVICE','READER-001','{\"result\": 1, \"batchNo\": \"B-PSG-1791463935600-H\", \"earTagNo\": \"ET-PSG-1791463935600-H\", \"operator\": \"赵检测\", \"sampleNo\": \"S-PSG-1791463935600-H\", \"testTime\": \"2026-10-08 20:52:16\", \"samplePart\": \"尿液\", \"testMethod\": \"胶体金快速检测卡\"}',NULL,NULL,NULL,NULL,'2026-10-08 20:52:17',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `ractopamine_test` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:56:58
