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
-- Dumping data for table `entry_inspection`
--
-- WHERE:  id >= 93

LOCK TABLES `entry_inspection` WRITE;
/*!40000 ALTER TABLE `entry_inspection` DISABLE KEYS */;
INSERT INTO `entry_inspection` (`id`, `pig_id`, `batch_no`, `ear_tag_no`, `source_farm`, `arrive_time`, `weight`, `quarantine_cert`, `vehicle_no`, `health_check`, `cert_verified`, `abnormal_note`, `status`, `remark`, `content_hash`, `inspector`, `source`, `source_ref`, `raw_payload`, `file_ids`, `create_time`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (93,91,'B-RB-1791463869199-X','ET-RB-1791463869199-A','宏图养殖公司','2026-10-08 20:51:09',118.00,'QC-RB-1791463869199-A',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 118, \"batchNo\": \"B-RB-1791463869199-X\", \"earTagNo\": \"ET-RB-1791463869199-A\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-RB-1791463869199-A\"}',NULL,'2026-10-08 20:51:10',0,NULL,NULL,NULL),(94,92,'B-RB-1791463869199-Y','ET-RB-1791463869199-B','宏图养殖公司','2026-10-08 20:51:09',118.00,'QC-RB-1791463869199-B',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 118, \"batchNo\": \"B-RB-1791463869199-Y\", \"earTagNo\": \"ET-RB-1791463869199-B\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-RB-1791463869199-B\"}',NULL,'2026-10-08 20:51:10',0,NULL,NULL,NULL),(95,93,'B-RB-1791463869199-Y','ET-RB-1791463869199-C','宏图养殖公司','2026-10-08 20:51:10',118.00,'QC-RB-1791463869199-C',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 118, \"batchNo\": \"B-RB-1791463869199-Y\", \"earTagNo\": \"ET-RB-1791463869199-C\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-RB-1791463869199-C\"}',NULL,'2026-10-08 20:51:10',0,NULL,NULL,NULL),(96,94,'B-RB-1791463869199-Z','ET-RB-1791463869199-D','宏图养殖公司','2026-10-08 20:51:10',118.00,'QC-RB-1791463869199-D',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 118, \"batchNo\": \"B-RB-1791463869199-Z\", \"earTagNo\": \"ET-RB-1791463869199-D\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-RB-1791463869199-D\"}',NULL,'2026-10-08 20:51:11',0,NULL,NULL,NULL),(97,95,'B-PSG-1791463935600-E','ET-PSG-1791463935600-E','宏图养殖公司','2026-10-08 20:52:15',119.00,'QC-PSG-1791463935600-E',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 119, \"batchNo\": \"B-PSG-1791463935600-E\", \"earTagNo\": \"ET-PSG-1791463935600-E\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-PSG-1791463935600-E\"}',NULL,'2026-10-08 20:52:16',0,NULL,NULL,NULL),(98,96,'B-PSG-1791463935600-F','ET-PSG-1791463935600-F','宏图养殖公司','2026-10-08 20:52:16',119.00,'QC-PSG-1791463935600-F',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 119, \"batchNo\": \"B-PSG-1791463935600-F\", \"earTagNo\": \"ET-PSG-1791463935600-F\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-PSG-1791463935600-F\"}',NULL,'2026-10-08 20:52:16',0,NULL,NULL,NULL),(99,97,'B-PSG-1791463935600-G','ET-PSG-1791463935600-G','宏图养殖公司','2026-10-08 20:52:16',119.00,'QC-PSG-1791463935600-G',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 119, \"batchNo\": \"B-PSG-1791463935600-G\", \"earTagNo\": \"ET-PSG-1791463935600-G\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-PSG-1791463935600-G\"}',NULL,'2026-10-08 20:52:17',0,NULL,NULL,NULL),(100,98,'B-PSG-1791463935600-H','ET-PSG-1791463935600-H','宏图养殖公司','2026-10-08 20:52:16',119.00,'QC-PSG-1791463935600-H',NULL,1,1,NULL,1,NULL,NULL,'张查验','DEVICE','GATE-001','{\"weight\": 119, \"batchNo\": \"B-PSG-1791463935600-H\", \"earTagNo\": \"ET-PSG-1791463935600-H\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-PSG-1791463935600-H\"}',NULL,'2026-10-08 20:52:17',0,NULL,NULL,NULL);
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

-- Dump completed on 2026-10-08 20:56:54
