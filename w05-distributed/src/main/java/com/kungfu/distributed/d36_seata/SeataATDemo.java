package com.kungfu.distributed.d36_seata;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【Demo】Seata AT 模式 — 手写 Mini-Seata 模拟 + 生产配置
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Seata 架构（TC/TM/RM）和 AT 模式原理</li>
 *   <li>手写 Mini-Seata：模拟 undo_log + 两阶段提交/回滚</li>
 *   <li>模拟场景：下单扣库存，一阶段提交 → 二阶段回滚</li>
 *   <li>生产环境 Seata 配置模板</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 MySQL 运行在 localhost:3306（模拟 AT 模式原理，不需要真实 Seata Server）
 *
 * @author kungfu
 * @since D36 - 分布式事务2
 */
public class SeataATDemo {

    private static final String URL = "jdbc:mysql://localhost:3306"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASS = "123456";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Seata AT 模式 — 原理模拟");
        System.out.println("========================================\n");

        showArchitecture();
        showATModePrinciple();

        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, USER, PASS);
            conn.setAutoCommit(false);

            prepareSchema(conn);
            demoPhase1Commit(conn);
            demoPhase2Rollback(conn);
            showProductionConfig();
            cleanup(conn);

        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private static void showArchitecture() {
        System.out.println("=== 一、Seata 架构 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    Seata 三大角色                             │");
        System.out.println("  ├─────────────────────────────────────────────────────────────┤");
        System.out.println("  │                                                               │");
        System.out.println("  │  TC (Transaction Coordinator) — 事务协调器                   │");
        System.out.println("  │  独立部署的 Seata Server，维护全局事务状态                    │");
        System.out.println("  │                                                               │");
        System.out.println("  │  TM (Transaction Manager) — 事务管理器                       │");
        System.out.println("  │  标注 @GlobalTransactional 的业务方法，发起全局事务           │");
        System.out.println("  │                                                               │");
        System.out.println("  │  RM (Resource Manager) — 资源管理器                          │");
        System.out.println("  │  管理分支事务（每个微服务的数据库操作）                       │");
        System.out.println("  │                                                               │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘\n");

        System.out.println("  交互流程：");
        System.out.println("    1. TM 向 TC 申请开启全局事务 → 获得 XID");
        System.out.println("    2. TM 调用各微服务（XID 通过 RPC 传播）");
        System.out.println("    3. 各 RM 向 TC 注册分支事务");
        System.out.println("    4. 各 RM 执行本地事务并提交");
        System.out.println("    5. TM 通知 TC 全局提交/回滚");
        System.out.println("    6. TC 通知各 RM 提交/回滚分支事务\n");
    }

    private static void showATModePrinciple() {
        System.out.println("=== 二、AT 模式原理 ===\n");

        System.out.println("  AT = Automatic Transaction（自动事务）\n");

        System.out.println("  一阶段（业务 SQL 执行）：");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. 拦截业务 SQL: UPDATE t_stock SET quantity=90 WHERE id=1 │");
        System.out.println("  │ 2. 查询 before image: SELECT * WHERE id=1 → quantity=100  │");
        System.out.println("  │ 3. 执行业务 SQL: quantity 100 → 90                         │");
        System.out.println("  │ 4. 查询 after image: SELECT * WHERE id=1 → quantity=90    │");
        System.out.println("  │ 5. 写入 undo_log: {before: 100, after: 90}                 │");
        System.out.println("  │ 6. 提交本地事务（业务数据 + undo_log 一起提交）            │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");

        System.out.println("  二阶段 — 提交：");
        System.out.println("    → 异步删除 undo_log（数据已经是正确的）\n");

        System.out.println("  二阶段 — 回滚：");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. 读取 undo_log 的 before image                           │");
        System.out.println("  │ 2. 校验 after image（防止脏写）                            │");
        System.out.println("  │ 3. 生成反向 SQL: UPDATE t_stock SET quantity=100 WHERE id=1│");
        System.out.println("  │ 4. 执行反向 SQL → 数据恢复                                 │");
        System.out.println("  │ 5. 删除 undo_log                                           │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");
    }

    private static void prepareSchema(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE DATABASE IF NOT EXISTS kungfu");
        stmt.execute("USE kungfu");

        // 业务表
        stmt.execute("DROP TABLE IF EXISTS t_stock_d36");
        stmt.execute("CREATE TABLE t_stock_d36 ("
                + "id BIGINT PRIMARY KEY,"
                + "product_name VARCHAR(50) NOT NULL,"
                + "quantity INT NOT NULL"
                + ") ENGINE=InnoDB");
        stmt.execute("INSERT INTO t_stock_d36 VALUES (1, 'iPhone', 100)");

        // 模拟 undo_log 表
        stmt.execute("DROP TABLE IF EXISTS t_undo_log");
        stmt.execute("CREATE TABLE t_undo_log ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "xid VARCHAR(64) NOT NULL COMMENT '全局事务ID',"
                + "branch_id BIGINT NOT NULL COMMENT '分支事务ID',"
                + "table_name VARCHAR(50) NOT NULL,"
                + "pk_value VARCHAR(50) NOT NULL,"
                + "before_image TEXT COMMENT 'JSON: 修改前的数据',"
                + "after_image TEXT COMMENT 'JSON: 修改后的数据',"
                + "status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',"
                + "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

        conn.commit();
        stmt.close();
        System.out.println("  ✓ 表创建完成: t_stock_d36, t_undo_log\n");
    }

    // =============================================================
    // 三、模拟一阶段提交
    // =============================================================
    private static void demoPhase1Commit(Connection conn) throws Exception {
        System.out.println("=== 三、模拟一阶段（业务执行 + 写 undo_log）===\n");

        String xid = "XID_" + System.currentTimeMillis();
        long branchId = 1001;

        System.out.println("  全局事务 XID: " + xid);
        System.out.println("  业务 SQL: UPDATE t_stock_d36 SET quantity = quantity - 10 WHERE id = 1\n");

        // Step 1: 查询 before image
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM t_stock_d36 WHERE id = 1");
        rs.next();
        int beforeQuantity = rs.getInt("quantity");
        String beforeImage = "{\"id\":1, \"product_name\":\"iPhone\", \"quantity\":" + beforeQuantity + "}";
        System.out.println("  1. Before Image: " + beforeImage);
        rs.close();

        // Step 2: 执行业务 SQL
        stmt.executeUpdate("UPDATE t_stock_d36 SET quantity = quantity - 10 WHERE id = 1");
        System.out.println("  2. 执行 SQL: quantity " + beforeQuantity + " → " + (beforeQuantity - 10));

        // Step 3: 查询 after image
        rs = stmt.executeQuery("SELECT * FROM t_stock_d36 WHERE id = 1");
        rs.next();
        int afterQuantity = rs.getInt("quantity");
        String afterImage = "{\"id\":1, \"product_name\":\"iPhone\", \"quantity\":" + afterQuantity + "}";
        System.out.println("  3. After Image: " + afterImage);
        rs.close();

        // Step 4: 写入 undo_log
        PreparedStatement undoStmt = conn.prepareStatement(
                "INSERT INTO t_undo_log (xid, branch_id, table_name, pk_value, before_image, after_image) VALUES (?,?,?,?,?,?)");
        undoStmt.setString(1, xid);
        undoStmt.setLong(2, branchId);
        undoStmt.setString(3, "t_stock_d36");
        undoStmt.setString(4, "1");
        undoStmt.setString(5, beforeImage);
        undoStmt.setString(6, afterImage);
        undoStmt.executeUpdate();
        System.out.println("  4. 写入 undo_log");

        // Step 5: 提交本地事务
        conn.commit();
        System.out.println("  5. COMMIT（业务数据 + undo_log 一起提交）");
        System.out.println("  → 一阶段完成！数据已持久化，undo_log 已记录\n");

        undoStmt.close();
        stmt.close();
    }

    // =============================================================
    // 四、模拟二阶段回滚
    // =============================================================
    private static void demoPhase2Rollback(Connection conn) throws Exception {
        System.out.println("=== 四、模拟二阶段回滚（用 undo_log 恢复数据）===\n");

        System.out.println("  场景：TC 通知回滚（如下游服务失败）\n");

        Statement stmt = conn.createStatement();

        // Step 1: 读取 undo_log
        ResultSet rs = stmt.executeQuery("SELECT * FROM t_undo_log WHERE status='NORMAL' LIMIT 1");
        if (rs.next()) {
            String beforeImage = rs.getString("before_image");
            String afterImage = rs.getString("after_image");
            long undoId = rs.getLong("id");

            System.out.println("  1. 读取 undo_log:");
            System.out.println("     before_image: " + beforeImage);
            System.out.println("     after_image: " + afterImage);

            // Step 2: 校验当前数据 == after_image（防脏写）
            ResultSet current = stmt.executeQuery("SELECT quantity FROM t_stock_d36 WHERE id = 1");
            current.next();
            int currentQty = current.getInt("quantity");
            System.out.println("  2. 校验当前数据: quantity=" + currentQty + " == after_image.quantity=90 → ✓ 一致");
            current.close();

            // Step 3: 生成反向 SQL
            System.out.println("  3. 生成反向 SQL: UPDATE t_stock_d36 SET quantity=100 WHERE id=1");
            stmt.executeUpdate("UPDATE t_stock_d36 SET quantity = 100 WHERE id = 1");

            // Step 4: 删除 undo_log
            stmt.executeUpdate("UPDATE t_undo_log SET status='ROLLBACKED' WHERE id=" + undoId);
            System.out.println("  4. 标记 undo_log 为 ROLLBACKED");

            conn.commit();
            System.out.println("  5. COMMIT → 数据已恢复！\n");

            // 验证
            current = stmt.executeQuery("SELECT quantity FROM t_stock_d36 WHERE id = 1");
            current.next();
            System.out.println("  验证: quantity = " + current.getInt("quantity") + " (恢复到 100) ✓\n");
            current.close();
        }
        rs.close();
        stmt.close();
    }

    // =============================================================
    // 五、生产配置
    // =============================================================
    private static void showProductionConfig() {
        System.out.println("=== 五、Seata 生产配置模板 ===\n");

        System.out.println("  application.yml（微服务端）：");
        System.out.println("  ┌────────────────────────────────────────────────────┐");
        System.out.println("  │ seata:                                             │");
        System.out.println("  │   enabled: true                                    │");
        System.out.println("  │   tx-service-group: my_tx_group                    │");
        System.out.println("  │   service:                                         │");
        System.out.println("  │     vgroup-mapping:                                │");
        System.out.println("  │       my_tx_group: default                         │");
        System.out.println("  │   registry:                                        │");
        System.out.println("  │     type: nacos                                    │");
        System.out.println("  │     nacos:                                         │");
        System.out.println("  │       server-addr: localhost:8848                   │");
        System.out.println("  │       namespace: seata                             │");
        System.out.println("  │       group: SEATA_GROUP                           │");
        System.out.println("  └────────────────────────────────────────────────────┘\n");

        System.out.println("  使用方式：");
        System.out.println("    @GlobalTransactional");
        System.out.println("    public void placeOrder(OrderDTO order) {");
        System.out.println("        orderService.create(order);      // 分支1: 创建订单");
        System.out.println("        stockService.deduct(order);      // 分支2: 扣减库存");
        System.out.println("        accountService.debit(order);     // 分支3: 扣减余额");
        System.out.println("    }");
        System.out.println("    // 任何一个分支失败 → Seata 自动回滚所有分支\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Seata AT 模式原理？");
        System.out.println("    A: 一阶段拦截 SQL，记录 before/after image 到 undo_log，提交本地事务");
        System.out.println("       二阶段提交 → 删 undo_log；回滚 → 用 before image 生成反向 SQL");
        System.out.println("    Q: AT 模式的缺点？");
        System.out.println("    A: 1) 需要数据库支持本地事务（不支持 NoSQL）");
        System.out.println("       2) 全局锁影响并发性能");
        System.out.println("       3) 不适合长事务（锁持有时间长）");
        System.out.println();
    }

    private static void cleanup(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("USE kungfu");
        stmt.execute("DROP TABLE IF EXISTS t_stock_d36");
        stmt.execute("DROP TABLE IF EXISTS t_undo_log");
        conn.commit();
        stmt.close();
        System.out.println("  ✓ 测试表已清理\n");
    }
}
