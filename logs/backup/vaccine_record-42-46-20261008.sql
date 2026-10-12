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
-- WHERE:  id BETWEEN 42 AND 46

/*!40000 ALTER TABLE `vaccine_record` DISABLE KEYS */;
INSERT INTO `vaccine_record` VALUES (42,34,'回归-带凭证疫苗','VF-OK-1791430393195',NULL,'2026-10-08 11:33:13','2ml',NULL,'刘兽医','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"fileIds\": [\"bf6f5ee53b5940c3b84154e4f64258b2\"], \"earTagNo\": \"ET-DEMO-1791194024670\", \"operator\": \"刘兽医\", \"vaccineName\": \"回归-带凭证疫苗\", \"vaccineBatchNo\": \"VF-OK-1791430393195\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 11:33:13',NULL,1,'测试数据清理：功能回归脚本产生的演示残留','admin','2026-10-08 11:44:46'),(43,34,'回归-人工补录疫苗','VF-MANUAL-1791430393533',NULL,'2026-10-08 11:33:14','2ml',NULL,'王复核','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"earTagNo\": \"ET-DEMO-1791194024670\", \"vaccineName\": \"回归-人工补录疫苗\", \"vaccineBatchNo\": \"VF-MANUAL-1791430393533\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 11:33:14',NULL,1,'测试数据清理：功能回归脚本产生的演示残留','admin','2026-10-08 11:44:47'),(44,34,'回归-带凭证疫苗','VF-OK-1791430440457',NULL,'2026-10-08 11:34:00','2ml',NULL,'刘兽医','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"fileIds\": [\"bf6f5ee53b5940c3b84154e4f64258b2\"], \"earTagNo\": \"ET-DEMO-1791194024670\", \"operator\": \"刘兽医\", \"vaccineName\": \"回归-带凭证疫苗\", \"vaccineBatchNo\": \"VF-OK-1791430440457\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 11:34:01',NULL,1,'测试数据清理：功能回归脚本产生的演示残留','admin','2026-10-08 11:44:47'),(45,34,'回归-人工补录疫苗','VF-MANUAL-1791430440594',NULL,'2026-10-08 11:34:01','2ml',NULL,'王复核','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"earTagNo\": \"ET-DEMO-1791194024670\", \"vaccineName\": \"回归-人工补录疫苗\", \"vaccineBatchNo\": \"VF-MANUAL-1791430440594\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 11:34:01',NULL,1,'测试数据清理：功能回归脚本产生的演示残留','admin','2026-10-08 11:44:47'),(46,34,'猪瘟兔化弱毒疫苗','VAC-VERIFY-1791430545539-OK',NULL,'2026-10-08 11:35:45','2ml',NULL,'刘兽医','DEVICE','INJ-001','{\"dosage\": \"2ml\", \"fileIds\": [\"bf6f5ee53b5940c3b84154e4f64258b2\"], \"earTagNo\": \"ET-DEMO-1791194024670\", \"operator\": \"刘兽医\", \"vaccineName\": \"猪瘟兔化弱毒疫苗\", \"vaccineBatchNo\": \"VAC-VERIFY-1791430545539-OK\"}','[\"bf6f5ee53b5940c3b84154e4f64258b2\"]','2026-10-08 11:35:46',NULL,1,'测试数据清理：功能回归脚本产生的演示残留','admin','2026-10-08 11:44:47');
/*!40000 ALTER TABLE `vaccine_record` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 11:44:56
