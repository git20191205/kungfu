package com.kungfu.seckill.common.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 数据库初始化工具 — 创建 kungfu_seckill 库 + 表 + 初始数据
 */
public class DatabaseInitializer {

    private static final String URL = "jdbc:mysql://localhost:3306?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            // 1. 创建数据库
            stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu_seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            stmt.execute("USE kungfu_seckill");

            // 2. 秒杀活动表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_seckill_activity ("
                    + "  id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "  activity_name VARCHAR(128) NOT NULL COMMENT '活动名称',"
                    + "  product_id BIGINT NOT NULL COMMENT '商品ID',"
                    + "  product_name VARCHAR(128) NOT NULL COMMENT '商品名称',"
                    + "  original_price DECIMAL(10,2) NOT NULL COMMENT '原价',"
                    + "  seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',"
                    + "  total_stock INT NOT NULL COMMENT '总库存',"
                    + "  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/ACTIVE/ENDED',"
                    + "  start_time DATETIME NOT NULL COMMENT '开始时间',"
                    + "  end_time DATETIME NOT NULL COMMENT '结束时间',"
                    + "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表'");

            // 3. 秒杀库存表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_seckill_stock ("
                    + "  id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "  activity_id BIGINT NOT NULL COMMENT '活动ID',"
                    + "  total_stock INT NOT NULL COMMENT '总库存',"
                    + "  available_stock INT NOT NULL COMMENT '可用库存',"
                    + "  lock_stock INT NOT NULL DEFAULT 0 COMMENT '锁定库存',"
                    + "  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',"
                    + "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  UNIQUE KEY uk_activity (activity_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存表'");

            // 4. 秒杀订单表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_seckill_order ("
                    + "  id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "  order_no VARCHAR(64) NOT NULL COMMENT '订单号',"
                    + "  activity_id BIGINT NOT NULL COMMENT '活动ID',"
                    + "  user_id BIGINT NOT NULL COMMENT '用户ID',"
                    + "  product_id BIGINT NOT NULL COMMENT '商品ID',"
                    + "  product_name VARCHAR(128) DEFAULT NULL COMMENT '商品名称',"
                    + "  seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',"
                    + "  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/CANCELLED/TIMEOUT',"
                    + "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  UNIQUE KEY uk_order_no (order_no),"
                    + "  UNIQUE KEY uk_activity_user (activity_id, user_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表'");

            // 5. 秒杀支付表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_seckill_payment ("
                    + "  id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "  payment_no VARCHAR(64) NOT NULL COMMENT '支付单号',"
                    + "  order_no VARCHAR(64) NOT NULL COMMENT '订单号',"
                    + "  amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',"
                    + "  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/FAILED',"
                    + "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  UNIQUE KEY uk_payment_no (payment_no),"
                    + "  INDEX idx_order_no (order_no)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀支付表'");

            // 7. Seata undo_log 表
            stmt.execute("CREATE TABLE IF NOT EXISTS undo_log ("
                    + "  branch_id BIGINT NOT NULL COMMENT 'branch transaction id',"
                    + "  xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',"
                    + "  context VARCHAR(128) NOT NULL COMMENT 'undo_log context, such as serialization',"
                    + "  rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',"
                    + "  log_status INT NOT NULL COMMENT '0: normal status, 1: defense status',"
                    + "  log_created DATETIME(6) NOT NULL COMMENT 'create datetime',"
                    + "  log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',"
                    + "  UNIQUE KEY ux_undo_log (xid, branch_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table'");

            // 8. 插入初始数据 — iPhone 15 秒杀活动
            stmt.execute("INSERT IGNORE INTO t_seckill_activity "
                    + "(id, activity_name, product_id, product_name, original_price, seckill_price, total_stock, status, start_time, end_time) "
                    + "VALUES (1, 'iPhone 15 限时秒杀', 1001, 'iPhone 15 128GB', 5999.00, 4999.00, 100, 'ACTIVE', "
                    + "'2025-01-01 00:00:00', '2025-12-31 23:59:59')");

            stmt.execute("INSERT IGNORE INTO t_seckill_stock "
                    + "(id, activity_id, total_stock, available_stock, lock_stock) "
                    + "VALUES (1, 1, 100, 100, 0)");

            System.out.println("[OK] kungfu_seckill 数据库初始化完成!");
            System.out.println("  - t_seckill_activity  (1 条初始数据)");
            System.out.println("  - t_seckill_stock     (1 条初始数据)");
            System.out.println("  - t_seckill_order     (空表)");
            System.out.println("  - t_seckill_payment   (空表)");
            System.out.println("  - undo_log            (Seata AT 模式)");
        }
    }
}
