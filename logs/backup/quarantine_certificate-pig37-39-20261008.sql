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
-- WHERE:  pig_id IN (37,38,39)

LOCK TABLES `quarantine_certificate` WRITE;
/*!40000 ALTER TABLE `quarantine_certificate` DISABLE KEYS */;
INSERT INTO `quarantine_certificate` VALUES (23,37,'QC-RB-1791451713965-A','回归验证动物卫生监督所','2026-10-08 17:28:34','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791451713965-A\", \"earTagNo\": \"ET-RB-1791451713965-A\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 17:28:34\", \"validUntil\": \"2027-10-08\"}','8e1d6e06ae98920efd857da84d21c96fa7d829c0099323edba39ad338f31cbe6','2026-10-08 17:28:34',0,NULL,NULL,NULL),(24,38,'QC-RB-1791451713965-B','回归验证动物卫生监督所','2026-10-08 17:28:34','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791451713965-B\", \"earTagNo\": \"ET-RB-1791451713965-B\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 17:28:34\", \"validUntil\": \"2027-10-08\"}','ecb95dabc2065c733a73b093989bb714fec4a11540269b76622b67ecf309fa85','2026-10-08 17:28:35',0,NULL,NULL,NULL),(25,39,'QC-RB-1791451713965-C','回归验证动物卫生监督所','2026-10-08 17:28:35','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791451713965-C\", \"earTagNo\": \"ET-RB-1791451713965-C\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 17:28:35\", \"validUntil\": \"2027-10-08\"}','fdf23333fb33796e251a6f0161f3c687a77291f7b227ec364b2258277d332dbe','2026-10-08 17:28:36',0,NULL,NULL,NULL);
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

-- Dump completed on 2026-10-08 17:29:05
