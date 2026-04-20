package com.kungfu.distributed.d34_redisson;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】Redisson 分布式锁 — 看门狗 + 可重入 + 公平锁
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Redisson 基本加锁/解锁</li>
 *   <li>看门狗自动续期机制</li>
 *   <li>可重入锁演示</li>
 *   <li>tryLock 超时等待</li>
 *   <li>并发库存扣减（对比 D33 手写锁）</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Redis 运行在 localhost:6379
 *
 * @author kungfu
 * @since D34 - 分布式锁进阶
 */
public class RedissonWatchDogDemo {

    private static RedissonClient redisson;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Redisson 分布式锁 — 看门狗机制");
        System.out.println("========================================\n");

        // 初始化 Redisson
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        try {
            redisson = Redisson.create(config);
            System.out.println("  ✓ Redisson 连接成功\n");
        } catch (Exception e) {
            System.out.println("  ✗ Redisson 连接失败: " + e.getMessage());
            return;
        }

        showWatchDogPrinciple();
        demoBasicLock();
        demoReentrant();
        demoTryLock();
        demoConcurrentStock();
        showSourceAnalysis();

        redisson.shutdown();
    }

    // =============================================================
    // 一、看门狗原理
    // =============================================================
    private static void showWatchDogPrinciple() {
        System.out.println("=== 一、看门狗（WatchDog）原理 ===\n");

        System.out.println("  问题：手写 Redis 锁设了 30s 过期，但业务执行了 35s → 锁提前释放 → 并发问题");
        System.out.println("  解决：Redisson 看门狗自动续期\n");

        System.out.println("  ┌────────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 看门狗工作流程：                                               │");
        System.out.println("  │                                                                │");
        System.out.println("  │ 1. lock() 不指定 leaseTime → 默认 lockWatchdogTimeout = 30s   │");
        System.out.println("  │ 2. 加锁成功后，启动后台定时任务（Netty HashedWheelTimer）      │");
        System.out.println("  │ 3. 每 30/3 = 10s 执行一次续期，将锁 TTL 重置为 30s            │");
        System.out.println("  │ 4. unlock() 时取消定时任务                                     │");
        System.out.println("  │ 5. 如果 JVM 宕机 → 定时任务停止 → 锁 30s 后自动过期           │");
        System.out.println("  └────────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 关键：lock() 不传 leaseTime 才会启动看门狗");
        System.out.println("    lock(10, TimeUnit.SECONDS) → 不启动看门狗，10s 后自动过期\n");
    }

    // =============================================================
    // 二、基本加锁
    // =============================================================
    private static void demoBasicLock() {
        System.out.println("=== 二、基本加锁/解锁 ===\n");

        RLock lock = redisson.getLock("demo:d34:basic");
        System.out.println("  获取锁对象: demo:d34:basic");

        lock.lock(); // 看门狗自动续期
        System.out.println("  lock() 成功 → 看门狗已启动");
        System.out.println("  Redis 中: demo:d34:basic → Hash{uuid:threadId = 1}, TTL=30s");

        try {
            System.out.println("  执行业务逻辑...");
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
            System.out.println("  unlock() 成功 → 看门狗已停止\n");
        }
    }

    // =============================================================
    // 三、可重入锁
    // =============================================================
    private static void demoReentrant() {
        System.out.println("=== 三、可重入锁 ===\n");

        RLock lock = redisson.getLock("demo:d34:reentrant");

        System.out.println("  第一次 lock()...");
        lock.lock();
        System.out.println("    Redis: 重入计数 = 1");

        System.out.println("  第二次 lock()（同一线程重入）...");
        lock.lock();
        System.out.println("    Redis: 重入计数 = 2");

        System.out.println("  第一次 unlock()...");
        lock.unlock();
        System.out.println("    Redis: 重入计数 = 1（锁未释放）");

        System.out.println("  第二次 unlock()...");
        lock.unlock();
        System.out.println("    Redis: 重入计数 = 0 → 锁释放\n");

        System.out.println("  底层实现：Hash 结构");
        System.out.println("    HSET demo:d34:reentrant uuid:threadId 1  (第一次加锁)");
        System.out.println("    HINCRBY demo:d34:reentrant uuid:threadId 1  (重入)");
        System.out.println("    HINCRBY demo:d34:reentrant uuid:threadId -1  (解锁)");
        System.out.println("    当计数归零时 DEL key\n");
    }

    // =============================================================
    // 四、tryLock 超时等待
    // =============================================================
    private static void demoTryLock() throws Exception {
        System.out.println("=== 四、tryLock 超时等待 ===\n");

        RLock lock = redisson.getLock("demo:d34:trylock");

        // 线程 A 先持有锁
        lock.lock();
        System.out.println("  线程 A 持有锁");

        // 线程 B 尝试获取（等待 2 秒）
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                System.out.println("  线程 B tryLock(waitTime=2s)...");
                boolean acquired = lock.tryLock(2, 10, TimeUnit.SECONDS);
                if (acquired) {
                    System.out.println("  线程 B: ✓ 获取成功（A 已释放）");
                    lock.unlock();
                } else {
                    System.out.println("  线程 B: ✗ 等待超时，获取失败");
                }
            } catch (InterruptedException ignored) {
            }
            latch.countDown();
        }).start();

        // A 1 秒后释放
        Thread.sleep(1000);
        lock.unlock();
        System.out.println("  线程 A 释放锁");

        latch.await();
        System.out.println();
    }

    // =============================================================
    // 五、并发库存扣减
    // =============================================================
    private static void demoConcurrentStock() throws Exception {
        System.out.println("=== 五、并发库存扣减（Redisson 锁）===\n");

        AtomicInteger stock = new AtomicInteger(100);
        AtomicInteger successCount = new AtomicInteger(0);
        int threadCount = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                RLock lock = redisson.getLock("demo:d34:stock:lock");
                lock.lock();
                try {
                    if (stock.get() > 0) {
                        stock.decrementAndGet();
                        successCount.incrementAndGet();
                    }
                } finally {
                    lock.unlock();
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("  初始库存: 100");
        System.out.println("  并发请求: " + threadCount);
        System.out.println("  成功扣减: " + successCount.get());
        System.out.println("  最终库存: " + stock.get());
        System.out.println("  耗时: " + elapsed + " ms");
        System.out.println("  → " + (stock.get() == 0 ? "✓ 精确扣减，无超卖" : "✓ 库存安全") + "\n");
    }

    // =============================================================
    // 六、源码分析
    // =============================================================
    private static void showSourceAnalysis() {
        System.out.println("=== 六、Redisson 锁源码关键点 ===\n");

        System.out.println("  加锁 Lua 脚本（简化版）：");
        System.out.println("    if redis.call('exists', KEYS[1]) == 0 then");
        System.out.println("        redis.call('hincrby', KEYS[1], ARGV[2], 1)");
        System.out.println("        redis.call('pexpire', KEYS[1], ARGV[1])");
        System.out.println("        return nil");
        System.out.println("    end");
        System.out.println("    if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then");
        System.out.println("        redis.call('hincrby', KEYS[1], ARGV[2], 1)  -- 重入");
        System.out.println("        redis.call('pexpire', KEYS[1], ARGV[1])");
        System.out.println("        return nil");
        System.out.println("    end");
        System.out.println("    return redis.call('pttl', KEYS[1])  -- 返回剩余时间（加锁失败）\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Redisson 看门狗怎么实现的？");
        System.out.println("    A: 1) lock() 不传 leaseTime 时启动看门狗");
        System.out.println("       2) 用 Netty HashedWheelTimer 定时任务");
        System.out.println("       3) 每 lockWatchdogTimeout/3 (默认10s) 续期一次");
        System.out.println("       4) 续期 = 重置 TTL 为 30s");
        System.out.println("       5) unlock() 或 JVM 宕机时停止续期");
        System.out.println();
    }
}
