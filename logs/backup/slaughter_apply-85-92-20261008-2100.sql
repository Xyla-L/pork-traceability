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
-- Dumping data for table `slaughter_apply`
--
-- WHERE:  id >= 85

LOCK TABLES `slaughter_apply` WRITE;
/*!40000 ALTER TABLE `slaughter_apply` DISABLE KEYS */;
INSERT INTO `slaughter_apply` (`id`, `pig_id`, `apply_no`, `apply_time`, `weight_kg`, `target_slaughterhouse`, `approval_status`, `approval_time`, `approver`, `reject_reason`, `source`, `source_ref`, `raw_payload`, `create_time`) VALUES (85,91,'SA20261008229568','2026-10-08 20:51:09',118.2,'回归验证屠宰场',1,'2026-10-08 20:51:09','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-RB-1791463869199-A\", \"weightKg\": 118.2, \"weighTime\": \"2026-10-08 20:51:09\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','2026-10-08 20:51:09'),(86,92,'SA20261008506840','2026-10-08 20:51:09',118.2,'回归验证屠宰场',1,'2026-10-08 20:51:10','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-RB-1791463869199-B\", \"weightKg\": 118.2, \"weighTime\": \"2026-10-08 20:51:09\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','2026-10-08 20:51:10'),(87,93,'SA20261008924364','2026-10-08 20:51:10',118.2,'回归验证屠宰场',1,'2026-10-08 20:51:10','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-RB-1791463869199-C\", \"weightKg\": 118.2, \"weighTime\": \"2026-10-08 20:51:10\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','2026-10-08 20:51:10'),(88,94,'SA20261008912943','2026-10-08 20:51:10',118.2,'回归验证屠宰场',1,'2026-10-08 20:51:11','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-RB-1791463869199-D\", \"weightKg\": 118.2, \"weighTime\": \"2026-10-08 20:51:10\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','2026-10-08 20:51:10'),(89,95,'SA20261008664647','2026-10-08 20:52:15',119.4,'宰前门槛验证屠宰场',1,'2026-10-08 20:52:16','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-PSG-1791463935600-E\", \"weightKg\": 119.4, \"weighTime\": \"2026-10-08 20:52:15\", \"targetSlaughterhouse\": \"宰前门槛验证屠宰场\"}','2026-10-08 20:52:16'),(90,96,'SA20261008845990','2026-10-08 20:52:16',119.4,'宰前门槛验证屠宰场',1,'2026-10-08 20:52:16','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-PSG-1791463935600-F\", \"weightKg\": 119.4, \"weighTime\": \"2026-10-08 20:52:16\", \"targetSlaughterhouse\": \"宰前门槛验证屠宰场\"}','2026-10-08 20:52:16'),(91,97,'SA20261008378898','2026-10-08 20:52:16',119.4,'宰前门槛验证屠宰场',1,'2026-10-08 20:52:17','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-PSG-1791463935600-G\", \"weightKg\": 119.4, \"weighTime\": \"2026-10-08 20:52:16\", \"targetSlaughterhouse\": \"宰前门槛验证屠宰场\"}','2026-10-08 20:52:17'),(92,98,'SA20261008108804','2026-10-08 20:52:16',119.4,'宰前门槛验证屠宰场',1,'2026-10-08 20:52:17','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-PSG-1791463935600-H\", \"weightKg\": 119.4, \"weighTime\": \"2026-10-08 20:52:16\", \"targetSlaughterhouse\": \"宰前门槛验证屠宰场\"}','2026-10-08 20:52:17');
/*!40000 ALTER TABLE `slaughter_apply` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:56:51
