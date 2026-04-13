package com.kungfu.concurrent.d1_thread_basis;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 【Demo】synchronized 原理与锁升级
 *
 * <p>这个Demo演示什么：
 * <ul>
 *   <li>synchronized 的三种使用方式及其锁对象</li>
 *   <li>锁升级过程：无锁 → 偏向锁 → 轻量级锁 → 重量级锁</li>
 *   <li>synchronized 的底层原理（monitorenter/monitorexit）</li>
 *   <li>wait/notify 机制实现生产者消费者模型</li>
 * </ul>
 *
 * <p>为什么重要：
 * <ul>
 *   <li>synchronized 是 Java 最基本的同步机制，理解它是学习并发编程的基石</li>
 *   <li>锁升级是 JVM 对 synchronized 的重要优化，面试高频考点</li>
 *   <li>wait/notify 是线程间通信的基础，生产者消费者是经典并发模型</li>
 * </ul>
 *
 * <p>运行方式：直接运行 main 方法，观察控制台输出
 *
 * @author kungfu
 * @since D8 - 并发编程
 */
public class SynchronizedDemo {

    // ========== 共享资源 ==========
    private int instanceCounter = 0;
    private static int staticCounter = 0;
    private final Object lock = new Object();

    // ========================================
    // 一、三种使用方式
    // ========================================

    /**
     * 演示 synchronized 的三种使用方式
     */
    public void demonstrateThreeWays() throws InterruptedException {
        System.out.println("=== 一、synchronized 三种使用方式 ===\n");

        // --- 方式1：同步实例方法，锁是 this ---
        System.out.println("  【方式1】同步实例方法 —— 锁对象是 this");
        instanceCounter = 0;
        Thread t1a = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                incrementInstance();
            }
        }, "实例方法线程A");
        Thread t1b = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                incrementInstance();
            }
        }, "实例方法线程B");
        t1a.start();
        t1b.start();
        t1a.join();
        t1b.join();
        System.out.println("  同步实例方法 → instanceCounter = " + instanceCounter + " (期望 20000)\n");

        // --- 方式2：同步静态方法，锁是 Class 对象 ---
        System.out.println("  【方式2】同步静态方法 —— 锁对象是 Class 对象");
        staticCounter = 0;
        Thread t2a = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                incrementStatic();
            }
        }, "静态方法线程A");
        Thread t2b = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                incrementStatic();
            }
        }, "静态方法线程B");
        t2a.start();
        t2b.start();
        t2a.join();
        t2b.join();
        System.out.println("  同步静态方法 → staticCounter = " + staticCounter + " (期望 20000)\n");

        // --- 方式3：同步代码块，锁是指定对象 ---
        System.out.println("  【方式3】同步代码块 —— 锁对象是指定的 lock 对象");
        instanceCounter = 0;
        Thread t3a = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                incrementWithBlock();
            }
        }, "代码块线程A");
        Thread t3b = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                incrementWithBlock();
            }
        }, "代码块线程B");
        t3a.start();
        t3b.start();
        t3a.join();
        t3b.join();
        System.out.println("  同步代码块   → instanceCounter = " + instanceCounter + " (期望 20000)\n");

        // --- 汇总表 ---
        System.out.println("  ┌──────────────────┬────────────────────────────┐");
        System.out.println("  │ 使用方式         │ 锁对象                     │");
        System.out.println("  ├──────────────────┼────────────────────────────┤");
        System.out.println("  │ 实例方法         │ this（当前实例）            │");
        System.out.println("  ├──────────────────┼────────────────────────────┤");
        System.out.println("  │ 静态方法         │ Class 对象（类级别锁）     │");
        System.out.println("  ├──────────────────┼────────────────────────────┤");
        System.out.println("  │ 同步代码块       │ 括号中指定的对象           │");
        System.out.println("  └──────────────────┴────────────────────────────┘");
        System.out.println();
    }

    /** 同步实例方法 */
    private synchronized void incrementInstance() {
        instanceCounter++;
    }

    /** 同步静态方法 */
    private static synchronized void incrementStatic() {
        staticCounter++;
    }

    /** 同步代码块 */
    private void incrementWithBlock() {
        synchronized (lock) {
            instanceCounter++;
        }
    }

    // ========================================
    // 二、锁升级过程
    // ========================================

    /**
     * 演示锁升级过程（知识讲解）
     */
    public void demonstrateLockUpgrade() {
        System.out.println("=== 二、锁升级过程 ===\n");

        System.out.println("  锁升级流程图：");
        System.out.println();
        System.out.println("  ┌────────┐    ┌────────┐    ┌──────────┐    ┌──────────┐");
        System.out.println("  │  无锁  │───→│ 偏向锁 │───→│ 轻量级锁 │───→│ 重量级锁 │");
        System.out.println("  └────────┘    └────────┘    └──────────┘    └──────────┘");
        System.out.println("     ↑              ↑              ↑               ↑");
        System.out.println("   无竞争      单线程访问     多线程交替访问    多线程竞争激烈");
        System.out.println();

        System.out.println("  ┌──────────┬─────────────────────┬──────────────────────────────┐");
        System.out.println("  │ 锁状态   │ 触发条件            │ 实现方式                     │");
        System.out.println("  ├──────────┼─────────────────────┼──────────────────────────────┤");
        System.out.println("  │ 无锁     │ 对象刚创建，无同步  │ Mark Word 存储 hashCode 等   │");
        System.out.println("  ├──────────┼─────────────────────┼──────────────────────────────┤");
        System.out.println("  │ 偏向锁   │ 单线程反复获取同    │ Mark Word 记录线程 ID，      │");
        System.out.println("  │          │ 一把锁              │ 后续同一线程无需 CAS         │");
        System.out.println("  ├──────────┼─────────────────────┼──────────────────────────────┤");
        System.out.println("  │ 轻量级锁 │ 多线程交替访问，    │ CAS 将 Mark Word 指向线程    │");
        System.out.println("  │          │ 无实际竞争          │ 栈中的 Lock Record           │");
        System.out.println("  ├──────────┼─────────────────────┼──────────────────────────────┤");
        System.out.println("  │ 重量级锁 │ 多线程同时竞争，    │ 依赖操作系统 Mutex Lock，    │");
        System.out.println("  │          │ CAS 自旋失败        │ 线程阻塞，涉及用户态/核心态  │");
        System.out.println("  └──────────┴─────────────────────┴──────────────────────────────┘");
        System.out.println();

        System.out.println("  ⚠ 注意：锁只能升级，不能降级！（偏向锁在 JDK 15 后默认关闭）");
        System.out.println();
    }

    // ========================================
    // 三、synchronized 底层原理
    // ========================================

    /**
     * 展示 Monitor 底层原理
     */
    public void showMonitorPrinciple() {
        System.out.println("=== 三、synchronized 底层原理 ===\n");

        System.out.println("  1. 字节码层面：");
        System.out.println("     - 同步代码块：编译为 monitorenter + monitorexit 指令");
        System.out.println("     - 同步方法：方法标志位 ACC_SYNCHRONIZED");
        System.out.println();
        System.out.println("     monitorenter  ← 获取锁，计数器 +1");
        System.out.println("       // 临界区代码");
        System.out.println("     monitorexit   ← 释放锁，计数器 -1（正常退出）");
        System.out.println("     monitorexit   ← 释放锁，计数器 -1（异常退出，保证释放）");
        System.out.println();

        System.out.println("  2. Monitor 对象结构：");
        System.out.println();
        System.out.println("     ┌─────────────────────────────────────┐");
        System.out.println("     │           Monitor 对象              │");
        System.out.println("     ├─────────────────────────────────────┤");
        System.out.println("     │  Owner     : 当前持有锁的线程       │");
        System.out.println("     │  EntryList : 阻塞等待锁的线程队列   │");
        System.out.println("     │  WaitSet   : 调用 wait() 的线程集合 │");
        System.out.println("     │  count     : 重入计数器             │");
        System.out.println("     └─────────────────────────────────────┘");
        System.out.println();

        System.out.println("  3. 线程与 Monitor 交互流程：");
        System.out.println();
        System.out.println("     线程A ──monitorenter──→ Owner = 线程A（获取锁成功）");
        System.out.println("     线程B ──monitorenter──→ EntryList（锁被占用，阻塞等待）");
        System.out.println("     线程A ──wait()───────→ WaitSet（释放锁，等待通知）");
        System.out.println("     线程B ──monitorenter──→ Owner = 线程B（从 EntryList 获取锁）");
        System.out.println("     线程B ──notify()─────→ 将线程A 从 WaitSet 移到 EntryList");
        System.out.println("     线程B ──monitorexit──→ Owner = null（释放锁）");
        System.out.println("     线程A ──monitorenter──→ Owner = 线程A（重新获取锁）");
        System.out.println();
    }

    // ========================================
    // 四、wait/notify 机制 — 生产者消费者
    // ========================================

    /**
     * 演示 wait/notify 实现生产者消费者模型
     */
    public void demonstrateProducerConsumer() throws InterruptedException {
        System.out.println("=== 四、wait/notify 机制 — 生产者消费者 ===\n");

        final int BUFFER_SIZE = 5;
        final int TOTAL_ITEMS = 10;
        final Queue<Integer> buffer = new LinkedList<>();
        final Object bufferLock = new Object();

        // --- 生产者线程 ---
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= TOTAL_ITEMS; i++) {
                synchronized (bufferLock) {
                    // 缓冲区满，等待消费者消费
                    while (buffer.size() == BUFFER_SIZE) {
                        try {
                            System.out.println("  [生产者] 缓冲区已满，等待消费...");
                            bufferLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    buffer.offer(i);
                    System.out.println("  [生产者] 生产: " + i + "  | 缓冲区: " + buffer);
                    bufferLock.notifyAll();
                }
                // 模拟生产耗时
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Producer");

        // --- 消费者线程 ---
        Thread consumer = new Thread(() -> {
            for (int i = 0; i < TOTAL_ITEMS; i++) {
                synchronized (bufferLock) {
                    // 缓冲区空，等待生产者生产
                    while (buffer.isEmpty()) {
                        try {
                            System.out.println("  [消费者] 缓冲区为空，等待生产...");
                            bufferLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    int item = buffer.poll();
                    System.out.println("  [消费者] 消费: " + item + "  | 缓冲区: " + buffer);
                    bufferLock.notifyAll();
                }
                // 模拟消费耗时
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println();
        System.out.println("  生产者消费者演示完成！缓冲区剩余: " + buffer);
        System.out.println();
    }

    // ========================================
    // main
    // ========================================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  synchronized 原理与锁升级");
        System.out.println("========================================\n");

        SynchronizedDemo demo = new SynchronizedDemo();

        // 一、三种使用方式
        demo.demonstrateThreeWays();

        // 二、锁升级过程
        demo.demonstrateLockUpgrade();

        // 三、底层原理
        demo.showMonitorPrinciple();

        // 四、生产者消费者
        demo.demonstrateProducerConsumer();

        // ========================================
        // 面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  ┌──────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. synchronized 三种用法和锁对象：                   │");
        System.out.println("  │    实例方法→this, 静态方法→Class, 代码块→指定对象    │");
        System.out.println("  ├──────────────────────────────────────────────────────┤");
        System.out.println("  │ 2. 锁升级：无锁 → 偏向锁 → 轻量级锁 → 重量级锁     │");
        System.out.println("  │    只能升级不能降级                                  │");
        System.out.println("  ├──────────────────────────────────────────────────────┤");
        System.out.println("  │ 3. 底层原理：monitorenter / monitorexit             │");
        System.out.println("  │    Monitor 含 Owner、EntryList、WaitSet             │");
        System.out.println("  ├──────────────────────────────────────────────────────┤");
        System.out.println("  │ 4. wait() 释放锁，sleep() 不释放锁                  │");
        System.out.println("  │    wait 必须在 synchronized 中调用                   │");
        System.out.println("  └──────────────────────────────────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了 synchronized 的原理后，下一步：
 * → ReentrantLock 提供了哪些 synchronized 没有的功能？（见 ReentrantLockDemo.java）
 * → AQS 是如何实现锁的？
 * → 并发工具类如何简化多线程编程？（见 ConcurrentToolsDemo.java）
 */
