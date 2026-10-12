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
-- Dumping data for table `ingest_staging`
--
-- WHERE:  id BETWEEN 355 AND 362

LOCK TABLES `ingest_staging` WRITE;
/*!40000 ALTER TABLE `ingest_staging` DISABLE KEYS */;
INSERT INTO `ingest_staging` VALUES (355,'TAG','TAG-W001','DEMO-1791441140105','2026-10-08 14:32:20','2026-10-08 14:32:20','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-GATE-1791441139752\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}','TAG-W001',2,'pig_individual',35,NULL,NULL,NULL,NULL,'2026-10-08 14:32:20'),(356,'QUARANTINE','QSCAN-001','DEMO-1791441140392','2026-10-08 14:32:20','2026-10-08 14:32:20','{\"certNo\": \"QC-GATE-1791441139752\", \"earTagNo\": \"ET-GATE-1791441139752\", \"issueOrg\": \"回归验证动物卫生监督所\", \"inspector\": \"王官方兽医\", \"issueTime\": \"2026-10-08 14:32:20\", \"validUntil\": \"2027-10-08\"}','QSCAN-001',2,'quarantine_certificate',21,NULL,NULL,NULL,NULL,'2026-10-08 14:32:20'),(357,'ENTRY','GATE-001','DEMO-1791441140728','2026-10-08 14:32:21','2026-10-08 14:32:21','{\"weight\": 116.5, \"batchNo\": \"B-GATE-1791441139752-A\", \"earTagNo\": \"ET-GATE-1791441139752\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-GATE-1791441139752\"}','GATE-001',3,'entry_inspection',NULL,'该猪（耳标 ET-GATE-1791441139752）没有出栏申报记录，未经出栏审批通过不得入场查验',NULL,NULL,NULL,'2026-10-08 14:32:21'),(358,'EXIT_APPLY','EXITSCALE-001','DEMO-1791441140940','2026-10-08 14:32:21','2026-10-08 14:32:21','{\"earTagNo\": \"ET-GATE-1791441139752\", \"weightKg\": 116.8, \"weighTime\": \"2026-10-08 14:32:20\", \"targetSlaughterhouse\": \"回归验证屠宰场\"}','EXITSCALE-001',2,'slaughter_apply',29,NULL,NULL,NULL,NULL,'2026-10-08 14:32:21'),(359,'ENTRY','GATE-001','DEMO-1791441141048','2026-10-08 14:32:21','2026-10-08 14:32:21','{\"weight\": 116.5, \"batchNo\": \"B-GATE-1791441139752-B\", \"earTagNo\": \"ET-GATE-1791441139752\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-GATE-1791441139752\"}','GATE-001',3,'entry_inspection',NULL,'该猪（耳标 ET-GATE-1791441139752）出栏申报仍在等待审批，未经出栏审批通过不得入场查验',NULL,NULL,NULL,'2026-10-08 14:32:21'),(360,'ENTRY','GATE-001','DEMO-1791441141132','2026-10-08 14:32:21','2026-10-08 14:32:21','{\"weight\": 116.5, \"batchNo\": \"B-GATE-1791441139752-C\", \"earTagNo\": \"ET-GATE-1791441139752\", \"quarantineCert\": \"QC-GATE-1791441139752\"}','GATE-001',1,'entry_inspection',NULL,'该猪（耳标 ET-GATE-1791441139752）出栏申报仍在等待审批，未经出栏审批通过不得入场查验','2026-10-08 14:32:21','系统管理员','回归验证：试试人工放行能不能绕过出栏审批｜人工补齐：查验员=张查验','2026-10-08 14:32:21'),(361,'ENTRY','GATE-001','DEMO-1791441141296','2026-10-08 14:32:21','2026-10-08 14:32:21','{\"weight\": 116.5, \"batchNo\": \"B-GATE-1791441139752-D\", \"earTagNo\": \"ET-GATE-1791441139752\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-GATE-1791441139752\"}','GATE-001',2,'entry_inspection',35,NULL,NULL,NULL,NULL,'2026-10-08 14:32:21'),(362,'ENTRY','GATE-001','DEMO-1791441141475','2026-10-08 14:32:21','2026-10-08 14:32:21','{\"weight\": 110, \"batchNo\": \"B-GATE-1791441139752-E\", \"earTagNo\": \"ET-TEST-0007\", \"inspector\": \"张查验\", \"quarantineCert\": \"QC-TEST-0007\"}','GATE-001',3,'entry_inspection',NULL,'该猪（耳标 ET-TEST-0007）出栏申报已被驳回，未经出栏审批通过不得入场查验',NULL,NULL,NULL,'2026-10-08 14:32:21');
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

-- Dump completed on 2026-10-08 14:33:08
