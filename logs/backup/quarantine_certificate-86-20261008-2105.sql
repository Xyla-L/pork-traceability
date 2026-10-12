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
-- WHERE:  id >= 86

LOCK TABLES `quarantine_certificate` WRITE;
/*!40000 ALTER TABLE `quarantine_certificate` DISABLE KEYS */;
INSERT INTO `quarantine_certificate` (`id`, `pig_id`, `cert_no`, `issue_org`, `issue_time`, `valid_until`, `inspector`, `cert_type`, `file_id`, `ca_signature`, `source`, `source_ref`, `raw_payload`, `content_hash`, `create_time`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (86,99,'QC-GATE-1791464273791','回归验证动物卫生监督所','2026-10-08 20:57:53','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-GATE-1791464273791\", \"earTagNo\": \"ET-GATE-1791464273791\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 20:57:53\", \"validUntil\": \"2027-10-08\"}','012e48b139a824c6eacbfc3b74f535e51d12965d2ed8d7bb75d821f9d12bef65','2026-10-08 20:57:54',0,NULL,NULL,NULL);
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

-- Dump completed on 2026-10-08 20:58:47
