package com.kungfu.order.common;

import java.sql.*;

/**
 * 数据库初始化工具 — 创建所有业务库和表
 * 运行一次即可，后续不需要再跑
 */
public class DatabaseInitializer {

    private static final String URL = "jdbc:mysql://localhost:3306"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASS = "123456";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            System.out.println("=== 初始化 W08 数据库 ===\n");

            // 1. kungfu_order
            stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu_order DEFAULT CHARACTER SET utf8mb4");
            stmt.execute("USE kungfu_order");
            stmt.execute("CREATE TABLE IF NOT EXISTS t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "order_no VARCHAR(64) NOT NULL UNIQUE,"
                    + "user_id BIGINT NOT NULL,"
                    + "product_id BIGINT NOT NULL,"
                    + "quantity INT NOT NULL,"
                    + "amount DECIMAL(10,2) NOT NULL,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB");
            stmt.execute("CREATE TABLE IF NOT EXISTS undo_log ("
                    + "branch_id BIGINT NOT NULL,"
                    + "xid VARCHAR(128) NOT NULL,"
                    + "context VARCHAR(128) NOT NULL,"
                    + "rollback_info LONGBLOB NOT NULL,"
                    + "log_status INT NOT NULL,"
                    + "log_created DATETIME(6) NOT NULL,"
                    + "log_modified DATETIME(6) NOT NULL,"
                    + "UNIQUE KEY ux_undo_log (xid, branch_id)"
                    + ") ENGINE=InnoDB");
            System.out.println("  ✓ kungfu_order: t_order + undo_log");

            // 2. kungfu_stock
            stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu_stock DEFAULT CHARACTER SET utf8mb4");
            stmt.execute("USE kungfu_stock");
            stmt.execute("CREATE TABLE IF NOT EXISTS t_stock ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "product_id BIGINT NOT NULL UNIQUE,"
                    + "product_name VARCHAR(100),"
                    + "quantity INT NOT NULL DEFAULT 0,"
                    + "locked INT NOT NULL DEFAULT 0"
                    + ") ENGINE=InnoDB");
            stmt.execute("CREATE TABLE IF NOT EXISTS undo_log ("
                    + "branch_id BIGINT NOT NULL,"
                    + "xid VARCHAR(128) NOT NULL,"
                    + "context VARCHAR(128) NOT NULL,"
                    + "rollback_info LONGBLOB NOT NULL,"
                    + "log_status INT NOT NULL,"
                    + "log_created DATETIME(6) NOT NULL,"
                    + "log_modified DATETIME(6) NOT NULL,"
                    + "UNIQUE KEY ux_undo_log (xid, branch_id)"
                    + ") ENGINE=InnoDB");
            // 初始数据
            stmt.execute("INSERT IGNORE INTO t_stock (product_id, product_name, quantity) VALUES (1, 'iPhone 15', 100)");
            stmt.execute("INSERT IGNORE INTO t_stock (product_id, product_name, quantity) VALUES (2, 'MacBook Pro', 50)");
            stmt.execute("INSERT IGNORE INTO t_stock (product_id, product_name, quantity) VALUES (3, 'AirPods Pro', 200)");
            System.out.println("  ✓ kungfu_stock: t_stock + undo_log + 3 products");

            // 3. kungfu_payment
            stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu_payment DEFAULT CHARACTER SET utf8mb4");
            stmt.execute("USE kungfu_payment");
            stmt.execute("CREATE TABLE IF NOT EXISTS t_payment ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "payment_no VARCHAR(64) NOT NULL UNIQUE,"
                    + "order_no VARCHAR(64) NOT NULL,"
                    + "amount DECIMAL(10,2) NOT NULL,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB");
            System.out.println("  ✓ kungfu_payment: t_payment");

            System.out.println("\n=== 初始化完成 ===");
        }
    }
}
