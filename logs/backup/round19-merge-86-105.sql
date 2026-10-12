
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
DROP TABLE IF EXISTS `cold_chain_transport`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cold_chain_transport` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transport_no` varchar(32) NOT NULL,
  `split_batch_id` bigint NOT NULL,
  `vehicle_no` varchar(16) NOT NULL,
  `vehicle_type` varchar(32) DEFAULT NULL,
  `refrigeration` varchar(64) DEFAULT NULL,
  `driver_name` varchar(32) DEFAULT NULL,
  `driver_phone` varchar(20) DEFAULT NULL,
  `driver_source` varchar(16) COMMENT '司机是怎么来的 CARD=刷卡/FACE=人脸/PLATE_INFERRED=车牌反查推定/MANUAL=人工录入',
  `driver_confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '司机是否本人确认 1=刷卡/人脸本人确认 0=系统推定或人工录入',
  `origin` varchar(256) DEFAULT NULL,
  `destination` varchar(256) DEFAULT NULL,
  `planned_depart` datetime DEFAULT NULL,
  `planned_arrive` datetime DEFAULT NULL,
  `depart_time` datetime DEFAULT NULL,
  `arrive_time` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `stop_count` int NOT NULL DEFAULT '1' COMMENT '途经站点数（1=单店直送；>1=一车多店，站点明细看 transport_item 按 store_id 聚合）',
  `source` varchar(16) DEFAULT 'MANUAL' COMMENT '数据来源 MANUAL=人工建单/PLAN=计划生成/DEVICE=装车口自动生成',
  `source_ref` varchar(64) DEFAULT NULL COMMENT '来源设备号（装车口自动生成记设备编号，如 LOADER-001）',
  `raw_payload` json DEFAULT NULL COMMENT '设备原始报文（装车口上报的完整报文，供逐字段核对）',
  `content_hash` char(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_no` (`transport_no`),
  KEY `idx_status` (`status`),
  KEY `idx_batch` (`split_batch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=160 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å†·é“¾è¿è¾“';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `cold_chain_transport` WRITE;
/*!40000 ALTER TABLE `cold_chain_transport` DISABLE KEYS */;
INSERT INTO `cold_chain_transport` VALUES (1,'TR-DEMO-0001',2,'京B-DEMO2','冷藏车','机械制冷','周司机','13800000002','MANUAL',0,'示范分割车间','安心生鲜门店','2026-09-13 19:55:18','2026-09-14 19:55:18','2026-09-13 19:55:18','2026-09-14 19:55:18',4,1,'MANUAL',NULL,NULL,'a7fd83ff247ed7e41f8e3962c207574c616e14a002886243d4a245f3d45d56fe','2026-09-16 19:55:18'),(2,'TR-TEST-0001',4,'京B-TEST01','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','安心生鲜门店','2026-09-02 19:55:18','2026-09-03 19:55:18','2026-09-02 19:55:18','2026-09-03 19:55:18',4,1,'MANUAL',NULL,NULL,'3b77c9c77df3767160ed976976b89e0528fe1cd002cd90f199a1b910b2239f94','2026-09-16 19:55:18'),(3,'TR-TEST-0002',6,'京B-TEST02','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','顺鑫生鲜门店','2026-09-04 19:55:18','2026-09-05 19:55:18','2026-09-04 19:55:18','2026-09-05 19:55:18',4,1,'MANUAL',NULL,NULL,'77639b8041631c62ad09d3deb35aebc2eda287118fe007a30977d49b0ee2ce49','2026-09-16 19:55:18'),(4,'TR-TEST-0003',8,'京B-TEST03','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','绿康生鲜门店','2026-09-06 19:55:18','2026-09-07 19:55:18','2026-09-06 19:55:18','2026-09-07 19:55:18',4,1,'MANUAL',NULL,NULL,'aa9d3d8d81fab89eb0cf9a62e0c662da35886c6bc3344f40458ed906341eb26f','2026-09-16 19:55:18'),(5,'TR-TEST-0004',10,'京B-TEST04','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','金牧生鲜门店','2026-09-08 19:55:18','2026-09-09 19:55:18','2026-09-08 19:55:18','2026-09-09 19:55:18',4,1,'MANUAL',NULL,NULL,'0fbbc460bc74555055ee9af90b4433e6f0b3dcff801580e35bf6829f13314782','2026-09-16 19:55:18'),(6,'TR-TEST-0005',12,'京B-TEST05','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','永发生鲜门店','2026-09-10 19:55:18','2026-09-11 19:55:18','2026-09-10 19:55:18','2026-09-11 19:55:18',4,1,'MANUAL',NULL,NULL,'47e3334fe92bf7dc29171e509c4643612b1f2124d511d3069beb39fc4fd7908f','2026-09-16 19:55:18'),(7,'TR-TEST-0006',14,'京B-TEST06','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','昌盛生鲜门店','2026-09-12 19:55:18','2026-09-13 19:55:18','2026-09-12 19:55:18','2026-09-13 19:55:18',4,1,'MANUAL',NULL,NULL,'32318b007c6940e623d21b003d240c70b9253baf2999990202c6c3751b64b950','2026-09-16 19:55:18'),(8,'TR-TEST-0007',16,'京B-TEST07','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','兴农生鲜门店','2026-09-14 19:55:18','2026-09-15 19:55:18','2026-09-14 19:55:18','2026-09-15 19:55:18',4,1,'MANUAL',NULL,NULL,'8f05782e67f654fd08880697b216a71a96350aca7a916e760b98c7b819137a85','2026-09-16 19:55:18'),(9,'TR-TEST-0008',4,'京B-TEST08','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','惠农生鲜门店','2026-09-15 19:55:18','2026-09-16 19:55:18','2026-09-15 19:55:18',NULL,2,1,'MANUAL',NULL,NULL,'6760099668de36c618b303f2e335a2483793796804359b635c450e35d915b199','2026-09-16 19:55:18'),(10,'TR-TEST-0009',6,'京B-TEST09','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','福田生鲜门店','2026-09-16 19:55:18','2026-09-17 19:55:18','2026-09-21 17:22:50','2026-09-21 17:22:50',3,1,'MANUAL',NULL,NULL,'66e1083b0f8a88d66959d97d25da3bba7354b6836f039ae7c2aff3812734c55e','2026-09-16 19:55:18'),(11,'TR-TEST-0010',8,'京B-TEST10','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','大众生鲜门店','2026-09-17 19:55:18','2026-09-18 19:55:18','2026-09-21 17:55:31','2026-09-21 17:55:31',3,1,'MANUAL',NULL,NULL,'87e334260e36ba78d774cf72092bef13f7efcd78c43d8c363b1fe07c2fcd5513','2026-09-16 19:55:18'),(12,'TR-TEST-0011',10,'京B-TEST11','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','利农生鲜门店','2026-09-18 19:55:18','2026-09-19 19:55:18',NULL,NULL,1,1,'MANUAL',NULL,NULL,'58f373ba831161cfb43b1b6bed8547a28e699002aef6b02ae43f8fc94ee998a7','2026-09-16 19:55:18'),(13,'TR-TEST-0012',12,'京B-TEST12','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','安康生鲜门店','2026-09-19 19:55:18','2026-09-20 19:55:18',NULL,NULL,1,1,'MANUAL',NULL,NULL,'7696e567a093466623269b2979e4fb9e6b5c2e9738ebc9fbdc9c1254efa2b815','2026-09-16 19:55:18'),(14,'TR-TEST-0013',14,'京B-TEST13','冷藏车','机械制冷','周司机','13800000020','MANUAL',0,'示范分割车间','丰禾生鲜门店','2026-09-20 19:55:18','2026-09-21 19:55:18',NULL,NULL,1,1,'MANUAL',NULL,NULL,'690ab0db2b32bd6bebb17865000b39c6d3ccf3bb5ca6b8a4ed9d35223a0f2207','2026-09-16 19:55:18'),(15,'TR-TEST-0014',16,'京B-TEST14','冷藏车','机械制冷','吴师傅','13800000021','MANUAL',0,'示范分割车间','宏图生鲜门店','2026-09-21 19:55:18','2026-09-22 19:55:18',NULL,NULL,1,1,'MANUAL',NULL,NULL,'2838e0e8c43e1cfcfc86b7204363beb2d0dfcb6550bedf102fb5b92bf8a0deea','2026-09-16 19:55:18'),(16,'TR-TEST-0015',4,'京B-TEST15','冷藏车','液氮制冷','周司机','13800000020','MANUAL',0,'示范分割车间','安心生鲜门店','2026-09-09 19:55:18','2026-09-10 19:55:18','2026-09-09 19:55:18','2026-09-10 19:55:18',4,1,'MANUAL',NULL,NULL,'71a7961258faee2b53909e7041adb2ffe3d37744155959101b50499186448756','2026-09-16 19:55:18'),(18,'TR20260917774090',19,'粤A-DEMO20','冷藏车','','张司机','12546935215','MANUAL',0,'示范定点屠宰场','大众生鲜门店','2026-09-17 00:00:00',NULL,'2026-09-17 05:22:02','2026-09-17 05:22:19',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-17 05:21:28'),(19,'TR20260917810714',20,'粤A-0017','冷藏车','','李司机','12547895321','MANUAL',0,'示范定点屠宰场','绿康生鲜门店','2026-09-17 00:00:00',NULL,'2026-09-17 23:20:15','2026-09-17 23:20:34',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-17 23:20:08'),(20,'TR20260918779000',21,'粤A-0018','冷藏车','','李司机','13457895623','MANUAL',0,'示范定点屠宰场','安心生鲜门店','2026-09-18 00:00:00',NULL,'2026-09-18 02:47:07','2026-09-18 02:47:21',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-18 02:47:04'),(21,'TR20260918499959',22,'粤A-0019','冷藏车','','李司机','13548956213','MANUAL',0,'示范定点屠宰场','绿康生鲜门店','2026-09-18 00:00:00',NULL,'2026-09-18 10:10:42','2026-09-18 10:10:54',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-18 10:10:38'),(22,'TR20260918484678',23,'粤A-0020','冷冻车','','李司机','13564825623','MANUAL',0,'示范定点屠宰场','金牧生鲜门店','2026-09-18 00:00:00',NULL,'2026-09-18 10:51:08','2026-09-18 10:51:18',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-18 10:51:02'),(23,'TR20260918403942',24,'1','冷藏车','','1','13568952144','MANUAL',0,'示范定点屠宰场','安心生鲜门店',NULL,NULL,'2026-09-18 11:48:17','2026-09-18 11:48:29',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-18 11:48:11'),(24,'TR20260921347325',24,'京A·T8483','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-21 17:23:49','2026-09-21 17:23:49',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-21 17:23:49'),(25,'TR20260921178079',24,'京A·T6780','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-21 17:24:27','2026-09-21 17:24:27',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-21 17:24:27'),(26,'TR20260921960501',24,'京A·T0379','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-21 17:27:50','2026-09-21 17:27:50',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-21 17:27:50'),(27,'TR-TEST-0016',12,'?B-TEST16','???','????','???','13800000021','MANUAL',0,'??????','??????','2026-09-20 20:00:58','2026-09-21 16:00:58','2026-09-20 20:00:58','2026-09-21 16:00:58',3,1,'MANUAL',NULL,NULL,NULL,'2026-09-21 20:00:58'),(28,'TR20260921903538',24,'京A·T7651','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-21 20:02:28','2026-09-21 20:02:28',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-21 20:02:28'),(29,'TR20260922763349',24,'京A·T5152','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-22 17:03:45','2026-09-22 17:03:45',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-22 17:03:45'),(30,'TR20260922845608',24,'京A·T4820','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-22 18:44:05','2026-09-22 18:44:05',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-22 18:44:05'),(31,'TR20260922097553',25,'京A·T3135','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-22 18:48:53','2026-09-22 18:48:53',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-22 18:48:53'),(32,'TR20260922978275',26,'京A·T7349','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-22 18:49:37','2026-09-22 18:49:37',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-22 18:49:37'),(33,'TR20260922002866',27,'京A·T1507','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-09-22 18:53:42','2026-09-22 18:53:42',4,1,'MANUAL',NULL,NULL,NULL,'2026-09-22 18:53:42'),(34,'TR20261003002406',28,'京A·T3820','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-10-03 16:09:24','2026-10-03 16:09:24',4,1,'MANUAL',NULL,NULL,NULL,'2026-10-03 16:09:24'),(35,'TR20261003003431',29,'京A·T6299','冷藏车','机械制冷','李四','13900000000','MANUAL',0,'分割车间','示范一店',NULL,NULL,'2026-10-03 21:04:26','2026-10-03 21:04:26',4,1,'MANUAL',NULL,NULL,NULL,'2026-10-03 21:04:26'),(86,'TR20261010975935',98,'京A·12345','冷藏车',NULL,'张师傅','13800000001','CARD',1,'示范定点屠宰场','安心生鲜门店',NULL,'2026-10-10 13:56:00',NULL,NULL,1,1,'DEVICE','LOADER-001','{\"cardNo\": \"8801\", \"batchNos\": \"SP20261009-001-05\", \"loadTime\": \"2026-10-10 13:57:01\", \"vehicleNo\": \"京A·12345\", \"driverName\": \"张师傅\", \"driverPhone\": \"13800000001\"}',NULL,'2026-10-10 13:57:07'),(105,'TR20261010655747',96,'京A·12345','冷藏车',NULL,'张师傅','13800000001','CARD',1,'示范定点屠宰场','安心生鲜门店',NULL,'2026-10-10 16:18:11',NULL,NULL,1,1,'DEVICE','LOADER-001','{\"cardNo\": \"8801\", \"batchNos\": \"SP20261009-001-03\", \"loadTime\": \"2026-10-10 16:29:02\", \"vehicleNo\": \"京A·12345\", \"driverName\": \"张师傅\", \"driverPhone\": \"13800000001\"}',NULL,'2026-10-10 16:29:05');
/*!40000 ALTER TABLE `cold_chain_transport` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `transport_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transport_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transport_id` bigint NOT NULL COMMENT '所属运单 cold_chain_transport.id',
  `split_batch_id` bigint NOT NULL COMMENT '装载的分割批次 split_batch.id',
  `batch_no` varchar(32) NOT NULL COMMENT '分割批次号（冗余）',
  `weight_kg` decimal(8,2) DEFAULT NULL COMMENT '该批次重量(kg)，装车时快照',
  `store_id` bigint DEFAULT NULL COMMENT '卸货门店 id（多站车次：这批货在哪一站卸下）',
  `store_name` varchar(128) DEFAULT NULL COMMENT '卸货门店名（冗余，展示免联表）',
  `seq` int DEFAULT NULL COMMENT '站点序号（按装车/扫码顺序，从 1 起）；NULL=混装未按站，顺序由签收时间回填',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_split` (`transport_id`,`split_batch_id`) COMMENT '同一运单内同一批次只记一次',
  KEY `idx_transport` (`transport_id`),
  KEY `idx_split` (`split_batch_id`) COMMENT '「这个批次发运了没有」的判定走它',
  KEY `idx_item_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单装载明细：一张运单实际装运的分割批次（一车多批）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `transport_item` WRITE;
/*!40000 ALTER TABLE `transport_item` DISABLE KEYS */;
INSERT INTO `transport_item` VALUES (1,1,2,'SP-DEMO-0002',10.50,NULL,'安心生鲜门店',1,'2026-09-16 19:55:18'),(2,2,4,'SP-TEST-0002',10.50,NULL,'安心生鲜门店',1,'2026-09-16 19:55:18'),(3,3,6,'SP-TEST-0004',12.00,NULL,'顺鑫生鲜门店',1,'2026-09-16 19:55:18'),(4,4,8,'SP-TEST-0006',8.50,NULL,'绿康生鲜门店',1,'2026-09-16 19:55:18'),(5,5,10,'SP-TEST-0008',15.00,NULL,'金牧生鲜门店',1,'2026-09-16 19:55:18'),(6,6,12,'SP-TEST-0010',5.50,NULL,'永发生鲜门店',1,'2026-09-16 19:55:18'),(7,7,14,'SP-TEST-0012',11.00,NULL,'昌盛生鲜门店',1,'2026-09-16 19:55:18'),(8,8,16,'SP-TEST-0014',9.00,NULL,'兴农生鲜门店',1,'2026-09-16 19:55:18'),(9,9,4,'SP-TEST-0002',10.50,NULL,'惠农生鲜门店',1,'2026-09-16 19:55:18'),(10,10,6,'SP-TEST-0004',12.00,NULL,'福田生鲜门店',1,'2026-09-16 19:55:18'),(11,11,8,'SP-TEST-0006',8.50,NULL,'大众生鲜门店',1,'2026-09-16 19:55:18'),(12,12,10,'SP-TEST-0008',15.00,NULL,'利农生鲜门店',1,'2026-09-16 19:55:18'),(13,13,12,'SP-TEST-0010',5.50,NULL,'安康生鲜门店',1,'2026-09-16 19:55:18'),(14,14,14,'SP-TEST-0012',11.00,NULL,'丰禾生鲜门店',1,'2026-09-16 19:55:18'),(15,15,16,'SP-TEST-0014',9.00,NULL,'宏图生鲜门店',1,'2026-09-16 19:55:18'),(16,16,4,'SP-TEST-0002',10.50,NULL,'安心生鲜门店',1,'2026-09-16 19:55:18'),(17,18,19,'SP20260917959868',10.00,NULL,'大众生鲜门店',1,'2026-09-17 05:21:28'),(18,19,20,'SP20260917295195',10.00,NULL,'绿康生鲜门店',1,'2026-09-17 23:20:08'),(19,20,21,'SP20260918692191',10.00,NULL,'安心生鲜门店',1,'2026-09-18 02:47:04'),(20,21,22,'SP20260918244095',10.00,NULL,'绿康生鲜门店',1,'2026-09-18 10:10:38'),(21,22,23,'SP20260918170247',10.00,NULL,'金牧生鲜门店',1,'2026-09-18 10:51:02'),(22,23,24,'SP20260918243893',10.00,NULL,'安心生鲜门店',1,'2026-09-18 11:48:11'),(23,24,24,'SP20260918243893',10.00,NULL,'示范一店',1,'2026-09-21 17:23:49'),(24,25,24,'SP20260918243893',10.00,NULL,'示范一店',1,'2026-09-21 17:24:27'),(25,26,24,'SP20260918243893',10.00,NULL,'示范一店',1,'2026-09-21 17:27:50'),(26,27,12,'SP-TEST-0010',5.50,NULL,'??????',1,'2026-09-21 20:00:58'),(27,28,24,'SP20260918243893',10.00,NULL,'示范一店',1,'2026-09-21 20:02:28'),(28,29,24,'SP20260918243893',10.00,NULL,'示范一店',1,'2026-09-22 17:03:45'),(29,30,24,'SP20260918243893',10.00,NULL,'示范一店',1,'2026-09-22 18:44:05'),(30,31,25,'SP20260922072778',78.50,NULL,'示范一店',1,'2026-09-22 18:48:53'),(31,32,26,'SP20260922575156',78.50,NULL,'示范一店',1,'2026-09-22 18:49:37'),(32,33,27,'SP20260922476934',78.50,NULL,'示范一店',1,'2026-09-22 18:53:42'),(33,34,28,'SP20260922139266',78.50,NULL,'示范一店',1,'2026-10-03 16:09:24'),(34,35,29,'SP20261003561802',78.50,NULL,'示范一店',1,'2026-10-03 21:04:26'),(155,86,98,'SP20261009-001-05',8.00,NULL,'安心生鲜门店',1,'2026-10-10 13:57:07'),(181,105,96,'SP20261009-001-03',19.00,1001,'安心生鲜门店',1,'2026-10-10 16:29:05');
/*!40000 ALTER TABLE `transport_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `transport_stop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transport_stop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transport_id` bigint NOT NULL COMMENT '运单 id',
  `stop_no` int NOT NULL COMMENT '站点序号（1 起；口径 = transport_item.seq，即装车扫码顺序 = 卸货顺序）',
  `store_id` bigint DEFAULT NULL COMMENT '卸货门店 id（权威来源是机构树；人工建单的老运单可能为空，此时按 store_name 定位）',
  `store_name` varchar(128) NOT NULL COMMENT '卸货门店名（展示快照）',
  `item_count` int NOT NULL DEFAULT '0' COMMENT '本站批次数',
  `weight_kg` decimal(12,2) DEFAULT NULL COMMENT '本站货物总重 kg',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '行程状态 0 未到达 / 1 已到达 / 2 已签收',
  `arrive_time` datetime DEFAULT NULL COMMENT '到达时刻（未到达为空）',
  `arrive_source` varchar(24) DEFAULT NULL COMMENT '到达怎么确认的 DEVICE=门店设备自动确认 / MANUAL=人工确认',
  `arrive_source_ref` varchar(64) DEFAULT NULL COMMENT '到达上报编号（设备号 / 操作留痕）',
  `receipt_id` bigint DEFAULT NULL COMMENT '签收记录 id（store_receipt，仅已签收时有值）',
  `raw_payload` text COMMENT '到达报文原文（设备通道才有，人工确认为空）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_stop` (`transport_id`,`stop_no`),
  KEY `idx_stop_store` (`transport_id`,`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=133 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车次站点行程台账：一站一条，记未到达/已到达/已签收与到达时刻';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `transport_stop` WRITE;
/*!40000 ALTER TABLE `transport_stop` DISABLE KEYS */;
INSERT INTO `transport_stop` VALUES (1,1,1,NULL,'安心生鲜门店',1,10.50,2,'2026-09-14 19:55:18','MANUAL',NULL,1,NULL,'2026-10-10 17:15:20'),(2,2,1,NULL,'安心生鲜门店',1,10.50,2,'2026-09-03 19:55:18','MANUAL',NULL,2,NULL,'2026-10-10 17:15:20'),(3,3,1,NULL,'顺鑫生鲜门店',1,12.00,2,'2026-09-05 19:55:18','MANUAL',NULL,3,NULL,'2026-10-10 17:15:20'),(4,4,1,NULL,'绿康生鲜门店',1,8.50,2,'2026-09-07 19:55:18','MANUAL',NULL,4,NULL,'2026-10-10 17:15:20'),(5,5,1,NULL,'金牧生鲜门店',1,15.00,2,'2026-09-09 19:55:18','MANUAL',NULL,5,NULL,'2026-10-10 17:15:20'),(6,6,1,NULL,'永发生鲜门店',1,5.50,2,'2026-09-11 19:55:18','MANUAL',NULL,6,NULL,'2026-10-10 17:15:20'),(7,7,1,NULL,'昌盛生鲜门店',1,11.00,2,'2026-09-13 19:55:18','MANUAL',NULL,7,NULL,'2026-10-10 17:15:20'),(8,8,1,NULL,'兴农生鲜门店',1,9.00,2,'2026-09-15 19:55:18','MANUAL',NULL,8,NULL,'2026-10-10 17:15:20'),(9,9,1,NULL,'惠农生鲜门店',1,10.50,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20'),(10,10,1,NULL,'福田生鲜门店',1,12.00,1,'2026-09-21 17:22:50','MANUAL',NULL,NULL,NULL,'2026-10-10 17:15:20'),(11,11,1,NULL,'大众生鲜门店',1,8.50,1,'2026-09-21 17:55:31','MANUAL',NULL,NULL,NULL,'2026-10-10 17:15:20'),(12,12,1,NULL,'利农生鲜门店',1,15.00,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20'),(13,13,1,NULL,'安康生鲜门店',1,5.50,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20'),(14,14,1,NULL,'丰禾生鲜门店',1,11.00,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20'),(15,15,1,NULL,'宏图生鲜门店',1,9.00,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20'),(16,16,1,NULL,'安心生鲜门店',1,10.50,2,'2026-09-10 19:55:18','MANUAL',NULL,9,NULL,'2026-10-10 17:15:20'),(17,18,1,NULL,'大众生鲜门店',1,10.00,2,'2026-09-17 05:22:19','MANUAL',NULL,18,NULL,'2026-10-10 17:15:20'),(18,19,1,NULL,'绿康生鲜门店',1,10.00,2,'2026-09-17 23:20:34','MANUAL',NULL,19,NULL,'2026-10-10 17:15:20'),(19,20,1,NULL,'安心生鲜门店',1,10.00,2,'2026-09-18 02:47:21','MANUAL',NULL,20,NULL,'2026-10-10 17:15:20'),(20,21,1,NULL,'绿康生鲜门店',1,10.00,2,'2026-09-18 10:10:54','MANUAL',NULL,21,NULL,'2026-10-10 17:15:20'),(21,22,1,NULL,'金牧生鲜门店',1,10.00,2,'2026-09-18 10:51:18','MANUAL',NULL,22,NULL,'2026-10-10 17:15:20'),(22,23,1,NULL,'安心生鲜门店',1,10.00,2,'2026-09-18 11:48:29','MANUAL',NULL,23,NULL,'2026-10-10 17:15:20'),(23,24,1,NULL,'示范一店',1,10.00,2,'2026-09-21 17:23:49','MANUAL',NULL,24,NULL,'2026-10-10 17:15:20'),(24,25,1,NULL,'示范一店',1,10.00,2,'2026-09-21 17:24:27','MANUAL',NULL,25,NULL,'2026-10-10 17:15:20'),(25,26,1,NULL,'示范一店',1,10.00,2,'2026-09-21 17:27:50','MANUAL',NULL,26,NULL,'2026-10-10 17:15:20'),(26,27,1,NULL,'??????',1,5.50,1,'2026-09-21 16:00:58','MANUAL',NULL,NULL,NULL,'2026-10-10 17:15:20'),(27,28,1,NULL,'示范一店',1,10.00,2,'2026-09-21 20:02:28','MANUAL',NULL,27,NULL,'2026-10-10 17:15:20'),(28,29,1,NULL,'示范一店',1,10.00,2,'2026-09-22 17:03:45','MANUAL',NULL,28,NULL,'2026-10-10 17:15:20'),(29,30,1,NULL,'示范一店',1,10.00,2,'2026-09-22 18:44:05','MANUAL',NULL,29,NULL,'2026-10-10 17:15:20'),(30,31,1,NULL,'示范一店',1,78.50,2,'2026-09-22 18:48:53','MANUAL',NULL,30,NULL,'2026-10-10 17:15:20'),(31,32,1,NULL,'示范一店',1,78.50,2,'2026-09-22 18:49:37','MANUAL',NULL,31,NULL,'2026-10-10 17:15:20'),(32,33,1,NULL,'示范一店',1,78.50,2,'2026-09-22 18:53:42','MANUAL',NULL,32,NULL,'2026-10-10 17:15:20'),(33,34,1,NULL,'示范一店',1,78.50,2,'2026-10-03 16:09:24','MANUAL',NULL,33,NULL,'2026-10-10 17:15:20'),(34,35,1,NULL,'示范一店',1,78.50,2,'2026-10-03 21:04:26','MANUAL',NULL,34,NULL,'2026-10-10 17:15:20'),(35,86,1,NULL,'安心生鲜门店',1,8.00,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20'),(36,105,1,1001,'安心生鲜门店',1,19.00,0,NULL,NULL,NULL,NULL,NULL,'2026-10-10 17:15:20');
/*!40000 ALTER TABLE `transport_stop` ENABLE KEYS */;
UNLOCK TABLES;
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
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '计划状态 1=待发货 2=已排车（货已全部排进运单）3=已取消 4=部分排车（还有货在等车）',
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
) ENGINE=InnoDB AUTO_INCREMENT=175 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货计划：一个目的地+一个车次+N个待发分割批次，运单由它一键生成';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `delivery_plan` WRITE;
/*!40000 ALTER TABLE `delivery_plan` DISABLE KEYS */;
INSERT INTO `delivery_plan` VALUES (64,'DP20261010-001','2026-10-10',1001,'安心生鲜门店','京A·12345',NULL,NULL,'张师傅','13800000001','示范定点屠宰场',NULL,'2026-10-10 13:56:00',2,'ORDER','SO20261010-001',1,8.00,86,'TR20261010975935',1,'2026-10-10 13:57:07','【订单转入】订单号 SO20261010-001；订单备注：演示：外部系统选购转入','订单转入（ORDER-TERM-001）','2026-10-10 13:56:19'),(96,'DP20261010-002','2026-10-10',1001,'安心生鲜门店',NULL,NULL,NULL,NULL,NULL,'示范定点屠宰场',NULL,'2026-10-10 16:17:07',1,'ORDER','SO20261010-161709',1,11.00,NULL,NULL,0,NULL,'【订单转入】订单号 SO20261010-161709；订单备注：演示：外部系统选购转入','订单转入（ORDER-TERM-001）','2026-10-10 16:17:11'),(97,'DP20261010-003','2026-10-10',1001,'安心生鲜门店','京A·12345',NULL,NULL,'张师傅','13800000001','示范定点屠宰场',NULL,'2026-10-10 16:18:11',2,'ORDER','SO20261010-161815',1,19.00,105,'TR20261010655747',1,'2026-10-10 16:29:05','【订单转入】订单号 SO20261010-161815；订单备注：演示：外部系统选购转入','订单转入（ORDER-TERM-001）','2026-10-10 16:18:17');
/*!40000 ALTER TABLE `delivery_plan` ENABLE KEYS */;
UNLOCK TABLES;
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
  `shipped` tinyint NOT NULL DEFAULT '0' COMMENT '是否已排车 0=待排车 1=已排进某张运单',
  `transport_id` bigint DEFAULT NULL COMMENT '实际发运这张明细的运单 id（分车时同一计划会指向不同运单）',
  `shipped_time` datetime DEFAULT NULL COMMENT '该明细实际发运时刻',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_split` (`plan_id`,`split_batch_id`) COMMENT '同一计划里同一分割批次只能出现一次',
  KEY `idx_plan` (`plan_id`),
  KEY `idx_split` (`split_batch_id`),
  KEY `idx_shipped` (`shipped`,`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=314 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货计划明细：本次要装车发运的分割批次清单';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `delivery_plan_item` WRITE;
/*!40000 ALTER TABLE `delivery_plan_item` DISABLE KEYS */;
INSERT INTO `delivery_plan_item` VALUES (145,64,98,'SP20261009-001-05','P07','肋排',8.00,1,86,'2026-10-10 13:57:07','2026-10-10 13:56:19'),(191,96,97,'SP20261009-001-04','P06','五花肉',11.00,0,NULL,NULL,'2026-10-10 16:17:11'),(192,97,96,'SP20261009-001-03','P04','后腿肉',19.00,1,105,'2026-10-10 16:29:05','2026-10-10 16:18:17');
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

