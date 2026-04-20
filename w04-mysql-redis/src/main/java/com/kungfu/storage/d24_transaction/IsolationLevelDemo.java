package com.kungfu.storage.d24_transaction;

import com.kungfu.storage.common.DBUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 【Demo】MySQL 4 种事务隔离级别实战 — 脏读/不可重复读/幻读
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>READ UNCOMMITTED: 脏读 — 读到其他事务未提交的数据</li>
 *   <li>READ COMMITTED: 不可重复读 — 同一事务两次读取结果不同</li>
 *   <li>REPEATABLE READ: InnoDB 默认级别，防止幻读（通过 MVCC + 间隙锁）</li>
 *   <li>SERIALIZABLE: 串行化 — 读加共享锁，写被阻塞</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："MySQL 默认隔离级别是什么？4 种隔离级别的区别？InnoDB 怎么解决幻读？"
 * 隔离级别是事务的核心概念，直接影响数据一致性和并发性能
 *
 * <h3>运行方式</h3>
 * <pre>
 * 1. 启动 MySQL，创建 kungfu 数据库
 * 2. 直接运行 main 方法，Demo 会自动建表、插入数据、演示并清理
 * </pre>
 *
 * @author kungfu
 * @since D24 - 事务隔离
 */
public class IsolationLevelDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  MySQL 4 种事务隔离级别实战");
        System.out.println("========================================\n");

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            System.out.println("  ✓ MySQL 连接成功\n");

            // 准备表和数据
            prepareTable(conn);

            // 展示隔离级别总览
            showOverview();

        } catch (Exception e) {
            System.out.println("  ✗ 初始化失败: " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            DBUtil.close(conn);
        }

        // 四种隔离级别演示（每个场景独立连接）
        demo1_ReadUncommitted();
        demo2_ReadCommitted();
        demo3_RepeatableRead();
        demo4_Serializable();

        // 最终总结
        showSummary();

        // 清理
        cleanupTable();
    }

    // =============================================================
    // 准备测试表
    // =============================================================

    private static void prepareTable(Connection conn) throws Exception {
        System.out.println("=== 准备测试数据 ===\n");
        DBUtil.execute(conn, "CREATE DATABASE IF NOT EXISTS kungfu");
        DBUtil.execute(conn, "USE kungfu");
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_account");
        DBUtil.execute(conn,
                "CREATE TABLE t_account (" +
                "  id INT PRIMARY KEY," +
                "  name VARCHAR(32) NOT NULL," +
                "  balance INT NOT NULL" +
                ") ENGINE=InnoDB");
        DBUtil.execute(conn, "INSERT INTO t_account VALUES (1, 'Alice', 1000), (2, 'Bob', 1000)");
        System.out.println("  ✓ 表 t_account 已创建");
        System.out.println("    id=1, name='Alice', balance=1000");
        System.out.println("    id=2, name='Bob',   balance=1000\n");
    }

    /** 重置表数据到初始状态 */
    private static void resetData() {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            DBUtil.execute(conn, "DELETE FROM t_account");
            DBUtil.execute(conn, "INSERT INTO t_account VALUES (1, 'Alice', 1000), (2, 'Bob', 1000)");
        } catch (Exception e) {
            System.out.println("    ✗ 重置数据失败: " + e.getMessage());
        } finally {
            DBUtil.close(conn);
        }
    }

    private static void cleanupTable() {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            DBUtil.execute(conn, "DROP TABLE IF EXISTS t_account");
            System.out.println("  ✓ 测试表已清理\n");
        } catch (Exception e) {
            System.out.println("  ✗ 清理失败: " + e.getMessage());
        } finally {
            DBUtil.close(conn);
        }
    }

    // =============================================================
    // 隔离级别总览
    // =============================================================

    private static void showOverview() {
        System.out.println("=== 4 种隔离级别总览 ===\n");
        System.out.println("  ┌────────────────────┬──────────┬──────────────┬──────────┐");
        System.out.println("  │ 隔离级别           │ 脏读     │ 不可重复读   │ 幻读     │");
        System.out.println("  ├────────────────────┼──────────┼──────────────┼──────────┤");
        System.out.println("  │ READ UNCOMMITTED   │ 可能 ✗   │ 可能 ✗       │ 可能 ✗   │");
        System.out.println("  │ READ COMMITTED     │ 防止 ✓   │ 可能 ✗       │ 可能 ✗   │");
        System.out.println("  │ REPEATABLE READ ★ │ 防止 ✓   │ 防止 ✓       │ 防止 ✓*  │");
        System.out.println("  │ SERIALIZABLE       │ 防止 ✓   │ 防止 ✓       │ 防止 ✓   │");
        System.out.println("  └────────────────────┴──────────┴──────────────┴──────────┘");
        System.out.println("  ★ InnoDB 默认级别");
        System.out.println("  * InnoDB 的 RR 通过 MVCC + 间隙锁解决幻读（SQL标准下 RR 不防止幻读）\n");
    }

    // =============================================================
    // 场景1: READ UNCOMMITTED — 脏读
    // =============================================================

    private static void demo1_ReadUncommitted() {
        System.out.println("===========================================================");
        System.out.println("  场景1: READ UNCOMMITTED — 脏读演示");
        System.out.println("===========================================================\n");

        System.out.println("  时间线：");
        System.out.println("    conn1                            conn2");
        System.out.println("    ─────                            ─────");
        System.out.println("    BEGIN");
        System.out.println("    UPDATE Alice balance → 9999");
        System.out.println("    （未提交！）");
        System.out.println("                                     BEGIN");
        System.out.println("                                     SELECT → 能读到 9999？");
        System.out.println("    ROLLBACK");
        System.out.println("                                     SELECT → 回到 1000\n");

        Connection conn1 = null;
        Connection conn2 = null;
        try {
            conn1 = DBUtil.getConnection(false);
            conn2 = DBUtil.getConnection(false);
            DBUtil.execute(conn1, "USE kungfu");
            DBUtil.execute(conn2, "USE kungfu");

            // 设置隔离级别
            setIsolationLevel(conn1, "READ UNCOMMITTED");
            setIsolationLevel(conn2, "READ UNCOMMITTED");

            // conn1: 修改但不提交
            System.out.println("    [conn1] UPDATE t_account SET balance = 9999 WHERE name = 'Alice'");
            DBUtil.execute(conn1, "UPDATE t_account SET balance = 9999 WHERE name = 'Alice'");
            System.out.println("    [conn1] 未提交！\n");

            // conn2: 读取 — 能读到未提交的数据（脏读）
            System.out.println("    [conn2] SELECT balance FROM t_account WHERE name = 'Alice'");
            int balance = queryBalance(conn2, "Alice");
            System.out.println("    [conn2] 读到 balance = " + balance);
            if (balance == 9999) {
                System.out.println("    ★ 脏读发生！conn2 读到了 conn1 未提交的 9999\n");
            } else {
                System.out.println("    （未发生脏读，balance = " + balance + "）\n");
            }

            // conn1 回滚
            conn1.rollback();
            System.out.println("    [conn1] ROLLBACK — 数据回滚到 1000");

            // conn2 再读
            balance = queryBalance(conn2, "Alice");
            System.out.println("    [conn2] 再次读取 balance = " + balance);
            System.out.println("    → conn2 之前读到的 9999 是「脏数据」，实际从未真正生效！\n");

            conn2.commit();
        } catch (Exception e) {
            System.out.println("    ✗ 演示异常: " + e.getMessage() + "\n");
        } finally {
            DBUtil.close(conn1);
            DBUtil.close(conn2);
        }

        resetData();
    }

    // =============================================================
    // 场景2: READ COMMITTED — 不可重复读
    // =============================================================

    private static void demo2_ReadCommitted() {
        System.out.println("===========================================================");
        System.out.println("  场景2: READ COMMITTED — 不可重复读演示");
        System.out.println("===========================================================\n");

        System.out.println("  时间线：");
        System.out.println("    conn1                            conn2");
        System.out.println("    ─────                            ─────");
        System.out.println("                                     BEGIN");
        System.out.println("                                     SELECT → 1000（第一次读）");
        System.out.println("    BEGIN");
        System.out.println("    UPDATE Alice → 2000");
        System.out.println("    COMMIT");
        System.out.println("                                     SELECT → 2000（第二次读，值变了！）\n");

        Connection conn1 = null;
        Connection conn2 = null;
        try {
            conn1 = DBUtil.getConnection(false);
            conn2 = DBUtil.getConnection(false);
            DBUtil.execute(conn1, "USE kungfu");
            DBUtil.execute(conn2, "USE kungfu");

            setIsolationLevel(conn1, "READ COMMITTED");
            setIsolationLevel(conn2, "READ COMMITTED");

            // conn2: 第一次读
            System.out.println("    [conn2] 第一次 SELECT");
            int balance1 = queryBalance(conn2, "Alice");
            System.out.println("    [conn2] balance = " + balance1 + "\n");

            // conn1: 修改并提交
            System.out.println("    [conn1] UPDATE t_account SET balance = 2000 WHERE name = 'Alice'");
            DBUtil.execute(conn1, "UPDATE t_account SET balance = 2000 WHERE name = 'Alice'");
            conn1.commit();
            System.out.println("    [conn1] COMMIT\n");

            // conn2: 第二次读
            System.out.println("    [conn2] 第二次 SELECT");
            int balance2 = queryBalance(conn2, "Alice");
            System.out.println("    [conn2] balance = " + balance2 + "\n");

            if (balance1 != balance2) {
                System.out.println("    ★ 不可重复读发生！同一事务内两次读到不同值");
                System.out.println("      第一次: " + balance1 + " → 第二次: " + balance2);
                System.out.println("      原因: RC 每次 SELECT 创建新 ReadView，能看到 conn1 的最新提交\n");
            }

            conn2.commit();
        } catch (Exception e) {
            System.out.println("    ✗ 演示异常: " + e.getMessage() + "\n");
        } finally {
            DBUtil.close(conn1);
            DBUtil.close(conn2);
        }

        resetData();
    }

    // =============================================================
    // 场景3: REPEATABLE READ — 幻读防护
    // =============================================================

    private static void demo3_RepeatableRead() {
        System.out.println("===========================================================");
        System.out.println("  场景3: REPEATABLE READ — 可重复读 + 幻读防护");
        System.out.println("===========================================================\n");

        System.out.println("  时间线：");
        System.out.println("    conn1                            conn2");
        System.out.println("    ─────                            ─────");
        System.out.println("                                     BEGIN");
        System.out.println("                                     SELECT → 2 rows（第一次读）");
        System.out.println("    BEGIN");
        System.out.println("    INSERT id=3 Charlie(500)");
        System.out.println("    UPDATE Alice → 5000");
        System.out.println("    COMMIT");
        System.out.println("                                     SELECT → 还是 2 rows？值不变？\n");

        Connection conn1 = null;
        Connection conn2 = null;
        try {
            conn1 = DBUtil.getConnection(false);
            conn2 = DBUtil.getConnection(false);
            DBUtil.execute(conn1, "USE kungfu");
            DBUtil.execute(conn2, "USE kungfu");

            setIsolationLevel(conn1, "REPEATABLE READ");
            setIsolationLevel(conn2, "REPEATABLE READ");

            // conn2: 第一次读
            System.out.println("    [conn2] 第一次 SELECT * FROM t_account");
            int count1 = queryAll(conn2);
            int balanceBefore = queryBalance(conn2, "Alice");
            System.out.println("    [conn2] 共 " + count1 + " 行，Alice balance = " + balanceBefore + "\n");

            // conn1: 插入新行 + 修改已有行并提交
            System.out.println("    [conn1] INSERT INTO t_account VALUES (3, 'Charlie', 500)");
            DBUtil.execute(conn1, "INSERT INTO t_account VALUES (3, 'Charlie', 500)");
            System.out.println("    [conn1] UPDATE t_account SET balance = 5000 WHERE name = 'Alice'");
            DBUtil.execute(conn1, "UPDATE t_account SET balance = 5000 WHERE name = 'Alice'");
            conn1.commit();
            System.out.println("    [conn1] COMMIT\n");

            // conn2: 第二次读 — RR 下应该看到一样的快照
            System.out.println("    [conn2] 第二次 SELECT * FROM t_account");
            int count2 = queryAll(conn2);
            int balanceAfter = queryBalance(conn2, "Alice");
            System.out.println("    [conn2] 共 " + count2 + " 行，Alice balance = " + balanceAfter + "\n");

            System.out.println("    ★ RR 结果分析：");
            if (count1 == count2) {
                System.out.println("      行数: " + count1 + " → " + count2 + "（没看到新插入的 Charlie → 幻读被防止 ✓）");
            } else {
                System.out.println("      行数: " + count1 + " → " + count2 + "（看到了新行）");
            }
            if (balanceBefore == balanceAfter) {
                System.out.println("      Alice: " + balanceBefore + " → " + balanceAfter + "（值不变 → 可重复读 ✓）");
            } else {
                System.out.println("      Alice: " + balanceBefore + " → " + balanceAfter + "（值变了）");
            }
            System.out.println("      原因: RR 复用第一次 SELECT 的 ReadView，看不到 conn1 的提交\n");

            conn2.commit();
        } catch (Exception e) {
            System.out.println("    ✗ 演示异常: " + e.getMessage() + "\n");
        } finally {
            DBUtil.close(conn1);
            DBUtil.close(conn2);
        }

        resetData();
    }

    // =============================================================
    // 场景4: SERIALIZABLE — 串行化
    // =============================================================

    private static void demo4_Serializable() {
        System.out.println("===========================================================");
        System.out.println("  场景4: SERIALIZABLE — 串行化（读加锁）");
        System.out.println("===========================================================\n");

        System.out.println("  时间线：");
        System.out.println("    conn1                            conn2");
        System.out.println("    ─────                            ─────");
        System.out.println("    BEGIN");
        System.out.println("    SELECT（自动加共享锁 S）");
        System.out.println("                                     BEGIN");
        System.out.println("                                     UPDATE → 被阻塞！（等 S 锁释放）");
        System.out.println("    COMMIT（释放 S 锁）");
        System.out.println("                                     UPDATE 成功\n");

        Connection conn1 = null;
        Connection conn2 = null;
        try {
            conn1 = DBUtil.getConnection(false);
            conn2 = DBUtil.getConnection(false);
            DBUtil.execute(conn1, "USE kungfu");
            DBUtil.execute(conn2, "USE kungfu");

            setIsolationLevel(conn1, "SERIALIZABLE");
            setIsolationLevel(conn2, "SERIALIZABLE");

            // 设置 conn2 锁等待超时为 3 秒（避免长时间阻塞）
            DBUtil.execute(conn2, "SET innodb_lock_wait_timeout = 3");

            // conn1: SELECT（SERIALIZABLE 下自动加共享锁）
            System.out.println("    [conn1] SELECT * FROM t_account WHERE name = 'Alice'");
            int balance = queryBalance(conn1, "Alice");
            System.out.println("    [conn1] 读到 balance = " + balance);
            System.out.println("    [conn1] （SERIALIZABLE 自动对读取的行加了 S 锁）\n");

            // conn2: 尝试 UPDATE — 会被 S 锁阻塞
            System.out.println("    [conn2] UPDATE t_account SET balance = 8888 WHERE name = 'Alice'");
            System.out.println("    [conn2] 预期：被 conn1 的共享锁阻塞...\n");

            long start = System.currentTimeMillis();
            boolean blocked = false;
            try {
                // 在 conn1 未提交时，conn2 的 UPDATE 会等待锁
                // 我们用一个子线程来做 UPDATE，主线程观察是否阻塞
                final Connection c2 = conn2;
                Thread updateThread = new Thread(() -> {
                    try {
                        DBUtil.execute(c2, "UPDATE t_account SET balance = 8888 WHERE name = 'Alice'");
                    } catch (Exception e) {
                        // 锁等待超时
                    }
                });
                updateThread.start();

                // 等一会看线程是否还在运行（说明被阻塞了）
                Thread.sleep(1000);
                if (updateThread.isAlive()) {
                    blocked = true;
                    long elapsed = System.currentTimeMillis() - start;
                    System.out.println("    ★ conn2 被阻塞了 " + elapsed + " ms（等待 conn1 释放 S 锁）\n");

                    // conn1 提交释放锁
                    System.out.println("    [conn1] COMMIT（释放共享锁）");
                    conn1.commit();

                    // 等 conn2 完成
                    updateThread.join(5000);
                    long total = System.currentTimeMillis() - start;
                    System.out.println("    [conn2] UPDATE 完成（等待了约 " + total + " ms）");
                } else {
                    System.out.println("    （UPDATE 未被阻塞，可能锁粒度不同）");
                    conn1.commit();
                }
            } catch (Exception e) {
                System.out.println("    锁等待超时或异常: " + e.getMessage());
                conn1.commit();
            }

            if (blocked) {
                System.out.println("\n    ★ SERIALIZABLE 特点：");
                System.out.println("      - 所有 SELECT 自动加 LOCK IN SHARE MODE（共享锁）");
                System.out.println("      - 其他事务的写操作必须等锁释放");
                System.out.println("      - 完全防止脏读、不可重复读、幻读");
                System.out.println("      - 代价：并发性能最低，容易死锁\n");
            }

            conn2.commit();
        } catch (Exception e) {
            System.out.println("    ✗ 演示异常: " + e.getMessage() + "\n");
        } finally {
            DBUtil.close(conn1);
            DBUtil.close(conn2);
        }

        resetData();
    }

    // =============================================================
    // 辅助方法
    // =============================================================

    /** 设置连接的事务隔离级别 */
    private static void setIsolationLevel(Connection conn, String level) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET SESSION TRANSACTION ISOLATION LEVEL " + level);
        }
    }

    /** 查询指定用户的 balance */
    private static int queryBalance(Connection conn, String name) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT balance FROM t_account WHERE name = '" + name + "'")) {
            if (rs.next()) {
                return rs.getInt("balance");
            }
            return -1;
        }
    }

    /** 查询全表并返回行数 */
    private static int queryAll(Connection conn) throws Exception {
        int count = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM t_account")) {
            while (rs.next()) {
                count++;
            }
        }
        return count;
    }

    // =============================================================
    // 最终总结
    // =============================================================

    private static void showSummary() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  ┌────────────────────┬──────────┬──────────────┬──────────┬────────────────┐");
        System.out.println("  │ 隔离级别           │ 脏读     │ 不可重复读   │ 幻读     │ 实现方式       │");
        System.out.println("  ├────────────────────┼──────────┼──────────────┼──────────┼────────────────┤");
        System.out.println("  │ READ UNCOMMITTED   │ ✗        │ ✗            │ ✗        │ 无 MVCC        │");
        System.out.println("  │ READ COMMITTED     │ ✓        │ ✗            │ ✗        │ 每次新 ReadView│");
        System.out.println("  │ REPEATABLE READ ★ │ ✓        │ ✓            │ ✓*       │ 复用 ReadView  │");
        System.out.println("  │ SERIALIZABLE       │ ✓        │ ✓            │ ✓        │ 读写都加锁     │");
        System.out.println("  └────────────────────┴──────────┴──────────────┴──────────┴────────────────┘");
        System.out.println("  ★ InnoDB 默认    * InnoDB 用 MVCC+间隙锁防止幻读\n");

        System.out.println("  1. InnoDB 默认 REPEATABLE READ，大多数业务够用");
        System.out.println("  2. 互联网业务常用 READ COMMITTED（阿里规范推荐），减少间隙锁死锁");
        System.out.println("  3. SERIALIZABLE 性能最差，几乎不用于生产环境");
        System.out.println("  4. READ UNCOMMITTED 无任何保护，生产环境禁用");
        System.out.println("  5. 查看当前级别: SELECT @@transaction_isolation;\n");
    }
}

/*
 * 【知识串联】
 * D24 全部知识点：
 *   1. MiniMVCCDemo          — 手写 MVCC 模拟（版本链 + ReadView）
 *   2. IsolationLevelDemo    — 真实 MySQL 4 种隔离级别实战（本类）
 *
 * 上一步：D23 学习了 EXPLAIN 执行计划分析
 * 下一步：D25 学习锁机制（行锁、间隙锁、死锁排查）
 */
