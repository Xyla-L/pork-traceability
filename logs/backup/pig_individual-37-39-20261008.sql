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
-- WHERE:  id IN (37,38,39)

LOCK TABLES `pig_individual` WRITE;
/*!40000 ALTER TABLE `pig_individual` DISABLE KEYS */;
INSERT INTO `pig_individual` VALUES (37,'ET-RB-1791451713965-A',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',3,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791451713965-A\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 17:28:34','2026-10-08 17:28:36',0,NULL,NULL,NULL),(38,'ET-RB-1791451713965-B',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791451713965-B\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 17:28:35','2026-10-08 17:28:35',0,NULL,NULL,NULL),(39,'ET-RB-1791451713965-C',14,'三元杂','2026-07-10',1,'回归验证舍','自繁',2,'DEVICE','TAG-W001','{\"breed\": \"三元杂\", \"penNo\": \"回归验证舍\", \"gender\": 1, \"origin\": \"自繁\", \"earTagNo\": \"ET-RB-1791451713965-C\", \"farmName\": \"宏图养殖公司\", \"birthDate\": \"2026-07-10\"}',0,NULL,'2026-10-08 17:28:35','2026-10-08 17:28:36',0,NULL,NULL,NULL);
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

-- Dump completed on 2026-10-08 17:29:03
