package com.kungfu.storage.d23_explain;

import com.kungfu.storage.common.DBUtil;

import java.sql.Connection;
import java.sql.Statement;

/**
 * 【Demo】EXPLAIN 逐字段解读 — 读懂 MySQL 执行计划
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>EXPLAIN 输出的每个字段含义（id, select_type, table, type, possible_keys, key, key_len, ref, rows, filtered, Extra）</li>
 *   <li>type 字段从最优到最差：system &gt; const &gt; eq_ref &gt; ref &gt; range &gt; index &gt; ALL</li>
 *   <li>针对每种 type 构造真实 SQL 并用 EXPLAIN 验证</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你用 EXPLAIN 分析过 SQL 吗？各字段什么含义？type 从好到差怎么排？"
 * EXPLAIN 是 SQL 调优的第一步，不会读执行计划就无法定位慢查询
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
public class ExplainAnalysisDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  EXPLAIN 逐字段解读");
        System.out.println("========================================\n");

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            System.out.println("  ✓ MySQL 连接成功\n");

            // 准备数据
            prepareData(conn);

            // 一、EXPLAIN 字段速查表
            showExplainFields();

            // 二、type 字段从最优到最差
            showTypeRanking();

            // 三、实战演示每种 type
            demoConst(conn);
            demoRef(conn);
            demoRange(conn);
            demoIndex(conn);
            demoAll(conn);

            // 四、Extra 字段常见值
            showExtraField();

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
                if (i % 5000 != 1) sb.append(",");
                // user_id 1~1000, product_id 1~500, amount 10~109, status 0/1/2
                // create_time 分布在 2025-01-01 ~ 2026-12-31
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
    // 一、EXPLAIN 字段速查表
    // =============================================================
    private static void showExplainFields() {
        System.out.println("=== 一、EXPLAIN 输出字段速查表 ===\n");

        System.out.println("  ┌──────────────┬──────────────────────────────────────────────────────────┐");
        System.out.println("  │ 字段         │ 含义                                                     │");
        System.out.println("  ├──────────────┼──────────────────────────────────────────────────────────┤");
        System.out.println("  │ id           │ 查询序号，id 相同 → 从上往下执行；id 不同 → id 大的先执行│");
        System.out.println("  │ select_type  │ 查询类型：SIMPLE / PRIMARY / SUBQUERY / DERIVED / UNION  │");
        System.out.println("  │ table        │ 当前行访问的表名                                         │");
        System.out.println("  │ type    ★   │ 访问类型（最重要！从好到差排列，见下表）                  │");
        System.out.println("  │ possible_keys│ 可能用到的索引                                           │");
        System.out.println("  │ key          │ 实际使用的索引（NULL 表示没用索引）                      │");
        System.out.println("  │ key_len      │ 使用的索引长度（越短说明用到的索引列越少）               │");
        System.out.println("  │ ref          │ 与索引比较的列或常量                                     │");
        System.out.println("  │ rows         │ 预估扫描行数（越少越好）                                │");
        System.out.println("  │ filtered     │ 经过条件过滤后剩余行的百分比                             │");
        System.out.println("  │ Extra        │ 额外信息（Using index / Using where / Using filesort 等）│");
        System.out.println("  └──────────────┴──────────────────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、type 字段排名
    // =============================================================
    private static void showTypeRanking() {
        System.out.println("=== 二、type 字段从最优到最差 ===\n");

        System.out.println("  ★ 性能排序：system > const > eq_ref > ref > range > index > ALL\n");

        System.out.println("  ┌──────────┬──────────────────────────────────┬─────────────────────────────┐");
        System.out.println("  │ type     │ 含义                             │ 典型场景                     │");
        System.out.println("  ├──────────┼──────────────────────────────────┼─────────────────────────────┤");
        System.out.println("  │ system   │ 表只有一行（系统表）             │ 很少见，可忽略               │");
        System.out.println("  │ const    │ 主键/唯一索引等值匹配，最多1行   │ WHERE id = 1                │");
        System.out.println("  │ eq_ref   │ 关联查询中主键/唯一索引等值匹配  │ JOIN ... ON a.id = b.id     │");
        System.out.println("  │ ref      │ 非唯一索引等值匹配               │ WHERE user_id = 42          │");
        System.out.println("  │ range    │ 索引范围扫描                     │ WHERE id BETWEEN 10 AND 50  │");
        System.out.println("  │ index    │ 全索引扫描（遍历整棵索引树）     │ SELECT user_id FROM t_order │");
        System.out.println("  │ ALL  ✗  │ 全表扫描（最差）                 │ 没有可用索引                 │");
        System.out.println("  └──────────┴──────────────────────────────────┴─────────────────────────────┘\n");

        System.out.println("  ★ 优化目标：至少达到 range 级别，避免 ALL 全表扫描\n");
    }

    // =============================================================
    // 三(a)、const — 主键等值匹配
    // =============================================================
    private static void demoConst(Connection conn) throws Exception {
        System.out.println("=== 三(a)、type = const — 主键等值匹配 ===\n");
        System.out.println("  场景：通过主键精确查找一行\n");

        DBUtil.explain(conn, "SELECT * FROM t_order WHERE id = 1");

        System.out.println("  解读：");
        System.out.println("    - type = const → 主键等值匹配，最多返回 1 行");
        System.out.println("    - key = PRIMARY → 使用主键索引");
        System.out.println("    - rows = 1 → 只扫描 1 行");
        System.out.println("    → 这是最高效的单行查询方式\n");
    }

    // =============================================================
    // 三(b)、ref — 非唯一索引等值匹配
    // =============================================================
    private static void demoRef(Connection conn) throws Exception {
        System.out.println("=== 三(b)、type = ref — 非唯一索引等值匹配 ===\n");
        System.out.println("  场景：通过普通索引查找（可能返回多行）\n");

        DBUtil.explain(conn, "SELECT * FROM t_order WHERE user_id = 42");

        System.out.println("  解读：");
        System.out.println("    - type = ref → 非唯一索引等值查找");
        System.out.println("    - key = idx_user_id → 使用 user_id 索引");
        System.out.println("    - ref = const → 与常量 42 比较");
        System.out.println("    - rows ≈ 50 → user_id=42 约有 50 行匹配");
        System.out.println("    → 常见于根据外键/状态等非唯一字段查询\n");
    }

    // =============================================================
    // 三(c)、range — 索引范围扫描
    // =============================================================
    private static void demoRange(Connection conn) throws Exception {
        System.out.println("=== 三(c)、type = range — 索引范围扫描 ===\n");
        System.out.println("  场景：使用索引进行范围查询（BETWEEN / > / < / IN）\n");

        DBUtil.explain(conn, "SELECT * FROM t_order WHERE user_id BETWEEN 10 AND 50");

        System.out.println("  解读：");
        System.out.println("    - type = range → 在索引树上做范围扫描");
        System.out.println("    - key = idx_user_id → 使用 user_id 索引");
        System.out.println("    - rows → 扫描的行数与范围大小成正比");
        System.out.println("    → BETWEEN/IN/>/</>=/<=  都可能走 range\n");
    }

    // =============================================================
    // 三(d)、index — 全索引扫描
    // =============================================================
    private static void demoIndex(Connection conn) throws Exception {
        System.out.println("=== 三(d)、type = index — 全索引扫描 ===\n");
        System.out.println("  场景：查询的列都在索引中（覆盖索引），但没有 WHERE 限制\n");

        DBUtil.explain(conn, "SELECT user_id FROM t_order");

        System.out.println("  解读：");
        System.out.println("    - type = index → 遍历整棵索引树（不回表）");
        System.out.println("    - key = idx_user_id → 直接扫描 user_id 索引");
        System.out.println("    - Extra: Using index → 覆盖索引，不需要回表");
        System.out.println("    - rows ≈ 50000 → 虽然全扫，但只读索引页，比 ALL 快");
        System.out.println("    → index 比 ALL 好，因为索引比数据行小得多\n");
    }

    // =============================================================
    // 三(e)、ALL — 全表扫描
    // =============================================================
    private static void demoAll(Connection conn) throws Exception {
        System.out.println("=== 三(e)、type = ALL — 全表扫描（最差）===\n");
        System.out.println("  场景：没有可用索引，只能逐行扫描整张表\n");

        DBUtil.explain(conn, "SELECT * FROM t_order WHERE amount > 100");

        System.out.println("  解读：");
        System.out.println("    - type = ALL → 全表扫描，逐行读取");
        System.out.println("    - key = NULL → 没有可用索引");
        System.out.println("    - rows ≈ 50000 → 扫描全部行");
        System.out.println("    - 这是最慢的访问方式，必须优化！");
        System.out.println("    → 解决：给 amount 加索引，或者改用有索引的列做筛选\n");
    }

    // =============================================================
    // 四、Extra 字段常见值
    // =============================================================
    private static void showExtraField() {
        System.out.println("=== 四、Extra 字段常见值解读 ===\n");

        System.out.println("  ┌──────────────────────────┬──────────────────────────────────────────────┐");
        System.out.println("  │ Extra 值                 │ 含义                                         │");
        System.out.println("  ├──────────────────────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ Using index         ✓   │ 覆盖索引，不需要回表（好）                   │");
        System.out.println("  │ Using where              │ 存储引擎返回后还需要在Server层过滤           │");
        System.out.println("  │ Using index condition    │ 索引下推（ICP），在存储引擎层用索引过滤       │");
        System.out.println("  │ Using temporary     ✗   │ 使用了临时表（常见于 GROUP BY / DISTINCT）    │");
        System.out.println("  │ Using filesort      ✗   │ 使用了文件排序（ORDER BY 没走索引）           │");
        System.out.println("  │ Using join buffer        │ 关联查询使用了 Block Nested Loop 缓冲         │");
        System.out.println("  └──────────────────────────┴──────────────────────────────────────────────┘\n");

        System.out.println("  ★ 看到 Using temporary 或 Using filesort 要警惕，考虑加索引优化\n");
    }

    // =============================================================
    // 面试速记
    // =============================================================
    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  1. EXPLAIN 最重要的字段是 type：从好到差 system>const>eq_ref>ref>range>index>ALL");
        System.out.println("  2. 优化目标：至少达到 range，杜绝 ALL 全表扫描");
        System.out.println("  3. key=NULL 说明没用到索引 → 需要添加索引或改写 SQL");
        System.out.println("  4. rows 越大说明扫描越多 → 索引选择性差或没走索引");
        System.out.println("  5. Extra 中 Using index = 覆盖索引（好），Using filesort/temporary = 需要优化");
        System.out.println("  6. const vs ref：const 是主键/唯一索引，最多一行；ref 是普通索引，可能多行");
        System.out.println();
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
 *   1. ExplainAnalysisDemo          — EXPLAIN 逐字段解读 + type 实战演示（本类）
 *   2. SlowQueryOptimizationDemo    — 慢查询优化 3 大经典案例
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
