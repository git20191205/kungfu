package com.kungfu.concurrent.d4_thread_pool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】线程池 7 参数与拒绝策略
 *
 * <p>这个Demo演示什么：
 * <ul>
 *   <li>线程池的 7 个核心构造参数及其含义</li>
 *   <li>4 种内置拒绝策略的行为差异</li>
 *   <li>线程池任务提交的完整执行流程</li>
 *   <li>常见线程池使用陷阱与线上配置建议</li>
 * </ul>
 *
 * <p>为什么重要：
 * <ul>
 *   <li>线程池是 Java 并发编程的核心基础设施，几乎所有服务端应用都依赖线程池</li>
 *   <li>错误的线程池配置会导致 OOM、线程爆炸、任务丢失等严重生产事故</li>
 *   <li>面试高频考点：7 参数、执行流程、拒绝策略是必问内容</li>
 * </ul>
 *
 * <p>运行方式：直接运行 main 方法，观察控制台输出
 *
 * @author kungfu
 * @since D11 - 并发编程
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("   线程池 7 参数与拒绝策略 Demo");
        System.out.println("========================================");

        showSevenParameters();

        Thread.sleep(500);
        demonstrateRejectPolicies();

        Thread.sleep(500);
        demonstrateExecutionFlow();

        showPoolTraps();

        showConfigSuggestions();

        // 面试速记
        System.out.println();
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================");
        System.out.println();
        System.out.println("  1. 7大参数一句话概括：");
        System.out.println("     核心线程数、最大线程数、空闲存活时间、时间单位、");
        System.out.println("     工作队列、线程工厂、拒绝策略处理器");
        System.out.println();
        System.out.println("  2. 执行流程：core -> queue -> max -> reject");
        System.out.println("     先用核心线程，满了放队列，队列满了扩到最大线程，再满就拒绝");
        System.out.println();
        System.out.println("  3. 永远别用 Executors 创建线程池！");
        System.out.println("     FixedThreadPool / SingleThreadExecutor -> 无界队列 -> OOM");
        System.out.println("     CachedThreadPool -> maxPoolSize=Integer.MAX_VALUE -> 线程爆炸");
        System.out.println();
    }

    // =============================================================
    // === 一、线程池 7 个核心参数 ===
    // =============================================================

    /**
     * 展示线程池的 7 个核心构造参数及其含义。
     */
    static void showSevenParameters() {
        System.out.println();
        System.out.println("=== 一、线程池 7 个核心参数 ===");
        System.out.println();
        System.out.println("  ThreadPoolExecutor 构造方法的 7 个参数：");
        System.out.println();
        System.out.println("  ┌────┬──────────────────────────┬────────────────────────────────────────┐");
        System.out.println("  │ #  │ 参数名                   │ 说明                                   │");
        System.out.println("  ├────┼──────────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ 1  │ corePoolSize             │ 核心线程数，即使空闲也不会被回收         │");
        System.out.println("  │ 2  │ maximumPoolSize           │ 最大线程数，线程池能创建的线程上限       │");
        System.out.println("  │ 3  │ keepAliveTime             │ 非核心线程空闲存活时间                   │");
        System.out.println("  │ 4  │ unit (TimeUnit)           │ keepAliveTime 的时间单位                │");
        System.out.println("  │ 5  │ workQueue                 │ 任务等待队列（BlockingQueue<Runnable>） │");
        System.out.println("  │ 6  │ threadFactory             │ 线程工厂，用于创建线程（可自定义命名）   │");
        System.out.println("  │ 7  │ handler                   │ 拒绝策略（RejectedExecutionHandler）    │");
        System.out.println("  └────┴──────────────────────────┴────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  示例代码：");
        System.out.println("    new ThreadPoolExecutor(");
        System.out.println("        2,                          // corePoolSize");
        System.out.println("        4,                          // maximumPoolSize");
        System.out.println("        60L,                        // keepAliveTime");
        System.out.println("        TimeUnit.SECONDS,           // unit");
        System.out.println("        new ArrayBlockingQueue<>(2),// workQueue");
        System.out.println("        Executors.defaultThreadFactory(), // threadFactory");
        System.out.println("        new ThreadPoolExecutor.AbortPolicy() // handler");
        System.out.println("    );");
        System.out.println();
    }

    // =============================================================
    // === 二、4 种拒绝策略 ===
    // =============================================================

    /**
     * 演示线程池的 4 种内置拒绝策略。
     * 每种策略使用一个 tiny pool (core=1, max=2, queue=2)，容量为 4，
     * 提交 5 个任务触发拒绝。
     */
    static void demonstrateRejectPolicies() throws InterruptedException {
        System.out.println("=== 二、4 种拒绝策略 ===");
        System.out.println();
        System.out.println("  线程池容量 = maxPoolSize + queueSize = 2 + 2 = 4");
        System.out.println("  提交 5 个任务，第 5 个任务将触发拒绝策略");
        System.out.println();

        // 1. AbortPolicy
        System.out.println("  --- 1. AbortPolicy（默认）：抛出 RejectedExecutionException ---");
        testRejectPolicy("AbortPolicy", new ThreadPoolExecutor.AbortPolicy());
        Thread.sleep(300);

        // 2. CallerRunsPolicy
        System.out.println("  --- 2. CallerRunsPolicy：由提交任务的线程（调用者）执行 ---");
        testRejectPolicy("CallerRunsPolicy", new ThreadPoolExecutor.CallerRunsPolicy());
        Thread.sleep(300);

        // 3. DiscardPolicy
        System.out.println("  --- 3. DiscardPolicy：静默丢弃被拒绝的任务 ---");
        testRejectPolicy("DiscardPolicy", new ThreadPoolExecutor.DiscardPolicy());
        Thread.sleep(300);

        // 4. DiscardOldestPolicy
        System.out.println("  --- 4. DiscardOldestPolicy：丢弃队列中最老的任务，重新提交当前任务 ---");
        testRejectPolicy("DiscardOldestPolicy", new ThreadPoolExecutor.DiscardOldestPolicy());
        Thread.sleep(300);

        System.out.println();
    }

    /**
     * 用指定的拒绝策略测试线程池行为。
     */
    private static void testRejectPolicy(String policyName, RejectedExecutionHandler handler) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                new NamedThreadFactory("reject-" + policyName),
                handler
        );

        try {
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                try {
                    pool.execute(() -> {
                        System.out.println("    任务" + taskId + " 执行中，线程=" + Thread.currentThread().getName());
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    System.out.println("    任务" + taskId + " 提交成功");
                } catch (RejectedExecutionException e) {
                    System.out.println("    任务" + taskId + " 被拒绝！抛出 RejectedExecutionException");
                }
            }
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println();
    }

    // =============================================================
    // === 三、线程池执行流程 ===
    // =============================================================

    /**
     * 演示线程池任务提交的执行流程。
     * core=2, max=4, queue=2，总容量=6，提交 7 个任务观察流程。
     */
    static void demonstrateExecutionFlow() throws InterruptedException {
        System.out.println("=== 三、线程池执行流程 ===");
        System.out.println();
        System.out.println("  线程池配置：core=2, max=4, queue=2");
        System.out.println("  执行流程：core -> queue -> max -> reject");
        System.out.println();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                new NamedThreadFactory("flow"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            for (int i = 1; i <= 7; i++) {
                final int taskId = i;
                try {
                    pool.execute(() -> {
                        String phase;
                        if (taskId <= 2) {
                            phase = "核心线程处理";
                        } else if (taskId <= 4) {
                            phase = "进入工作队列";
                        } else if (taskId <= 6) {
                            phase = "创建新线程（扩展到max）";
                        } else {
                            phase = "触发拒绝策略";
                        }
                        System.out.println("    任务" + taskId + " -> " + phase
                                + "，线程=" + Thread.currentThread().getName());
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    int poolSize = pool.getPoolSize();
                    int queueSize = pool.getQueue().size();
                    System.out.println("    [提交任务" + taskId + "] poolSize=" + poolSize
                            + ", queueSize=" + queueSize);
                } catch (RejectedExecutionException e) {
                    System.out.println("    [提交任务" + taskId + "] 被拒绝！（pool已满，触发拒绝策略）");
                }
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        System.out.println();
        System.out.println("  执行流程总结：");
        System.out.println("  ┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────┐");
        System.out.println("  │ 新任务   │───>│ 核心线程 │───>│ 工作队列     │───>│ 非核心   │───> 拒绝策略");
        System.out.println("  │ 提交     │    │ < core   │    │ 已满？       │    │ 线程<max │");
        System.out.println("  └──────────┘    └──────────┘    └──────────────┘    └──────────┘");
        System.out.println();
    }

    // =============================================================
    // === 四、常见线程池陷阱 ===
    // =============================================================

    /**
     * 展示常见线程池陷阱（仅打印说明，不实际触发 OOM）。
     */
    static void showPoolTraps() {
        System.out.println("=== 四、常见线程池陷阱 ===");
        System.out.println();
        System.out.println("  ┌───────────────────────────┬───────────────────────┬──────────────────────────┐");
        System.out.println("  │ Executors 工厂方法        │ 隐患                  │ 原因                     │");
        System.out.println("  ├───────────────────────────┼───────────────────────┼──────────────────────────┤");
        System.out.println("  │ newFixedThreadPool(n)     │ OOM（内存溢出）       │ 使用 LinkedBlockingQueue  │");
        System.out.println("  │                           │                       │ 无界队列，任务无限堆积   │");
        System.out.println("  ├───────────────────────────┼───────────────────────┼──────────────────────────┤");
        System.out.println("  │ newCachedThreadPool()     │ 线程爆炸              │ max=Integer.MAX_VALUE    │");
        System.out.println("  │                           │                       │ 无限创建线程             │");
        System.out.println("  ├───────────────────────────┼───────────────────────┼──────────────────────────┤");
        System.out.println("  │ newSingleThreadExecutor() │ OOM（内存溢出）       │ 同 FixedThreadPool       │");
        System.out.println("  │                           │                       │ 无界队列，任务无限堆积   │");
        System.out.println("  └───────────────────────────┴───────────────────────┴──────────────────────────┘");
        System.out.println();
        System.out.println("  结论：线上环境永远使用 ThreadPoolExecutor 手动创建线程池！");
        System.out.println();
    }

    // =============================================================
    // === 五、线上线程池配置建议 ===
    // =============================================================

    /**
     * 展示线上线程池配置建议。
     */
    static void showConfigSuggestions() {
        System.out.println("=== 五、线上线程池配置建议 ===");
        System.out.println();

        int cpuCores = Runtime.getRuntime().availableProcessors();
        System.out.println("  当前机器 CPU 核心数：" + cpuCores);
        System.out.println();
        System.out.println("  ┌──────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 任务类型         │ 推荐线程数                           │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ CPU 密集型       │ CPU 核心数 + 1 = " + padRight(String.valueOf(cpuCores + 1), 20) + "│");
        System.out.println("  │ （计算、排序等） │ 多1个线程防止页缺失等偶发暂停        │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ IO 密集型        │ CPU 核心数 * 2 = " + padRight(String.valueOf(cpuCores * 2), 20) + "│");
        System.out.println("  │ （网络、磁盘等） │ 或 cores / (1 - blockingRatio)       │");
        System.out.println("  └──────────────────┴──────────────────────────────────────┘");
        System.out.println();
        System.out.println("  公式说明：");
        System.out.println("    blockingRatio = 线程等待时间 / (等待时间 + 计算时间)");
        System.out.println("    例如：blockingRatio=0.8 时，线程数 = " + cpuCores + " / (1 - 0.8) = " + (cpuCores * 5));
        System.out.println();
    }

    // =============================================================
    // 辅助工具
    // =============================================================

    /**
     * 自定义线程工厂，为线程池中的线程提供有意义的命名。
     */
    static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-thread-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }

    /**
     * 右侧填充空格到指定长度。
     */
    private static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}

/*
 * 【知识串联】
 * 理解了线程池后，下一步：
 * → 能否手写一个简化版线程池？（见 MiniThreadPool.java）
 * → 并发容器如何保证线程安全？（见 ConcurrentHashMapDemo.java）
 */
