package com.kungfu.storage.d25_lock;

import com.kungfu.storage.common.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】死锁制造与排查
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>用 2 个线程制造 InnoDB 死锁</li>
 *   <li>观察 MySQL 死锁检测和自动回滚</li>
 *   <li>死锁排查 SOP：SHOW ENGINE INNODB STATUS</li>
 *   <li>常见死锁模式 + 预防方案</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："线上遇到死锁怎么排查？怎么避免？"
 *
 * <h3>运行方式</h3>
 * 需要 MySQL 运行中，直接运行 main
 *
 * @author kungfu
 * @since D25 - 锁机制
 */
public class DeadlockDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  死锁制造与排查");
        System.out.println("========================================\n");

        // 一、死锁原理
        showDeadlockTheory();

        // 二、制造死锁
        createDeadlock();

        // 三、排查 SOP
        showTroubleshootingSOP();

        // 四、预防方案
        showPreventionStrategies();
    }

    // =============================================================
    // 一、死锁原理
    // =============================================================
    private static void showDeadlockTheory() {
        System.out.println("=== 一、死锁的四个必要条件 ===\n");
        System.out.println("  ┌─────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 条件            │ 说明                                  │");
        System.out.println("  ├─────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 互斥            │ 资源同一时刻只能被一个事务持有        │");
        System.out.println("  │ 持有并等待      │ 持有锁 A 的同时等待锁 B              │");
        System.out.println("  │ 不可剥夺        │ 锁不能被强行释放                      │");
        System.out.println("  │ 循环等待        │ T1→T2→T1 形成等待环路                │");
        System.out.println("  └─────────────────┴──────────────────────────────────────┘\n");
        System.out.println("  InnoDB 死锁检测：wait-for graph（等待图）检测到环路 → 回滚代价小的事务\n");
    }

    // =============================================================
    // 二、制造死锁
    // =============================================================
    private static void createDeadlock() {
        System.out.println("=== 二、制造死锁 ===\n");
        System.out.println("  场景：");
        System.out.println("    Thread-1: 锁 SKU001 → 等待锁 SKU002");
        System.out.println("    Thread-2: 锁 SKU002 → 等待锁 SKU001");
        System.out.println("    → 循环等待 → 死锁！\n");

        Connection conn1 = null, conn2 = null;
        try {
            conn1 = DBUtil.getConnection(false);
            conn2 = DBUtil.getConnection(false);

            // 准备数据
            DBUtil.execute(conn1, "CREATE DATABASE IF NOT EXISTS kungfu");
            DBUtil.execute(conn1, "USE kungfu");
            DBUtil.execute(conn2, "USE kungfu");
            DBUtil.execute(conn1, "DROP TABLE IF EXISTS t_inventory");
            DBUtil.execute(conn1,
                "CREATE TABLE t_inventory (id BIGINT PRIMARY KEY, sku VARCHAR(32) UNIQUE, quantity INT) ENGINE=InnoDB");
            DBUtil.execute(conn1, "INSERT INTO t_inventory VALUES (1,'SKU001',100),(2,'SKU002',200)");
            conn1.commit();

            final Connection c1 = conn1;
            final Connection c2 = conn2;
            CountDownLatch bothLocked = new CountDownLatch(2);
            CountDownLatch done = new CountDownLatch(2);

            // Thread-1
            Thread t1 = new Thread(() -> {
                try {
                    DBUtil.execute(c1, "SELECT * FROM t_inventory WHERE sku='SKU001' FOR UPDATE");
                    System.out.println("    [T1] 已锁 SKU001");
                    bothLocked.countDown();
                    bothLocked.await(5, TimeUnit.SECONDS);
                    Thread.sleep(100);

                    System.out.println("    [T1] 尝试锁 SKU002...");
                    DBUtil.execute(c1, "SELECT * FROM t_inventory WHERE sku='SKU002' FOR UPDATE");
                    System.out.println("    [T1] 成功获取 SKU002");
                    c1.commit();
                } catch (Exception e) {
                    System.out.println("    [T1] ★ 被回滚: " + e.getMessage());
                    try { c1.rollback(); } catch (SQLException ignored) {}
                }
                done.countDown();
            });

            // Thread-2
            Thread t2 = new Thread(() -> {
                try {
                    DBUtil.execute(c2, "SELECT * FROM t_inventory WHERE sku='SKU002' FOR UPDATE");
                    System.out.println("    [T2] 已锁 SKU002");
                    bothLocked.countDown();
                    bothLocked.await(5, TimeUnit.SECONDS);
                    Thread.sleep(100);

                    System.out.println("    [T2] 尝试锁 SKU001...");
                    DBUtil.execute(c2, "SELECT * FROM t_inventory WHERE sku='SKU001' FOR UPDATE");
                    System.out.println("    [T2] 成功获取 SKU001");
                    c2.commit();
                } catch (Exception e) {
                    System.out.println("    [T2] ★ 被回滚: " + e.getMessage());
                    try { c2.rollback(); } catch (SQLException ignored) {}
                }
                done.countDown();
            });

            t1.start();
            t2.start();
            done.await(10, TimeUnit.SECONDS);

            System.out.println("\n  → InnoDB 检测到死锁，自动回滚了其中一个事务");
            System.out.println("  → 另一个事务成功完成\n");

            DBUtil.execute(conn1, "DROP TABLE IF EXISTS t_inventory");
            conn1.commit();

        } catch (Exception e) {
            System.out.println("  执行异常: " + e.getMessage());
        } finally {
            DBUtil.close(conn1);
            DBUtil.close(conn2);
        }
    }

    // =============================================================
    // 三、排查 SOP
    // =============================================================
    private static void showTroubleshootingSOP() {
        System.out.println("=== 三、死锁排查 SOP ===\n");
        System.out.println("  Step 1: 查看最近一次死锁");
        System.out.println("    SHOW ENGINE INNODB STATUS;  → 搜索 'LATEST DETECTED DEADLOCK'");
        System.out.println("    关注：哪两个事务、各持有什么锁、各等待什么锁\n");

        System.out.println("  Step 2: 查看当前锁等待");
        System.out.println("    SELECT * FROM information_schema.INNODB_LOCK_WAITS;    -- MySQL 5.7");
        System.out.println("    SELECT * FROM performance_schema.data_lock_waits;       -- MySQL 8.0\n");

        System.out.println("  Step 3: 查看当前事务");
        System.out.println("    SELECT * FROM information_schema.INNODB_TRX;");
        System.out.println("    关注 trx_state, trx_query, trx_wait_started\n");

        System.out.println("  Step 4: 定位 SQL 并优化");
        System.out.println("    根据死锁日志中的 SQL → 找到代码位置 → 调整加锁顺序\n");
    }

    // =============================================================
    // 四、预防方案
    // =============================================================
    private static void showPreventionStrategies() {
        System.out.println("=== 四、死锁预防方案 ===\n");
        System.out.println("  ┌───────────────────────┬────────────────────────────────────────────┐");
        System.out.println("  │ 策略                  │ 说明                                        │");
        System.out.println("  ├───────────────────────┼────────────────────────────────────────────┤");
        System.out.println("  │ 固定加锁顺序          │ 所有事务按相同顺序加锁（如按 ID 升序）     │");
        System.out.println("  │ 缩短事务              │ 事务越短，持锁时间越少，死锁概率越低       │");
        System.out.println("  │ 降低隔离级别          │ RC 没有 Gap Lock，死锁概率低于 RR          │");
        System.out.println("  │ 合理使用索引          │ 避免锁范围过大（全表扫描 → 表锁 → 死锁） │");
        System.out.println("  │ 乐观锁替代            │ 用 version 字段做 CAS 更新，无锁设计       │");
        System.out.println("  │ 设置锁等待超时        │ innodb_lock_wait_timeout = 5s（默认 50s）  │");
        System.out.println("  └───────────────────────┴────────────────────────────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: 线上遇到死锁怎么办？");
        System.out.println("    A: 1) SHOW ENGINE INNODB STATUS 看死锁日志");
        System.out.println("       2) 找到两个事务的 SQL 和加锁顺序");
        System.out.println("       3) 调整为统一加锁顺序 / 缩短事务 / 降级到 RC");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * D25 全部知识点：
 *   1. LockMechanismDemo   — Record Lock / Gap Lock / 表锁退化
 *   2. DeadlockDemo        — 死锁制造与排查（本类）
 */
