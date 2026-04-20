package com.kungfu.storage.d28_cache;

import com.kungfu.storage.common.DBUtil;
import com.kungfu.storage.common.RedisUtil;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 【Demo】缓存一致性 — Cache-Aside / 延迟双删
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Cache-Aside 模式：先更新 DB，再删缓存</li>
 *   <li>延迟双删：删缓存 → 更新 DB → sleep → 再删缓存</li>
 *   <li>为什么「先删缓存再更新 DB」有并发问题</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 MySQL + Redis 运行中
 *
 * @author kungfu
 * @since D28 - 缓存问题
 */
public class CacheConsistencyDemo {

    private static final String CACHE_KEY = "demo:d28:product:1001";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  缓存一致性方案对比");
        System.out.println("========================================\n");

        showOverview();

        boolean hasRedis = RedisUtil.testConnection();
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            prepareData(conn);

            if (hasRedis) {
                demo1_CacheAside(conn);
                demo2_DoubleDelete(conn);
            } else {
                System.out.println("  ✗ Redis 未连接，仅展示理论部分\n");
            }

            demo3_WhyNotDeleteFirst();
            cleanup(conn);
        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
        } finally {
            DBUtil.close(conn);
            if (hasRedis) RedisUtil.shutdown();
        }
    }

    private static void showOverview() {
        System.out.println("=== 三种方案对比 ===\n");
        System.out.println("  ┌──────────────────┬──────────────────────────────┬──────────────┐");
        System.out.println("  │ 方案             │ 流程                          │ 一致性       │");
        System.out.println("  ├──────────────────┼──────────────────────────────┼──────────────┤");
        System.out.println("  │ Cache-Aside ★   │ 更新 DB → 删缓存             │ 最终一致     │");
        System.out.println("  │ 延迟双删         │ 删缓存 → 更新 DB → sleep → 删│ 更强一致     │");
        System.out.println("  │ 先删缓存再更新DB │ 删缓存 → 更新 DB             │ ✗ 有并发问题│");
        System.out.println("  └──────────────────┴──────────────────────────────┴──────────────┘\n");
    }

    private static void prepareData(Connection conn) throws Exception {
        DBUtil.execute(conn, "CREATE DATABASE IF NOT EXISTS kungfu");
        DBUtil.execute(conn, "USE kungfu");
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_product");
        DBUtil.execute(conn, "CREATE TABLE t_product (id BIGINT PRIMARY KEY, name VARCHAR(50), price DECIMAL(10,2)) ENGINE=InnoDB");
        DBUtil.execute(conn, "INSERT INTO t_product VALUES (1001, 'iPhone', 9999.00)");
    }

    // =============================================================
    // 方案一：Cache-Aside
    // =============================================================
    private static void demo1_CacheAside(Connection conn) throws Exception {
        System.out.println("=== 方案一：Cache-Aside（旁路缓存）===\n");

        try (Jedis jedis = RedisUtil.getJedis()) {
            // 读流程
            System.out.println("  ★ 读流程：");
            String cached = jedis.get(CACHE_KEY);
            System.out.println("    1. 查缓存: " + (cached == null ? "MISS" : cached));
            if (cached == null) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM t_product WHERE id=1001")) {
                    if (rs.next()) {
                        String data = rs.getString("name") + ":" + rs.getBigDecimal("price");
                        jedis.setex(CACHE_KEY, 3600, data);
                        System.out.println("    2. 查 DB: " + data);
                        System.out.println("    3. 写缓存: " + CACHE_KEY + " → " + data + " (TTL=3600s)");
                    }
                }
            }
            System.out.println();

            // 写流程
            System.out.println("  ★ 写流程（价格改为 8888）：");
            DBUtil.execute(conn, "UPDATE t_product SET price=8888.00 WHERE id=1001");
            System.out.println("    1. 更新 DB: price → 8888.00");
            jedis.del(CACHE_KEY);
            System.out.println("    2. 删缓存: DEL " + CACHE_KEY);
            System.out.println("    → 下次读取时会重新从 DB 加载最新数据\n");
        }
    }

    // =============================================================
    // 方案二：延迟双删
    // =============================================================
    private static void demo2_DoubleDelete(Connection conn) throws Exception {
        System.out.println("=== 方案二：延迟双删 ===\n");

        try (Jedis jedis = RedisUtil.getJedis()) {
            // 先预热缓存
            jedis.setex(CACHE_KEY, 3600, "iPhone:8888.00");

            System.out.println("  流程：");
            System.out.println("    1. 删缓存");
            jedis.del(CACHE_KEY);

            System.out.println("    2. 更新 DB: price → 7777.00");
            DBUtil.execute(conn, "UPDATE t_product SET price=7777.00 WHERE id=1001");

            System.out.println("    3. sleep 500ms（等待可能的并发读写入旧缓存）");
            Thread.sleep(500);

            System.out.println("    4. 再次删缓存");
            jedis.del(CACHE_KEY);

            System.out.println("    → 即使 step 2-3 之间有并发读写入了旧缓存，step 4 也会清除它\n");

            System.out.println("  延迟时间怎么定？");
            System.out.println("    = 业务读取耗时 + 几百毫秒余量");
            System.out.println("    通常 500ms ~ 1s 即可\n");
        }
    }

    // =============================================================
    // 方案三（反面教材）：先删缓存再更新 DB
    // =============================================================
    private static void demo3_WhyNotDeleteFirst() {
        System.out.println("=== 为什么「先删缓存再更新 DB」有问题 ===\n");

        System.out.println("  并发场景时序：");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 时间  │ 线程 A（写）              │ 线程 B（读）             │");
        System.out.println("  ├───────┼──────────────────────────┼─────────────────────────┤");
        System.out.println("  │ T1    │ 删缓存                   │                         │");
        System.out.println("  │ T2    │                          │ 查缓存 → MISS           │");
        System.out.println("  │ T3    │                          │ 查 DB → 旧值 9999       │");
        System.out.println("  │ T4    │                          │ 写缓存 → 9999（旧值！） │");
        System.out.println("  │ T5    │ 更新 DB → 8888           │                         │");
        System.out.println("  │       │                          │                         │");
        System.out.println("  │ 结果  │ DB=8888, 缓存=9999 → 不一致！                     │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 面试速记：");
        System.out.println("    Q: 缓存和 DB 怎么保证一致性？");
        System.out.println("    A: 用 Cache-Aside（先更新 DB，再删缓存）");
        System.out.println("       更强一致性用延迟双删");
        System.out.println("       不要先删缓存再更新 DB（有并发问题）");
        System.out.println();
    }

    private static void cleanup(Connection conn) throws Exception {
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_product");
    }
}
