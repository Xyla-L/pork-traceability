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
-- Dumping data for table `source_fallback_unlock`
--
-- WHERE:  id >= 18

LOCK TABLES `source_fallback_unlock` WRITE;
/*!40000 ALTER TABLE `source_fallback_unlock` DISABLE KEYS */;
INSERT INTO `source_fallback_unlock` (`id`, `stage_code`, `reason`, `applicant_id`, `applicant_name`, `apply_time`, `expire_time`, `closed`, `close_operator`, `close_reason`, `close_time`) VALUES (18,'BATCH','回归验证：验证胴体批次对瘦肉精受控猪的拦截','1','系统管理员','2026-10-08 20:51:12','2026-10-08 22:51:12',0,NULL,NULL,NULL),(19,'QUARANTINE','链路验证：确认产地检疫出证的人工入口可正常出证并上链','1','系统管理员','2026-10-08 20:54:24','2026-10-08 22:54:24',0,NULL,NULL,NULL),(20,'QUARANTINE','回归验证清理：作废脚本登记的测试检疫证','1','系统管理员','2026-10-08 20:54:27','2026-10-08 22:54:27',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `source_fallback_unlock` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:57:05
