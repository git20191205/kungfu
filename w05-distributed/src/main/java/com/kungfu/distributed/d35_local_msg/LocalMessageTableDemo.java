package com.kungfu.distributed.d35_local_msg;

import java.sql.*;
import java.util.UUID;

/**
 * 【Demo】本地消息表 — 分布式事务方案
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>本地消息表原理和完整流程</li>
 *   <li>模拟：下单 + 写消息表在同一个事务中</li>
 *   <li>模拟：定时扫描未发送消息 → 发送 → 标记已发送</li>
 *   <li>模拟：消费端幂等处理</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 MySQL 运行在 localhost:3306
 *
 * @author kungfu
 * @since D35 - 分布式事务1
 */
public class LocalMessageTableDemo {

    private static final String URL = "jdbc:mysql://localhost:3306"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASS = "123456";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  本地消息表 — 分布式事务");
        System.out.println("========================================\n");

        showPrinciple();

        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, USER, PASS);
            conn.setAutoCommit(false);

            prepareSchema(conn);
            demoPlaceOrder(conn);
            demoScanAndSend(conn);
            demoIdempotentConsume(conn);
            showComparison();
            cleanup(conn);

        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private static void showPrinciple() {
        System.out.println("=== 一、本地消息表原理 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 订单服务                                                     │");
        System.out.println("  │ ┌─────────────────────────────────────────────────────────┐ │");
        System.out.println("  │ │ 本地事务（同一个 MySQL 事务）                            │ │");
        System.out.println("  │ │   1. INSERT INTO t_order (下单)                          │ │");
        System.out.println("  │ │   2. INSERT INTO t_local_msg (写消息表)                  │ │");
        System.out.println("  │ │   3. COMMIT                                              │ │");
        System.out.println("  │ └─────────────────────────────────────────────────────────┘ │");
        System.out.println("  │                                                               │");
        System.out.println("  │ 定时任务（每 5s 扫描一次）                                   │");
        System.out.println("  │   4. SELECT * FROM t_local_msg WHERE status='PENDING'        │");
        System.out.println("  │   5. 发送消息到 MQ / 调用下游服务                            │");
        System.out.println("  │   6. UPDATE t_local_msg SET status='SENT'                    │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.println("                               ↓ MQ / HTTP");
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 库存服务                                                     │");
        System.out.println("  │   7. 消费消息 → 扣减库存                                    │");
        System.out.println("  │   8. 幂等处理（根据 msg_id 去重）                           │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘\n");
    }

    private static void prepareSchema(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu");
        stmt.execute("USE kungfu");

        stmt.execute("DROP TABLE IF EXISTS t_order_d35");
        stmt.execute("CREATE TABLE t_order_d35 ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "order_no VARCHAR(64) NOT NULL,"
                + "user_id BIGINT NOT NULL,"
                + "amount DECIMAL(10,2) NOT NULL,"
                + "status VARCHAR(20) NOT NULL DEFAULT 'CREATED'"
                + ") ENGINE=InnoDB");

        stmt.execute("DROP TABLE IF EXISTS t_local_msg");
        stmt.execute("CREATE TABLE t_local_msg ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "msg_id VARCHAR(64) NOT NULL UNIQUE COMMENT '消息唯一ID',"
                + "msg_body TEXT NOT NULL COMMENT '消息内容(JSON)',"
                + "status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/CONSUMED',"
                + "retry_count INT NOT NULL DEFAULT 0,"
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

        conn.commit();
        stmt.close();
        System.out.println("  ✓ 表创建完成: t_order_d35, t_local_msg\n");
    }

    // =============================================================
    // 二、下单 + 写消息表（同一事务）
    // =============================================================
    private static void demoPlaceOrder(Connection conn) throws Exception {
        System.out.println("=== 二、下单 + 写消息表（同一事务）===\n");

        String orderNo = "ORD_" + System.currentTimeMillis();
        String msgId = UUID.randomUUID().toString();

        // 同一个事务中：下单 + 写消息
        PreparedStatement orderStmt = conn.prepareStatement(
                "INSERT INTO t_order_d35 (order_no, user_id, amount) VALUES (?, ?, ?)");
        orderStmt.setString(1, orderNo);
        orderStmt.setLong(2, 1001);
        orderStmt.setBigDecimal(3, new java.math.BigDecimal("299.00"));
        orderStmt.executeUpdate();
        System.out.println("  1. INSERT t_order_d35: " + orderNo);

        PreparedStatement msgStmt = conn.prepareStatement(
                "INSERT INTO t_local_msg (msg_id, msg_body) VALUES (?, ?)");
        msgStmt.setString(1, msgId);
        msgStmt.setString(2, "{\"orderNo\":\"" + orderNo + "\",\"action\":\"DEDUCT_STOCK\",\"productId\":1001,\"quantity\":1}");
        msgStmt.executeUpdate();
        System.out.println("  2. INSERT t_local_msg: msgId=" + msgId);

        conn.commit();
        System.out.println("  3. COMMIT → 订单和消息在同一个事务中提交\n");

        System.out.println("  ★ 关键：如果下单失败 → 消息也不会写入（事务回滚）");
        System.out.println("    如果消息写入失败 → 订单也会回滚");
        System.out.println("    → 保证了「要么都成功，要么都失败」\n");

        orderStmt.close();
        msgStmt.close();
    }

    // =============================================================
    // 三、定时扫描 + 发送
    // =============================================================
    private static void demoScanAndSend(Connection conn) throws Exception {
        System.out.println("=== 三、定时扫描未发送消息 ===\n");

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM t_local_msg WHERE status='PENDING'");

        System.out.println("  扫描 PENDING 消息：");
        while (rs.next()) {
            String msgId = rs.getString("msg_id");
            String body = rs.getString("msg_body");
            int retryCount = rs.getInt("retry_count");

            System.out.println("    msgId=" + msgId);
            System.out.println("    body=" + body);
            System.out.println("    retryCount=" + retryCount);

            // 模拟发送消息（实际是发到 MQ 或调用 HTTP）
            boolean sendSuccess = true; // 模拟成功
            System.out.println("    → 发送消息... " + (sendSuccess ? "✓ 成功" : "✗ 失败"));

            if (sendSuccess) {
                PreparedStatement update = conn.prepareStatement(
                        "UPDATE t_local_msg SET status='SENT' WHERE msg_id=?");
                update.setString(1, msgId);
                update.executeUpdate();
                update.close();
                System.out.println("    → 更新状态: PENDING → SENT");
            }
        }
        conn.commit();
        rs.close();
        stmt.close();
        System.out.println();
    }

    // =============================================================
    // 四、消费端幂等
    // =============================================================
    private static void demoIdempotentConsume(Connection conn) throws Exception {
        System.out.println("=== 四、消费端幂等处理 ===\n");

        System.out.println("  幂等策略：用 msg_id 做去重");
        System.out.println("  ┌────────────────────────────────────────────────────────┐");
        System.out.println("  │ 消费流程：                                              │");
        System.out.println("  │ 1. 收到消息，提取 msg_id                                │");
        System.out.println("  │ 2. SELECT * FROM t_consume_log WHERE msg_id = ?         │");
        System.out.println("  │ 3. 如果已存在 → 跳过（重复消费）                        │");
        System.out.println("  │ 4. 如果不存在 → 执行业务 + INSERT t_consume_log          │");
        System.out.println("  │ 5. 业务和去重记录在同一个事务中                          │");
        System.out.println("  └────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 为什么需要幂等？");
        System.out.println("    - 网络超时 → 生产者重发消息");
        System.out.println("    - MQ 重试 → 消费者收到重复消息");
        System.out.println("    - 定时扫描 → 可能重复发送");
        System.out.println("    → 消费端必须能处理重复消息\n");
    }

    private static void showComparison() {
        System.out.println("=== 五、分布式事务方案对比 ===\n");

        System.out.println("  ┌──────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("  │ 方案             │ 一致性       │ 性能         │ 复杂度       │ 适用场景     │");
        System.out.println("  ├──────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 本地消息表 ★    │ 最终一致     │ ★★★       │ ★           │ 跨服务调用   │");
        System.out.println("  │ 事务消息(RocketMQ)│ 最终一致    │ ★★★       │ ★★         │ 有 MQ 的场景 │");
        System.out.println("  │ Seata AT         │ 强一致       │ ★★         │ ★★         │ 简单跨库事务 │");
        System.out.println("  │ Seata TCC        │ 强一致       │ ★★★       │ ★★★       │ 高性能场景   │");
        System.out.println("  │ 2PC (XA)         │ 强一致       │ ★           │ ★★         │ 传统企业应用 │");
        System.out.println("  │ Saga             │ 最终一致     │ ★★         │ ★★★       │ 长事务       │");
        System.out.println("  └──────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘\n");
    }

    private static void cleanup(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("USE kungfu");
        stmt.execute("DROP TABLE IF EXISTS t_order_d35");
        stmt.execute("DROP TABLE IF EXISTS t_local_msg");
        conn.commit();
        stmt.close();
        System.out.println("  ✓ 测试表已清理\n");
    }
}
