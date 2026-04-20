package com.kungfu.storage.d22_index;

import com.kungfu.storage.common.DBUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 【Demo】MySQL 索引优化实战 — 3 个经典案例
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>案例1: 无索引全表扫描 → 添加索引后 EXPLAIN 对比</li>
 *   <li>案例2: 联合索引最左前缀匹配（命中 vs 不命中）</li>
 *   <li>案例3: 回表查询 → 覆盖索引优化</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你做过索引优化吗？怎么做的？"
 * 索引优化是日常开发中最高频的 SQL 调优手段
 *
 * <h3>运行方式</h3>
 * <pre>
 * 1. 启动 MySQL，创建 kungfu 数据库
 * 2. 运行 sql/schema-d22.sql 建表
 * 3. 直接运行 main 方法
 * </pre>
 *
 * @author kungfu
 * @since D22 - MySQL索引原理
 */
public class IndexOptimizationDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  MySQL 索引优化实战");
        System.out.println("========================================\n");

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            System.out.println("  ✓ MySQL 连接成功\n");

            // 准备数据
            prepareData(conn);

            // 案例1: 无索引 → 有索引
            case1_AddIndex(conn);

            // 案例2: 联合索引最左前缀
            case2_LeftmostPrefix(conn);

            // 案例3: 回表 → 覆盖索引
            case3_CoveringIndex(conn);

            // 清理
            cleanup(conn);

        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtil.close(conn);
        }
    }

    // =============================================================
    // 准备测试数据（插入 5 万行）
    // =============================================================
    private static void prepareData(Connection conn) throws Exception {
        System.out.println("=== 准备测试数据 ===\n");

        // 确保表存在
        DBUtil.execute(conn, "CREATE DATABASE IF NOT EXISTS kungfu");
        DBUtil.execute(conn, "USE kungfu");
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_order");
        DBUtil.execute(conn,
            "CREATE TABLE t_order (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  order_no VARCHAR(32) NOT NULL," +
            "  user_id BIGINT NOT NULL," +
            "  product_id BIGINT NOT NULL," +
            "  amount DECIMAL(10,2) NOT NULL," +
            "  status TINYINT NOT NULL DEFAULT 0," +
            "  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB");

        // 批量插入 5 万行
        long start = System.currentTimeMillis();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder("INSERT INTO t_order (order_no, user_id, product_id, amount, status) VALUES ");
            for (int i = 1; i <= 50000; i++) {
                if (i > 1) sb.append(",");
                sb.append(String.format("('ORD%06d', %d, %d, %.2f, %d)",
                        i, (i % 1000) + 1, (i % 500) + 1, 10 + (i % 990) * 0.1, i % 3));
                if (i % 5000 == 0) {
                    stmt.execute(sb.toString());
                    sb = new StringBuilder("INSERT INTO t_order (order_no, user_id, product_id, amount, status) VALUES ");
                }
            }
        }
        conn.commit();
        conn.setAutoCommit(true);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  插入 50000 行，耗时 " + elapsed + " ms\n");
    }

    // =============================================================
    // 案例1: 无索引全表扫描 → 添加索引
    // =============================================================
    private static void case1_AddIndex(Connection conn) throws Exception {
        System.out.println("=== 案例1: 无索引 → 添加索引 ===\n");

        String sql = "SELECT * FROM t_order WHERE user_id = 42";

        // 无索引时的 EXPLAIN
        System.out.println("  ★ 无索引时：");
        DBUtil.explain(conn, sql);

        // 添加索引
        System.out.println("  添加索引: ALTER TABLE t_order ADD INDEX idx_user_id(user_id)\n");
        DBUtil.execute(conn, "ALTER TABLE t_order ADD INDEX idx_user_id(user_id)");

        // 有索引时的 EXPLAIN
        System.out.println("  ★ 有索引后：");
        DBUtil.explain(conn, sql);

        System.out.println("  对比要点：");
        System.out.println("    - type: ALL(全表扫描) → ref(索引查找)");
        System.out.println("    - key: NULL → idx_user_id");
        System.out.println("    - rows: ~50000 → ~50（扫描行数大幅减少）\n");
    }

    // =============================================================
    // 案例2: 联合索引最左前缀
    // =============================================================
    private static void case2_LeftmostPrefix(Connection conn) throws Exception {
        System.out.println("=== 案例2: 联合索引最左前缀 ===\n");

        // 创建联合索引
        System.out.println("  创建联合索引: (user_id, status, product_id)\n");
        try {
            DBUtil.execute(conn, "ALTER TABLE t_order DROP INDEX idx_user_id");
        } catch (Exception ignored) {}
        DBUtil.execute(conn, "ALTER TABLE t_order ADD INDEX idx_composite(user_id, status, product_id)");

        // 命中：从最左列开始
        System.out.println("  ✓ 命中索引（从 user_id 开始）：");
        DBUtil.explain(conn, "SELECT * FROM t_order WHERE user_id = 42 AND status = 1");

        // 命中：只用最左列
        System.out.println("  ✓ 命中索引（只用 user_id）：");
        DBUtil.explain(conn, "SELECT * FROM t_order WHERE user_id = 42");

        // 不命中：跳过最左列
        System.out.println("  ✗ 不命中（跳过 user_id，只用 status）：");
        DBUtil.explain(conn, "SELECT * FROM t_order WHERE status = 1");

        // 不命中：只用中间列
        System.out.println("  ✗ 不命中（跳过 user_id，只用 product_id）：");
        DBUtil.explain(conn, "SELECT * FROM t_order WHERE product_id = 100");

        System.out.println("  最左前缀规则：");
        System.out.println("    索引 (a, b, c) 能命中的查询：");
        System.out.println("    ✓ WHERE a=1");
        System.out.println("    ✓ WHERE a=1 AND b=2");
        System.out.println("    ✓ WHERE a=1 AND b=2 AND c=3");
        System.out.println("    ✗ WHERE b=2（跳过了 a）");
        System.out.println("    ✗ WHERE c=3（跳过了 a, b）");
        System.out.println("    ✗ WHERE b=2 AND c=3（跳过了 a）\n");
    }

    // =============================================================
    // 案例3: 回表 → 覆盖索引
    // =============================================================
    private static void case3_CoveringIndex(Connection conn) throws Exception {
        System.out.println("=== 案例3: 回表 → 覆盖索引 ===\n");

        // 回表查询（SELECT * 需要回聚簇索引取全部列）
        System.out.println("  ★ 回表查询（SELECT * 需要回聚簇索引）：");
        DBUtil.explain(conn, "SELECT * FROM t_order WHERE user_id = 42 AND status = 1");

        // 覆盖索引（只查索引中已有的列）
        System.out.println("  ★ 覆盖索引（只查 user_id, status, product_id — 都在索引里）：");
        DBUtil.explain(conn, "SELECT user_id, status, product_id FROM t_order WHERE user_id = 42 AND status = 1");

        System.out.println("  对比要点：");
        System.out.println("    - 覆盖索引的 Extra 列出现 'Using index'");
        System.out.println("    - 说明只读索引就够了，不需要回表读数据行");
        System.out.println("    - 回表查询的 Extra 没有 'Using index'，需要额外 IO\n");

        System.out.println("  ★ 实战建议：");
        System.out.println("    1. 高频查询尽量只 SELECT 需要的列，而非 SELECT *");
        System.out.println("    2. 把高频查询的列加入联合索引，实现覆盖索引");
        System.out.println("    3. 索引不是越多越好 — 每个索引都有写入和存储开销\n");
    }

    // =============================================================
    // 清理
    // =============================================================
    private static void cleanup(Connection conn) throws Exception {
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_order");
        System.out.println("  ✓ 测试表已清理\n");
    }
}

/*
 * 【知识串联】
 * D22 全部知识点：
 *   1. BTreeIndexSimulationDemo   — B+Tree 结构模拟
 *   2. IndexOptimizationDemo      — 真实 MySQL 索引优化实战（本类）
 *
 * 下一步：D23 学习 EXPLAIN 执行计划的完整分析方法
 */
