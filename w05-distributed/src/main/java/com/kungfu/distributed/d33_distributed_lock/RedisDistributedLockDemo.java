package com.kungfu.distributed.d33_distributed_lock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】Redis 分布式锁 — 从原理到实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>手写 Redis 分布式锁（SET NX EX + Lua 解锁）</li>
 *   <li>模拟并发库存扣减：无锁 vs 有锁对比</li>
 *   <li>锁的常见问题和解决方案</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Redis 运行在 localhost:6379
 *
 * @author kungfu
 * @since D33 - 分布式锁
 */
public class RedisDistributedLockDemo {

    private static JedisPool pool;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Redis 分布式锁");
        System.out.println("========================================\n");

        pool = new JedisPool(new JedisPoolConfig(), "localhost", 6379, 2000);

        try (Jedis jedis = pool.getResource()) {
            if (!"PONG".equals(jedis.ping())) {
                System.out.println("  ✗ Redis 连接失败\n");
                return;
            }
            System.out.println("  ✓ Redis 连接成功\n");
        }

        showLockPrinciple();
        demoWithoutLock();
        demoWithLock();
        showLockProblems();

        pool.close();
    }

    // =============================================================
    // 一、锁原理
    // =============================================================
    private static void showLockPrinciple() {
        System.out.println("=== 一、Redis 分布式锁原理 ===\n");

        System.out.println("  加锁：SET lock_key unique_value NX EX 30");
        System.out.println("    NX: 只在 key 不存在时设置（互斥）");
        System.out.println("    EX 30: 30 秒过期（防死锁）");
        System.out.println("    unique_value: UUID（防误删别人的锁）\n");

        System.out.println("  解锁（Lua 脚本，保证原子性）：");
        System.out.println("    if redis.call('get', KEYS[1]) == ARGV[1] then");
        System.out.println("        return redis.call('del', KEYS[1])");
        System.out.println("    else");
        System.out.println("        return 0");
        System.out.println("    end\n");

        System.out.println("  为什么解锁要用 Lua？");
        System.out.println("    GET + DEL 不是原子操作 → 可能删掉别人的锁");
        System.out.println("    Lua 脚本在 Redis 中原子执行 → 安全\n");
    }

    // =============================================================
    // 二、无锁并发（超卖）
    // =============================================================
    private static void demoWithoutLock() throws Exception {
        System.out.println("=== 二、无锁并发 — 超卖问题 ===\n");

        try (Jedis jedis = pool.getResource()) {
            jedis.set("demo:d33:stock", "100");
        }

        AtomicInteger successCount = new AtomicInteger(0);
        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try (Jedis jedis = pool.getResource()) {
                    int stock = Integer.parseInt(jedis.get("demo:d33:stock"));
                    if (stock > 0) {
                        jedis.decr("demo:d33:stock");
                        successCount.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }
        latch.await();

        try (Jedis jedis = pool.getResource()) {
            String finalStock = jedis.get("demo:d33:stock");
            System.out.println("  初始库存: 100");
            System.out.println("  并发请求: " + threadCount + " 个线程同时扣减");
            System.out.println("  成功扣减: " + successCount.get() + " 次");
            System.out.println("  最终库存: " + finalStock);
            int expected = 100 - successCount.get();
            System.out.println("  预期库存: " + expected);
            System.out.println("  → " + (finalStock.equals(String.valueOf(expected)) ? "✓ 一致" : "✗ 超卖！库存为负") + "\n");
            jedis.del("demo:d33:stock");
        }
    }

    // =============================================================
    // 三、有锁并发（安全）
    // =============================================================
    private static void demoWithLock() throws Exception {
        System.out.println("=== 三、有锁并发 — 安全扣减 ===\n");

        try (Jedis jedis = pool.getResource()) {
            jedis.set("demo:d33:stock", "100");
        }

        AtomicInteger successCount = new AtomicInteger(0);
        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                String lockValue = UUID.randomUUID().toString();
                boolean locked = false;
                try (Jedis jedis = pool.getResource()) {
                    // 加锁（重试 3 次）
                    for (int retry = 0; retry < 3; retry++) {
                        String result = jedis.set("demo:d33:lock:stock",
                                lockValue, SetParams.setParams().nx().ex(5));
                        if ("OK".equals(result)) {
                            locked = true;
                            break;
                        }
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }

                    if (locked) {
                        int stock = Integer.parseInt(jedis.get("demo:d33:stock"));
                        if (stock > 0) {
                            jedis.decr("demo:d33:stock");
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    if (locked) {
                        // Lua 脚本安全解锁
                        try (Jedis jedis = pool.getResource()) {
                            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                                    + "return redis.call('del', KEYS[1]) else return 0 end";
                            jedis.eval(script, Collections.singletonList("demo:d33:lock:stock"),
                                    Collections.singletonList(lockValue));
                        }
                    }
                    latch.countDown();
                }
            }).start();
        }
        latch.await();

        try (Jedis jedis = pool.getResource()) {
            String finalStock = jedis.get("demo:d33:stock");
            System.out.println("  初始库存: 100");
            System.out.println("  并发请求: " + threadCount + " 个线程同时扣减");
            System.out.println("  成功扣减: " + successCount.get() + " 次");
            System.out.println("  最终库存: " + finalStock);
            System.out.println("  → " + (Integer.parseInt(finalStock) >= 0 ? "✓ 无超卖" : "✗ 超卖！") + "\n");
            jedis.del("demo:d33:stock", "demo:d33:lock:stock");
        }
    }

    // =============================================================
    // 四、锁的常见问题
    // =============================================================
    private static void showLockProblems() {
        System.out.println("=== 四、分布式锁常见问题 ===\n");

        System.out.println("  ┌──────────────────────┬──────────────────────────────────────────────┐");
        System.out.println("  │ 问题                 │ 解决方案                                      │");
        System.out.println("  ├──────────────────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 锁过期业务未完成     │ 看门狗续期（Redisson WatchDog，D34 详解）     │");
        System.out.println("  │ 误删别人的锁         │ value 用 UUID，解锁时 Lua 判断               │");
        System.out.println("  │ 不可重入             │ 用 Hash 结构记录重入次数（Redisson 实现）     │");
        System.out.println("  │ 主从切换锁丢失       │ RedLock 算法（多数节点加锁成功才算成功）      │");
        System.out.println("  │ 加锁失败怎么办       │ 自旋重试 + 退避策略 / 直接失败返回            │");
        System.out.println("  └──────────────────────┴──────────────────────────────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Redis 分布式锁怎么实现？");
        System.out.println("    A: SET key uuid NX EX 30 加锁，Lua 脚本判断 uuid 后 DEL 解锁");
        System.out.println("    Q: 锁过期了业务没执行完怎么办？");
        System.out.println("    A: Redisson 看门狗机制，后台线程每 10s 续期到 30s");
        System.out.println();
    }
}
