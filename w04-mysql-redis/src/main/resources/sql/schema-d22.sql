-- ============================================
-- D22/D23: 索引 + 执行计划 Demo 用表
-- 使用方法: Demo 启动时自动执行
-- ============================================

CREATE DATABASE IF NOT EXISTS kungfu DEFAULT CHARACTER SET utf8mb4;
USE kungfu;

-- 订单表（索引演示核心表）
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no    VARCHAR(32)  NOT NULL COMMENT '订单号',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    product_id  BIGINT       NOT NULL COMMENT '商品ID',
    amount      DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0待支付 1已支付 2已发货',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 演示用索引（Demo 中会动态添加/删除）
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='订单表（D22/D23 索引Demo）';

-- 用户表（JOIN 演示）
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email    VARCHAR(100),
    age      INT,
    city     VARCHAR(50),
    INDEX idx_username (username),
    INDEX idx_city_age (city, age)
) ENGINE=InnoDB COMMENT='用户表（D22/D23 索引Demo）';
