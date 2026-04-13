package com.kungfu.concurrent.d10_readwritelock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * 【Demo】读写锁 — 读多写少场景的性能优化
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>ReentrantReadWriteLock 读读共享、读写互斥、写写互斥</li>
 *   <li>锁降级：写锁 → 读锁 → 释放写锁</li>
 *   <li>StampedLock 乐观读的高性能方案</li>
 *   <li>读写锁 vs synchronized vs ReentrantLock 对比</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * 在缓存、配置管理等读多写少的场景中，读写锁可以显著提升并发性能。
 * StampedLock 的乐观读机制进一步减少了锁开销，是 JDK 8 并发优化的重要工具。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可
 *
 * @author kungfu
 * @since D18 - 并发编程
 */
public class ReadWriteLockDemo {

    // ========== 共享缓存，受读写锁保护 ==========
    private static final Map<String, String> CACHE = new HashMap<>();
    private static final ReadWriteLock RW_LOCK = new ReentrantReadWriteLock();
    private static final Lock READ_LOCK = RW_LOCK.readLock();
    private static final Lock WRITE_LOCK = RW_LOCK.writeLock();

    /**
     * 一、ReentrantReadWriteLock 基本用法
     * 5 个读线程 + 2 个写线程并发操作共享缓存，
     * 通过时间戳观察：读线程可以并发执行，写线程互斥。
     */
    private static void demonstrateReadWriteLock() throws InterruptedException {
        System.out.println("=== 一、ReentrantReadWriteLock 基本用法 ===\n");
        System.out.println("  5 个读线程 + 2 个写线程操作共享缓存");
        System.out.println("  观察：读线程并发执行，写线程独占执行\n");

        // 预置数据
        CACHE.put("config", "初始值");

        int readerCount = 5;
        int writerCount = 2;
        CountDownLatch latch = new CountDownLatch(readerCount + writerCount);
        long startTime = System.currentTimeMillis();

        // 启动读线程
        for (int i = 0; i < readerCount; i++) {
            final int id = i;
            new Thread(() -> {
                READ_LOCK.lock();
                try {
                    long elapsed = System.currentTimeMillis() - startTime;
                    String value = CACHE.get("config");
                    System.out.println("  [+" + String.format("%3d", elapsed) + "ms] 读线程-" + id
                            + " 读取: " + value);
                    Thread.sleep(200); // 模拟读操作耗时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    READ_LOCK.unlock();
                    latch.countDown();
                }
            }, "Reader-" + i).start();
        }

        // 稍等一下让读线程先获取锁
        Thread.sleep(50);

        // 启动写线程
        for (int i = 0; i < writerCount; i++) {
            final int id = i;
            new Thread(() -> {
                WRITE_LOCK.lock();
                try {
                    long elapsed = System.currentTimeMillis() - startTime;
                    String newValue = "写线程-" + id + " 更新于 +" + elapsed + "ms";
                    CACHE.put("config", newValue);
                    System.out.println("  [+" + String.format("%3d", elapsed) + "ms] 写线程-" + id
                            + " 写入: " + newValue);
                    Thread.sleep(100); // 模拟写操作耗时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    WRITE_LOCK.unlock();
                    latch.countDown();
                }
            }, "Writer-" + i).start();
        }

        latch.await();
        System.out.println();
        System.out.println("  结论：");
        System.out.println("    - 读线程的时间戳接近 → 读读共享（并发执行）");
        System.out.println("    - 写线程的时间戳晚于读线程 → 读写互斥");
        System.out.println("    - 两个写线程时间戳间隔 ~100ms → 写写互斥");
        System.out.println();
    }

    /**
     * 二、锁降级
     * 写锁 → 读锁 → 释放写锁 → 使用数据 → 释放读锁
     * 保证数据修改后，当前线程仍能看到最新值。
     */
    private static void demonstrateLockDowngrade() {
        System.out.println("=== 二、锁降级 ===\n");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        Lock readLock = rwLock.readLock();
        Lock writeLock = rwLock.writeLock();

        System.out.println("  【正确的锁降级流程】");
        System.out.println("  步骤：写锁 → 获取读锁 → 释放写锁 → 使用数据 → 释放读锁\n");

        // 模拟一个需要更新后立即读取的场景
        final String[] data = {"旧数据"};

        // 锁降级演示
        System.out.println("  Step 1: 获取写锁");
        writeLock.lock();
        try {
            System.out.println("  Step 2: 修改数据 \"旧数据\" → \"新数据\"");
            data[0] = "新数据";

            System.out.println("  Step 3: 获取读锁（降级）— 写锁未释放时可以获取读锁");
            readLock.lock(); // 降级：在持有写锁的同时获取读锁
        } finally {
            System.out.println("  Step 4: 释放写锁 — 此时仍持有读锁，其他写线程无法介入");
            writeLock.unlock();
        }
        try {
            System.out.println("  Step 5: 使用数据（持有读锁保证可见性）: " + data[0]);
        } finally {
            System.out.println("  Step 6: 释放读锁");
            readLock.unlock();
        }
        System.out.println();

        // 锁升级说明
        System.out.println("  【锁升级为什么会死锁？（原理说明，不实际执行）】");
        System.out.println("  假设线程 A 和线程 B 都持有读锁：");
        System.out.println("    线程 A: readLock.lock() → 成功");
        System.out.println("    线程 B: readLock.lock() → 成功");
        System.out.println("    线程 A: writeLock.lock() → 阻塞，等待线程 B 释放读锁");
        System.out.println("    线程 B: writeLock.lock() → 阻塞，等待线程 A 释放读锁");
        System.out.println("    → 死锁！双方都在等待对方释放读锁");
        System.out.println();
        System.out.println("  结论：锁降级（写→读）安全，锁升级（读→写）会死锁");
        System.out.println();
    }

    /**
     * 三、StampedLock（JDK 8）
     * 演示乐观读模式：先不加锁读取，验证后如果数据被修改则升级为悲观读锁。
     */
    private static void demonstrateStampedLock() throws InterruptedException {
        System.out.println("=== 三、StampedLock（JDK 8） ===\n");

        StampedLock stampedLock = new StampedLock();
        final double[] point = {1.0, 2.0}; // 共享数据：坐标点

        System.out.println("  共享数据: point(" + point[0] + ", " + point[1] + ")\n");

        // 乐观读示例（无写线程干扰）
        System.out.println("  【场景1: 乐观读成功（无写线程干扰）】");
        long stamp = stampedLock.tryOptimisticRead();  // 获取邮戳，不加锁
        System.out.println("  Step 1: tryOptimisticRead() 获取邮戳 stamp=" + stamp);
        double x = point[0];
        double y = point[1];
        System.out.println("  Step 2: 读取数据 x=" + x + ", y=" + y);
        if (stampedLock.validate(stamp)) {
            System.out.println("  Step 3: validate(stamp) = true → 数据有效，无需加锁");
            System.out.println("  结果: point(" + x + ", " + y + ")\n");
        }

        // 乐观读失败 → 升级为悲观读锁
        System.out.println("  【场景2: 乐观读失败（有写线程干扰）→ 升级为悲观读锁】");
        CountDownLatch writerReady = new CountDownLatch(1);
        CountDownLatch readerDone = new CountDownLatch(1);

        // 写线程：在读线程乐观读之后修改数据
        Thread writer = new Thread(() -> {
            try {
                writerReady.await(); // 等待读线程发出信号
                long ws = stampedLock.writeLock();
                try {
                    point[0] = 10.0;
                    point[1] = 20.0;
                    System.out.println("  [写线程] 修改数据为 point(10.0, 20.0)");
                } finally {
                    stampedLock.unlockWrite(ws);
                }
                readerDone.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "StampedWriter");
        writer.start();

        // 读线程逻辑
        stamp = stampedLock.tryOptimisticRead();
        System.out.println("  Step 1: tryOptimisticRead() 获取邮戳 stamp=" + stamp);
        x = point[0];
        y = point[1];
        System.out.println("  Step 2: 读取数据 x=" + x + ", y=" + y);

        // 触发写线程修改数据
        writerReady.countDown();
        readerDone.await(); // 等待写线程完成

        // 验证邮戳
        boolean valid = stampedLock.validate(stamp);
        System.out.println("  Step 3: validate(stamp) = " + valid + " → 数据已被修改！");
        if (!valid) {
            System.out.println("  Step 4: 升级为悲观读锁 readLock()");
            stamp = stampedLock.readLock();
            try {
                x = point[0];
                y = point[1];
                System.out.println("  Step 5: 重新读取数据 x=" + x + ", y=" + y);
            } finally {
                stampedLock.unlockRead(stamp);
                System.out.println("  Step 6: 释放悲观读锁");
            }
        }
        System.out.println("  结果: point(" + x + ", " + y + ")\n");

        // 完整代码模式
        System.out.println("  【乐观读标准代码模式】");
        System.out.println("    long stamp = lock.tryOptimisticRead();");
        System.out.println("    // 读取共享数据到局部变量");
        System.out.println("    if (!lock.validate(stamp)) {");
        System.out.println("        stamp = lock.readLock();  // 升级为悲观读锁");
        System.out.println("        try {");
        System.out.println("            // 重新读取共享数据");
        System.out.println("        } finally {");
        System.out.println("            lock.unlockRead(stamp);");
        System.out.println("        }");
        System.out.println("    }");
        System.out.println("    // 使用局部变量中的数据");
        System.out.println();
    }

    /**
     * 四、对比表
     * synchronized / ReentrantLock / ReentrantReadWriteLock / StampedLock
     */
    private static void showComparisonTable() {
        System.out.println("=== 四、对比表 ===\n");
        System.out.println("  ┌───────────────────────┬──────┬──────┬──────┬──────┬──────┬────────┐");
        System.out.println("  │         锁类型        │ 读读 │ 读写 │ 写写 │可重入│公平性│  性能  │");
        System.out.println("  ├───────────────────────┼──────┼──────┼──────┼──────┼──────┼────────┤");
        System.out.println("  │ synchronized          │ 互斥 │ 互斥 │ 互斥 │  是  │  否  │  一般  │");
        System.out.println("  ├───────────────────────┼──────┼──────┼──────┼──────┼──────┼────────┤");
        System.out.println("  │ ReentrantLock         │ 互斥 │ 互斥 │ 互斥 │  是  │ 可选 │  较好  │");
        System.out.println("  ├───────────────────────┼──────┼──────┼──────┼──────┼──────┼────────┤");
        System.out.println("  │ ReentrantReadWriteLock│ 共享 │ 互斥 │ 互斥 │  是  │ 可选 │  好    │");
        System.out.println("  ├───────────────────────┼──────┼──────┼──────┼──────┼──────┼────────┤");
        System.out.println("  │ StampedLock           │ 共享 │ 互斥 │ 互斥 │  否  │  否  │  最好  │");
        System.out.println("  └───────────────────────┴──────┴──────┴──────┴──────┴──────┴────────┘");
        System.out.println();
        System.out.println("  选择建议：");
        System.out.println("    - 简单同步 → synchronized（JVM 优化后性能已不差）");
        System.out.println("    - 需要 tryLock/公平锁/Condition → ReentrantLock");
        System.out.println("    - 读多写少 → ReentrantReadWriteLock");
        System.out.println("    - 读多写少 + 极致性能 → StampedLock（注意不可重入）");
        System.out.println();
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  读写锁 — 读多写少场景的性能优化");
        System.out.println("========================================\n");

        demonstrateReadWriteLock();
        demonstrateLockDowngrade();
        demonstrateStampedLock();
        showComparisonTable();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. 读写锁核心：读读共享、读写互斥、写写互斥");
        System.out.println("  2. 锁降级支持（写→读），锁升级不支持（读→写会死锁）");
        System.out.println("  3. StampedLock 乐观读不加锁，性能最好，但不可重入");
        System.out.println("  4. 读多写少场景：StampedLock > ReadWriteLock > ReentrantLock > synchronized");
        System.out.println("  5. StampedLock 不支持 Condition，不可重入，使用需谨慎");
        System.out.println();
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * ReadWriteLock → AQS 共享/独占模式 → StampedLock → 缓存系统设计
 * 至此 W02 并发编程核心模块全部完成，下一步进入 W03 Spring 框架
 */
