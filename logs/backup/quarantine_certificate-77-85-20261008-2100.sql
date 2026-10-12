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
-- Dumping data for table `quarantine_certificate`
--
-- WHERE:  id >= 77

LOCK TABLES `quarantine_certificate` WRITE;
/*!40000 ALTER TABLE `quarantine_certificate` DISABLE KEYS */;
INSERT INTO `quarantine_certificate` (`id`, `pig_id`, `cert_no`, `issue_org`, `issue_time`, `valid_until`, `inspector`, `cert_type`, `file_id`, `ca_signature`, `source`, `source_ref`, `raw_payload`, `content_hash`, `create_time`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (77,91,'QC-RB-1791463869199-A','回归验证动物卫生监督所','2026-10-08 20:51:09','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791463869199-A\", \"earTagNo\": \"ET-RB-1791463869199-A\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:51:09\", \"validUntil\": \"2027-10-08\"}','8bb97532b44039fa0a56c7dfdc8614cd43dcbc991813d0c75bc2380352ccfd1e','2026-10-08 20:51:09',0,NULL,NULL,NULL),(78,92,'QC-RB-1791463869199-B','回归验证动物卫生监督所','2026-10-08 20:51:09','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791463869199-B\", \"earTagNo\": \"ET-RB-1791463869199-B\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:51:09\", \"validUntil\": \"2027-10-08\"}','5bd1346cdca80a8d116c123830af6c3943f403e8791833b418b776f227a22566','2026-10-08 20:51:10',0,NULL,NULL,NULL),(79,93,'QC-RB-1791463869199-C','回归验证动物卫生监督所','2026-10-08 20:51:09','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791463869199-C\", \"earTagNo\": \"ET-RB-1791463869199-C\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:51:09\", \"validUntil\": \"2027-10-08\"}','673719f255dea4772408aeeddb15aabd5fd0a7b586ab5e680c90f4296869c45d','2026-10-08 20:51:10',0,NULL,NULL,NULL),(80,94,'QC-RB-1791463869199-D','回归验证动物卫生监督所','2026-10-08 20:51:10','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791463869199-D\", \"earTagNo\": \"ET-RB-1791463869199-D\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:51:10\", \"validUntil\": \"2027-10-08\"}','b5c213f8ab47476240f0146851fa048f88f6c2249316601dac06814edc786672','2026-10-08 20:51:10',0,NULL,NULL,NULL),(81,95,'QC-PSG-1791463935600-E','宰前门槛验证动物卫生监督所','2026-10-08 20:52:15','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-PSG-1791463935600-E\", \"earTagNo\": \"ET-PSG-1791463935600-E\", \"issueOrg\": \"宰前门槛验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:52:15\", \"validUntil\": \"2027-10-08\"}','24147c1757d2913cae743b60cebad3d6b1984bd2a42b389057826050d39b5dac','2026-10-08 20:52:16',0,NULL,NULL,NULL),(82,96,'QC-PSG-1791463935600-F','宰前门槛验证动物卫生监督所','2026-10-08 20:52:16','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-PSG-1791463935600-F\", \"earTagNo\": \"ET-PSG-1791463935600-F\", \"issueOrg\": \"宰前门槛验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:52:16\", \"validUntil\": \"2027-10-08\"}','56a32d0ae8275f156522ca5191eecf34f676f9fc5db98fc7687ffd17cd4ba082','2026-10-08 20:52:16',0,NULL,NULL,NULL),(83,97,'QC-PSG-1791463935600-G','宰前门槛验证动物卫生监督所','2026-10-08 20:52:16','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-PSG-1791463935600-G\", \"earTagNo\": \"ET-PSG-1791463935600-G\", \"issueOrg\": \"宰前门槛验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:52:16\", \"validUntil\": \"2027-10-08\"}','f01e95ad0f9a7f9e4ef31d40b332799a11d097cb602fb30d1fdbe47d757ad74f','2026-10-08 20:52:16',0,NULL,NULL,NULL),(84,98,'QC-PSG-1791463935600-H','宰前门槛验证动物卫生监督所','2026-10-08 20:52:16','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-PSG-1791463935600-H\", \"earTagNo\": \"ET-PSG-1791463935600-H\", \"issueOrg\": \"宰前门槛验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:52:16\", \"validUntil\": \"2027-10-08\"}','1bf877b537a4e7eba12e86f2865aeb632f803527bae3aef98aab328f34a6b31b','2026-10-08 20:52:17',0,NULL,NULL,NULL),(85,33,'QC-VERIFY-1791464066387','回归验证签发机构','2026-10-08 20:54:26','2027-04-06','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-VERIFY-1791464066387\", \"earTagNo\": \"ET-DEMO-1791193435869\", \"issueOrg\": \"回归验证签发机构\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:54:26\", \"validUntil\": \"2027-04-06\"}','c1562b035711564fc43719e78685033474c60884314bac49350f6f9a6ed3f5bb','2026-10-08 20:54:26',1,'测试数据清理：扫码登记通道回归脚本产生','系统管理员','2026-10-08 20:54:26');
/*!40000 ALTER TABLE `quarantine_certificate` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:56:53
