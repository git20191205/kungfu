package com.kungfu.concurrent.d7_threadlocal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】ThreadLocal 残留值复现 — 修复 ThreadLocalDemo 小节三的瑕疵
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <p>
 * ThreadLocalDemo 中"错误用法"使用 newFixedThreadPool(2)，
 * 两个任务分配到不同线程，thread-2 从未 set 过值，读到 null 是正常行为，
 * 并非真正的"残留值泄露"。
 * </p>
 * <p>
 * 本 Demo 使用 newFixedThreadPool(1) 强制线程复用，
 * 稳定复现残留值问题，形成鲜明对比。
 * </p>
 *
 * <h3>核心原理</h3>
 * <pre>
 * core=1 的线程池 → 只有 1 个 Worker 线程
 *   → 任务1 和 任务2 必定在同一个 Thread 上执行
 *     → 同一个 Thread = 同一个 ThreadLocalMap
 *       → 任务1 set 的值，任务2 能 get 到（残留！）
 * </pre>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可
 *
 * ● ThreadLocalDemo2.java 已创建，包含 3 个对比实验：
 *
 *   ┌──────────────┬────────┬────────────────────┬─────────────────────────────────────────────┐
 *   │     实验     │ 线程池 │      remove?       │                  预期结果                   │
 *   ├──────────────┼────────┼────────────────────┼─────────────────────────────────────────────┤
 *   │ 一、错误用法 │ core=1 │ 不 remove          │ 任务2 读到 "敏感数据-用户A"（残留泄露）     │
 *   ├──────────────┼────────┼────────────────────┼─────────────────────────────────────────────┤
 *   │ 二、正确用法 │ core=1 │ try-finally remove │ 任务2 读到 null（已清理）                   │
 *   ├──────────────┼────────┼────────────────────┼─────────────────────────────────────────────┤
 *   │ 三、对比实验 │ core=2 │ 不 remove          │ 任务2 大概率读到 null（不同线程，从未 set） │
 *   └──────────────┴────────┴────────────────────┴─────────────────────────────────────────────┘
 *
 *   核心修复点：newFixedThreadPool(2) → newFixedThreadPool(1)，强制线程复用，让残留值问题无处可逃。
 *
 *   下一步：请在 IDEA 中运行 ThreadLocalDemo2 的 main 方法，把日志分享给我。
 * @author kungfu
 * @since D15 - 并发编程
 */
public class ThreadLocalDemo2 {

    // ========================================
    //  实验一：错误用法 — 不 remove，残留值泄露
    // ========================================

    /**
     * 错误用法：core=1 线程池中不调用 remove()，
     * 任务2 复用同一线程，直接读到任务1 的残留值。
     */
    private static void demonstrateLeakedValue() throws InterruptedException {
        System.out.println("=== 一、错误用法 — 不 remove()，残留值泄露 ===\n");

        ExecutorService pool = Executors.newFixedThreadPool(1);
        ThreadLocal<String> threadLocal = new ThreadLocal<>();

        // 任务1：set 值，故意不 remove
        pool.execute(() -> {
            threadLocal.set("敏感数据-用户A");
            System.out.println("  [任务1] " + Thread.currentThread().getName()
                    + " set: 敏感数据-用户A");
            System.out.println("  [任务1] 处理完毕，忘记 remove()！");
        });

        // 等待任务1 完成，确保任务2 复用同一线程
        Thread.sleep(200);

        // 任务2：什么都没 set，直接 get
        pool.execute(() -> {
            String leaked = threadLocal.get();
            System.out.println("  [任务2] " + Thread.currentThread().getName()
                    + " get: " + leaked);
            if (leaked != null) {
                System.out.println("  [任务2] 残留值泄露！读到了用户A的数据！");
            } else {
                System.out.println("  [任务2] 未读到残留值（null）");
            }
        });

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }

    // ========================================
    //  实验二：正确用法 — try-finally remove
    // ========================================

    /**
     * 正确用法：core=1 线程池中用 try-finally 确保 remove()，
     * 任务2 复用同一线程，读到 null（已清理）。
     */
    private static void demonstrateCorrectUsage() throws InterruptedException {
        System.out.println("=== 二、正确用法 — try-finally remove() ===\n");

        ExecutorService pool = Executors.newFixedThreadPool(1);
        ThreadLocal<String> threadLocal = new ThreadLocal<>();

        // 任务1：set 值，try-finally 保证 remove
        pool.execute(() -> {
            try {
                threadLocal.set("敏感数据-用户B");
                System.out.println("  [任务1] " + Thread.currentThread().getName()
                        + " set: 敏感数据-用户B");
                System.out.println("  [任务1] 处理业务...");
            } finally {
                threadLocal.remove();
                System.out.println("  [任务1] finally 中 remove()，清理完毕");
            }
        });

        // 等待任务1 完成，确保任务2 复用同一线程
        Thread.sleep(200);

        // 任务2：复用同一线程，验证已清理
        pool.execute(() -> {
            String value = threadLocal.get();
            System.out.println("  [任务2] " + Thread.currentThread().getName()
                    + " get: " + value);
            System.out.println("  [任务2] 值为 null，说明 remove() 生效，无残留");
        });

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }

    // ========================================
    //  实验三：对比 — core=2 为什么无法复现？
    // ========================================

    /**
     * 对比实验：core=2 时任务可能分配到不同线程，
     * thread-2 从未 set 过值，读到 null 不是"清理干净"，
     * 而是"压根没设置过"。
     */
    private static void demonstrateCore2Pitfall() throws InterruptedException {
        System.out.println("=== 三、对比 — core=2 为什么无法复现残留？ ===\n");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        ThreadLocal<String> threadLocal = new ThreadLocal<>();

        // 任务1：set 值，不 remove
        pool.execute(() -> {
            threadLocal.set("敏感数据-用户C");
            System.out.println("  [任务1] " + Thread.currentThread().getName()
                    + " set: 敏感数据-用户C（不 remove）");
        });

        Thread.sleep(200);

        // 任务2：可能分配到 thread-2（不同线程）
        pool.execute(() -> {
            String value = threadLocal.get();
            System.out.println("  [任务2] " + Thread.currentThread().getName()
                    + " get: " + value);
            System.out.println("  [任务2] 如果线程名不同 → null 是因为从未 set，不是 remove 的功劳");
        });

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  ThreadLocal 残留值复现");
        System.out.println("  — 修复 ThreadLocalDemo 小节三的瑕疵");
        System.out.println("========================================\n");

        demonstrateLeakedValue();
        demonstrateCorrectUsage();
        demonstrateCore2Pitfall();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  1. 残留值复现条件：core=1 线程池，保证线程复用");
        System.out.println("     线程复用 = 同一 Thread 对象 = 同一 ThreadLocalMap");
        System.out.println();
        System.out.println("  2. core=2 时任务可能在不同线程执行，null 不等于\"已清理\"");
        System.out.println();
        System.out.println("  3. 最佳实践：ThreadLocal 用完必须在 finally 中 remove()");
        System.out.println("     不 remove 的后果：数据泄露（用户A看到用户B的数据）+ 内存泄漏");
        System.out.println();
        System.out.println("  4. Spring 的做法：Filter/Interceptor 的 afterCompletion 中清理");
        System.out.println("     RequestContextHolder.resetRequestAttributes()");
        System.out.println();
        System.out.println("========================================");
    }
}
