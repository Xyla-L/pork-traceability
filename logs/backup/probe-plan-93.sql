-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_distribution
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
-- Table structure for table `delivery_plan`
--

DROP TABLE IF EXISTS `delivery_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_no` varchar(32) NOT NULL COMMENT '计划号 DP+yyyyMMdd+3位当日流水；当日全局排流水，含已取消的不复用',
  `plan_date` date NOT NULL COMMENT '计划发货日',
  `store_id` bigint NOT NULL COMMENT '目的地门店 id（取自机构树 type=retail）',
  `store_name` varchar(128) NOT NULL COMMENT '目的地门店名（冗余，展示免联表）',
  `vehicle_no` varchar(16) DEFAULT NULL COMMENT '车牌号；计划阶段可空（车还没定），生成运单时必填',
  `vehicle_type` varchar(32) DEFAULT NULL COMMENT '车辆类型：冷藏车/冷冻车/保温车',
  `refrigeration` varchar(64) DEFAULT NULL COMMENT '制冷方式：机械制冷/蓄冷板/液氮',
  `driver_name` varchar(32) DEFAULT NULL COMMENT '司机姓名（实名）；计划阶段可空',
  `driver_phone` varchar(20) DEFAULT NULL COMMENT '司机电话',
  `origin` varchar(256) DEFAULT NULL COMMENT '起运地（由货物批次带出，可改）',
  `planned_depart` datetime DEFAULT NULL COMMENT '计划发车',
  `planned_arrive` datetime DEFAULT NULL COMMENT '计划到达',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=待发货 2=已生成运单 3=已取消',
  `plan_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '计划来源 MANUAL=内部调度/ORDER=订单转入（口径见 PlanSource）',
  `order_no` varchar(64) DEFAULT NULL COMMENT '来源订单号（订单转入时填；订单权威副本在外部系统，本系统只留号以便回溯）',
  `item_count` int NOT NULL DEFAULT '0' COMMENT '明细条数快照（= 要发运的分割批次数）',
  `total_weight_kg` decimal(10,2) DEFAULT NULL COMMENT '计划总重(kg)快照 = 各明细分割批次重量合计',
  `transport_id` bigint DEFAULT NULL COMMENT '生成运单后回填 cold_chain_transport.id',
  `transport_no` varchar(32) DEFAULT NULL COMMENT '生成运单后回填运单号（冗余，展示免联表）',
  `transport_count` int NOT NULL DEFAULT '0' COMMENT '已生成的运单张数（分车后 >1）',
  `generated_time` datetime DEFAULT NULL COMMENT '运单生成时刻',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `operator` varchar(32) DEFAULT NULL COMMENT '制单人（实名）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_no` (`plan_no`),
  KEY `idx_status` (`status`),
  KEY `idx_plan_date` (`plan_date`),
  KEY `idx_store` (`store_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=94 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货计划：一个目的地+一个车次+N个待发分割批次，运单由它一键生成';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `delivery_plan`
--
-- WHERE:  id=93

LOCK TABLES `delivery_plan` WRITE;
/*!40000 ALTER TABLE `delivery_plan` DISABLE KEYS */;
INSERT INTO `delivery_plan` VALUES (93,'DP20261010-002','2026-10-10',1001,'安心生鲜门店',NULL,NULL,NULL,NULL,NULL,'示范定点屠宰场',NULL,NULL,1,'ORDER','SO20261010-154756',1,11.00,NULL,NULL,0,NULL,'【订单转入】订单号 SO20261010-154756；订单备注：探针：新单号首次转入','订单转入（ORDER-TERM-001）','2026-10-10 15:47:57');
/*!40000 ALTER TABLE `delivery_plan` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-10 15:48:03
-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: db_distribution
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
-- Table structure for table `delivery_plan_item`
--

DROP TABLE IF EXISTS `delivery_plan_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_plan_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL COMMENT '所属计划 delivery_plan.id',
  `split_batch_id` bigint NOT NULL COMMENT '要发运的分割批次 split_batch.id',
  `batch_no` varchar(32) NOT NULL COMMENT '分割批次号（冗余，展示免联表）',
  `part_code` varchar(16) DEFAULT NULL COMMENT '部位编码（冗余）',
  `product_name` varchar(64) DEFAULT NULL COMMENT '部位名（冗余）',
  `weight_kg` decimal(8,2) DEFAULT NULL COMMENT '该批次重量(kg)，制单时快照',
  `shipped` tinyint NOT NULL DEFAULT '0' COMMENT '是否已发运 0=待装车 1=已随某张运单发运',
  `transport_id` bigint DEFAULT NULL COMMENT '实际发运这张明细的运单 id（分车时同一计划会指向不同运单）',
  `shipped_time` datetime DEFAULT NULL COMMENT '该明细实际发运时刻',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_split` (`plan_id`,`split_batch_id`) COMMENT '同一计划里同一分割批次只能出现一次',
  KEY `idx_plan` (`plan_id`),
  KEY `idx_split` (`split_batch_id`),
  KEY `idx_shipped` (`shipped`,`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=189 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货计划明细：本次要装车发运的分割批次清单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `delivery_plan_item`
--
-- WHERE:  plan_id=93

LOCK TABLES `delivery_plan_item` WRITE;
/*!40000 ALTER TABLE `delivery_plan_item` DISABLE KEYS */;
INSERT INTO `delivery_plan_item` VALUES (188,93,97,'SP20261009-001-04','P06','五花肉',11.00,0,NULL,NULL,'2026-10-10 15:47:57');
/*!40000 ALTER TABLE `delivery_plan_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-10 15:48:04
