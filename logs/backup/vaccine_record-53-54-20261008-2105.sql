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
-- Dumping data for table `vaccine_record`
--
-- WHERE:  id >= 53

LOCK TABLES `vaccine_record` WRITE;
/*!40000 ALTER TABLE `vaccine_record` DISABLE KEYS */;
INSERT INTO `vaccine_record` (`id`, `pig_id`, `vaccine_name`, `batch_no`, `manufacturer`, `inject_time`, `dosage`, `inject_site`, `operator`, `source`, `source_ref`, `raw_payload`, `file_ids`, `create_time`, `content_hash`, `void_flag`, `void_reason`, `void_operator`, `void_time`) VALUES (53,31,'回归-带凭证疫苗','VF-OK-1791464278832',NULL,'2026-10-08 20:57:58','2ml',NULL,'刘兽医','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"fileIds\": [\"bf6f5ee53b5940c3b84154e4f64258b2\"], \"earTagNo\": \"ET-DEMO-1791185982174\", \"operator\": \"刘兽医\", \"vaccineName\": \"回归-带凭证疫苗\", \"vaccineBatchNo\": \"VF-OK-1791464278832\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 20:57:59',NULL,0,NULL,NULL,NULL),(54,31,'回归-人工补录疫苗','VF-MANUAL-1791464278923',NULL,'2026-10-08 20:57:59','2ml',NULL,'王复核','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"earTagNo\": \"ET-DEMO-1791185982174\", \"vaccineName\": \"回归-人工补录疫苗\", \"vaccineBatchNo\": \"VF-MANUAL-1791464278923\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 20:57:59',NULL,0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `vaccine_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:58:50
