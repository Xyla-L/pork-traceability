-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_common
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
-- Table structure for table `sys_org`
--

DROP TABLE IF EXISTS `sys_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0',
  `type` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `manager` varchar(32) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `address` varchar(256) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=1016 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织机构';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_org`
--

LOCK TABLES `sys_org` WRITE;
/*!40000 ALTER TABLE `sys_org` DISABLE KEYS */;
INSERT INTO `sys_org` VALUES (1,0,'supervisor','市市场监督管理局','王监管','010-12345678','北京市朝阳区监管大道100号','顶层监管机构','2026-09-16 19:55:18','2026-09-16 19:55:18'),(2,1,'farm','示范生态养殖场','李牧','13800000001','北京市顺义区示范路1号','合作养殖基地','2026-09-16 19:55:18','2026-09-16 19:55:18'),(3,1,'slaughter','示范定点屠宰场','张场长','010-87654321','北京市通州区屠宰路8号','定点屠宰企业','2026-09-16 19:55:18','2026-09-16 19:55:18'),(4,1,'distribution','冷链配送中心','陈经理','010-55667788','北京市大兴区冷链物流园','中央仓配中心','2026-09-16 19:55:18','2026-09-16 19:55:18'),(1001,4,'retail','安心生鲜门店','孙店长','13800000003','北京市海淀区中关村大街99号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1002,4,'retail','顺鑫生鲜门店','周店长','13800000012','北京市东城区东直门内大街12号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1003,4,'retail','绿康生鲜门店','吴店长','13800000013','北京市西城区西直门外大街18号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1004,4,'retail','金牧生鲜门店','郑店长','13800000014','北京市丰台区南三环西路20号','加盟店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1005,4,'retail','永发生鲜门店','王店长','13800000015','北京市朝阳区建国路88号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1006,4,'retail','昌盛生鲜门店','冯店长','13800000016','北京市石景山区石景山路22号','加盟店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1007,4,'retail','兴农生鲜门店','蒋店长','13800000017','北京市通州区新华大街33号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1008,4,'retail','惠农生鲜门店','韩店长','13800000018','北京市昌平区政府街55号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1009,4,'retail','福田生鲜门店','杨店长','13800000019','北京市顺义区光明街道7号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1010,4,'retail','大众生鲜门店','朱店长','13800000020','北京市大兴区兴华大街9号','加盟店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1011,4,'retail','利农生鲜门店','徐店长','13800000021','北京市房山区良乡大街11号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1012,4,'retail','安康生鲜门店','黄店长','13800000022','北京市门头沟区新桥大街15号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1013,4,'retail','丰禾生鲜门店','彭店长','13800000023','北京市怀柔区青春路21号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41'),(1014,4,'retail','宏图生鲜门店','鲁店长','13800000024','北京市密云区鼓楼东大街6号','直营店','2026-09-16 22:00:41','2026-09-16 22:00:41');
/*!40000 ALTER TABLE `sys_org` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-04 18:22:29
