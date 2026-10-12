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
-- Dumping data for table `pig_individual`
--
-- WHERE:  id >= 91

LOCK TABLES `pig_individual` WRITE;
/*!40000 ALTER TABLE `pig_individual` DISABLE KEYS */;
INSERT INTO `pig_individual` (`id`, `ear_tag_no`, `farm_id`, `breed`, `birth_date`, `gender`, `pen_no`, `source`, `status`, `data_source`, `source_ref`, `raw_payload`, `deleted`, `content_hash`, `create_time`, `update_time`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (91,'ET-RB-1791463869199-A',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',3,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791463869199-A\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:51:09','2026-10-08 20:51:11',0,NULL,NULL,NULL),(92,'ET-RB-1791463869199-B',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791463869199-B\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:51:10','2026-10-08 20:51:10',0,NULL,NULL,NULL),(93,'ET-RB-1791463869199-C',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791463869199-C\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:51:10','2026-10-08 20:51:10',0,NULL,NULL,NULL),(94,'ET-RB-1791463869199-D',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791463869199-D\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:51:10','2026-10-08 20:51:11',0,NULL,NULL,NULL),(95,'ET-PSG-1791463935600-E',14,'三元杂','2026-07-10',1,'宰前门槛验证舍','自繁',3,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"宰前门槛验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-PSG-1791463935600-E\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:52:16','2026-10-08 20:52:17',0,NULL,NULL,NULL),(96,'ET-PSG-1791463935600-F',14,'三元杂','2026-07-10',1,'宰前门槛验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"宰前门槛验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-PSG-1791463935600-F\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:52:16','2026-10-08 20:52:16',0,NULL,NULL,NULL),(97,'ET-PSG-1791463935600-G',14,'三元杂','2026-07-10',1,'宰前门槛验证舍','自繁',3,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"宰前门槛验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-PSG-1791463935600-G\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:52:16','2026-10-08 20:52:17',0,NULL,NULL,NULL),(98,'ET-PSG-1791463935600-H',14,'三元杂','2026-07-10',1,'宰前门槛验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"宰前门槛验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-PSG-1791463935600-H\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 20:52:17','2026-10-08 20:52:17',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `pig_individual` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:56:49
