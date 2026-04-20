CREATE DATABASE IF NOT EXISTS kungfu_stock DEFAULT CHARACTER SET utf8mb4;
USE kungfu_stock;

CREATE TABLE IF NOT EXISTS t_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    product_name VARCHAR(100),
    quantity INT NOT NULL DEFAULT 0,
    locked INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- Seata undo_log
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id BIGINT NOT NULL COMMENT 'branch transaction id',
    xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    context VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',
    log_status INT NOT NULL COMMENT '0:normal status,1:defense status',
    log_created DATETIME(6) NOT NULL COMMENT 'create datetime',
    log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO t_stock (product_id, product_name, quantity) VALUES (1, 'iPhone 15', 100);
INSERT IGNORE INTO t_stock (product_id, product_name, quantity) VALUES (2, 'MacBook Pro', 50);
INSERT IGNORE INTO t_stock (product_id, product_name, quantity) VALUES (3, 'AirPods Pro', 200);
