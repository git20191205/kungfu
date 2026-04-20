-- ============================================
-- D24: 事务隔离 Demo 用表
-- ============================================

CREATE DATABASE IF NOT EXISTS kungfu DEFAULT CHARACTER SET utf8mb4;
USE kungfu;

DROP TABLE IF EXISTS t_account;
CREATE TABLE t_account (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(50)   NOT NULL,
    balance DECIMAL(10,2) NOT NULL DEFAULT 0.00
) ENGINE=InnoDB COMMENT='账户表（D24 事务Demo）';

INSERT INTO t_account (name, balance) VALUES ('Alice', 1000.00);
INSERT INTO t_account (name, balance) VALUES ('Bob', 1000.00);
