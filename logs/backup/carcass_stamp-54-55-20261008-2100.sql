-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_slaughter
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
-- Dumping data for table `carcass_stamp`
--
-- WHERE:  id >= 54

LOCK TABLES `carcass_stamp` WRITE;
/*!40000 ALTER TABLE `carcass_stamp` DISABLE KEYS */;
INSERT INTO `carcass_stamp` (`id`, `pig_id`, `batch_no`, `ear_tag_no`, `carcass_no`, `stamp_no`, `stamp_type`, `stamp_time`, `stamp_position`, `veterinary`, `e_signature`, `status`, `remark`, `content_hash`, `source`, `source_ref`, `raw_payload`, `create_time`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (54,91,'B-RB-1791463869199-X','ET-RB-1791463869199-A','CS-RB-1791463869199-A','ST20261008721684','检疫合格章','2026-10-08 20:51:11','胴体两侧臀部','李兽医',NULL,1,NULL,'27e3a1b299facb6695bd20d6f5dce46049a8fcb65475c233d0bc2bbc1cd30962','DEVICE','STAMPER-001','{\"batchNo\": \"B-RB-1791463869199-X\", \"earTagNo\": \"ET-RB-1791463869199-A\", \"carcassNo\": \"CS-RB-1791463869199-A\", \"veterinary\": \"李兽医\"}','2026-10-08 20:51:11',0,NULL,NULL,NULL),(55,97,'B-PSG-1791463935600-G','ET-PSG-1791463935600-G','CS-PSG-1791463935600-G','ST20261008037197','检疫合格章','2026-10-08 20:52:18','胴体两侧臀部','李兽医',NULL,1,NULL,'68622dac1a514ca87e0894bca78aea0e913fa9624343def7e4231f72b178f23c','DEVICE','STAMPER-001','{\"batchNo\": \"B-PSG-1791463935600-G\", \"earTagNo\": \"ET-PSG-1791463935600-G\", \"carcassNo\": \"CS-PSG-1791463935600-G\", \"veterinary\": \"李兽医\"}','2026-10-08 20:52:18',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `carcass_stamp` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:57:00
