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
-- WHERE:  id >= 93

LOCK TABLES `slaughter_apply` WRITE;
/*!40000 ALTER TABLE `slaughter_apply` DISABLE KEYS */;
INSERT INTO `slaughter_apply` (`id`, `pig_id`, `apply_no`, `apply_time`, `weight_kg`, `target_slaughterhouse`, `approval_status`, `approval_time`, `approver`, `reject_reason`, `source`, `source_ref`, `raw_payload`, `create_time`) VALUES (93,99,'SA20261008526736','2026-10-08 20:57:54',116.8,'回归验证屠宰场',1,'2026-10-08 20:57:54','系统管理员',NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-GATE-1791464273791\", \"weightKg\": 116.8, \"weighTime\": \"2026-10-08 20:57:54\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','2026-10-08 20:57:54'),(94,31,'SA20261008801012','2026-10-08 20:57:56',118.5,'回归验证屠宰场',0,NULL,NULL,NULL,'DEVICE','EXITSCALE-001','{\"earTagNo\": \"ET-DEMO-1791185982174\", \"weightKg\": 118.5, \"weighTime\": \"2026-10-08 20:57:56\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','2026-10-08 20:57:56'),(95,33,'SA20261008174303','2026-10-08 20:57:57',120.5,'回归验证屠宰场',1,'2026-10-08 20:57:57','系统管理员',NULL,'MANUAL','系统管理员',NULL,'2026-10-08 20:57:57');
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

-- Dump completed on 2026-10-08 20:58:45
