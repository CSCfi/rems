-- MySQL dump 10.13  Distrib 5.1.73, for redhat-linux-gnu (x86_64)

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `rms_application_attachment_values`
--

DROP TABLE IF EXISTS `rms_application_attachment_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_attachment_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `formMapId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `attachmentId` int(11) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `formMapId` (`formMapId`),
  KEY `attachmentId` (`attachmentId`),
  CONSTRAINT `rms_application_attachment_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_application_attachment_values_ibfk_2` FOREIGN KEY (`formMapId`) REFERENCES `rms_application_form_item_map` (`id`),
  CONSTRAINT `rms_application_attachment_values_ibfk_3` FOREIGN KEY (`attachmentId`) REFERENCES `rms_attachment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_attachment_values`
--

LOCK TABLES `rms_application_attachment_values` WRITE;
/*!40000 ALTER TABLE `rms_application_attachment_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_application_attachment_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_checkbox_values`
--

DROP TABLE IF EXISTS `rms_application_checkbox_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_checkbox_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `formMapId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `value` bit(1) DEFAULT b'0',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `formMapId` (`formMapId`),
  CONSTRAINT `rms_application_checkbox_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_application_checkbox_values_ibfk_2` FOREIGN KEY (`formMapId`) REFERENCES `rms_application_form_item_map` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_checkbox_values`
--

LOCK TABLES `rms_application_checkbox_values` WRITE;
/*!40000 ALTER TABLE `rms_application_checkbox_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_application_checkbox_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_form`
--

DROP TABLE IF EXISTS `rms_application_form`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_form` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `title` varchar(256) NOT NULL,
  `visibility` enum('private','public') NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_form`
--

LOCK TABLES `rms_application_form` WRITE;
/*!40000 ALTER TABLE `rms_application_form` DISABLE KEYS */;
INSERT INTO `rms_application_form` VALUES (1,11220,11220,'Suppea','public','2014-03-06 11:03:13',NULL),(2,11220,11220,'Limited','public','2014-03-06 11:21:50',NULL),(3,11220,11220,'Keskinkertainen','public','2014-03-06 11:23:12',NULL),(4,11220,11220,'Moderate','public','2014-03-06 11:23:37',NULL),(5,11220,11220,'Laaja','public','2014-03-07 08:32:30',NULL),(6,11220,11220,'Extensive','public','2014-03-07 08:34:33',NULL);
/*!40000 ALTER TABLE `rms_application_form` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_form_item`
--

DROP TABLE IF EXISTS `rms_application_form_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_form_item` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `title` varchar(256) NOT NULL,
  `toolTip` varchar(256) DEFAULT NULL,
  `inputPrompt` varchar(256) DEFAULT NULL,
  `type` enum('text','texta','label','license','attachment','referee','checkbox','dropdown','date') DEFAULT NULL,
  `value` bigint(20) NOT NULL,
  `visibility` enum('private','public') NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_form_item`
--

LOCK TABLES `rms_application_form_item` WRITE;
/*!40000 ALTER TABLE `rms_application_form_item` DISABLE KEYS */;
INSERT INTO `rms_application_form_item` VALUES (1,11220,11220,'Aineiston kyttötarkoitus',NULL,NULL,'text',128,'public','2014-03-06 09:41:58',NULL),(2,11220,11220,'Purpose of Use',NULL,NULL,'text',128,'public','2014-03-06 09:42:49',NULL),(3,11220,11220,'Tutkimuksen nimi',NULL,NULL,'text',128,'public','2014-03-06 09:43:13',NULL),(4,11220,11220,'Project Name',NULL,NULL,'text',128,'public','2014-03-06 09:43:34',NULL),(5,11220,11220,'Tutkimuksen kuvaus',NULL,NULL,'texta',512,'public','2014-03-06 09:44:03',NULL),(6,11220,11220,'Project Description',NULL,NULL,'texta',512,'public','2014-03-06 09:44:26',NULL),(7,11220,11220,'Lisätiedot',NULL,NULL,'texta',512,'public','2014-03-06 09:44:46',NULL),(8,11220,11220,'Additional Information',NULL,NULL,'texta',512,'public','2014-03-06 09:45:10',NULL),(9,11220,11220,'Tutkimussuunnitelma',NULL,NULL,'attachment',1048576,'public','2014-03-06 09:47:54',NULL),(10,11220,11220,'Research Plan',NULL,NULL,'attachment',1048576,'public','2014-03-06 09:48:34',NULL),(11,11220,11220,'Koulutus',NULL,NULL,'dropdown',1,'public','2014-03-06 11:45:56',NULL),(12,11220,11220,'Education',NULL,NULL,'dropdown',1,'public','2014-03-06 11:46:32',NULL),(13,11220,11220,'REMS Demo lisenssi',NULL,NULL,'license',1,'public','2014-03-06 11:49:40',NULL),(14,11220,11220,'REMS Demo License',NULL,NULL,'license',1,'public','2014-03-06 11:50:13',NULL),(15,11220,11220,'Suosittelija',NULL,NULL,'referee',0,'public','2014-03-07 07:35:29',NULL),(16,11220,11220,'Referee',NULL,NULL,'referee',0,'public','2014-03-07 07:35:42',NULL),(17,11220,11220,'Tutkimusprojekti ei ole vielä käynnistynyt',NULL,NULL,'checkbox',0,'public','2014-03-07 07:51:07',NULL),(18,11220,11220,'Project has not started yet',NULL,NULL,'checkbox',0,'public','2014-03-07 07:51:44',NULL);
/*!40000 ALTER TABLE `rms_application_form_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_form_item_map`
--

DROP TABLE IF EXISTS `rms_application_form_item_map`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_form_item_map` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `formId` int(11) DEFAULT NULL,
  `formItemId` int(11) DEFAULT NULL,
  `formItemOptional` bit(1) DEFAULT b'0',
  `modifierUserId` bigint(20) NOT NULL,
  `itemOrder` int(11) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `formId` (`formId`),
  KEY `formItemId` (`formItemId`),
  CONSTRAINT `rms_application_form_item_map_ibfk_1` FOREIGN KEY (`formId`) REFERENCES `rms_application_form` (`id`),
  CONSTRAINT `rms_application_form_item_map_ibfk_2` FOREIGN KEY (`formItemId`) REFERENCES `rms_application_form_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_form_item_map`
--

LOCK TABLES `rms_application_form_item_map` WRITE;
/*!40000 ALTER TABLE `rms_application_form_item_map` DISABLE KEYS */;
INSERT INTO `rms_application_form_item_map` VALUES (1,1,1,'\0',11220,1,'2014-03-06 11:03:13',NULL),(2,1,7,'\0',11220,2,'2014-03-06 11:03:13',NULL),(3,2,2,'\0',11220,1,'2014-03-06 11:21:50',NULL),(4,2,8,'\0',11220,2,'2014-03-06 11:21:50',NULL),(5,3,1,'\0',11220,1,'2014-03-06 11:23:12',NULL),(6,3,3,'\0',11220,2,'2014-03-06 11:23:12',NULL),(7,3,5,'\0',11220,3,'2014-03-06 11:23:12',NULL),(8,3,7,'\0',11220,4,'2014-03-06 11:23:12',NULL),(9,3,9,'\0',11220,5,'2014-03-06 11:23:12',NULL),(10,4,2,'\0',11220,1,'2014-03-06 11:23:37',NULL),(11,4,4,'\0',11220,2,'2014-03-06 11:23:37',NULL),(12,4,6,'\0',11220,3,'2014-03-06 11:23:37',NULL),(13,4,8,'\0',11220,4,'2014-03-06 11:23:37',NULL),(14,4,10,'\0',11220,5,'2014-03-06 11:23:37',NULL),(15,5,1,'\0',11220,1,'2014-03-07 08:32:30',NULL),(16,5,3,'\0',11220,2,'2014-03-07 08:32:30',NULL),(17,5,5,'\0',11220,3,'2014-03-07 08:32:30',NULL),(18,5,17,'\0',11220,4,'2014-03-07 08:32:30',NULL),(19,5,7,'\0',11220,5,'2014-03-07 08:32:30',NULL),(20,5,9,'\0',11220,6,'2014-03-07 08:32:30',NULL),(21,5,11,'\0',11220,7,'2014-03-07 08:32:30',NULL),(22,5,13,'\0',11220,8,'2014-03-07 08:32:30',NULL),(23,5,15,'\0',11220,9,'2014-03-07 08:32:30',NULL),(24,6,2,'\0',11220,1,'2014-03-07 08:34:33',NULL),(25,6,4,'\0',11220,2,'2014-03-07 08:34:33',NULL),(26,6,6,'\0',11220,3,'2014-03-07 08:34:33',NULL),(27,6,18,'\0',11220,4,'2014-03-07 08:34:33',NULL),(28,6,8,'\0',11220,5,'2014-03-07 08:34:33',NULL),(29,6,10,'\0',11220,6,'2014-03-07 08:34:33',NULL),(30,6,12,'\0',11220,7,'2014-03-07 08:34:33',NULL),(31,6,14,'\0',11220,8,'2014-03-07 08:34:33',NULL),(32,6,16,'\0',11220,9,'2014-03-07 08:34:33',NULL);
/*!40000 ALTER TABLE `rms_application_form_item_map` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_form_item_string_values`
--

DROP TABLE IF EXISTS `rms_application_form_item_string_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_form_item_string_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `formItemId` int(11) DEFAULT NULL,
  `value` varchar(4096) NOT NULL,
  `itemOrder` int(11) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `formItemId` (`formItemId`),
  CONSTRAINT `rms_application_form_item_string_values_ibfk_1` FOREIGN KEY (`formItemId`) REFERENCES `rms_application_form_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_form_item_string_values`
--

LOCK TABLES `rms_application_form_item_string_values` WRITE;
/*!40000 ALTER TABLE `rms_application_form_item_string_values` DISABLE KEYS */;
INSERT INTO `rms_application_form_item_string_values` VALUES (1,11,'Ylempi korkeakoulututkinto',0,'2014-03-06 11:45:56',NULL),(2,12,'Master\'s degree',0,'2014-03-06 11:46:32',NULL),(3,11,'Muu',1,'2014-03-06 11:45:56',NULL),(4,12,'Other',1,'2014-03-06 11:46:32',NULL);
/*!40000 ALTER TABLE `rms_application_form_item_string_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_form_meta`
--

DROP TABLE IF EXISTS `rms_application_form_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_form_meta` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `title` varchar(256) DEFAULT NULL,
  `visibility` enum('private','public') NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_form_meta`
--

LOCK TABLES `rms_application_form_meta` WRITE;
/*!40000 ALTER TABLE `rms_application_form_meta` DISABLE KEYS */;
INSERT INTO `rms_application_form_meta` VALUES (1,11220,11220,NULL,'public','2014-03-06 11:03:13',NULL),(2,11220,11220,NULL,'public','2014-03-06 11:21:50',NULL),(3,11220,11220,NULL,'public','2014-03-06 11:23:12',NULL),(4,11220,11220,NULL,'public','2014-03-06 11:23:37',NULL),(5,11220,11220,'Localized/lokalisoitu limited/suppea','public','2014-03-06 11:57:43',NULL),(6,11220,11220,'Localized/lokalisoitu moderate/keskinkertainen','public','2014-03-06 11:58:55',NULL),(7,11220,11220,NULL,'public','2014-03-07 08:32:30',NULL),(8,11220,11220,NULL,'public','2014-03-07 08:34:33',NULL),(9,11220,11220,'Localized/lokalisoitu extensive/laaja','public','2014-03-07 08:36:20',NULL);
/*!40000 ALTER TABLE `rms_application_form_meta` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_form_meta_map`
--

DROP TABLE IF EXISTS `rms_application_form_meta_map`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_form_meta_map` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `metaFormId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `langCode` varchar(64) DEFAULT NULL,
  `formId` int(11) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `metaFormId` (`metaFormId`),
  KEY `formId` (`formId`),
  CONSTRAINT `rms_application_form_meta_map_ibfk_1` FOREIGN KEY (`metaFormId`) REFERENCES `rms_application_form_meta` (`id`),
  CONSTRAINT `rms_application_form_meta_map_ibfk_2` FOREIGN KEY (`formId`) REFERENCES `rms_application_form` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_form_meta_map`
--

LOCK TABLES `rms_application_form_meta_map` WRITE;
/*!40000 ALTER TABLE `rms_application_form_meta_map` DISABLE KEYS */;
INSERT INTO `rms_application_form_meta_map` VALUES (1,1,11220,NULL,1,'2014-03-06 11:03:13',NULL),(2,2,11220,NULL,2,'2014-03-06 11:21:50',NULL),(3,3,11220,NULL,3,'2014-03-06 11:23:12',NULL),(4,4,11220,NULL,4,'2014-03-06 11:23:37',NULL),(5,5,11220,'fi',1,'2014-03-06 11:57:43',NULL),(6,5,11220,'en',2,'2014-03-06 11:57:43',NULL),(7,6,11220,'fi',3,'2014-03-06 11:58:55',NULL),(8,6,11220,'en',4,'2014-03-06 11:58:55',NULL),(9,7,11220,NULL,5,'2014-03-07 08:32:30',NULL),(10,8,11220,NULL,6,'2014-03-07 08:34:33',NULL),(11,9,11220,'fi',5,'2014-03-07 08:36:20',NULL),(12,9,11220,'en',6,'2014-03-07 08:36:20',NULL);
/*!40000 ALTER TABLE `rms_application_form_meta_map` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_license_approval_values`
--

DROP TABLE IF EXISTS `rms_application_license_approval_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_license_approval_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `formMapId` int(11) DEFAULT NULL,
  `licId` int(11) NOT NULL,
  `modifierUserId` bigint(20) DEFAULT NULL,
  `state` enum('approved','rejected') NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `formMapId` (`formMapId`),
  KEY `licId` (`licId`),
  CONSTRAINT `rms_application_license_approval_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_application_license_approval_values_ibfk_2` FOREIGN KEY (`formMapId`) REFERENCES `rms_application_form_item_map` (`id`),
  CONSTRAINT `rms_application_license_approval_values_ibfk_3` FOREIGN KEY (`licId`) REFERENCES `rms_license` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_license_approval_values`
--

LOCK TABLES `rms_application_license_approval_values` WRITE;
/*!40000 ALTER TABLE `rms_application_license_approval_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_application_license_approval_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_referee_invite_values`
--

DROP TABLE IF EXISTS `rms_application_referee_invite_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_referee_invite_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `formMapId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(2) DEFAULT NULL,
  `email` varchar(256) NOT NULL,
  `hash` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `formMapId` (`formMapId`),
  CONSTRAINT `rms_application_referee_invite_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_application_referee_invite_values_ibfk_2` FOREIGN KEY (`formMapId`) REFERENCES `rms_application_form_item_map` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_referee_invite_values`
--

LOCK TABLES `rms_application_referee_invite_values` WRITE;
/*!40000 ALTER TABLE `rms_application_referee_invite_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_application_referee_invite_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_referee_values`
--

DROP TABLE IF EXISTS `rms_application_referee_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_referee_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `formMapId` int(11) DEFAULT NULL,
  `refereeUserId` bigint(20) NOT NULL,
  `comment` varchar(4096) DEFAULT NULL,
  `state` enum('created','recommended','rejected','returned') NOT NULL DEFAULT 'created',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `formMapId` (`formMapId`),
  CONSTRAINT `rms_application_referee_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_application_referee_values_ibfk_2` FOREIGN KEY (`formMapId`) REFERENCES `rms_application_form_item_map` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_referee_values`
--

LOCK TABLES `rms_application_referee_values` WRITE;
/*!40000 ALTER TABLE `rms_application_referee_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_application_referee_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_application_text_values`
--

DROP TABLE IF EXISTS `rms_application_text_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_application_text_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `formMapId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `value` varchar(4096) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `formMapId` (`formMapId`),
  CONSTRAINT `rms_application_text_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_application_text_values_ibfk_2` FOREIGN KEY (`formMapId`) REFERENCES `rms_application_form_item_map` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_application_text_values`
--

LOCK TABLES `rms_application_text_values` WRITE;
/*!40000 ALTER TABLE `rms_application_text_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_application_text_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_attachment`
--

DROP TABLE IF EXISTS `rms_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_attachment` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `userId` bigint(20) NOT NULL,
  `title` varchar(256) NOT NULL,
  `file_name` varchar(256) NOT NULL,
  `file_type` varchar(15) DEFAULT NULL,
  `file_size` bigint(20) NOT NULL,
  `file_content` longblob NOT NULL,
  `file_ext` varchar(10) NOT NULL,
  `state` enum('visible','hidden') NOT NULL DEFAULT 'visible',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_attachment`
--

LOCK TABLES `rms_attachment` WRITE;
/*!40000 ALTER TABLE `rms_attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item`
--

DROP TABLE IF EXISTS `rms_catalogue_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(256) NOT NULL,
  `resId` int(11) DEFAULT NULL,
  `wfId` int(11) DEFAULT NULL,
  `formId` int(11) DEFAULT '1',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  KEY `wfId` (`wfId`),
  KEY `formId` (`formId`),
  CONSTRAINT `rms_catalogue_item_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`),
  CONSTRAINT `rms_catalogue_item_ibfk_2` FOREIGN KEY (`wfId`) REFERENCES `rms_workflow` (`id`),
  CONSTRAINT `rms_catalogue_item_ibfk_3` FOREIGN KEY (`formId`) REFERENCES `rms_application_form_meta` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item`
--

LOCK TABLES `rms_catalogue_item` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item` DISABLE KEYS */;
INSERT INTO `rms_catalogue_item` VALUES (1,'Dataset 001 A dataset with a minimal workflow. The user just commits to licence terms and gets entitlement.',2,1,5,'2014-03-06 12:00:41',NULL),(2,'Dataset 002 A dataset with a simple workflow. The application is sent to a single person (RDapprover1) for approval.',3,2,5,'2014-03-06 12:04:24',NULL),(3,'Dataset 003 A dataset with a reviewer (RDreview), who can just provide comments on the application to the approver.',4,3,6,'2014-03-06 12:22:41',NULL),(4,'Dataset 004 A dataset with a two-step approval. The application is first approved by RDapprover1 and then by RDapprover2.',5,4,6,'2014-03-06 12:33:47',NULL),(5,'Dataset 005 A dataset with two parallel approvers (RDapprover1, RDapprover2). Only one needs to approve the application.',6,5,6,'2014-03-06 12:36:58',NULL),(6,'Dataset 006 A dataset with an extensive application form, a reviewer (RDreview) and two consecutive approvers.',7,6,9,'2014-03-07 09:54:48',NULL);
/*!40000 ALTER TABLE `rms_catalogue_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catId` int(11) DEFAULT NULL,
  `applicantUserId` bigint(20) NOT NULL,
  `fnlround` int(11) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  `modifierUserId` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catId` (`catId`),
  CONSTRAINT `rms_catalogue_item_application_ibfk_1` FOREIGN KEY (`catId`) REFERENCES `rms_catalogue_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application`
--

LOCK TABLES `rms_catalogue_item_application` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_approvers`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_approvers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_approvers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `wfApprId` int(11) DEFAULT NULL,
  `apprUserId` bigint(20) NOT NULL,
  `round` int(11) NOT NULL,
  `comment` varchar(4096) DEFAULT NULL,
  `state` enum('created','approved','rejected','returned','rerouted','closed') DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `wfApprId` (`wfApprId`),
  CONSTRAINT `rms_catalogue_item_application_approvers_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_catalogue_item_application_approvers_ibfk_2` FOREIGN KEY (`wfApprId`) REFERENCES `rms_workflow_approvers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_approvers`
--

LOCK TABLES `rms_catalogue_item_application_approvers` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_approvers` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_approvers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_catid_overflow`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_catid_overflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_catid_overflow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `catId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `catId` (`catId`),
  CONSTRAINT `rms_catalogue_item_application_catid_overflow_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_catalogue_item_application_catid_overflow_ibfk_2` FOREIGN KEY (`catId`) REFERENCES `rms_catalogue_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_catid_overflow`
--

LOCK TABLES `rms_catalogue_item_application_catid_overflow` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_catid_overflow` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_catid_overflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_free_comment_values`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_free_comment_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_free_comment_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `userId` bigint(20) NOT NULL,
  `catAppId` int(11) DEFAULT NULL,
  `comment` varchar(4096) DEFAULT NULL,
  `public` bit(1) NOT NULL DEFAULT b'1',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_catalogue_item_application_free_comment_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_free_comment_values`
--

LOCK TABLES `rms_catalogue_item_application_free_comment_values` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_free_comment_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_free_comment_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_licenses`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_licenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_licenses` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `licId` int(11) DEFAULT NULL,
  `actorUserId` bigint(20) NOT NULL,
  `round` int(11) NOT NULL,
  `stalling` bit(1) NOT NULL DEFAULT b'0',
  `state` enum('created','approved','rejected') NOT NULL DEFAULT 'created',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `licId` (`licId`),
  CONSTRAINT `rms_catalogue_item_application_licenses_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_catalogue_item_application_licenses_ibfk_2` FOREIGN KEY (`licId`) REFERENCES `rms_license` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=71 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_licenses`
--

LOCK TABLES `rms_catalogue_item_application_licenses` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_licenses` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_licenses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_member_invite_values`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_member_invite_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_member_invite_values` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(2) DEFAULT NULL,
  `email` varchar(256) NOT NULL,
  `hash` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_catalogue_item_application_member_invite_values_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_member_invite_values`
--

LOCK TABLES `rms_catalogue_item_application_member_invite_values` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_member_invite_values` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_member_invite_values` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_members`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_members` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `memberUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) DEFAULT '-1',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_catalogue_item_application_members_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_members`
--

LOCK TABLES `rms_catalogue_item_application_members` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_members` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_metadata`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_metadata`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_metadata` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `userId` bigint(20) NOT NULL,
  `catAppId` int(11) DEFAULT NULL,
  `key` varchar(32) NOT NULL,
  `value` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `keyvalue` (`key`,`value`(255)),
  CONSTRAINT `rms_catalogue_item_application_metadata_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_metadata`
--

LOCK TABLES `rms_catalogue_item_application_metadata` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_metadata` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_metadata` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_predecessor`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_predecessor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_predecessor` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `pre_catAppId` int(11) DEFAULT NULL,
  `suc_catAppId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `pre_catAppId` (`pre_catAppId`),
  KEY `suc_catAppId` (`suc_catAppId`),
  CONSTRAINT `rms_catalogue_item_application_predecessor_ibfk_1` FOREIGN KEY (`pre_catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_catalogue_item_application_predecessor_ibfk_2` FOREIGN KEY (`suc_catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_predecessor`
--

LOCK TABLES `rms_catalogue_item_application_predecessor` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_predecessor` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_predecessor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_publications`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_publications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_publications` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `publication` varchar(512) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_catalogue_item_application_publications_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_publications`
--

LOCK TABLES `rms_catalogue_item_application_publications` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_publications` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_publications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_reviewers`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_reviewers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_reviewers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `revUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) DEFAULT NULL,
  `round` int(11) NOT NULL,
  `comment` varchar(4096) DEFAULT NULL,
  `state` enum('created','commented') NOT NULL DEFAULT 'created',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_catalogue_item_application_reviewers_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_reviewers`
--

LOCK TABLES `rms_catalogue_item_application_reviewers` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_reviewers` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_reviewers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_state`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_state` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `curround` int(11) NOT NULL,
  `state` enum('applied','approved','rejected','returned','closed','draft','onhold') DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_catalogue_item_application_state_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_state`
--

LOCK TABLES `rms_catalogue_item_application_state` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_application_state_reason`
--

DROP TABLE IF EXISTS `rms_catalogue_item_application_state_reason`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_application_state_reason` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catAppId` int(11) NOT NULL,
  `catAppStateId` int(11) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `reason` varchar(32) NOT NULL,
  `comment` varchar(4096) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catAppId` (`catAppId`),
  KEY `catAppStateId` (`catAppStateId`),
  CONSTRAINT `rms_catalogue_item_application_state_reason_ibfk_1` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`),
  CONSTRAINT `rms_catalogue_item_application_state_reason_ibfk_2` FOREIGN KEY (`catAppStateId`) REFERENCES `rms_catalogue_item_application_state` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_application_state_reason`
--

LOCK TABLES `rms_catalogue_item_application_state_reason` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_application_state_reason` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_application_state_reason` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_localization`
--

DROP TABLE IF EXISTS `rms_catalogue_item_localization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_localization` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catId` int(11) DEFAULT NULL,
  `langCode` varchar(64) NOT NULL,
  `title` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catId` (`catId`),
  CONSTRAINT `rms_catalogue_item_localization_ibfk_1` FOREIGN KEY (`catId`) REFERENCES `rms_catalogue_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_localization`
--

LOCK TABLES `rms_catalogue_item_localization` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_localization` DISABLE KEYS */;
INSERT INTO `rms_catalogue_item_localization` VALUES (1,1,'fi','Dataset 001 Aineisto, jossa on minimaalinen työkulku. Käyttäjä sitoutuu lisenssiehtoihin ja saa käyttöluvan.','2014-03-06 12:00:41',NULL),(2,1,'en','Dataset 001 A dataset with a minimal workflow. The user just commits to licence terms and gets entitlement.','2014-03-06 12:00:41',NULL),(3,2,'fi','Dataset 002 Aineisto, jossa on yksinkertainen työkulku. Hakemus lähetetään yhdelle henkilölle (RDapprover1) hyväksyttäväksi.','2014-03-06 12:04:24',NULL),(4,2,'en','Dataset 002 A dataset with a simple workflow. The application is sent to a single person (RDapprover1) for approval.','2014-03-06 12:04:24',NULL),(5,3,'fi','Dataset 003 Aineisto, jossa on yksinkertainen työkulku. Katselmoija (RDreview) voi kommentoida hakemusta hyväksyjälle.','2014-03-06 12:22:41',NULL),(6,3,'en','Dataset 003 A dataset with a reviewer (RDreview), who can just provide comments on the application to the approver.','2014-03-06 12:22:41',NULL),(7,4,'fi','Dataset 004 Aineisto, jossa on kaksivaiheinen hyväksyntä. Hakemuksen hyväksyy ensin RDapprover1, sitten RDapprover2.','2014-03-06 12:33:47',NULL),(8,4,'en','Dataset 004 A dataset with a two-step approval. The application is first approved by RDapprover1 and then by RDapprover2.','2014-03-06 12:33:47',NULL),(9,5,'fi','Dataset 005 Aineisto, jossa on kaksi rinnakkaista hyväksyjää (RDapprover1, RDapprover2), joista toinen  hyväksyy hakemuksen.','2014-03-06 12:36:58',NULL),(10,5,'en','Dataset 005 A dataset with two parallel approvers (RDapprover1, RDapprover2). Only one needs to approve the application.','2014-03-06 12:36:58',NULL),(11,6,'fi','Dataset 006 Aineisto, jossa on laaja hakulomake, katselmoija (RDreview) ja kaksi perättäistä hyväksyjää.','2014-03-07 09:54:48',NULL),(12,6,'en','Dataset 006 A dataset with an extensive application form, a reviewer (RDreview) and two consecutive approvers.','2014-03-07 09:54:48',NULL);
/*!40000 ALTER TABLE `rms_catalogue_item_localization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_predecessor`
--

DROP TABLE IF EXISTS `rms_catalogue_item_predecessor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_predecessor` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `pre_catId` int(11) DEFAULT NULL,
  `suc_catId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `pre_catId` (`pre_catId`),
  KEY `suc_catId` (`suc_catId`),
  CONSTRAINT `rms_catalogue_item_predecessor_ibfk_1` FOREIGN KEY (`pre_catId`) REFERENCES `rms_catalogue_item` (`id`),
  CONSTRAINT `rms_catalogue_item_predecessor_ibfk_2` FOREIGN KEY (`suc_catId`) REFERENCES `rms_catalogue_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_predecessor`
--

LOCK TABLES `rms_catalogue_item_predecessor` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_predecessor` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_catalogue_item_predecessor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_catalogue_item_state`
--

DROP TABLE IF EXISTS `rms_catalogue_item_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_catalogue_item_state` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `catId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `state` enum('disabled','enabled','copied') DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `catId` (`catId`),
  CONSTRAINT `rms_catalogue_item_state_ibfk_1` FOREIGN KEY (`catId`) REFERENCES `rms_catalogue_item` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_catalogue_item_state`
--

LOCK TABLES `rms_catalogue_item_state` WRITE;
/*!40000 ALTER TABLE `rms_catalogue_item_state` DISABLE KEYS */;
INSERT INTO `rms_catalogue_item_state` VALUES (1,1,11220,'enabled','2014-03-06 13:14:07',NULL),(2,2,11220,'enabled','2014-03-06 13:14:11',NULL),(3,3,11220,'enabled','2014-03-06 13:14:13',NULL),(4,4,11220,'enabled','2014-03-06 13:14:16',NULL),(5,5,11220,'enabled','2014-03-06 13:14:18',NULL),(6,6,11220,'enabled','2014-03-07 09:54:53',NULL);
/*!40000 ALTER TABLE `rms_catalogue_item_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_entitlement`
--

DROP TABLE IF EXISTS `rms_entitlement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_entitlement` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `catAppId` int(11) DEFAULT NULL,
  `userId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  KEY `catAppId` (`catAppId`),
  CONSTRAINT `rms_entitlement_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`),
  CONSTRAINT `rms_entitlement_ibfk_2` FOREIGN KEY (`catAppId`) REFERENCES `rms_catalogue_item_application` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_entitlement`
--

LOCK TABLES `rms_entitlement` WRITE;
/*!40000 ALTER TABLE `rms_entitlement` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_entitlement` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_entitlement_ebi`
--

DROP TABLE IF EXISTS `rms_entitlement_ebi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_entitlement_ebi` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `eppn` varchar(255) NOT NULL,
  `domain` varchar(255) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `dacId` varchar(256) NOT NULL,
  `userId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `dacidend` (`dacId`(255),`end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_entitlement_ebi`
--

LOCK TABLES `rms_entitlement_ebi` WRITE;
/*!40000 ALTER TABLE `rms_entitlement_ebi` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_entitlement_ebi` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_entitlement_saml`
--

DROP TABLE IF EXISTS `rms_entitlement_saml`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_entitlement_saml` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `eppn` varchar(255) NOT NULL,
  `domain` varchar(255) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `entityId` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `eppnentityend` (`eppn`,`entityId`(255),`end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_entitlement_saml`
--

LOCK TABLES `rms_entitlement_saml` WRITE;
/*!40000 ALTER TABLE `rms_entitlement_saml` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_entitlement_saml` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_entitlement_saml_migration`
--

DROP TABLE IF EXISTS `rms_entitlement_saml_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_entitlement_saml_migration` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `eppn` varchar(255) NOT NULL,
  `domain` varchar(255) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `entityId` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `eppnentityend` (`eppn`,`entityId`(255),`end`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_entitlement_saml_migration`
--

LOCK TABLES `rms_entitlement_saml_migration` WRITE;
/*!40000 ALTER TABLE `rms_entitlement_saml_migration` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_entitlement_saml_migration` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_invitations`
--

DROP TABLE IF EXISTS `rms_invitations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_invitations` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(256) NOT NULL,
  `hash` varchar(256) NOT NULL,
  `userId` bigint(20) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_invitations`
--

LOCK TABLES `rms_invitations` WRITE;
/*!40000 ALTER TABLE `rms_invitations` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_invitations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_license`
--

DROP TABLE IF EXISTS `rms_license`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_license` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `title` varchar(256) NOT NULL,
  `type` enum('text','attachment','link') NOT NULL,
  `textContent` varchar(16384) DEFAULT NULL,
  `attId` int(11) DEFAULT NULL,
  `visibility` enum('private','public') NOT NULL DEFAULT 'private',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `attId` (`attId`),
  CONSTRAINT `rms_license_ibfk_1` FOREIGN KEY (`attId`) REFERENCES `rms_attachment` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_license`
--

LOCK TABLES `rms_license` WRITE;
/*!40000 ALTER TABLE `rms_license` DISABLE KEYS */;
INSERT INTO `rms_license` VALUES (1,11220,11220,'REMS Demo License','text','This is an example of a licence terms of a dataset. You must use these datasets for research purposes only. You must destroy the datasets when your research is over. You must report back any publications based on these datasets, using the â€œview application =>report publicationâ€� feature of REMS.',NULL,'private','2014-03-06 09:56:04',NULL),(2,11220,11220,'CC0 1.0','link','https://creativecommons.org/publicdomain/zero/1.0/',NULL,'public','2016-04-06 21:07:04',NULL),(3,11220,11220,'Creative Commons Attribution 4.0','link','https://creativecommons.org/licenses/by/4.0/',NULL,'public','2016-04-06 21:07:04',NULL),(4,11220,11220,'Creative Commons Attribution Share-Alike 4.0','link','https://creativecommons.org/licenses/by-sa/4.0/',NULL,'public','2016-04-06 21:07:04',NULL),(5,11220,11220,'Creative Commons Attribution-NonCommercial 4.0','link','https://creativecommons.org/licenses/by-nc/4.0/',NULL,'public','2016-04-06 21:07:05',NULL),(6,11220,11220,'Open Data Commons Public Domain Dedication and Licence 1.0','link','http://www.opendefinition.org/licenses/odc-pddl',NULL,'public','2016-04-06 21:07:05',NULL),(7,11220,11220,'Open Data Commons Open Database License 1.0','link','http://www.opendefinition.org/licenses/odc-odbl',NULL,'public','2016-04-06 21:07:05',NULL),(8,11220,11220,'Open Data Commons Attribution License 1.0','link','http://www.opendefinition.org/licenses/odc-by',NULL,'public','2016-04-06 21:07:05',NULL),(9,11220,11220,'GNU Free Documentation License 1.3 with no cover texts and no invariant sections','link','http://www.opendefinition.org/licenses/gfdl',NULL,'public','2016-04-06 21:07:05',NULL),(10,11220,11220,'Creative Commons Attribution 3.0','link','https://creativecommons.org/licenses/by/3.0/',NULL,'public','2016-04-06 21:07:06',NULL),(11,11220,11220,'Creative Commons Attribution Share-Alike 3.0','link','https://creativecommons.org/licenses/by-sa/3.0/',NULL,'public','2016-04-06 21:07:06',NULL),(12,11220,11220,'Creative Commons Attribution-NonCommercial 3.0','link','https://creativecommons.org/licenses/by-nc/3.0/',NULL,'public','2016-04-06 21:07:06',NULL),(13,11220,11220,'Creative Commons Attribution 2.0','link','https://creativecommons.org/licenses/by/2.0/',NULL,'public','2016-04-06 21:07:06',NULL),(14,11220,11220,'Creative Commons Attribution Share-Alike 2.0','link','https://creativecommons.org/licenses/by-sa/2.0/',NULL,'public','2016-04-06 21:07:07',NULL),(15,11220,11220,'Creative Commons Attribution-NonCommercial 2.0','link','https://creativecommons.org/licenses/by-nc/2.0/',NULL,'public','2016-04-06 21:07:07',NULL),(16,11220,11220,'Creative Commons Attribution 1.0','link','https://creativecommons.org/licenses/by/1.0/',NULL,'public','2016-04-06 21:07:08',NULL),(17,11220,11220,'Creative Commons Attribution Share-Alike 1.0','link','https://creativecommons.org/licenses/by-sa/1.0/',NULL,'public','2016-04-06 21:07:09',NULL),(18,11220,11220,'Creative Commons Attribution-NonCommercial 1.0','link','https://creativecommons.org/licenses/by-nc/1.0/',NULL,'public','2016-04-06 21:07:11',NULL),(19,11220,11220,'Other','link','',NULL,'public','2016-04-06 21:07:13',NULL),(20,11220,11220,'Other (Open)','link','',NULL,'public','2016-04-06 21:07:13',NULL),(21,11220,11220,'Other (Public Domain)','link','',NULL,'public','2016-04-06 21:07:14',NULL),(22,11220,11220,'Other (Attribution)','link','',NULL,'public','2016-04-06 21:07:14',NULL),(23,11220,11220,'Other (Non-Commercial)','link','',NULL,'public','2016-04-06 21:07:15',NULL),(24,11220,11220,'Other (Not Open)','link','',NULL,'public','2016-04-06 21:07:15',NULL),(25,11220,11220,'License Not Specified','link','',NULL,'public','2016-04-06 21:07:16',NULL),(26,11220,11220,'Apache Software License 2.0','link','http://www.opensource.org/licenses/Apache-2.0',NULL,'public','2016-04-06 21:07:16',NULL),(27,11220,11220,'CLARIN PUB (Public) End-User License 1.0','link','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaPub',NULL,'public','2016-04-06 21:07:17',NULL),(28,11220,11220,'CLARIN ACA (Academic) End-User License 1.0','link','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaAca',NULL,'public','2016-04-06 21:07:17',NULL),(29,11220,11220,'CLARIN ACA+NC (Academic, Non-Commercial) End-User License 1.0','link','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaAcaNCDep',NULL,'public','2016-04-06 21:07:18',NULL),(30,11220,11220,'CLARIN RES (Restricted) End-User License 1.0','link','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaRes',NULL,'public','2016-04-06 21:07:18',NULL),(31,11220,11220,'Creative Commons Attribution-NoDerivatives 3.0','link','https://creativecommons.org/licenses/by-nd/3.0/',NULL,'public','2016-04-06 21:07:19',NULL),(32,11220,11220,'Creative Commons Attribution-NonCommercial-ShareAlike 3.0','link','https://creativecommons.org/licenses/by-nc-sa/3.0/',NULL,'public','2016-04-06 21:07:19',NULL),(33,11220,11220,'Creative Commons Attribution-NonCommercial-NoDerivatives 3.0','link','https://creativecommons.org/licenses/by-nc-nd/3.0/',NULL,'public','2016-04-06 21:07:20',NULL),(34,11220,11220,'Under negotiation','link','',NULL,'public','2016-04-06 21:07:21',NULL),(35,11220,11220,'Open Data Commons Public Domain Dedication and License 1.0','link','http://www.opendefinition.org/licenses/odc-pddl',NULL,'public','2017-11-05 22:07:06',NULL),(36,11220,11220,'Creative Commons Attribution-NoDerivatives 4.0','link','https://creativecommons.org/licenses/by-nd/4.0/',NULL,'public','2017-11-05 22:07:14',NULL),(37,11220,11220,'Creative Commons Attribution-NonCommercial-ShareAlike 4.0','link','https://creativecommons.org/licenses/by-nc-sa/4.0/',NULL,'public','2017-11-05 22:07:15',NULL),(38,11220,11220,'Creative Commons Attribution-NonCommercial-NoDerivatives 4.0','link','https://creativecommons.org/licenses/by-nc-nd/4.0/',NULL,'public','2017-11-05 22:07:16',NULL);
/*!40000 ALTER TABLE `rms_license` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_license_localization`
--

DROP TABLE IF EXISTS `rms_license_localization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_license_localization` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `licId` int(11) DEFAULT NULL,
  `langCode` varchar(64) NOT NULL,
  `title` varchar(256) NOT NULL,
  `textContent` varchar(16384) DEFAULT NULL,
  `attId` int(11) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `licId` (`licId`),
  KEY `attId` (`attId`),
  CONSTRAINT `rms_license_localization_ibfk_1` FOREIGN KEY (`licId`) REFERENCES `rms_license` (`id`),
  CONSTRAINT `rms_license_localization_ibfk_2` FOREIGN KEY (`attId`) REFERENCES `rms_attachment` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_license_localization`
--

LOCK TABLES `rms_license_localization` WRITE;
/*!40000 ALTER TABLE `rms_license_localization` DISABLE KEYS */;
INSERT INTO `rms_license_localization` VALUES (1,1,'fi','REMS Demo lisenssi','Tämä on esimerkki lisenssiehdot aineisolle. Aineistoa tulee käyttää vain tutkimustarkoituksiin. Aineisto on tuhottava kun tukimustyö loppuu. Sinun tulee raportoida kaikista julkaisuista tähän aineistoon liittyen, käyttäen REMS toiminnallisuuksia.',NULL,'2014-03-06 09:56:04',NULL),(2,1,'en','REMS Demo License','This is an example of a licence terms of a dataset. You must use these datasets for research purposes only. You must destroy the datasets when your research is over. You must report back any publications based on these datasets, using the â€œview application =>report publicationâ€� feature of REMS.',NULL,'2014-03-06 09:56:04',NULL),(3,2,'en','CC0 1.0','https://creativecommons.org/publicdomain/zero/1.0/',NULL,'2016-04-06 21:07:04',NULL),(4,3,'en','Creative Commons Attribution 4.0','https://creativecommons.org/licenses/by/4.0/',NULL,'2016-04-06 21:07:04',NULL),(5,4,'en','Creative Commons Attribution Share-Alike 4.0','https://creativecommons.org/licenses/by-sa/4.0/',NULL,'2016-04-06 21:07:04',NULL),(6,5,'en','Creative Commons Attribution-NonCommercial 4.0','https://creativecommons.org/licenses/by-nc/4.0/',NULL,'2016-04-06 21:07:05',NULL),(7,6,'en','Open Data Commons Public Domain Dedication and Licence 1.0','http://www.opendefinition.org/licenses/odc-pddl',NULL,'2016-04-06 21:07:05',NULL),(8,7,'en','Open Data Commons Open Database License 1.0','http://www.opendefinition.org/licenses/odc-odbl',NULL,'2016-04-06 21:07:05',NULL),(9,8,'en','Open Data Commons Attribution License 1.0','http://www.opendefinition.org/licenses/odc-by',NULL,'2016-04-06 21:07:05',NULL),(10,9,'en','GNU Free Documentation License 1.3 with no cover texts and no invariant sections','http://www.opendefinition.org/licenses/gfdl',NULL,'2016-04-06 21:07:05',NULL),(11,10,'en','Creative Commons Attribution 3.0','https://creativecommons.org/licenses/by/3.0/',NULL,'2016-04-06 21:07:06',NULL),(12,11,'en','Creative Commons Attribution Share-Alike 3.0','https://creativecommons.org/licenses/by-sa/3.0/',NULL,'2016-04-06 21:07:06',NULL),(13,12,'en','Creative Commons Attribution-NonCommercial 3.0','https://creativecommons.org/licenses/by-nc/3.0/',NULL,'2016-04-06 21:07:06',NULL),(14,13,'en','Creative Commons Attribution 2.0','https://creativecommons.org/licenses/by/2.0/',NULL,'2016-04-06 21:07:06',NULL),(15,14,'en','Creative Commons Attribution Share-Alike 2.0','https://creativecommons.org/licenses/by-sa/2.0/',NULL,'2016-04-06 21:07:07',NULL),(16,15,'en','Creative Commons Attribution-NonCommercial 2.0','https://creativecommons.org/licenses/by-nc/2.0/',NULL,'2016-04-06 21:07:07',NULL),(17,16,'en','Creative Commons Attribution 1.0','https://creativecommons.org/licenses/by/1.0/',NULL,'2016-04-06 21:07:08',NULL),(18,17,'en','Creative Commons Attribution Share-Alike 1.0','https://creativecommons.org/licenses/by-sa/1.0/',NULL,'2016-04-06 21:07:09',NULL),(19,18,'en','Creative Commons Attribution-NonCommercial 1.0','https://creativecommons.org/licenses/by-nc/1.0/',NULL,'2016-04-06 21:07:11',NULL),(20,19,'en','Other','',NULL,'2016-04-06 21:07:13',NULL),(21,20,'en','Other (Open)','',NULL,'2016-04-06 21:07:13',NULL),(22,21,'en','Other (Public Domain)','',NULL,'2016-04-06 21:07:14',NULL),(23,22,'en','Other (Attribution)','',NULL,'2016-04-06 21:07:14',NULL),(24,23,'en','Other (Non-Commercial)','',NULL,'2016-04-06 21:07:15',NULL),(25,24,'en','Other (Not Open)','',NULL,'2016-04-06 21:07:15',NULL),(26,25,'en','License Not Specified','',NULL,'2016-04-06 21:07:16',NULL),(27,26,'en','Apache Software License 2.0','http://www.opensource.org/licenses/Apache-2.0',NULL,'2016-04-06 21:07:16',NULL),(28,27,'en','CLARIN PUB (Public) End-User License 1.0','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaPub',NULL,'2016-04-06 21:07:17',NULL),(29,28,'en','CLARIN ACA (Academic) End-User License 1.0','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaAca',NULL,'2016-04-06 21:07:17',NULL),(30,29,'en','CLARIN ACA+NC (Academic, Non-Commercial) End-User License 1.0','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaAcaNCDep',NULL,'2016-04-06 21:07:18',NULL),(31,30,'en','CLARIN RES (Restricted) End-User License 1.0','https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/ClarinEulaRes',NULL,'2016-04-06 21:07:18',NULL),(32,31,'en','Creative Commons Attribution-NoDerivatives 3.0','https://creativecommons.org/licenses/by-nd/3.0/',NULL,'2016-04-06 21:07:19',NULL),(33,32,'en','Creative Commons Attribution-NonCommercial-ShareAlike 3.0','https://creativecommons.org/licenses/by-nc-sa/3.0/',NULL,'2016-04-06 21:07:19',NULL),(34,33,'en','Creative Commons Attribution-NonCommercial-NoDerivatives 3.0','https://creativecommons.org/licenses/by-nc-nd/3.0/',NULL,'2016-04-06 21:07:20',NULL),(35,34,'en','Under negotiation','',NULL,'2016-04-06 21:07:21',NULL),(36,35,'en','Open Data Commons Public Domain Dedication and License 1.0','http://www.opendefinition.org/licenses/odc-pddl',NULL,'2017-11-05 22:07:07',NULL),(37,36,'en','Creative Commons Attribution-NoDerivatives 4.0','https://creativecommons.org/licenses/by-nd/4.0/',NULL,'2017-11-05 22:07:14',NULL),(38,37,'en','Creative Commons Attribution-NonCommercial-ShareAlike 4.0','https://creativecommons.org/licenses/by-nc-sa/4.0/',NULL,'2017-11-05 22:07:15',NULL),(39,38,'en','Creative Commons Attribution-NonCommercial-NoDerivatives 4.0','https://creativecommons.org/licenses/by-nc-nd/4.0/',NULL,'2017-11-05 22:07:16',NULL);
/*!40000 ALTER TABLE `rms_license_localization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_license_references`
--

DROP TABLE IF EXISTS `rms_license_references`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_license_references` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `licId` int(11) DEFAULT NULL,
  `referenceName` varchar(64) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `reference` (`rsPrId`,`referenceName`),
  KEY `licId` (`licId`),
  CONSTRAINT `rms_license_references_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`),
  CONSTRAINT `rms_license_references_ibfk_2` FOREIGN KEY (`licId`) REFERENCES `rms_license` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_license_references`
--

LOCK TABLES `rms_license_references` WRITE;
/*!40000 ALTER TABLE `rms_license_references` DISABLE KEYS */;
INSERT INTO `rms_license_references` VALUES (1,1,2,'CC0-1.0',-1,'2016-04-06 21:07:04',NULL),(2,1,3,'CC-BY-4.0',-1,'2016-04-06 21:07:04',NULL),(3,1,4,'CC-BY-SA-4.0',-1,'2016-04-06 21:07:05',NULL),(4,1,5,'CC-BY-NC-4.0',-1,'2016-04-06 21:07:05',NULL),(5,1,6,'ODC-PDDL-1.0',-1,'2016-04-06 21:07:05','2017-11-05 22:07:07'),(6,1,7,'ODbL-1.0',-1,'2016-04-06 21:07:05',NULL),(7,1,8,'ODC-BY-1.0',-1,'2016-04-06 21:07:05',NULL),(8,1,9,'GFDL-1.3-no-cover-texts-no-invariant-sections',-1,'2016-04-06 21:07:06',NULL),(9,1,10,'CC-BY-3.0',-1,'2016-04-06 21:07:06',NULL),(10,1,11,'CC-BY-SA-3.0',-1,'2016-04-06 21:07:06',NULL),(11,1,12,'CC-BY-NC-3.0',-1,'2016-04-06 21:07:06',NULL),(12,1,13,'CC-BY-2.0',-1,'2016-04-06 21:07:06',NULL),(13,1,14,'CC-BY-SA-2.0',-1,'2016-04-06 21:07:07',NULL),(14,1,15,'CC-BY-NC-2.0',-1,'2016-04-06 21:07:07',NULL),(15,1,16,'CC-BY-1.0',-1,'2016-04-06 21:07:08',NULL),(16,1,17,'CC-BY-SA-1.0',-1,'2016-04-06 21:07:10',NULL),(17,1,18,'CC-BY-NC-1.0',-1,'2016-04-06 21:07:11',NULL),(18,1,19,'other',-1,'2016-04-06 21:07:13',NULL),(19,1,20,'other-open',-1,'2016-04-06 21:07:14',NULL),(20,1,21,'other-pd',-1,'2016-04-06 21:07:14',NULL),(21,1,22,'other-at',-1,'2016-04-06 21:07:15',NULL),(22,1,23,'other-nc',-1,'2016-04-06 21:07:15',NULL),(23,1,24,'other-closed',-1,'2016-04-06 21:07:15',NULL),(24,1,25,'notspecified',-1,'2016-04-06 21:07:16',NULL),(25,1,26,'Apache-2.0',-1,'2016-04-06 21:07:16',NULL),(26,1,27,'ClarinPUB-1.0',-1,'2016-04-06 21:07:17',NULL),(27,1,28,'ClarinACA-1.0',-1,'2016-04-06 21:07:17',NULL),(28,1,29,'ClarinACA+NC-1.0',-1,'2016-04-06 21:07:18',NULL),(29,1,30,'ClarinRES-1.0',-1,'2016-04-06 21:07:18',NULL),(30,1,31,'CC-BY-ND-3.0',-1,'2016-04-06 21:07:19',NULL),(31,1,32,'CC-BY-NC-SA-3.0',-1,'2016-04-06 21:07:20',NULL),(32,1,33,'CC-BY-NC-ND-3.0',-1,'2016-04-06 21:07:20',NULL),(33,1,34,'undernegotiation',-1,'2016-04-06 21:07:21',NULL),(34,1,35,'ODC-PDDL-1.0',-1,'2017-11-05 22:07:07',NULL),(35,1,36,'CC-BY-ND-4.0',-1,'2017-11-05 22:07:14',NULL),(36,1,37,'CC-BY-NC-SA-4.0',-1,'2017-11-05 22:07:15',NULL),(37,1,38,'CC-BY-NC-ND-4.0',-1,'2017-11-05 22:07:16',NULL);
/*!40000 ALTER TABLE `rms_license_references` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_manifestations`
--

DROP TABLE IF EXISTS `rms_manifestations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_manifestations` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `manifId` varchar(256) NOT NULL,
  `resId` int(11) NOT NULL,
  `manifConf` varchar(256) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_manifestations_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_manifestations`
--

LOCK TABLES `rms_manifestations` WRITE;
/*!40000 ALTER TABLE `rms_manifestations` DISABLE KEYS */;
INSERT INTO `rms_manifestations` VALUES (1,'csc_rems_demo_SM',2,'https://testsp.funet.fi/shibboleth',11220,'2014-09-15 12:28:22',NULL),(2,'csc_rems_demo_SM',3,'https://testsp.funet.fi/shibboleth',11220,'2014-09-15 12:28:33',NULL),(3,'csc_rems_demo_SM',4,'https://testsp.funet.fi/shibboleth',11220,'2014-09-15 12:28:38',NULL),(4,'csc_rems_demo_SM',5,'https://testsp.funet.fi/shibboleth',11220,'2014-09-15 12:28:43',NULL),(5,'csc_rems_demo_SM',6,'https://testsp.funet.fi/shibboleth',11220,'2014-09-15 12:28:49',NULL),(6,'csc_rems_demo_SM',7,'https://testsp.funet.fi/shibboleth',11220,'2014-09-15 12:28:53',NULL);
/*!40000 ALTER TABLE `rms_manifestations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource`
--

DROP TABLE IF EXISTS `rms_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `modifierUserId` bigint(20) NOT NULL,
  `rsPrId` int(11) DEFAULT NULL,
  `prefix` varchar(255) NOT NULL,
  `resId` varchar(255) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource`
--

LOCK TABLES `rms_resource` WRITE;
/*!40000 ALTER TABLE `rms_resource` DISABLE KEYS */;
INSERT INTO `rms_resource` VALUES (1,11220,1,'REMS-DEMO','0000001','2014-02-19 11:33:26',NULL),(2,11220,1,'REMS-DEMO','001','2014-03-06 09:49:38',NULL),(3,11220,1,'REMS-DEMO','002','2014-03-06 09:49:44',NULL),(4,11220,1,'REMS-DEMO','003','2014-03-06 09:49:48',NULL),(5,11220,1,'REMS-DEMO','004','2014-03-06 09:49:53',NULL),(6,11220,1,'REMS-DEMO','005','2014-03-06 09:49:58',NULL),(7,11220,1,'REMS-DEMO','006','2014-03-06 09:50:03',NULL);
/*!40000 ALTER TABLE `rms_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_close_period`
--

DROP TABLE IF EXISTS `rms_resource_close_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_close_period` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `closePeriod` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_resource_close_period_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_close_period`
--

LOCK TABLES `rms_resource_close_period` WRITE;
/*!40000 ALTER TABLE `rms_resource_close_period` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_close_period` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_licenses`
--

DROP TABLE IF EXISTS `rms_resource_licenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_licenses` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `licId` int(11) DEFAULT NULL,
  `stalling` bit(1) NOT NULL DEFAULT b'0',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  KEY `licId` (`licId`),
  CONSTRAINT `rms_resource_licenses_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`),
  CONSTRAINT `rms_resource_licenses_ibfk_2` FOREIGN KEY (`licId`) REFERENCES `rms_license` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_licenses`
--

LOCK TABLES `rms_resource_licenses` WRITE;
/*!40000 ALTER TABLE `rms_resource_licenses` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_licenses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_link_location`
--

DROP TABLE IF EXISTS `rms_resource_link_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_link_location` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `link` varchar(2048) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_resource_link_location_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_link_location`
--

LOCK TABLES `rms_resource_link_location` WRITE;
/*!40000 ALTER TABLE `rms_resource_link_location` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_link_location` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_mf_ebi_dac_target`
--

DROP TABLE IF EXISTS `rms_resource_mf_ebi_dac_target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_mf_ebi_dac_target` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `dacId` varchar(256) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_resource_mf_ebi_dac_target_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_mf_ebi_dac_target`
--

LOCK TABLES `rms_resource_mf_ebi_dac_target` WRITE;
/*!40000 ALTER TABLE `rms_resource_mf_ebi_dac_target` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_mf_ebi_dac_target` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_mf_saml_target`
--

DROP TABLE IF EXISTS `rms_resource_mf_saml_target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_mf_saml_target` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `entityId` varchar(256) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_resource_mf_saml_target_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_mf_saml_target`
--

LOCK TABLES `rms_resource_mf_saml_target` WRITE;
/*!40000 ALTER TABLE `rms_resource_mf_saml_target` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_mf_saml_target` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix`
--

DROP TABLE IF EXISTS `rms_resource_prefix`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `modifierUserId` bigint(20) NOT NULL,
  `prefix` varchar(255) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix`
--

LOCK TABLES `rms_resource_prefix` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix` VALUES (1,11220,'REMS-DEMO','2014-02-19 10:25:47',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_allow_form_editing`
--

DROP TABLE IF EXISTS `rms_resource_prefix_allow_form_editing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_allow_form_editing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_allow_form_editing_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_allow_form_editing`
--

LOCK TABLES `rms_resource_prefix_allow_form_editing` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_allow_form_editing` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_prefix_allow_form_editing` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_allow_members`
--

DROP TABLE IF EXISTS `rms_resource_prefix_allow_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_allow_members` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_allow_members_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_allow_members`
--

LOCK TABLES `rms_resource_prefix_allow_members` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_allow_members` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_allow_members` VALUES (1,1,'\0',11220,'2014-10-01 11:06:29',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_allow_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_allow_updates`
--

DROP TABLE IF EXISTS `rms_resource_prefix_allow_updates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_allow_updates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_allow_updates_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_allow_updates`
--

LOCK TABLES `rms_resource_prefix_allow_updates` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_allow_updates` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_prefix_allow_updates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_application`
--

DROP TABLE IF EXISTS `rms_resource_prefix_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_application` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `application` varchar(2048) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_application_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_application`
--

LOCK TABLES `rms_resource_prefix_application` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_application` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_application` VALUES (1,1,'REMS-DEMO',11220,'2014-02-19 10:25:47',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_certificates`
--

DROP TABLE IF EXISTS `rms_resource_prefix_certificates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_certificates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `subjectDn` varchar(256) DEFAULT NULL,
  `base64content` varchar(16384) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_certificates_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_certificates`
--

LOCK TABLES `rms_resource_prefix_certificates` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_certificates` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_certificates` VALUES (1,1,'C=FI,ST=Finland,L=Espoo,O=CSC - IT Center for Science Ltd.,OU=IAM,CN=remsdemo,E=rems@csc.fi','MIIDrDCCApQCCQDp/A3tNGX6FzANBgkqhkiG9w0BAQUFADCBlzELMAkGA1UEBhMCRkkxEDAOBgNVBAgMB0ZpbmxhbmQxDjAMBgNVBAcMBUVzcG9vMSkwJwYDVQQKDCBDU0MgLSBJVCBDZW50ZXIgZm9yIFNjaWVuY2UgTHRkLjEMMAoGA1UECwwDSUFNMREwDwYDVQQDDAhyZW1zZGVtbzEaMBgGCSqGSIb3DQEJARYLcmVtc0Bjc2MuZmkwHhcNMTQwMzE3MDgxMzU5WhcNMjQwMzE0MDgxMzU5WjCBlzELMAkGA1UEBhMCRkkxEDAOBgNVBAgMB0ZpbmxhbmQxDjAMBgNVBAcMBUVzcG9vMSkwJwYDVQQKDCBDU0MgLSBJVCBDZW50ZXIgZm9yIFNjaWVuY2UgTHRkLjEMMAoGA1UECwwDSUFNMREwDwYDVQQDDAhyZW1zZGVtbzEaMBgGCSqGSIb3DQEJARYLcmVtc0Bjc2MuZmkwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDZYhlCW/LNbewL7Yp78AdKFeV22eN1czbc1P1uGVLVguClg2F0hJkDaH61QTG2lsElAtlfdjeMv+THbZHjQj0U+EkPPJUCZv2qlFQ4HU8OKsnKCQEfe/ZTRoBd7tQvyG2D8XmP46u5FZPh42TgXSnPRw7nIdTgp2oPi4Ibmtw6ooovlIgUc7GW6JfyvhZ+M1vVwKx1mGRbA4nWh4hTXPlYSOxPgUkpbkRy02BDjs49CSWUR80WLW3DuZLA1BGH3NT7hVyApGTt7xyWAUHXGna9DcvmNZhw63GArqCR+I+BjcR/r/WQNIuMSIxKU1zcdgxzuyvW6nuWrmgInjcqsTwzAgMBAAEwDQYJKoZIhvcNAQEFBQADggEBANZthNPWPJIJ8zJEHsDjLxqYwPrchyWYDt4S6nFKlvsWVjakmWnPa1dwrKpvoVeCOOTlXCO6G/WrGOYPy6M4XDLk+tbaNtDcp6UgSIcu4jrlsKAZ9a3JhH5CI62uXdY4gHf+RhN7fzzc2aAMfDOOR+qEMpsVXN5vSi+S9VG3ZQfyhm89LgtrTbE9pxGeoFPkaRNBdkMONmRQ3i5EiFag/dWfyj7AFI1ebrMjCeb43f5FWTCnBaympCgn6aq5m9L+2CBlmNYtNTy54di4TjUrVksdP2ZGttQuakVRaSPtZ0/y4R7jjds+NCsSPSpRsDh1flXvVbysx4qA3SrPVg02/1M=',11220,'2014-03-18 08:11:57',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_certificates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_close_period`
--

DROP TABLE IF EXISTS `rms_resource_prefix_close_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_close_period` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `closePeriod` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_close_period_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_close_period`
--

LOCK TABLES `rms_resource_prefix_close_period` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_close_period` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_prefix_close_period` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_default_form`
--

DROP TABLE IF EXISTS `rms_resource_prefix_default_form`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_default_form` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `metaFormId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  KEY `metaFormId` (`metaFormId`),
  CONSTRAINT `rms_resource_prefix_default_form_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`),
  CONSTRAINT `rms_resource_prefix_default_form_ibfk_2` FOREIGN KEY (`metaFormId`) REFERENCES `rms_application_form_meta` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_default_form`
--

LOCK TABLES `rms_resource_prefix_default_form` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_default_form` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_default_form` VALUES (1,1,6,11220,'2014-03-18 08:12:32',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_default_form` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_link_location`
--

DROP TABLE IF EXISTS `rms_resource_prefix_link_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_link_location` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `link` varchar(2048) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_link_location_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_link_location`
--

LOCK TABLES `rms_resource_prefix_link_location` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_link_location` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_prefix_link_location` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_mf_ebi`
--

DROP TABLE IF EXISTS `rms_resource_prefix_mf_ebi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_mf_ebi` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_mf_ebi_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_mf_ebi`
--

LOCK TABLES `rms_resource_prefix_mf_ebi` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_mf_ebi` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_prefix_mf_ebi` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_owners`
--

DROP TABLE IF EXISTS `rms_resource_prefix_owners`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_owners` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_owners_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_owners`
--

LOCK TABLES `rms_resource_prefix_owners` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_owners` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_owners` VALUES (1,1,11220,-1,'2014-02-19 10:25:47',NULL),(2,1,10948,11220,'2014-02-19 11:11:59','2014-03-06 09:39:17'),(3,1,11203,11220,'2014-03-04 13:28:55',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_owners` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_refresh_period`
--

DROP TABLE IF EXISTS `rms_resource_prefix_refresh_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_refresh_period` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `refreshPeriod` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_refresh_period_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_refresh_period`
--

LOCK TABLES `rms_resource_prefix_refresh_period` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_refresh_period` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_prefix_refresh_period` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_reporters`
--

DROP TABLE IF EXISTS `rms_resource_prefix_reporters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_reporters` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `reporterUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_reporters_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_reporters`
--

LOCK TABLES `rms_resource_prefix_reporters` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_reporters` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_reporters` VALUES (1,1,11486,11220,'2014-03-06 09:39:07',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_reporters` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_prefix_state`
--

DROP TABLE IF EXISTS `rms_resource_prefix_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_prefix_state` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rsPrId` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `state` enum('applied','approved','denied') NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `rsPrId` (`rsPrId`),
  CONSTRAINT `rms_resource_prefix_state_ibfk_1` FOREIGN KEY (`rsPrId`) REFERENCES `rms_resource_prefix` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_prefix_state`
--

LOCK TABLES `rms_resource_prefix_state` WRITE;
/*!40000 ALTER TABLE `rms_resource_prefix_state` DISABLE KEYS */;
INSERT INTO `rms_resource_prefix_state` VALUES (1,1,11220,'applied','2014-02-19 10:25:47','2014-02-19 10:30:07'),(2,1,11220,'approved','2014-02-19 10:30:07',NULL);
/*!40000 ALTER TABLE `rms_resource_prefix_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_refresh_period`
--

DROP TABLE IF EXISTS `rms_resource_refresh_period`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_refresh_period` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `refreshPeriod` int(11) DEFAULT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_resource_refresh_period_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_refresh_period`
--

LOCK TABLES `rms_resource_refresh_period` WRITE;
/*!40000 ALTER TABLE `rms_resource_refresh_period` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_resource_refresh_period` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_resource_state`
--

DROP TABLE IF EXISTS `rms_resource_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_resource_state` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `resId` int(11) DEFAULT NULL,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `resId` (`resId`),
  CONSTRAINT `rms_resource_state_ibfk_1` FOREIGN KEY (`resId`) REFERENCES `rms_resource` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_resource_state`
--

LOCK TABLES `rms_resource_state` WRITE;
/*!40000 ALTER TABLE `rms_resource_state` DISABLE KEYS */;
INSERT INTO `rms_resource_state` VALUES (1,1,11220,11220,'2014-02-19 11:33:26','2014-02-19 11:33:52'),(2,1,10948,11220,'2014-02-19 11:33:48',NULL),(3,2,11220,11220,'2014-03-06 09:49:38',NULL),(4,3,11220,11220,'2014-03-06 09:49:44',NULL),(5,4,11220,11220,'2014-03-06 09:49:48',NULL),(6,5,11220,11220,'2014-03-06 09:49:53',NULL),(7,6,11220,11220,'2014-03-06 09:49:58',NULL),(8,7,11220,11220,'2014-03-06 09:50:03',NULL),(9,2,11220,11220,'2014-03-06 13:12:55',NULL),(10,3,11220,11220,'2014-03-06 13:13:08',NULL),(11,4,11220,11220,'2014-03-06 13:13:16',NULL),(12,5,11220,11220,'2014-03-06 13:13:22',NULL),(13,6,11220,11220,'2014-03-06 13:13:27',NULL),(14,7,11220,11220,'2014-03-06 13:13:32',NULL);
/*!40000 ALTER TABLE `rms_resource_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_user_selection_names`
--

DROP TABLE IF EXISTS `rms_user_selection_names`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_user_selection_names` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `actionId` bigint(20) NOT NULL,
  `groupId` int(11) NOT NULL,
  `listName` varchar(32) DEFAULT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `actionId` (`actionId`,`groupId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_user_selection_names`
--

LOCK TABLES `rms_user_selection_names` WRITE;
/*!40000 ALTER TABLE `rms_user_selection_names` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_user_selection_names` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_user_selections`
--

DROP TABLE IF EXISTS `rms_user_selections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_user_selections` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `actionId` bigint(20) NOT NULL,
  `groupId` int(11) NOT NULL,
  `userId` bigint(20) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `actionId` (`actionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_user_selections`
--

LOCK TABLES `rms_user_selections` WRITE;
/*!40000 ALTER TABLE `rms_user_selections` DISABLE KEYS */;
/*!40000 ALTER TABLE `rms_user_selections` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_workflow`
--

DROP TABLE IF EXISTS `rms_workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_workflow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `ownerUserId` bigint(20) NOT NULL,
  `modifierUserId` bigint(20) NOT NULL,
  `title` varchar(256) NOT NULL,
  `fnlround` int(11) NOT NULL,
  `visibility` enum('private','public') NOT NULL DEFAULT 'private',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_workflow`
--

LOCK TABLES `rms_workflow` WRITE;
/*!40000 ALTER TABLE `rms_workflow` DISABLE KEYS */;
INSERT INTO `rms_workflow` VALUES (1,11220,11220,'Minimal',0,'public','2014-03-06 11:01:50',NULL),(2,11220,11220,'Simple workflow, 1 approver',0,'public','2014-03-06 11:09:15',NULL),(3,11220,11220,'Simple workflow, 1 approver with 1 reviewer',0,'public','2014-03-06 11:10:28',NULL),(4,11220,11220,'Moderate workflow, 2 approval rounds',1,'public','2014-03-06 11:13:33',NULL),(5,11220,11220,'Moderate workflow, 2 parallel approvers',0,'private','2014-03-06 11:16:33',NULL),(6,11220,11220,'Moderate workflow, 2 approval rounds with reviewer',1,'public','2014-03-07 09:27:08',NULL);
/*!40000 ALTER TABLE `rms_workflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_workflow_approver_options`
--

DROP TABLE IF EXISTS `rms_workflow_approver_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_workflow_approver_options` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `wfApprId` int(11) DEFAULT NULL,
  `keyValue` varchar(256) NOT NULL,
  `optionValue` varchar(256) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `wfApprId` (`wfApprId`),
  CONSTRAINT `rms_workflow_approver_options_ibfk_1` FOREIGN KEY (`wfApprId`) REFERENCES `rms_workflow_approvers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_workflow_approver_options`
--

LOCK TABLES `rms_workflow_approver_options` WRITE;
/*!40000 ALTER TABLE `rms_workflow_approver_options` DISABLE KEYS */;
INSERT INTO `rms_workflow_approver_options` VALUES (1,1,'check_reject','FALSE','2014-03-06 11:09:15',NULL),(2,1,'check_return','FALSE','2014-03-06 11:09:15',NULL),(3,1,'check_super_approve','FALSE','2014-03-06 11:09:15',NULL),(4,1,'check_super_reject','FALSE','2014-03-06 11:09:15',NULL),(5,1,'check_dynamic_review','FALSE','2014-03-06 11:09:15',NULL),(6,1,'check_public_decision','FALSE','2014-03-06 11:09:15',NULL),(7,2,'check_reject','FALSE','2014-03-06 11:10:28',NULL),(8,2,'check_return','FALSE','2014-03-06 11:10:28',NULL),(9,2,'check_super_approve','FALSE','2014-03-06 11:10:28',NULL),(10,2,'check_super_reject','FALSE','2014-03-06 11:10:28',NULL),(11,2,'check_dynamic_review','FALSE','2014-03-06 11:10:28',NULL),(12,2,'check_public_decision','FALSE','2014-03-06 11:10:28',NULL),(13,3,'check_reject','FALSE','2014-03-06 11:13:33',NULL),(14,3,'check_return','FALSE','2014-03-06 11:13:33',NULL),(15,3,'check_super_approve','FALSE','2014-03-06 11:13:33',NULL),(16,3,'check_super_reject','FALSE','2014-03-06 11:13:33',NULL),(17,3,'check_dynamic_review','FALSE','2014-03-06 11:13:33',NULL),(18,3,'check_public_decision','FALSE','2014-03-06 11:13:33',NULL),(19,4,'check_reject','FALSE','2014-03-06 11:13:33',NULL),(20,4,'check_return','FALSE','2014-03-06 11:13:33',NULL),(21,4,'check_super_approve','FALSE','2014-03-06 11:13:33',NULL),(22,4,'check_super_reject','FALSE','2014-03-06 11:13:33',NULL),(23,4,'check_dynamic_review','FALSE','2014-03-06 11:13:33',NULL),(24,4,'check_public_decision','FALSE','2014-03-06 11:13:33',NULL),(25,5,'check_reject','FALSE','2014-03-06 11:16:33',NULL),(26,5,'check_return','FALSE','2014-03-06 11:16:33',NULL),(27,5,'check_super_approve','FALSE','2014-03-06 11:16:33',NULL),(28,5,'check_super_reject','FALSE','2014-03-06 11:16:33',NULL),(29,5,'check_dynamic_review','FALSE','2014-03-06 11:16:33',NULL),(30,5,'check_public_decision','FALSE','2014-03-06 11:16:33',NULL),(31,6,'check_reject','FALSE','2014-03-06 11:16:33',NULL),(32,6,'check_return','FALSE','2014-03-06 11:16:33',NULL),(33,6,'check_super_approve','FALSE','2014-03-06 11:16:33',NULL),(34,6,'check_super_reject','FALSE','2014-03-06 11:16:33',NULL),(35,6,'check_dynamic_review','FALSE','2014-03-06 11:16:33',NULL),(36,6,'check_public_decision','FALSE','2014-03-06 11:16:33',NULL),(37,7,'check_reject','FALSE','2014-03-07 09:27:08',NULL),(38,7,'check_return','FALSE','2014-03-07 09:27:08',NULL),(39,7,'check_super_approve','FALSE','2014-03-07 09:27:08',NULL),(40,7,'check_super_reject','FALSE','2014-03-07 09:27:08',NULL),(41,7,'check_dynamic_review','FALSE','2014-03-07 09:27:08',NULL),(42,7,'check_public_decision','FALSE','2014-03-07 09:27:08',NULL),(43,8,'check_reject','FALSE','2014-03-07 09:27:08',NULL),(44,8,'check_return','FALSE','2014-03-07 09:27:08',NULL),(45,8,'check_super_approve','FALSE','2014-03-07 09:27:08',NULL),(46,8,'check_super_reject','FALSE','2014-03-07 09:27:08',NULL),(47,8,'check_dynamic_review','FALSE','2014-03-07 09:27:08',NULL),(48,8,'check_public_decision','FALSE','2014-03-07 09:27:08',NULL);
/*!40000 ALTER TABLE `rms_workflow_approver_options` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_workflow_approvers`
--

DROP TABLE IF EXISTS `rms_workflow_approvers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_workflow_approvers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `wfId` int(11) DEFAULT NULL,
  `apprUserId` bigint(20) NOT NULL,
  `round` int(11) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `wfId` (`wfId`),
  CONSTRAINT `rms_workflow_approvers_ibfk_1` FOREIGN KEY (`wfId`) REFERENCES `rms_workflow` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_workflow_approvers`
--

LOCK TABLES `rms_workflow_approvers` WRITE;
/*!40000 ALTER TABLE `rms_workflow_approvers` DISABLE KEYS */;
INSERT INTO `rms_workflow_approvers` VALUES (1,2,11254,0,'2014-03-06 11:09:15',NULL),(2,3,11254,0,'2014-03-06 11:10:28',NULL),(3,4,11254,0,'2014-03-06 11:13:33',NULL),(4,4,11271,1,'2014-03-06 11:13:33',NULL),(5,5,11254,0,'2014-03-06 11:16:33',NULL),(6,5,11271,0,'2014-03-06 11:16:33',NULL),(7,6,11254,0,'2014-03-07 09:27:08',NULL),(8,6,11271,1,'2014-03-07 09:27:08',NULL);
/*!40000 ALTER TABLE `rms_workflow_approvers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_workflow_licenses`
--

DROP TABLE IF EXISTS `rms_workflow_licenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_workflow_licenses` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `wfId` int(11) DEFAULT NULL,
  `licId` int(11) DEFAULT NULL,
  `round` int(11) NOT NULL,
  `stalling` bit(1) NOT NULL DEFAULT b'0',
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `wfId` (`wfId`),
  KEY `licId` (`licId`),
  CONSTRAINT `rms_workflow_licenses_ibfk_1` FOREIGN KEY (`wfId`) REFERENCES `rms_workflow` (`id`),
  CONSTRAINT `rms_workflow_licenses_ibfk_2` FOREIGN KEY (`licId`) REFERENCES `rms_license` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_workflow_licenses`
--

LOCK TABLES `rms_workflow_licenses` WRITE;
/*!40000 ALTER TABLE `rms_workflow_licenses` DISABLE KEYS */;
INSERT INTO `rms_workflow_licenses` VALUES (1,1,1,0,'\0','2014-03-06 11:01:50',NULL);
/*!40000 ALTER TABLE `rms_workflow_licenses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_workflow_reviewers`
--

DROP TABLE IF EXISTS `rms_workflow_reviewers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_workflow_reviewers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `wfId` int(11) DEFAULT NULL,
  `revUserId` bigint(20) NOT NULL,
  `round` int(11) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `wfId` (`wfId`),
  CONSTRAINT `rms_workflow_reviewers_ibfk_1` FOREIGN KEY (`wfId`) REFERENCES `rms_workflow` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_workflow_reviewers`
--

LOCK TABLES `rms_workflow_reviewers` WRITE;
/*!40000 ALTER TABLE `rms_workflow_reviewers` DISABLE KEYS */;
INSERT INTO `rms_workflow_reviewers` VALUES (1,3,11237,0,'2014-03-06 11:10:28',NULL),(2,6,11237,0,'2014-03-07 09:27:08',NULL),(3,6,11237,1,'2014-03-07 09:27:08',NULL);
/*!40000 ALTER TABLE `rms_workflow_reviewers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rms_workflow_round_min`
--

DROP TABLE IF EXISTS `rms_workflow_round_min`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rms_workflow_round_min` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `wfId` int(11) DEFAULT NULL,
  `min` int(11) NOT NULL,
  `round` int(11) NOT NULL,
  `start` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `wfId` (`wfId`),
  CONSTRAINT `rms_workflow_round_min_ibfk_1` FOREIGN KEY (`wfId`) REFERENCES `rms_workflow` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rms_workflow_round_min`
--

LOCK TABLES `rms_workflow_round_min` WRITE;
/*!40000 ALTER TABLE `rms_workflow_round_min` DISABLE KEYS */;
INSERT INTO `rms_workflow_round_min` VALUES (1,2,1,0,'2014-03-06 11:09:15',NULL),(2,3,1,0,'2014-03-06 11:10:28',NULL),(3,4,1,0,'2014-03-06 11:13:33',NULL),(4,4,1,1,'2014-03-06 11:13:33',NULL),(5,5,1,0,'2014-03-06 11:16:33',NULL),(6,6,1,0,'2014-03-07 09:27:08',NULL),(7,6,1,1,'2014-03-07 09:27:08',NULL);
/*!40000 ALTER TABLE `rms_workflow_round_min` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2017-11-06 15:38:04
