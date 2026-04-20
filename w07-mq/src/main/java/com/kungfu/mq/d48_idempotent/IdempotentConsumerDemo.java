package com.kungfu.mq.d48_idempotent;

import java.sql.*;
import java.util.UUID;

/**
 * 【Demo】消息重复消费 — 幂等性设计
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>为什么消息会被重复消费</li>
 *   <li>方案1: 数据库唯一键去重</li>
 *   <li>方案2: 业务状态机幂等</li>
 *   <li>模拟：同一消息消费 3 次，只处理 1 次</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 MySQL 运行在 localhost:3306
 *
 * @author kungfu
 * @since D48 - 消息重复消费
 */
public class IdempotentConsumerDemo {

    private static final String URL = "jdbc:mysql://localhost:3306"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASS = "123456";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  消息重复消费 — 幂等性设计");
        System.out.println("========================================\n");

        showWhyDuplicate();

        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, USER, PASS);
            conn.setAutoCommit(false);

            prepareSchema(conn);
            demoUniqueKeyDedup(conn);
            demoStateMachine(conn);
            showStrategies();
            cleanup(conn);

        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private static void showWhyDuplicate() {
        System.out.println("=== 一、为什么消息会被重复消费 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 场景1: Consumer Rebalance                                    │");
        System.out.println("  │   Consumer 处理完消息但未提交 offset → Rebalance 发生        │");
        System.out.println("  │   → 新 Consumer 从旧 offset 开始消费 → 重复                 │");
        System.out.println("  │                                                              │");
        System.out.println("  │ 场景2: Consumer 宕机                                         │");
        System.out.println("  │   处理完消息 → 准备提交 offset → 宕机                       │");
        System.out.println("  │   → 重启后从未提交的 offset 开始 → 重复                     │");
        System.out.println("  │                                                              │");
        System.out.println("  │ 场景3: Producer 重试                                         │");
        System.out.println("  │   Producer 发送成功但未收到 ACK → 重试 → Broker 收到两条     │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 结论：At-Least-Once 语义下，重复消费不可避免");
        System.out.println("    → 消费端必须做幂等处理\n");
    }

    private static void prepareSchema(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu");
        stmt.execute("USE kungfu");

        stmt.execute("DROP TABLE IF EXISTS t_consume_log");
        stmt.execute("CREATE TABLE t_consume_log ("
                + "msg_id VARCHAR(64) PRIMARY KEY,"
                + "topic VARCHAR(50) NOT NULL,"
                + "processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

        stmt.execute("DROP TABLE IF EXISTS t_order_d48");
        stmt.execute("CREATE TABLE t_order_d48 ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "order_no VARCHAR(64) NOT NULL UNIQUE,"
                + "status VARCHAR(20) NOT NULL DEFAULT 'CREATED',"
                + "amount DECIMAL(10,2)"
                + ") ENGINE=InnoDB");
        stmt.execute("INSERT INTO t_order_d48 (order_no, status, amount) VALUES ('ORD_001', 'CREATED', 299.00)");

        conn.commit();
        stmt.close();
        System.out.println("  ✓ 表创建完成\n");
    }

    // =============================================================
    // 方案1: 数据库唯一键去重
    // =============================================================
    private static void demoUniqueKeyDedup(Connection conn) throws Exception {
        System.out.println("=== 二、方案1: 数据库唯一键去重 ===\n");

        String msgId = UUID.randomUUID().toString();
        System.out.println("  模拟同一消息消费 3 次: msgId=" + msgId + "\n");

        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("  第 " + attempt + " 次消费: ");

            // 检查是否已消费
            PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM t_consume_log WHERE msg_id = ?");
            check.setString(1, msgId);
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                System.out.println("✗ 已消费过，跳过（幂等）");
                rs.close();
                check.close();
                continue;
            }
            rs.close();
            check.close();

            // 处理业务 + 记录消费日志（同一事务）
            PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO t_consume_log (msg_id, topic) VALUES (?, ?)");
            insert.setString(1, msgId);
            insert.setString(2, "demo-d48");
            insert.executeUpdate();
            insert.close();

            // 模拟业务处理
            System.out.println("✓ 首次消费，执行业务逻辑");
            conn.commit();
        }
        System.out.println();
    }

    // =============================================================
    // 方案2: 业务状态机幂等
    // =============================================================
    private static void demoStateMachine(Connection conn) throws Exception {
        System.out.println("=== 三、方案2: 业务状态机幂等 ===\n");

        System.out.println("  订单状态机: CREATED → PAID → SHIPPED → DELIVERED");
        System.out.println("  规则: 只能正向流转，不能回退\n");

        System.out.println("  模拟：收到 3 次「支付成功」消息\n");

        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("  第 " + attempt + " 次收到支付消息: ");

            // 用 UPDATE ... WHERE status='CREATED' 保证幂等
            PreparedStatement update = conn.prepareStatement(
                    "UPDATE t_order_d48 SET status='PAID' WHERE order_no='ORD_001' AND status='CREATED'");
            int affected = update.executeUpdate();
            conn.commit();
            update.close();

            if (affected > 0) {
                System.out.println("✓ 状态 CREATED→PAID，执行业务");
            } else {
                System.out.println("✗ 状态已不是 CREATED，跳过（幂等）");
            }
        }
        System.out.println();

        System.out.println("  ★ 关键 SQL: UPDATE ... WHERE status='CREATED'");
        System.out.println("    第一次: affected=1（状态变更成功）");
        System.out.println("    后续:   affected=0（状态已变，不再处理）\n");
    }

    private static void showStrategies() {
        System.out.println("=== 四、幂等策略对比 ===\n");

        System.out.println("  ┌──────────────────┬──────────────────────────┬──────────────────────────┐");
        System.out.println("  │ 方案             │ 实现方式                 │ 适用场景                  │");
        System.out.println("  ├──────────────────┼──────────────────────────┼──────────────────────────┤");
        System.out.println("  │ 唯一键去重 ★    │ msg_id 做 DB 主键/唯一索引│ 通用，最常用             │");
        System.out.println("  │ 状态机           │ WHERE status=前置状态    │ 有状态流转的业务         │");
        System.out.println("  │ Redis SETNX      │ SETNX msg_id + TTL      │ 高并发，允许短暂窗口     │");
        System.out.println("  │ 乐观锁           │ WHERE version=N          │ 更新操作                 │");
        System.out.println("  │ Token 机制       │ 请求前获取 token，用后删除│ 前端防重复提交           │");
        System.out.println("  └──────────────────┴──────────────────────────┴──────────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: 如何保证消息不被重复消费？");
        System.out.println("    A: 消费端做幂等处理：");
        System.out.println("       1) 用 msg_id 做数据库唯一键，消费前查重");
        System.out.println("       2) 业务层面用状态机（只能正向流转）");
        System.out.println("       3) 高并发场景用 Redis SETNX 做分布式去重");
        System.out.println();
    }

    private static void cleanup(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("USE kungfu");
        stmt.execute("DROP TABLE IF EXISTS t_consume_log");
        stmt.execute("DROP TABLE IF EXISTS t_order_d48");
        conn.commit();
        stmt.close();
    }
}
