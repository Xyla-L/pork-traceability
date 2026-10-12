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
-- WHERE:  pig_id IN (40,41,42)

LOCK TABLES `quarantine_certificate` WRITE;
/*!40000 ALTER TABLE `quarantine_certificate` DISABLE KEYS */;
INSERT INTO `quarantine_certificate` VALUES (26,40,'QC-RB-1791451781089-A','回归验证动物卫生监督所','2026-10-08 17:29:41','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791451781089-A\", \"earTagNo\": \"ET-RB-1791451781089-A\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 17:29:41\", \"validUntil\": \"2027-10-08\"}','c6cacc4850c6911bb4e4ae13d1a648222e45f42c66b33dd382013b75b7af3ee5','2026-10-08 17:29:41',0,NULL,NULL,NULL),(27,41,'QC-RB-1791451781089-B','回归验证动物卫生监督所','2026-10-08 17:29:41','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791451781089-B\", \"earTagNo\": \"ET-RB-1791451781089-B\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 17:29:41\", \"validUntil\": \"2027-10-08\"}','5b0d435a0222f00faf16d07b7f9208460f483bb5b2d744712e1caf8d52b86017','2026-10-08 17:29:42',0,NULL,NULL,NULL),(28,42,'QC-RB-1791451781089-C','回归验证动物卫生监督所','2026-10-08 17:29:41','2027-10-08','王官方兽医','产地检疫',NULL,NULL,'DEVICE','QSCAN-001','{\"certNo\": \"QC-RB-1791451781089-C\", \"earTagNo\": \"ET-RB-1791451781089-C\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 17:29:41\", \"validUntil\": \"2027-10-08\"}','b3a5a4e2407de930b8885bda4e29a6d47cff6bc882263bdcd5dca9d64ba19c09','2026-10-08 17:29:42',0,NULL,NULL,NULL);
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

-- Dump completed on 2026-10-08 17:31:03
