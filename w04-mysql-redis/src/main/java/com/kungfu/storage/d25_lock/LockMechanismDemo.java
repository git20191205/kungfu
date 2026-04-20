package com.kungfu.storage.d25_lock;

import com.kungfu.storage.common.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 【Demo】InnoDB 锁机制全景 — Record Lock / Gap Lock / 表锁
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Demo 1: Record Lock（记录锁）— 同一行上的互斥阻塞</li>
 *   <li>Demo 2: Gap Lock（间隙锁）   — 阻止在索引"空档"里 INSERT，防幻读</li>
 *   <li>Demo 3: 行锁 vs 表锁         — WHERE 不走索引时行锁升级为表锁</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你遇到过锁冲突吗？怎么排查的？"
 * 线上高并发更新时，一个没走索引的 UPDATE 就可能把整张表锁住，
 * 只有真正理解 Record / Gap / Next-Key / 表锁，才能写出不踩坑的 SQL。
 *
 * <h3>运行方式</h3>
 * <pre>
 * 1. 启动 MySQL 8.x，创建 kungfu 数据库
 * 2. 默认隔离级别保持 REPEATABLE READ（RR）
 * 3. 直接运行 main 方法，Demo 会自动建表、插入数据、演示、清理
 * </pre>
 *
 * @author kungfu
 * @since D25 - 锁机制
 */
public class LockMechanismDemo {

    /** 阻塞线程的超时时间（秒）：超时即认为"被锁住了" */
    private static final int BLOCK_TIMEOUT_SECONDS = 3;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  InnoDB 锁机制全景演示");
        System.out.println("========================================\n");

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            System.out.println("  ✓ MySQL 连接成功\n");

            // 0. 锁类型总览
            showLockTypeOverview();

            // 1. 准备测试数据（ids: 1,2,3,5,10 ——id=4、id=6~9 故意留空用于间隙锁演示）
            prepareData(conn);

            // 2. Demo 1: Record Lock
            demo1_RecordLock();

            // 3. Demo 2: Gap Lock
            demo2_GapLock();

            // 4. Demo 3: 行锁 vs 表锁（无索引导致升级）
            demo3_RowLockVsTableLock();

            // 5. 清理
            cleanup(conn);

        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtil.close(conn);
        }

        // 面试速记
        showInterviewTips();
    }

    // =============================================================
    // 0. 锁类型总览
    // =============================================================
    private static void showLockTypeOverview() {
        System.out.println("=== 一、InnoDB 锁类型总览 ===\n");
        System.out.println("  ┌──────────────────┬──────────────────────────────┬──────────────────────────┐");
        System.out.println("  │ 锁类型           │ 锁住什么                     │ 典型触发场景             │");
        System.out.println("  ├──────────────────┼──────────────────────────────┼──────────────────────────┤");
        System.out.println("  │ Record Lock      │ 索引上的一条记录             │ WHERE id=1 FOR UPDATE    │");
        System.out.println("  │ Gap Lock         │ 索引记录之间的间隙（开区间） │ 范围查询 / 唯一键不存在  │");
        System.out.println("  │ Next-Key Lock ★  │ Record + Gap（左开右闭）     │ RR 级别范围查询默认      │");
        System.out.println("  │ Insert Intention │ 插入意向锁（特殊 Gap Lock）  │ INSERT 时申请            │");
        System.out.println("  │ 表锁             │ 整张表                       │ LOCK TABLES / 无索引更新 │");
        System.out.println("  │ 意向锁 IS/IX     │ 表级标记（与行锁配合）       │ 自动加，不用手动管       │");
        System.out.println("  └──────────────────┴──────────────────────────────┴──────────────────────────┘\n");

        System.out.println("  关键原则：");
        System.out.println("    1. InnoDB 的行锁加在「索引」上，不是数据行本身");
        System.out.println("    2. WHERE 不走索引 → 只能全表扫描 → 所有行都被加锁 → 退化为表锁");
        System.out.println("    3. RR 级别默认用 Next-Key Lock 防幻读；RC 级别只用 Record Lock\n");
    }

    // =============================================================
    // 准备测试数据
    // =============================================================
    private static void prepareData(Connection conn) throws Exception {
        System.out.println("=== 二、准备测试数据 ===\n");

        DBUtil.execute(conn, "CREATE DATABASE IF NOT EXISTS kungfu");
        DBUtil.execute(conn, "USE kungfu");
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_inventory");
        DBUtil.execute(conn,
            "CREATE TABLE t_inventory (" +
            "  id BIGINT PRIMARY KEY," +
            "  sku VARCHAR(32) NOT NULL," +
            "  quantity INT NOT NULL," +
            "  version INT NOT NULL DEFAULT 0," +
            "  KEY idx_sku(sku)" +
            ") ENGINE=InnoDB");

        // 故意在 id 上留空档：3-5 之间空一个(4)，5-10 之间空 4 个(6,7,8,9)
        // 用于演示 Gap Lock
        DBUtil.execute(conn, "INSERT INTO t_inventory(id, sku, quantity) VALUES " +
                "(1,'SKU-A',100), (2,'SKU-B',200), (3,'SKU-C',300), (5,'SKU-E',500), (10,'SKU-J',1000)");

        System.out.println("  建表 t_inventory(id, sku, quantity, version)");
        System.out.println("  插入 5 行数据，ids = {1, 2, 3, 5, 10}（3~5 之间空 id=4，5~10 之间空 id=6,7,8,9）\n");

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, sku, quantity, version FROM t_inventory ORDER BY id")) {
            DBUtil.printResultSet(rs);
        }
    }

    // =============================================================
    // Demo 1: Record Lock（记录锁）
    // =============================================================
    private static void demo1_RecordLock() throws Exception {
        System.out.println("=== 三、Demo 1: Record Lock（记录锁） ===\n");
        System.out.println("  场景：事务 A 锁住 id=1 的行，事务 B 尝试更新同一行 → 阻塞");
        System.out.println("  SQL：");
        System.out.println("    [A] BEGIN; SELECT * FROM t_inventory WHERE id=1 FOR UPDATE;");
        System.out.println("    [B] UPDATE t_inventory SET quantity=999 WHERE id=1;  ← 阻塞\n");

        Connection connA = DBUtil.getConnection(false);
        try {
            // 事务 A 持有 id=1 的 X 锁
            try (PreparedStatement ps = connA.prepareStatement(
                    "SELECT id, sku, quantity FROM t_inventory WHERE id = ? FOR UPDATE")) {
                ps.setLong(1, 1L);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    System.out.println("  [A] SELECT ... FOR UPDATE 成功，持有 id=1 的 X 锁，当前 quantity=" + rs.getInt("quantity"));
                }
            }

            // 启动后台线程模拟事务 B
            System.out.println("  [B] 后台线程尝试 UPDATE id=1（等待最多 " + BLOCK_TIMEOUT_SECONDS + " 秒）...");
            boolean blocked = runWithTimeout(() -> {
                Connection connB = DBUtil.getConnection(true);
                try {
                    // 设置 innodb_lock_wait_timeout 为较大值，让它等到我们的超时
                    DBUtil.execute(connB, "SET innodb_lock_wait_timeout = 30");
                    try (PreparedStatement ps = connB.prepareStatement(
                            "UPDATE t_inventory SET quantity = 999 WHERE id = ?")) {
                        ps.setLong(1, 1L);
                        ps.executeUpdate();
                    }
                } finally {
                    DBUtil.close(connB);
                }
                return null;
            });

            if (blocked) {
                System.out.println("  [B] ✓ 如预期：UPDATE 被阻塞，" + BLOCK_TIMEOUT_SECONDS + " 秒内未完成");
                System.out.println("        → 证明事务 A 的 Record Lock 生效\n");
            } else {
                System.out.println("  [B] ✗ 异常：UPDATE 居然完成了，请检查隔离级别/连接配置\n");
            }

            // 事务 A 提交，释放锁
            connA.commit();
            System.out.println("  [A] COMMIT → 释放锁，现在其他事务可以更新 id=1 了\n");

        } finally {
            DBUtil.close(connA);
        }
    }

    // =============================================================
    // Demo 2: Gap Lock（间隙锁）
    // =============================================================
    private static void demo2_GapLock() throws Exception {
        System.out.println("=== 四、Demo 2: Gap Lock（间隙锁） ===\n");
        System.out.println("  场景：事务 A 对 id BETWEEN 3 AND 8 加范围锁，事务 B 往间隙中 INSERT → 阻塞");
        System.out.println("  说明：表中 id = {1,2,3,5,10}，BETWEEN 3 AND 8 覆盖的间隙有：");
        System.out.println("        (3,5) 和 (5,10) 的左半部分 → id=4、id=6/7/8 都会被 Gap Lock 拦住");
        System.out.println("  SQL：");
        System.out.println("    [A] BEGIN; SELECT * FROM t_inventory WHERE id BETWEEN 3 AND 8 FOR UPDATE;");
        System.out.println("    [B] INSERT INTO t_inventory(id, sku, quantity) VALUES (4, 'SKU-D', 400); ← 阻塞\n");

        Connection connA = DBUtil.getConnection(false);
        try {
            try (PreparedStatement ps = connA.prepareStatement(
                    "SELECT id, sku FROM t_inventory WHERE id BETWEEN ? AND ? FOR UPDATE")) {
                ps.setLong(1, 3L);
                ps.setLong(2, 8L);
                try (ResultSet rs = ps.executeQuery()) {
                    int n = 0;
                    while (rs.next()) n++;
                    System.out.println("  [A] 范围 FOR UPDATE 成功，命中 " + n + " 条记录 + 锁住 (3,5)、(5,10) 间隙");
                }
            }

            System.out.println("  [B] 后台线程尝试 INSERT id=4（位于间隙中，等待最多 " + BLOCK_TIMEOUT_SECONDS + " 秒）...");
            boolean blocked = runWithTimeout(() -> {
                Connection connB = DBUtil.getConnection(true);
                try {
                    DBUtil.execute(connB, "SET innodb_lock_wait_timeout = 30");
                    try (PreparedStatement ps = connB.prepareStatement(
                            "INSERT INTO t_inventory(id, sku, quantity) VALUES (?, ?, ?)")) {
                        ps.setLong(1, 4L);
                        ps.setString(2, "SKU-D");
                        ps.setInt(3, 400);
                        ps.executeUpdate();
                    }
                } finally {
                    DBUtil.close(connB);
                }
                return null;
            });

            if (blocked) {
                System.out.println("  [B] ✓ 如预期：INSERT 被阻塞，" + BLOCK_TIMEOUT_SECONDS + " 秒内未完成");
                System.out.println("        → 证明 Gap Lock 生效（阻止在间隙里插入新数据）\n");
            } else {
                System.out.println("  [B] ✗ 异常：INSERT 居然成功，可能隔离级别为 RC 或 innodb_locks_unsafe_for_binlog=1\n");
            }

            connA.commit();
            System.out.println("  [A] COMMIT → 释放所有锁\n");

            System.out.println("  ★ 关键认知：");
            System.out.println("    - Gap Lock 只在 RR 隔离级别默认开启（用于防幻读）");
            System.out.println("    - Gap Lock 之间「不互斥」（两个事务可以同时持有相同的 Gap Lock）");
            System.out.println("    - 但 Gap Lock 会阻塞 Insert Intention Lock → 并发 INSERT 容易死锁\n");

        } finally {
            DBUtil.close(connA);
        }
    }

    // =============================================================
    // Demo 3: 行锁 vs 表锁（WHERE 不走索引 → 退化为表锁）
    // =============================================================
    private static void demo3_RowLockVsTableLock() throws Exception {
        System.out.println("=== 五、Demo 3: 行锁 vs 表锁（索引决定锁粒度） ===\n");
        System.out.println("  场景：事务 A 用「非索引列」做 UPDATE，事务 B 尝试更新「另一行」 → 也被阻塞");
        System.out.println("  原理：quantity 上没有索引 → InnoDB 必须全表扫描 → 给每一行都加 X 锁 → 事实上的表锁");
        System.out.println("  SQL：");
        System.out.println("    [A] BEGIN; UPDATE t_inventory SET version=version+1 WHERE quantity=100;  ← 锁全表");
        System.out.println("    [B] UPDATE t_inventory SET version=version+1 WHERE id=10;              ← 也阻塞！\n");

        Connection connA = DBUtil.getConnection(false);
        try {
            // 事务 A: 用无索引列 quantity 做更新 → 全表加锁
            try (PreparedStatement ps = connA.prepareStatement(
                    "UPDATE t_inventory SET version = version + 1 WHERE quantity = ?")) {
                ps.setInt(1, 100); // 只有 id=1 这行 quantity=100
                int n = ps.executeUpdate();
                System.out.println("  [A] 用非索引列 quantity=100 做 UPDATE，影响 " + n + " 行");
                System.out.println("      但因为 quantity 没有索引，InnoDB 会全表扫描 → 给每一行加 X 锁");
            }

            // 事务 B: 尝试更新 id=10（和事务 A 操作的完全不是同一行）
            System.out.println("  [B] 后台线程尝试 UPDATE id=10（不同行！等待最多 " + BLOCK_TIMEOUT_SECONDS + " 秒）...");
            boolean blocked = runWithTimeout(() -> {
                Connection connB = DBUtil.getConnection(true);
                try {
                    DBUtil.execute(connB, "SET innodb_lock_wait_timeout = 30");
                    try (PreparedStatement ps = connB.prepareStatement(
                            "UPDATE t_inventory SET version = version + 1 WHERE id = ?")) {
                        ps.setLong(1, 10L);
                        ps.executeUpdate();
                    }
                } finally {
                    DBUtil.close(connB);
                }
                return null;
            });

            if (blocked) {
                System.out.println("  [B] ✓ 如预期：UPDATE id=10 也被阻塞");
                System.out.println("        → 证明行锁升级为「事实上的表锁」");
                System.out.println("        → 线上高并发时，一条没走索引的 SQL 能把整张表卡住！\n");
            } else {
                System.out.println("  [B] ✗ UPDATE 未被阻塞（可能 MySQL 8 在该场景下做了优化，需要额外检查）\n");
            }

            connA.commit();
            System.out.println("  [A] COMMIT → 释放全表锁\n");

            System.out.println("  ★ 对比（对 RR 隔离级别）：");
            System.out.println("  ┌─────────────────────────────┬───────────────┬────────────────────────┐");
            System.out.println("  │ WHERE 条件                  │ 锁范围        │ 并发影响               │");
            System.out.println("  ├─────────────────────────────┼───────────────┼────────────────────────┤");
            System.out.println("  │ WHERE id = 1 (主键)         │ 仅 id=1 一行  │ 最小，其他行可并发更新 │");
            System.out.println("  │ WHERE sku = 'X' (普通索引)  │ 匹配的几行    │ 小，未匹配的行可并发   │");
            System.out.println("  │ WHERE quantity = 100 (无索引)│ 全表所有行   │ 最大，实际等同表锁 ★   │");
            System.out.println("  └─────────────────────────────┴───────────────┴────────────────────────┘\n");
        } finally {
            DBUtil.close(connA);
        }
    }

    // =============================================================
    // 清理
    // =============================================================
    private static void cleanup(Connection conn) throws Exception {
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_inventory");
        System.out.println("  ✓ 测试表已清理\n");
    }

    // =============================================================
    // 面试速记
    // =============================================================
    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. Record Lock 锁一行；Gap Lock 锁间隙；Next-Key = Record + Gap");
        System.out.println("  2. InnoDB 行锁加在「索引」上 → 不走索引就退化为表锁");
        System.out.println("  3. Gap Lock 只在 RR 级别默认开启，用于防幻读");
        System.out.println("  4. Gap Lock 彼此不互斥，但会阻塞 INSERT → 并发 INSERT 易死锁");
        System.out.println("  5. SELECT ... FOR UPDATE 加 X 锁；SELECT ... LOCK IN SHARE MODE 加 S 锁");
        System.out.println("  6. 普通 SELECT 不加锁（走 MVCC 快照）");
        System.out.println();
    }

    // =============================================================
    // 工具方法：在后台线程运行任务，超时即视为被阻塞
    // =============================================================
    @FunctionalInterface
    private interface SqlTask {
        Void run() throws Exception;
    }

    /**
     * 在后台线程执行 task：
     *   - 如果 task 在 BLOCK_TIMEOUT_SECONDS 内完成 → 返回 false（未被阻塞）
     *   - 如果超时仍未完成 → 返回 true（被锁住了），并中断线程
     *   - 如果 task 抛出异常（如锁等待超时） → 打印异常并返回 true
     */
    private static boolean runWithTimeout(SqlTask task) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        Thread t = new Thread(() -> {
            try {
                task.run();
                completed.set(true);
            } catch (Exception e) {
                errorRef.set(e);
            } finally {
                done.countDown();
            }
        }, "lock-demo-bg");
        t.setDaemon(true);
        t.start();

        boolean finishedInTime = done.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finishedInTime) {
            // 超时了，说明线程正卡在 MySQL 的锁等待上
            t.interrupt();
            return true; // 被阻塞
        }

        if (errorRef.get() != null) {
            System.out.println("      （后台线程捕获异常：" + errorRef.get().getClass().getSimpleName()
                    + " - " + errorRef.get().getMessage() + "）");
            return true; // 异常也算被锁
        }

        return !completed.get(); // 未正常完成也算被阻塞
    }
}

/*
 * 【知识串联】
 * D25 全部知识点：
 *   1. LockMechanismDemo  — InnoDB 锁类型全景（本类：Record / Gap / 表锁）
 *   2. DeadlockDemo        — 主动制造死锁 + 排查方法
 *
 * W04 完整路线：
 *   D22 → 索引原理（B+Tree、聚簇/二级索引、回表、覆盖索引）
 *   D23 → 执行计划（EXPLAIN 逐字段解读、慢查询优化）
 *   D24 → 事务隔离（MVCC、4种隔离级别）
 *   D25 → 锁机制（行锁、间隙锁、死锁排查）← 你在这里
 *   D26 → Redis数据结构（5种结构+场景）
 *   D27 → Redis持久化（RDB vs AOF）
 *   D28 → 缓存问题（穿透/击穿/雪崩 + 一致性）
 *   D29 → 综合实战（缓存架构设计）
 *
 * 关联：
 *   - D22 索引：没有索引 → 行锁退化为表锁（Demo 3 亲自演示）
 *   - D24 事务：隔离级别决定锁的形态（RR 有 Gap Lock，RC 没有）
 *   - D28 缓存：热点行避免加行锁 → 用 Redis 分布式锁或乐观锁
 */
