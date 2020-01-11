CREATE SCHEMA `microssa`;

CREATE TABLE `microssa`.`inbound_orders` (
  `sequence_number` INT NOT NULL AUTO_INCREMENT,
  `action` CHAR(1) NOT NULL,
  `order_id` VARCHAR(45) NOT NULL,
  `symbol` VARCHAR(45) NOT NULL,
  `customer` VARCHAR(45) NOT NULL,
  `arrive_date` CHAR(8) NOT NULL,
  `tif` CHAR(3) NOT NULL,
  `currency` CHAR(3) NULL,
  `side` CHAR(1) NOT NULL,
  `price` DOUBLE NOT NULL,
  `quantity` DOUBLE NOT NULL,
  `available_quantity` DOUBLE NULL,
  `min_fill_quantity` DOUBLE NULL,
  PRIMARY KEY (`sequence_number`));

CREATE TABLE `microssa`.`trades` (
  `sequence_number` INT NOT NULL AUTO_INCREMENT,
  `order_id` VARCHAR(45) NOT NULL,
  `internal_id` VARCHAR(45) NOT NULL,
  `symbol` VARCHAR(45) NOT NULL,
  `customer` VARCHAR(45) NOT NULL,
  `currency` CHAR(3) NOT NULL,
  `side` CHAR(1) NOT NULL,
  `price` DOUBLE NOT NULL,
  `quantity` DOUBLE NOT NULL,
  `role` CHAR(1) NOT NULL,
  PRIMARY KEY (`sequence_number`));

