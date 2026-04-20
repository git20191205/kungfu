package com.kungfu.storage.d28_cache;

import com.kungfu.storage.common.DBUtil;
import com.kungfu.storage.common.RedisUtil;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】缓存三大问题 — 穿透 / 击穿 / 雪崩
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>缓存穿透：查不存在的数据 → 布隆过滤器 + 空值缓存</li>
 *   <li>缓存击穿：热点 key 过期 → 互斥锁（SETNX）重建</li>
 *   <li>缓存雪崩：大批 key 同时过期 → 随机 TTL + 多级缓存</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 MySQL + Redis 运行中
 *
 * @author kungfu
 * @since D28 - 缓存问题
 */
public class CacheProblemDemo {

    private static final String KEY_PREFIX = "demo:d28:product:";
    private static final String LOCK_PREFIX = "demo:d28:lock:product:";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  缓存三大问题");
        System.out.println("========================================\n");

        if (!RedisUtil.testConnection()) {
            System.out.println("  ✗ Redis 连接失败，退出\n");
            return;
        }

        problem1_Penetration();
        problem2_Breakdown();
        problem3_Avalanche();

        RedisUtil.shutdown();
    }

    // =============================================================
    // 问题一：缓存穿透
    // =============================================================
    private static void problem1_Penetration() {
        System.out.println("=== 问题一：缓存穿透（查不存在的数据） ===\n");
        System.out.println("  现象：恶意攻击或 bug 导致大量查询不存在的 key");
        System.out.println("  后果：缓存不命中 → 每次都打到 DB → DB 压力暴增\n");

        System.out.println("  ★ 解决方案 1：空值缓存");
        try (Jedis jedis = RedisUtil.getJedis()) {
            String key = KEY_PREFIX + "-999";
            // 模拟查 DB 不存在，缓存空值（短 TTL）
            jedis.setex(key, 60, "NULL");
            System.out.println("    缓存了空值: " + key + " → \"NULL\" (TTL=60s)");
            System.out.println("    下次查询直接命中缓存，不会打到 DB");
            jedis.del(key);
        }
        System.out.println();

        System.out.println("  ★ 解决方案 2：布隆过滤器");
        // 模拟简化版布隆过滤器（用 BitSet）
        BitSet bloom = new BitSet(10000);
        Set<Integer> validIds = new HashSet<>();
        // 假设 DB 中有 id 1-1000 的商品
        for (int i = 1; i <= 1000; i++) {
            validIds.add(i);
            bloom.set(i % 10000);
            bloom.set((i * 31) % 10000);
            bloom.set((i * 131) % 10000);
        }

        System.out.println("    布隆过滤器加载了 1000 个有效 product_id");

        int[] testIds = {42, 500, 1500, 99999};
        for (int id : testIds) {
            boolean maybeExists = id >= 0
                    && bloom.get(Math.abs(id) % 10000)
                    && bloom.get(Math.abs(id * 31) % 10000)
                    && bloom.get(Math.abs(id * 131) % 10000);
            if (!maybeExists) {
                System.out.println("    id=" + id + " → 布隆过滤器拦截，直接返回不存在（不查缓存，不查 DB）");
            } else {
                boolean actuallyExists = validIds.contains(id);
                System.out.println("    id=" + id + " → 布隆过滤器放行（实际" + (actuallyExists ? "存在" : "是误判，需查缓存+DB") + "）");
            }
        }
        System.out.println();
    }

    // =============================================================
    // 问题二：缓存击穿
    // =============================================================
    private static void problem2_Breakdown() {
        System.out.println("=== 问题二：缓存击穿（热点 key 过期） ===\n");
        System.out.println("  现象：某个热点 key（如首页商品）过期瞬间，大量并发请求同时打到 DB");
        System.out.println("  后果：DB 瞬时压力激增，可能被打挂\n");

        System.out.println("  ★ 解决方案：互斥锁（SETNX）重建缓存");

        String hotKey = KEY_PREFIX + "hot:1001";
        String lockKey = LOCK_PREFIX + "1001";

        // 清理
        try (Jedis jedis = RedisUtil.getJedis()) {
            jedis.del(hotKey, lockKey);
        }

        System.out.println("    模拟 10 个并发线程同时查询过期的热点商品...\n");

        AtomicInteger dbQueryCount = new AtomicInteger(0);
        AtomicInteger cacheHitCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            int tid = i;
            new Thread(() -> {
                try (Jedis jedis = RedisUtil.getJedis()) {
                    String value = jedis.get(hotKey);
                    if (value != null) {
                        cacheHitCount.incrementAndGet();
                        return;
                    }
                    // 尝试获取互斥锁
                    String locked = jedis.set(lockKey, String.valueOf(tid), redis.clients.jedis.params.SetParams.setParams().nx().ex(5));
                    if ("OK".equals(locked)) {
                        try {
                            System.out.println("    [T" + tid + "] 获得锁，开始查 DB 重建缓存");
                            Thread.sleep(200); // 模拟 DB 查询
                            jedis.setex(hotKey, 60, "product_data_1001");
                            dbQueryCount.incrementAndGet();
                        } finally {
                            jedis.del(lockKey);
                        }
                    } else {
                        // 未获得锁，稍等后重试读缓存
                        Thread.sleep(50);
                        String v = jedis.get(hotKey);
                        if (v != null) cacheHitCount.incrementAndGet();
                        else System.out.println("    [T" + tid + "] 未获得锁，重试仍无缓存");
                    }
                } catch (Exception e) {
                    System.out.println("    [T" + tid + "] 异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        try { latch.await(); } catch (InterruptedException ignored) {}

        System.out.println("\n    结果: DB 查询次数 = " + dbQueryCount.get() + "（预期 1）");
        System.out.println("    结果: 缓存命中次数 = " + cacheHitCount.get());
        System.out.println("    → 10 个并发请求，只有 1 个查了 DB，其他等缓存重建完成\n");

        // 清理
        try (Jedis jedis = RedisUtil.getJedis()) {
            jedis.del(hotKey);
        }
    }

    // =============================================================
    // 问题三：缓存雪崩
    // =============================================================
    private static void problem3_Avalanche() {
        System.out.println("=== 问题三：缓存雪崩（大批 key 同时过期） ===\n");
        System.out.println("  现象：大批 key 设置了相同的 TTL，到期时同时失效");
        System.out.println("  后果：所有请求同时打到 DB → DB 被打挂\n");

        System.out.println("  ★ 解决方案 1：随机 TTL");
        System.out.println("    错误做法: jedis.setex(key, 3600, value);  // 全部 1 小时");
        System.out.println("    正确做法: jedis.setex(key, 3600 + random.nextInt(600), value);  // 1 小时 + 随机 0-10 分钟\n");

        try (Jedis jedis = RedisUtil.getJedis()) {
            Random random = new Random();
            System.out.println("    模拟 10 个商品，TTL = 3600 + random(0, 600) 秒：");
            for (int i = 1; i <= 10; i++) {
                int ttl = 3600 + random.nextInt(600);
                String key = KEY_PREFIX + "avalanche:" + i;
                jedis.setex(key, ttl, "data_" + i);
                System.out.println("    " + key + " → TTL=" + ttl + "s");
            }
            // 清理
            for (int i = 1; i <= 10; i++) {
                jedis.del(KEY_PREFIX + "avalanche:" + i);
            }
        }
        System.out.println();

        System.out.println("  ★ 解决方案 2：多级缓存");
        System.out.println("    L1: 本地缓存（Caffeine / Guava）— 进程内，纳秒级");
        System.out.println("    L2: Redis 分布式缓存 — 毫秒级");
        System.out.println("    L3: MySQL — 兜底");
        System.out.println("    → 即使 Redis 集体失效，本地缓存还能扛一波\n");

        System.out.println("  ★ 解决方案 3：熔断降级");
        System.out.println("    Sentinel / Hystrix 监控 DB 负载，过载时直接返回默认值或降级页面\n");

        System.out.println("========================================");
        System.out.println("  三大问题速记");
        System.out.println("========================================\n");
        System.out.println("  穿透（Penetration）: 查不存在 → 布隆过滤器 + 空值缓存");
        System.out.println("  击穿（Breakdown）:   热点过期 → 互斥锁重建");
        System.out.println("  雪崩（Avalanche）:   批量过期 → 随机 TTL + 多级缓存 + 熔断");
        System.out.println();
    }
}
