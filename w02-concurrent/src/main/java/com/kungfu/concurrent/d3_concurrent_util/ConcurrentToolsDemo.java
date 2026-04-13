package com.kungfu.concurrent.d3_concurrent_util;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】并发工具类 — CountDownLatch / CyclicBarrier / Semaphore
 *
 * <p>这个Demo演示什么：
 *   演示 JUC 中三大并发协调工具的核心用法与典型场景，
 *   通过可运行的示例直观感受它们的行为差异。
 *
 * <p>为什么重要：
 *   多线程编程中，线程间的"等待-通知"与"限流"是最常见的协调需求。
 *   CountDownLatch、CyclicBarrier、Semaphore 分别解决了
 *   "等待多任务完成"、"多线程集合点"、"并发资源限流"三类问题，
 *   是面试高频考点，也是生产级代码的基础组件。
 *
 * <p>运行方式：
 *   直接运行 main 方法，依次观察三个工具的运行效果和对比表。
 *
 * @author kungfu
 * @since D10 - 并发编程
 */
public class ConcurrentToolsDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  并发工具类 — CountDownLatch / CyclicBarrier / Semaphore");
        System.out.println("========================================\n");

        demonstrateCountDownLatch();

        System.out.println();

        demonstrateCyclicBarrier();

        System.out.println();

        demonstrateSemaphore();

        System.out.println();

        showComparisonTable();

        System.out.println();

        // ==================== 面试速记 ====================
        System.out.println("=== 面试速记 ===");
        System.out.println("  1. CountDownLatch：一次性门闩，countDown() 减到 0 后 await() 放行，不可重用");
        System.out.println("  2. CyclicBarrier：可循环屏障，所有线程 await() 到齐后一起放行，可重用");
        System.out.println("  3. Semaphore：信号量/令牌桶，acquire() 获取许可，release() 归还，用于限流");
        System.out.println("  4. CountDownLatch 基于 AQS 共享模式，CyclicBarrier 基于 ReentrantLock + Condition");
        System.out.println("  5. Semaphore 基于 AQS 共享模式，支持公平/非公平两种策略");
        System.out.println("  6. 三者都不能替代 synchronized，它们解决的是线程间协调，而非互斥");
    }

    // ==========================================
    // === 一、CountDownLatch — 等待多个任务完成 ===
    // ==========================================

    /**
     * 场景：微服务健康检查 — 主线程等待 3 个服务全部就绪后再启动系统。
     */
    public static void demonstrateCountDownLatch() throws InterruptedException {
        System.out.println("=== 一、CountDownLatch — 等待多个任务完成 ===");
        System.out.println("  场景：微服务健康检查，3 个服务全部就绪后系统才启动\n");

        String[] services = {"UserService", "OrderService", "PaymentService"};
        CountDownLatch latch = new CountDownLatch(3);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            final String serviceName = services[i];
            new Thread(() -> {
                try {
                    // 模拟健康检查耗时（200~800ms）
                    int checkTime = ThreadLocalRandom.current().nextInt(200, 800);
                    TimeUnit.MILLISECONDS.sleep(checkTime);
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println("  [" + elapsed + "ms] " + serviceName
                            + " 健康检查通过 ✓  (耗时 " + checkTime + "ms)  → countDown()");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }, "check-" + serviceName).start();
        }

        System.out.println("  [主线程] 等待所有服务就绪... await()");
        latch.await();

        long totalElapsed = System.currentTimeMillis() - startTime;
        System.out.println("  [" + totalElapsed + "ms] 所有服务健康检查通过，系统启动！");
        System.out.println("  → 总耗时取决于最慢的服务（并行检查），而非三者之和");
    }

    // ==========================================
    // === 二、CyclicBarrier — 多线程到齐后一起出发 ===
    // ==========================================

    /**
     * 场景：赛跑 — 3 个选手到齐后一起出发，跑 2 轮（展示可重用特性）。
     */
    public static void demonstrateCyclicBarrier() throws InterruptedException {
        System.out.println("=== 二、CyclicBarrier — 多线程到齐后一起出发 ===");
        System.out.println("  场景：赛跑 — 3 个选手到齐后一起出发，跑 2 轮展示可重用\n");

        final int RUNNER_COUNT = 3;
        final int ROUNDS = 2;

        CyclicBarrier barrier = new CyclicBarrier(RUNNER_COUNT, () -> {
            System.out.println("  >>> 所有选手就位，GO！<<<");
        });

        // 用 CountDownLatch 确保所有轮次结束后再继续 main
        CountDownLatch allDone = new CountDownLatch(RUNNER_COUNT);

        for (int i = 1; i <= RUNNER_COUNT; i++) {
            final int runnerId = i;
            new Thread(() -> {
                try {
                    for (int round = 1; round <= ROUNDS; round++) {
                        // 模拟准备时间
                        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                        System.out.println("  [第" + round + "轮] Runner-" + runnerId + " 准备就绪 → await()");

                        barrier.await();

                        // 屏障打开后开始跑
                        System.out.println("  [第" + round + "轮] Runner-" + runnerId + " 起跑中...");
                        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(100, 300));
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            }, "Runner-" + runnerId).start();
        }

        allDone.await();
        System.out.println("  → CyclicBarrier 可重用：同一个 barrier 跑了 " + ROUNDS + " 轮");
    }

    // ==========================================
    // === 三、Semaphore — 限流/资源池 ===
    // ==========================================

    /**
     * 场景：停车场 — 3 个车位，5 辆车，同一时刻最多 3 辆车停放。
     */
    public static void demonstrateSemaphore() throws InterruptedException {
        System.out.println("=== 三、Semaphore — 限流/资源池 ===");
        System.out.println("  场景：停车场 — 3 个车位，5 辆车\n");

        final int PARKING_SPOTS = 3;
        final int CAR_COUNT = 5;

        Semaphore semaphore = new Semaphore(PARKING_SPOTS);
        CountDownLatch allParked = new CountDownLatch(CAR_COUNT);

        for (int i = 1; i <= CAR_COUNT; i++) {
            final int carId = i;
            new Thread(() -> {
                try {
                    System.out.println("  Car-" + carId + " 到达停车场，等待车位... (可用车位: "
                            + semaphore.availablePermits() + ")");

                    semaphore.acquire();

                    System.out.println("  Car-" + carId + " 停入车位 ← acquire()  (剩余车位: "
                            + semaphore.availablePermits() + ")");

                    // 模拟停车时间
                    TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(500, 1000));

                    System.out.println("  Car-" + carId + " 驶出车位 → release()  (剩余车位: "
                            + (semaphore.availablePermits() + 1) + ")");

                    semaphore.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allParked.countDown();
                }
            }, "Car-" + carId).start();

            // 稍微错开到达时间，让输出更清晰
            TimeUnit.MILLISECONDS.sleep(50);
        }

        allParked.await();
        System.out.println("  → 虽然有 5 辆车，但同一时刻最多只有 " + PARKING_SPOTS + " 辆在停车场内");
    }

    // ==========================================
    // === 四、三者对比表 ===
    // ==========================================

    /**
     * 以 box-drawing 字符打印 CountDownLatch / CyclicBarrier / Semaphore 对比表。
     */
    public static void showComparisonTable() {
        System.out.println("=== 四、三者对比表 ===\n");

        System.out.println("  ┌──────────┬──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │   特性   │  CountDownLatch  │  CyclicBarrier   │    Semaphore     │");
        System.out.println("  ├──────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │   作用   │ 等待N个任务完成  │ N个线程互相等待  │ 控制并发访问数量 │");
        System.out.println("  ├──────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 计数方式 │ countDown()减1   │ await()自动减1   │ acquire()/release│");
        System.out.println("  ├──────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 可重用   │ 否（一次性）     │ 是（自动重置）   │ 是（许可可归还） │");
        System.out.println("  ├──────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 线程关系 │ 一等多           │ 多等多           │ 限流，无等待关系 │");
        System.out.println("  ├──────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 底层实现 │ AQS 共享模式     │ ReentrantLock    │ AQS 共享模式     │");
        System.out.println("  │          │                  │ + Condition      │                  │");
        System.out.println("  └──────────┴──────────────────┴──────────────────┴──────────────────┘");
    }
}

/*
 * 【知识串联】
 * 理解了并发工具类后，下一步：
 * → 线程池如何统一管理线程？（见 ThreadPoolDemo.java）
 * → 如何手写一个简化版线程池？（见 MiniThreadPool.java）
 * → 并发容器如何保证线程安全？（见 ConcurrentHashMapDemo.java）
 */
