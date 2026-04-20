package com.kungfu.storage.d29_integration;

import com.kungfu.storage.common.DBUtil;
import com.kungfu.storage.common.RedisUtil;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】商品查询缓存架构 — D22-D28 知识综合实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>完整缓存链路：布隆过滤器 → Redis → MySQL</li>
 *   <li>集成所有防护：防穿透 + 防击穿 + 防雪崩 + 保一致性</li>
 *   <li>性能对比：有缓存 vs 无缓存</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 MySQL + Redis 运行中
 *
 * @author kungfu
 * @since D29 - 综合实战
 */
public class CacheArchitectureDemo {

    private static final String CACHE_PREFIX = "demo:d29:product:";
    private static final String LOCK_PREFIX = "demo:d29:lock:";
    private static final int PRODUCT_COUNT = 500;
    private static final Random RANDOM = new Random();

    // 简化版布隆过滤器
    private static final BitSet BLOOM = new BitSet(65536);

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  商品查询缓存架构 — 综合实战");
        System.out.println("========================================\n");

        showArchitecture();

        boolean hasRedis = RedisUtil.testConnection();
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            prepareData(conn);

            if (hasRedis) {
                loadBloomFilter(conn);
                demoQueryFlow(conn);
                demoPenetrationBlock();
                demoConcurrentBreakdown(conn);
                demoPerformanceComparison(conn);
                cleanupRedis();
            } else {
                System.out.println("  ✗ Redis 未连接，仅展示架构图\n");
            }

            cleanup(conn);
        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtil.close(conn);
            if (hasRedis) RedisUtil.shutdown();
        }
    }

    private static void showArchitecture() {
        System.out.println("=== 缓存架构图 ===\n");
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │                     商品查询请求                             │");
        System.out.println("  └────────────────────────────┬──────────────────────────────────┘");
        System.out.println("                               ↓");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │  ① 布隆过滤器 — 不存在的 ID 直接拦截（防穿透）            │");
        System.out.println("  └────────────────────────────┬───────────────────────────────┘");
        System.out.println("                               ↓ 可能存在");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │  ② Redis 缓存 — 命中直接返回（随机 TTL 防雪崩）           │");
        System.out.println("  └────────────────────────────┬───────────────────────────────┘");
        System.out.println("                               ↓ 未命中");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │  ③ 互斥锁（SETNX）— 只允许 1 个线程查 DB（防击穿）       │");
        System.out.println("  └────────────────────────────┬───────────────────────────────┘");
        System.out.println("                               ↓ 获得锁");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │  ④ MySQL 查询 → 写入 Redis（Cache-Aside 保一致性）        │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");
    }

    private static void prepareData(Connection conn) throws Exception {
        System.out.println("=== 准备数据 ===\n");
        DBUtil.execute(conn, "CREATE DATABASE IF NOT EXISTS kungfu");
        DBUtil.execute(conn, "USE kungfu");
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_product");
        DBUtil.execute(conn, "CREATE TABLE t_product (id BIGINT PRIMARY KEY, name VARCHAR(50), price DECIMAL(10,2)) ENGINE=InnoDB");

        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder("INSERT INTO t_product VALUES ");
            for (int i = 1; i <= PRODUCT_COUNT; i++) {
                if (i > 1) sb.append(",");
                sb.append(String.format("(%d, 'Product_%d', %.2f)", i, i, 10 + RANDOM.nextDouble() * 990));
            }
            stmt.execute(sb.toString());
        }
        conn.commit();
        conn.setAutoCommit(true);
        System.out.println("  插入 " + PRODUCT_COUNT + " 个商品到 MySQL\n");
    }

    private static void loadBloomFilter(Connection conn) throws Exception {
        System.out.println("=== 加载布隆过滤器 ===\n");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM t_product")) {
            int count = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                addToBloom(id);
                count++;
            }
            System.out.println("  加载了 " + count + " 个商品 ID 到布隆过滤器\n");
        }
    }

    private static void addToBloom(int id) {
        BLOOM.set(hash1(id));
        BLOOM.set(hash2(id));
        BLOOM.set(hash3(id));
    }

    private static boolean mightContain(int id) {
        return BLOOM.get(hash1(id)) && BLOOM.get(hash2(id)) && BLOOM.get(hash3(id));
    }

    private static int hash1(int id) { return Math.abs(id * 31) % 65536; }
    private static int hash2(int id) { return Math.abs(id * 131 + 7) % 65536; }
    private static int hash3(int id) { return Math.abs(id * 1049 + 13) % 65536; }

    // =============================================================
    // 完整查询流程
    // =============================================================
    private static void demoQueryFlow(Connection conn) throws Exception {
        System.out.println("=== 完整查询流程演示 ===\n");

        int productId = 42;
        System.out.println("  查询商品 id=" + productId + "：\n");

        // Step 1: 布隆过滤器
        if (!mightContain(productId)) {
            System.out.println("  ① 布隆过滤器: 不存在 → 直接返回 null");
            return;
        }
        System.out.println("  ① 布隆过滤器: 可能存在 → 继续");

        // Step 2: Redis 缓存
        try (Jedis jedis = RedisUtil.getJedis()) {
            String cacheKey = CACHE_PREFIX + productId;
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                System.out.println("  ② Redis 缓存: 命中 → " + cached);
                return;
            }
            System.out.println("  ② Redis 缓存: MISS → 需要查 DB");

            // Step 3: 互斥锁
            String lockKey = LOCK_PREFIX + productId;
            String locked = jedis.set(lockKey, "1", redis.clients.jedis.params.SetParams.setParams().nx().ex(5));
            if (!"OK".equals(locked)) {
                System.out.println("  ③ 互斥锁: 未获得 → 等待其他线程重建");
                return;
            }
            System.out.println("  ③ 互斥锁: 获得 → 查 DB");

            // Step 4: 查 DB + 写缓存
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM t_product WHERE id=" + productId)) {
                if (rs.next()) {
                    String data = rs.getString("name") + ":" + rs.getBigDecimal("price");
                    int ttl = 3600 + RANDOM.nextInt(600); // 随机 TTL 防雪崩
                    jedis.setex(cacheKey, ttl, data);
                    System.out.println("  ④ MySQL 查询: " + data);
                    System.out.println("     写入 Redis: TTL=" + ttl + "s（随机防雪崩）");
                }
            } finally {
                jedis.del(lockKey);
            }
        }
        System.out.println();
    }

    // =============================================================
    // 穿透拦截演示
    // =============================================================
    private static void demoPenetrationBlock() {
        System.out.println("=== 穿透拦截演示 ===\n");
        int[] fakeIds = {99999, -1, 88888, 77777};
        for (int id : fakeIds) {
            boolean pass = mightContain(id);
            System.out.println("  id=" + id + " → 布隆过滤器: " + (pass ? "放行（误判）" : "拦截 ✓"));
        }
        System.out.println();
    }

    // =============================================================
    // 并发击穿防护演示
    // =============================================================
    private static void demoConcurrentBreakdown(Connection conn) throws Exception {
        System.out.println("=== 并发击穿防护演示 ===\n");
        System.out.println("  模拟 20 个并发请求查询同一个未缓存的商品...\n");

        int productId = 100;
        String cacheKey = CACHE_PREFIX + productId;
        try (Jedis jedis = RedisUtil.getJedis()) { jedis.del(cacheKey); }

        AtomicInteger dbHits = new AtomicInteger(0);
        AtomicInteger cacheHits = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try (Jedis jedis = RedisUtil.getJedis()) {
                    String cached = jedis.get(cacheKey);
                    if (cached != null) {
                        cacheHits.incrementAndGet();
                        return;
                    }
                    String lockKey = LOCK_PREFIX + productId;
                    String locked = jedis.set(lockKey, "1", redis.clients.jedis.params.SetParams.setParams().nx().ex(5));
                    if ("OK".equals(locked)) {
                        try {
                            Thread.sleep(100); // 模拟 DB 查询
                            jedis.setex(cacheKey, 3600, "Product_100:price");
                            dbHits.incrementAndGet();
                        } finally {
                            jedis.del(lockKey);
                        }
                    } else {
                        Thread.sleep(150);
                        if (jedis.get(cacheKey) != null) cacheHits.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();

        System.out.println("  结果: DB 查询 = " + dbHits.get() + " 次（预期 1）");
        System.out.println("  结果: 缓存命中 = " + cacheHits.get() + " 次");
        System.out.println("  → 互斥锁成功将 20 个并发请求收敛为 1 次 DB 查询\n");
    }

    // =============================================================
    // 性能对比
    // =============================================================
    private static void demoPerformanceComparison(Connection conn) throws Exception {
        System.out.println("=== 性能对比：有缓存 vs 无缓存 ===\n");

        // 预热缓存
        try (Jedis jedis = RedisUtil.getJedis()) {
            for (int i = 1; i <= 100; i++) {
                jedis.setex(CACHE_PREFIX + i, 3600, "Product_" + i + ":price");
            }
        }

        // 有缓存
        long start = System.currentTimeMillis();
        try (Jedis jedis = RedisUtil.getJedis()) {
            for (int i = 0; i < 1000; i++) {
                jedis.get(CACHE_PREFIX + (RANDOM.nextInt(100) + 1));
            }
        }
        long cacheTime = System.currentTimeMillis() - start;

        // 无缓存（直接查 DB）
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM t_product WHERE id=" + (RANDOM.nextInt(100) + 1))) {
                rs.next();
            }
        }
        long dbTime = System.currentTimeMillis() - start;

        System.out.println("  1000 次随机查询：");
        System.out.println("    Redis 缓存: " + cacheTime + " ms");
        System.out.println("    MySQL 直查: " + dbTime + " ms");
        System.out.println("    提升倍数:   " + String.format("%.1fx", (double) dbTime / Math.max(cacheTime, 1)));
        System.out.println();
    }

    private static void cleanupRedis() {
        try (Jedis jedis = RedisUtil.getJedis()) {
            for (int i = 1; i <= PRODUCT_COUNT; i++) {
                jedis.del(CACHE_PREFIX + i);
            }
        }
    }

    private static void cleanup(Connection conn) throws Exception {
        DBUtil.execute(conn, "DROP TABLE IF EXISTS t_product");
    }
}
