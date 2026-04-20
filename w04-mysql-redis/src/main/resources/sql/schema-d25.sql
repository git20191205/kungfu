-- ============================================
-- D25: 锁机制 Demo 用表
-- ============================================

CREATE DATABASE IF NOT EXISTS kungfu DEFAULT CHARACTER SET utf8mb4;
USE kungfu;

DROP TABLE IF EXISTS t_inventory;
CREATE TABLE t_inventory (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku      VARCHAR(32) NOT NULL COMMENT '商品SKU',
    quantity INT         NOT NULL DEFAULT 0 COMMENT '库存数量',
    version  INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE INDEX idx_sku (sku)
) ENGINE=InnoDB COMMENT='库存表（D25 锁Demo）';

INSERT INTO t_inventory (sku, quantity) VALUES ('SKU001', 100);
INSERT INTO t_inventory (sku, quantity) VALUES ('SKU002', 200);
INSERT INTO t_inventory (sku, quantity) VALUES ('SKU003', 50);
INSERT INTO t_inventory (sku, quantity) VALUES ('SKU005', 80);
INSERT INTO t_inventory (sku, quantity) VALUES ('SKU010', 150);
