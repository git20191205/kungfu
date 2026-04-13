package com.kungfu.concurrent.d2_lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 【Demo】ReentrantLock 与 AQS 原理
 *
 * <p>这个Demo演示什么：
 *   1. ReentrantLock 与 synchronized 的对比
 *   2. 公平锁与非公平锁的行为差异
 *   3. 可中断锁的使用方式
 *   4. Condition 条件变量的精确唤醒机制
 *   5. AQS（AbstractQueuedSynchronizer）的核心原理
 *
 * <p>为什么重要：
 *   - ReentrantLock 是 JUC 中最基础的显式锁，理解它是掌握并发编程的关键
 *   - AQS 是 JUC 众多并发工具的底层框架，理解其原理可以举一反三
 *   - 面试高频考点，工作中也经常使用
 *
 * <p>运行方式：
 *   直接运行 main 方法，观察控制台输出
 *
 * @author kungfu
 * @since D9 - 并发编程
 */
public class ReentrantLockDemo {

    /** 精确唤醒中，控制当前该哪个线程执行的标志：1=A, 2=B, 3=C */
    private static int flag = 1;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("   ReentrantLock 与 AQS 原理 Demo");
        System.out.println("========================================\n");

        // === 一、ReentrantLock vs synchronized 对比 ===
        showComparisonTable();

        // === 二、公平锁 vs 非公平锁 ===
        demonstrateFairLock();

        // === 二补充、非公平锁乱序复现 ===
        demonstrateUnfairLockReorder();

        // === 三、可中断锁 ===
        demonstrateInterruptibleLock();

        // === 四、Condition 条件变量 — 精确唤醒 ===
        demonstrateCondition();

        // === 五、AQS 原理 ===
        showAQSPrinciple();

        // 面试速记
        showInterviewNotes();
    }

    // ========================================
    // === 一、ReentrantLock vs synchronized 对比 ===
    // ========================================

    /**
     * 打印 ReentrantLock 与 synchronized 的对比表格
     */
    public static void showComparisonTable() {
        System.out.println("=== 一、ReentrantLock vs synchronized 对比 ===\n");

        System.out.println("  ┌──────────┬────────────────┬────────────────────┐");
        System.out.println("  │   特性   │  synchronized  │   ReentrantLock    │");
        System.out.println("  ├──────────┼────────────────┼────────────────────┤");
        System.out.println("  │ 实现方式 │ JVM 内置关键字 │ Java API (AQS)     │");
        System.out.println("  ├──────────┼────────────────┼────────────────────┤");
        System.out.println("  │ 锁获取   │ 自动获取       │ 手动 lock/unlock   │");
        System.out.println("  ├──────────┼────────────────┼────────────────────┤");
        System.out.println("  │ 公平性   │ 非公平         │ 可选公平/非公平    │");
        System.out.println("  ├──────────┼────────────────┼────────────────────┤");
        System.out.println("  │ 条件变量 │ 单一 wait/notify│ 多个 Condition     │");
        System.out.println("  ├──────────┼────────────────┼────────────────────┤");
        System.out.println("  │ 可中断   │ 不可中断       │ lockInterruptibly  │");
        System.out.println("  ├──────────┼────────────────┼────────────────────┤");
        System.out.println("  │ 自动释放 │ 是（退出同步块）│ 否（需 finally）   │");
        System.out.println("  └──────────┴────────────────┴────────────────────┘");

        System.out.println();
    }

    // ========================================
    // === 二、公平锁 vs 非公平锁 ===
    // ========================================

    /**
     * 演示公平锁与非公平锁的行为差异。
     * 公平锁按照线程请求锁的顺序（FIFO）来分配锁；
     * 非公平锁允许插队，吞吐量更高但可能导致饥饿。
     */
    public static void demonstrateFairLock() throws InterruptedException {
        System.out.println("=== 二、公平锁 vs 非公平锁 ===\n");

        // --- 公平锁演示 ---
        System.out.println("  【公平锁 ReentrantLock(true)】— 按 FIFO 顺序获取锁：");
        ReentrantLock fairLock = new ReentrantLock(true);
        Thread[] fairThreads = new Thread[5];

        for (int i = 0; i < fairThreads.length; i++) {
            fairThreads[i] = new Thread(() -> {
                fairLock.lock();
                try {
                    System.out.println("    公平锁 - " + Thread.currentThread().getName() + " 获取到锁");
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fairLock.unlock();
                }
            }, "Fair-Thread-" + i);
        }

        // 先让主线程持有锁，确保所有子线程都在等待队列中排队
        fairLock.lock();
        for (Thread t : fairThreads) {
            t.start();
            Thread.sleep(20); // 让线程按顺序进入等待队列
        }
        fairLock.unlock();

        for (Thread t : fairThreads) {
            t.join();
        }

        System.out.println();

        // --- 非公平锁演示 ---
        System.out.println("  【非公平锁 ReentrantLock(false)】— 允许插队，顺序不保证：");
        ReentrantLock unfairLock = new ReentrantLock(false);
        Thread[] unfairThreads = new Thread[5];

        for (int i = 0; i < unfairThreads.length; i++) {
            unfairThreads[i] = new Thread(() -> {
                unfairLock.lock();
                try {
                    System.out.println("    非公平锁 - " + Thread.currentThread().getName() + " 获取到锁");
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    unfairLock.unlock();
                }
            }, "Unfair-Thread-" + i);
        }

        unfairLock.lock();
        for (Thread t : unfairThreads) {
            t.start();
            Thread.sleep(20);
        }
        unfairLock.unlock();

        for (Thread t : unfairThreads) {
            t.join();
        }

        System.out.println();
    }

    // ========================================
    // === 二补充、非公平锁乱序复现 ===
    // ========================================

    /**
     * 稳定复现非公平锁的乱序效果。
     * 设计思路：10 个线程各获取锁 3 次，对比公平锁和非公平锁的获取顺序。
     * 非公平锁下，刚释放锁的线程立刻再次 CAS 获取，大概率"插队"成功，
     * 导致获取顺序与线程到达顺序不一致。
     */
    public static void demonstrateUnfairLockReorder() throws InterruptedException {
        System.out.println("=== 二补充、非公平锁乱序复现（10 线程 × 3 轮）===\n");

        final int threadCount = 10;
        final int rounds = 3;

        // --- 公平锁 ---
        List<String> fairOrder = Collections.synchronizedList(new ArrayList<>());
        ReentrantLock fairLock = new ReentrantLock(true);
        CountDownLatch fairReady = new CountDownLatch(threadCount);
        CountDownLatch fairStart = new CountDownLatch(1);

        Thread[] fairThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            fairThreads[i] = new Thread(() -> {
                fairReady.countDown();
                try {
                    fairStart.await(); // 所有线程同时起跑
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int r = 0; r < rounds; r++) {
                    fairLock.lock();
                    try {
                        fairOrder.add(Thread.currentThread().getName());
                    } finally {
                        fairLock.unlock();
                    }
                }
            }, "F-" + i);
            fairThreads[i].start();
        }

        fairReady.await();   // 等所有线程就位
        fairStart.countDown(); // 同时放行
        for (Thread t : fairThreads) t.join();

        // --- 非公平锁 ---
        List<String> unfairOrder = Collections.synchronizedList(new ArrayList<>());
        ReentrantLock unfairLock = new ReentrantLock(false);
        CountDownLatch unfairReady = new CountDownLatch(threadCount);
        CountDownLatch unfairStart = new CountDownLatch(1);

        Thread[] unfairThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            unfairThreads[i] = new Thread(() -> {
                unfairReady.countDown();
                try {
                    unfairStart.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int r = 0; r < rounds; r++) {
                    unfairLock.lock();
                    try {
                        unfairOrder.add(Thread.currentThread().getName());
                    } finally {
                        unfairLock.unlock();
                    }
                }
            }, "U-" + i);
            unfairThreads[i].start();
        }

        unfairReady.await();
        unfairStart.countDown();
        for (Thread t : unfairThreads) t.join();

        // --- 输出对比 ---
        System.out.println("  【公平锁获取顺序】");
        System.out.println("    " + String.join(", ", fairOrder));

        // 检查公平锁是否严格轮转（每个线程依次出现）
        boolean fairIsOrdered = checkRoundRobin(fairOrder, threadCount);

        System.out.println();
        System.out.println("  【非公平锁获取顺序】");
        System.out.println("    " + String.join(", ", unfairOrder));

        // 检查非公平锁是否出现连续重复（同一线程连续获取 = 插队）
        int consecutiveCount = countConsecutive(unfairOrder);

        System.out.println();
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │ 公平锁：" + (fairIsOrdered ? "✓ 严格轮转（FIFO）" : "近似有序（存在微小偏差）")
                + padRight("", 39 - (fairIsOrdered ? 17 : 21)) + "│");
        System.out.println("  │ 非公平锁：" + (consecutiveCount > 0
                ? "✓ 出现 " + consecutiveCount + " 次插队（同一线程连续获取锁）"
                : "✗ 本次恰好有序，请多运行几次")
                + padRight("", consecutiveCount > 0
                ? 44 - (19 + String.valueOf(consecutiveCount).length())
                : 44 - 22) + "│");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    /** 检查是否严格轮转：F-0, F-1, ..., F-9, F-0, F-1, ... */
    private static boolean checkRoundRobin(List<String> order, int threadCount) {
        for (int i = 0; i < order.size(); i++) {
            String expected = order.get(0).charAt(0) + "-" + (i % threadCount);
            if (!order.get(i).equals(expected)) return false;
        }
        return true;
    }

    /** 统计连续重复次数（同一线程连续获取锁 = 插队现象） */
    private static int countConsecutive(List<String> order) {
        int count = 0;
        for (int i = 1; i < order.size(); i++) {
            if (order.get(i).equals(order.get(i - 1))) count++;
        }
        return count;
    }

    /** 右侧填充空格 */
    private static String padRight(String s, int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    // ========================================
    // === 三、可中断锁 ===
    // ========================================

    /**
     * 演示 lockInterruptibly() 的可中断特性。
     * 当线程在等待锁的过程中被中断，会抛出 InterruptedException，
     * 而不是像 lock() 那样一直阻塞。
     */
    public static void demonstrateInterruptibleLock() throws InterruptedException {
        System.out.println("=== 三、可中断锁 ===\n");

        ReentrantLock lock = new ReentrantLock();

        // Thread A: 持有锁较长时间
        Thread threadA = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  Thread-A: 获取到锁，将持有 3 秒...");
                Thread.sleep(3000);
                System.out.println("  Thread-A: 释放锁");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "Thread-A");

        // Thread B: 尝试以可中断方式获取锁
        Thread threadB = new Thread(() -> {
            System.out.println("  Thread-B: 尝试以 lockInterruptibly() 获取锁...");
            try {
                lock.lockInterruptibly();
                try {
                    System.out.println("  Thread-B: 获取到锁");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("  Thread-B: 等待锁时被中断！捕获 InterruptedException");
                System.out.println("  Thread-B: 可以执行清理逻辑或退出，而不会一直阻塞");
            }
        }, "Thread-B");

        threadA.start();
        Thread.sleep(100); // 确保 A 先获取锁

        threadB.start();
        Thread.sleep(500); // 让 B 进入等待状态

        // 主线程中断 Thread B
        System.out.println("  Main: 中断 Thread-B");
        threadB.interrupt();

        threadB.join();
        threadA.join();

        System.out.println();
    }

    // ========================================
    // === 四、Condition 条件变量 — 精确唤醒 ===
    // ========================================

    /**
     * 演示 Condition 实现精确唤醒。
     * 使用 3 个 Condition 控制 A→B→C→A 的执行顺序：
     *   Thread-A 执行后 signal conditionB → Thread-B 执行后 signal conditionC
     *   → Thread-C 执行后 signal conditionA → Thread-A 再次执行
     */
    public static void demonstrateCondition() throws InterruptedException {
        System.out.println("=== 四、Condition 条件变量 — 精确唤醒 ===\n");
        System.out.println("  精确唤醒链：A → B → C → A\n");

        ReentrantLock lock = new ReentrantLock();
        Condition conditionA = lock.newCondition();
        Condition conditionB = lock.newCondition();
        Condition conditionC = lock.newCondition();

        // 重置 flag
        flag = 1;

        // Thread A: 当 flag==1 时执行，执行后 signal conditionB
        Thread threadA = new Thread(() -> {
            lock.lock();
            try {
                // 第一轮：等待 flag==1，执行后唤醒 B
                while (flag != 1) {
                    conditionA.await();
                }
                System.out.println("  Thread-A: 第 1 轮执行（flag=1），唤醒 B");
                flag = 2;
                conditionB.signal();

                // 第二轮：等待被 C 唤醒
                while (flag != 1) {
                    conditionA.await();
                }
                System.out.println("  Thread-A: 第 2 轮执行（被 C 唤醒），精确唤醒验证成功！");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "Thread-A");

        // Thread B: 当 flag==2 时执行，执行后 signal conditionC
        Thread threadB = new Thread(() -> {
            lock.lock();
            try {
                while (flag != 2) {
                    conditionB.await();
                }
                System.out.println("  Thread-B: 执行（被 A 唤醒，flag=2），唤醒 C");
                flag = 3;
                conditionC.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "Thread-B");

        // Thread C: 当 flag==3 时执行，执行后 signal conditionA
        Thread threadC = new Thread(() -> {
            lock.lock();
            try {
                while (flag != 3) {
                    conditionC.await();
                }
                System.out.println("  Thread-C: 执行（被 B 唤醒，flag=3），唤醒 A");
                flag = 1;
                conditionA.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "Thread-C");

        threadA.start();
        threadB.start();
        threadC.start();

        threadA.join();
        threadB.join();
        threadC.join();

        System.out.println("\n  执行顺序：A(第1轮) → B → C → A(第2轮)  ✓ 精确唤醒成功");
        System.out.println();
    }

    // ========================================
    // === 五、AQS 原理 ===
    // ========================================

    /**
     * 打印 AQS 核心原理说明和 CLH 队列结构图
     */
    public static void showAQSPrinciple() {
        System.out.println("=== 五、AQS 原理 ===\n");

        System.out.println("  AQS（AbstractQueuedSynchronizer）是 JUC 锁和同步工具的基础框架。\n");

        // --- state 变量 ---
        System.out.println("  【state 变量】");
        System.out.println("  ┌────────────────────────────────────────────────────────┐");
        System.out.println("  │ volatile int state                                     │");
        System.out.println("  │                                                        │");
        System.out.println("  │ - ReentrantLock: 0=未锁定, >=1=锁定(值表示重入次数)    │");
        System.out.println("  │ - Semaphore:     state 表示可用许可数                   │");
        System.out.println("  │ - CountDownLatch: state 表示剩余计数                    │");
        System.out.println("  └────────────────────────────────────────────────────────┘");
        System.out.println();

        // --- CLH 队列结构 ---
        System.out.println("  【CLH 队列结构（双向链表）】\n");
        System.out.println("         ┌──────────┐    ┌──────────┐    ┌──────────┐");
        System.out.println("  head → │  Node    │ ←→ │  Node    │ ←→ │  Node    │ ← tail");
        System.out.println("         │ (哨兵)   │    │ Thread-1 │    │ Thread-2 │");
        System.out.println("         │ status=0 │    │ SIGNAL   │    │ SIGNAL   │");
        System.out.println("         └──────────┘    └──────────┘    └──────────┘");
        System.out.println();
        System.out.println("  - head 指向哨兵节点（持有锁的线程或虚拟节点）");
        System.out.println("  - tail 指向最后一个等待节点");
        System.out.println("  - 每个 Node 通过 prev/next 双向链接");
        System.out.println("  - waitStatus: SIGNAL(-1) 表示后继节点需要被唤醒");
        System.out.println();

        // --- acquire 流程 ---
        System.out.println("  【acquire（加锁）流程】");
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. tryAcquire(arg) 尝试 CAS 修改 state             │");
        System.out.println("  │    ├─ 成功 → 设置 exclusiveOwnerThread，返回       │");
        System.out.println("  │    └─ 失败 → 进入步骤 2                             │");
        System.out.println("  │ 2. addWaiter() 将当前线程封装为 Node 加入 CLH 队列  │");
        System.out.println("  │ 3. acquireQueued() 自旋：                           │");
        System.out.println("  │    ├─ 前驱是 head → 再次 tryAcquire()               │");
        System.out.println("  │    │  ├─ 成功 → 设置自己为 head，返回               │");
        System.out.println("  │    │  └─ 失败 → park 挂起                           │");
        System.out.println("  │    └─ 前驱非 head → park 挂起                       │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();

        // --- release 流程 ---
        System.out.println("  【release（解锁）流程】");
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. tryRelease(arg) 修改 state                       │");
        System.out.println("  │    ├─ state=0 → 清除 exclusiveOwnerThread           │");
        System.out.println("  │    └─ state>0 → 仍持有锁（重入未完全释放）          │");
        System.out.println("  │ 2. 若 state=0，unpark 唤醒 head 的后继节点          │");
        System.out.println("  │ 3. 被唤醒的线程从 acquireQueued() 中继续自旋        │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ========================================
    // 面试速记
    // ========================================

    /**
     * 打印面试高频考点速记
     */
    public static void showInterviewNotes() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  1. ReentrantLock vs synchronized：");
        System.out.println("     - 都是可重入锁，ReentrantLock 功能更丰富");
        System.out.println("     - ReentrantLock 支持公平锁、可中断、多条件变量");
        System.out.println("     - synchronized 由 JVM 优化（偏向锁→轻量级锁→重量级锁）\n");

        System.out.println("  2. 公平锁 vs 非公平锁：");
        System.out.println("     - 公平锁：FIFO 顺序，不会饥饿，吞吐量较低");
        System.out.println("     - 非公平锁（默认）：允许插队，吞吐量高，可能饥饿\n");

        System.out.println("  3. AQS 核心三要素：");
        System.out.println("     - state（volatile int）：同步状态");
        System.out.println("     - CLH 队列（双向链表）：等待线程排队");
        System.out.println("     - CAS + park/unpark：线程安全的阻塞与唤醒\n");

        System.out.println("  4. Condition 精确唤醒：");
        System.out.println("     - 一个 Lock 可创建多个 Condition");
        System.out.println("     - await/signal 比 wait/notify 更灵活精确");
        System.out.println("     - 典型场景：生产者-消费者、顺序执行控制\n");

        System.out.println("  5. ReentrantLock 使用注意：");
        System.out.println("     - 必须在 finally 中 unlock()，防止死锁");
        System.out.println("     - lock() 不要写在 try 块内（避免未获取锁却执行 unlock）");
        System.out.println("     - 推荐用法：lock.lock(); try { ... } finally { lock.unlock(); }\n");

        System.out.println("========================================\n");
    }
}

/*
 * 【知识串联】
 * 理解了 Lock 和 AQS 后，下一步：
 * → AQS 的共享模式如何支持并发工具类？（见 ConcurrentToolsDemo.java）
 * → 线程池如何管理线程生命周期？（见 ThreadPoolDemo.java）
 * → 并发容器的锁策略有何不同？（见 ConcurrentHashMapDemo.java）
 */
