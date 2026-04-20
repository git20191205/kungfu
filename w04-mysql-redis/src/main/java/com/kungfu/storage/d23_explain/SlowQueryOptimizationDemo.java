package com.kungfu.storage.d23_explain;

import com.kungfu.storage.common.DBUtil;

import java.sql.Connection;
import java.sql.Statement;

/**
 * 【Demo】慢查询优化 3 大经典案例 — 索引失效与修复
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>案例1: 对索引列使用函数 → 索引失效 → 改写为范围查询</li>
 *   <li>案例2: 隐式类型转换 → 索引失效 → 修正数据类型</li>
 *   <li>案例3: 前导通配符 LIKE '%keyword%' → 全表扫描 → 覆盖索引兜底</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你遇到过索引失效吗？什么情况下索引会失效？怎么解决？"
 * 这三种场景覆盖了 90% 的索引失效问题，是 DBA 和后端开发的必备技能
 *
 * <h3>运行方式</h3>
 * <pre>
 * 1. 启动 MySQL，确保 kungfu 数据库存在
 * 2. 直接运行 main 方法（会自动建表、插数据、演示、清理）
 * </pre>
 *
 * @author kungfu
 * @since D23 - 执行计划
 */
public class SlowQueryOptimizationDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  慢查询优化 3 大经典案例");
        System.out.println("========================================\n");

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            System.out.println("  ✓ MySQL 连接成功\n");

            // 准备数据
            prepareData(conn);

            // 索引失效总览
            showIndexInvalidationOverview();

            // 案例1: 函数导致索引失效
            case1_FunctionOnColumn(conn);

            // 案例2: 隐式类型转换导致索引失效
            case2_ImplicitConversion(conn);

            // 案例3: 前导通配符导致全表扫描
            case3_LeadingWildcard(conn);

            // 面试速记
            showInterviewTips();

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
            "  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  INDEX idx_user_id(user_id)," +
            "  INDEX idx_order_no(order_no)," +
            "  INDEX idx_create_time(create_time)" +
            ") ENGINE=InnoDB");

        // 批量插入 5 万行
        long start = System.currentTimeMillis();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder(
                "INSERT INTO t_order (order_no, user_id, product_id, amount, status, create_time) VALUES ");
            for (int i = 1; i <= 50000; i++) {
                if (i > 1) sb.append(",");
                int dayOffset = i % 730;
                sb.append(String.format(
                    "('ORD%06d', %d, %d, %.2f, %d, DATE_ADD('2025-01-01', INTERVAL %d DAY))",
                    i, (i % 1000) + 1, (i % 500) + 1,
                    10 + (i % 990) * 0.1, i % 3, dayOffset));
                if (i % 5000 == 0) {
                    stmt.execute(sb.toString());
                    sb = new StringBuilder(
                        "INSERT INTO t_order (order_no, user_id, product_id, amount, status, create_time) VALUES ");
                }
            }
        }
        conn.commit();
        conn.setAutoCommit(true);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  插入 50000 行，耗时 " + elapsed + " ms\n");
    }

    // =============================================================
    // 索引失效总览
    // =============================================================
    private static void showIndexInvalidationOverview() {
        System.out.println("=== 索引失效常见场景总览 ===\n");

        System.out.println("  ┌────┬─────────────────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ #  │ 失效场景                    │ 原因                                  │");
        System.out.println("  ├────┼─────────────────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 1  │ 对索引列使用函数            │ YEAR(create_time) 破坏了 B+Tree 有序性│");
        System.out.println("  │ 2  │ 隐式类型转换                │ VARCHAR 列与 INT 比较，MySQL 自动转型  │");
        System.out.println("  │ 3  │ 前导通配符 LIKE '%xx'       │ 无法利用索引的有序性定位起始位置       │");
        System.out.println("  │ 4  │ OR 连接无索引列             │ 只要有一个条件没索引就走全扫           │");
        System.out.println("  │ 5  │ 不满足最左前缀              │ 联合索引 (a,b,c)，查 WHERE b=1        │");
        System.out.println("  │ 6  │ 使用 != 或 NOT IN           │ 优化器可能认为全扫更快                │");
        System.out.println("  └────┴─────────────────────────────┴──────────────────────────────────────┘\n");

        System.out.println("  ★ 下面用 EXPLAIN 逐一验证前 3 种最高频的场景\n");
    }

    // =============================================================
    // 案例1: 对索引列使用函数 → 索引失效
    // =============================================================
    private static void case1_FunctionOnColumn(Connection conn) throws Exception {
        System.out.println("========================================");
        System.out.println("  案例1: 对索引列使用函数");
        System.out.println("========================================\n");

        // ---- 问题 SQL ----
        System.out.println("  ✗ 问题 SQL：WHERE YEAR(create_time) = 2026\n");
        DBUtil.explain(conn,
            "SELECT * FROM t_order WHERE YEAR(create_time) = 2026");

        System.out.println("  问题分析：");
        System.out.println("    - type = ALL → 全表扫描！");
        System.out.println("    - key = NULL → 索引 idx_create_time 没有被使用");
        System.out.println("    - 原因：对索引列套函数 YEAR() 后，B+Tree 的有序性被破坏");
        System.out.println("    - MySQL 无法用索引定位 YEAR(create_time)=2026 的起始位置\n");

        System.out.println("  -----------------------------------------------\n");

        // ---- 修复 SQL ----
        System.out.println("  ✓ 修复 SQL：改写为范围查询\n");
        DBUtil.explain(conn,
            "SELECT * FROM t_order WHERE create_time >= '2026-01-01' AND create_time < '2027-01-01'");

        System.out.println("  修复分析：");
        System.out.println("    - type = range → 索引范围扫描（从 ALL 优化到 range）");
        System.out.println("    - key = idx_create_time → 索引被正确使用");
        System.out.println("    - rows 大幅减少");
        System.out.println("    - 原则：永远不要对索引列使用函数，把计算放到常量侧\n");

        System.out.println("  ★ 类似场景：");
        System.out.println("    ✗ WHERE DATE(create_time) = '2026-06-01'");
        System.out.println("    ✓ WHERE create_time >= '2026-06-01' AND create_time < '2026-06-02'");
        System.out.println("    ✗ WHERE MONTH(create_time) = 6");
        System.out.println("    ✓ WHERE create_time >= '2026-06-01' AND create_time < '2026-07-01'\n");
    }

    // =============================================================
    // 案例2: 隐式类型转换 → 索引失效
    // =============================================================
    private static void case2_ImplicitConversion(Connection conn) throws Exception {
        System.out.println("========================================");
        System.out.println("  案例2: 隐式类型转换");
        System.out.println("========================================\n");

        // ---- 问题 SQL ----
        System.out.println("  ✗ 问题 SQL：WHERE order_no = 12345（order_no 是 VARCHAR，但传了 INT）\n");
        DBUtil.explain(conn,
            "SELECT * FROM t_order WHERE order_no = 12345");

        System.out.println("  问题分析：");
        System.out.println("    - type = ALL → 全表扫描！");
        System.out.println("    - key = NULL → 索引 idx_order_no 没有被使用");
        System.out.println("    - 原因：order_no 是 VARCHAR，但与 INT 比较");
        System.out.println("    - MySQL 会对 order_no 做隐式转换：CAST(order_no AS SIGNED)");
        System.out.println("    - 相当于对索引列使用了函数 → 索引失效（和案例1同理）\n");

        System.out.println("  -----------------------------------------------\n");

        // ---- 修复 SQL ----
        System.out.println("  ✓ 修复 SQL：传入正确的字符串类型\n");
        DBUtil.explain(conn,
            "SELECT * FROM t_order WHERE order_no = 'ORD012345'");

        System.out.println("  修复分析：");
        System.out.println("    - type = ref → 索引等值查找");
        System.out.println("    - key = idx_order_no → 索引被正确使用");
        System.out.println("    - rows = 1 → 只扫描 1 行");
        System.out.println("    - 原则：参数类型必须和列类型一致，避免隐式转换\n");

        System.out.println("  ★ 隐式转换规则：");
        System.out.println("    - 字符串列 vs 数字 → MySQL 把字符串转为数字（对列做了转换 → 失效）");
        System.out.println("    - 数字列 vs 字符串 → MySQL 把字符串转为数字（对常量做了转换 → 不失效）");
        System.out.println("    - 记忆：谁被转换谁吃亏，列被转了索引就废了\n");
    }

    // =============================================================
    // 案例3: 前导通配符 LIKE '%keyword%'
    // =============================================================
    private static void case3_LeadingWildcard(Connection conn) throws Exception {
        System.out.println("========================================");
        System.out.println("  案例3: 前导通配符 LIKE");
        System.out.println("========================================\n");

        // ---- 问题 SQL ----
        System.out.println("  ✗ 问题 SQL：WHERE order_no LIKE '%0123%'\n");
        DBUtil.explain(conn,
            "SELECT * FROM t_order WHERE order_no LIKE '%0123%'");

        System.out.println("  问题分析：");
        System.out.println("    - type = ALL → 全表扫描！");
        System.out.println("    - key = NULL → 索引 idx_order_no 没有被使用");
        System.out.println("    - 原因：LIKE '%keyword%' 的前导 % 让 MySQL 无法确定起始位置");
        System.out.println("    - 索引是按字典序排列的，'%0123%' 可以匹配任何开头 → 只能全扫\n");

        System.out.println("  -----------------------------------------------\n");

        // ---- 改善方案1: 去掉前导通配符 ----
        System.out.println("  ✓ 改善方案1：去掉前导 %（如果业务允许）\n");
        DBUtil.explain(conn,
            "SELECT * FROM t_order WHERE order_no LIKE 'ORD0123%'");

        System.out.println("  分析：");
        System.out.println("    - type = range → 索引范围扫描");
        System.out.println("    - key = idx_order_no → 索引生效");
        System.out.println("    - 右边的 % 不影响索引使用，因为可以确定起始位置\n");

        System.out.println("  -----------------------------------------------\n");

        // ---- 改善方案2: 覆盖索引兜底 ----
        System.out.println("  ✓ 改善方案2：覆盖索引兜底（必须 LIKE '%xx%' 时）\n");

        // 添加覆盖索引
        DBUtil.execute(conn,
            "ALTER TABLE t_order ADD INDEX idx_orderno_userid(order_no, user_id)");

        DBUtil.explain(conn,
            "SELECT order_no, user_id FROM t_order WHERE order_no LIKE '%0123%'");

        System.out.println("  分析：");
        System.out.println("    - type = index → 全索引扫描（比 ALL 好）");
        System.out.println("    - key = idx_orderno_userid → 使用覆盖索引");
        System.out.println("    - Extra: Using where; Using index → 虽然全扫，但只扫索引不回表");
        System.out.println("    - 索引页比数据页小得多，IO 开销大幅降低\n");

        // 清理临时索引
        DBUtil.execute(conn, "ALTER TABLE t_order DROP INDEX idx_orderno_userid");

        System.out.println("  ★ LIKE 优化总结：");
        System.out.println("    1. 尽量使用右通配 LIKE 'prefix%' → 能走索引");
        System.out.println("    2. 如果必须 LIKE '%keyword%'，用覆盖索引减少回表");
        System.out.println("    3. 大数据量模糊搜索 → 考虑 Elasticsearch 全文检索\n");
    }

    // =============================================================
    // 面试速记
    // =============================================================
    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  1. 索引失效三大杀手：");
        System.out.println("     - 对索引列用函数/计算 → 改写为范围查询");
        System.out.println("     - 隐式类型转换 → 保证参数类型与列类型一致");
        System.out.println("     - 前导通配符 LIKE '%xx' → 去掉前导% / 覆盖索引 / ES\n");

        System.out.println("  2. 排查慢查询的标准流程：");
        System.out.println("     Step 1: 开启慢查询日志（slow_query_log = ON, long_query_time = 1）");
        System.out.println("     Step 2: 用 EXPLAIN 分析慢 SQL → 看 type / key / rows / Extra");
        System.out.println("     Step 3: 根据 EXPLAIN 结果优化（加索引 / 改写SQL / 覆盖索引）");
        System.out.println("     Step 4: 再次 EXPLAIN 验证优化效果\n");

        System.out.println("  3. 口诀 — 索引失效速记：");
        System.out.println("     函数计算索引废，类型不同隐式推");
        System.out.println("     左模糊来右边飞，最左前缀不能违");
        System.out.println("     OR 连接全得有，NOT IN 范围别乱追\n");
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
 * D23 全部知识点：
 *   1. ExplainAnalysisDemo          — EXPLAIN 逐字段解读 + type 实战演示
 *   2. SlowQueryOptimizationDemo    — 慢查询优化 3 大经典案例（本类）
 *
 * W04 完整路线：
 *   D22 → 索引原理（B+Tree、聚簇/二级索引、回表、覆盖索引）
 *   D23 → 执行计划（EXPLAIN 逐字段解读、慢查询优化）
 *   D24 → 事务隔离（MVCC、4种隔离级别）
 *   D25 → 锁机制（行锁、间隙锁、死锁排查）
 *   D26 → Redis数据结构（5种结构+场景）
 *   D27 → Redis持久化（RDB vs AOF）
 *   D28 → 缓存问题（穿透/击穿/雪崩 + 一致性）
 *   D29 → 综合实战（缓存架构设计）
 */
