package com.kungfu.concurrent.d5_concurrent_container;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】ConcurrentHashMap 原理 — 1.7 vs 1.8
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 通过对比实验和原理讲解，深入理解 ConcurrentHashMap 的设计演进：
 * <ul>
 *   <li>HashMap 在多线程下的不安全表现</li>
 *   <li>ConcurrentHashMap 的线程安全保证</li>
 *   <li>JDK 1.7 分段锁（Segment）原理</li>
 *   <li>JDK 1.8 CAS + synchronized 原理</li>
 *   <li>size() 方法的实现演变</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * ConcurrentHashMap 是 Java 并发编程中使用频率最高的线程安全容器，
 * 理解其底层原理（分段锁 → CAS + synchronized 的演进）是面试高频考点，
 * 也是掌握并发设计思想的关键。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可：
 * <pre>
 *   java com.kungfu.concurrent.d5_concurrent_container.ConcurrentHashMapDemo
 * </pre>
 *
 * @author kungfu
 * @since D13 - 并发编程
 */
public class ConcurrentHashMapDemo {

    // ========== 线程数与每线程操作次数 ==========
    private static final int THREAD_COUNT = 10;
    private static final int OPERATIONS_PER_THREAD = 1000;

    /**
     * 一、HashMap 线程不安全演示
     * 多线程并发写入 HashMap，实际 size 往往小于预期值。
     * 注意：在 JDK 7 中，HashMap 并发扩容还可能导致链表成环（死循环）。
     */
    private static void demonstrateHashMapUnsafe() throws InterruptedException {
        System.out.println("=== 一、HashMap 线程不安全演示 ===\n");
        System.out.println("  启动 " + THREAD_COUNT + " 个线程，每个线程 put " + OPERATIONS_PER_THREAD + " 个不同 key");
        System.out.println("  预期 size = " + (THREAD_COUNT * OPERATIONS_PER_THREAD) + "\n");

        // 运行 2 次观察不一致性（加超时保护，防止 HashMap 并发扩容导致死循环）
        for (int round = 1; round <= 2; round++) {
            Map<Integer, Integer> map = new HashMap<>();
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            Thread[] threads = new Thread[THREAD_COUNT];

            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                        map.put(threadId * OPERATIONS_PER_THREAD + i, 1);
                    }
                    latch.countDown();
                }, "HashMap-Thread-" + t);
            }

            for (Thread thread : threads) {
                thread.start();
            }

            boolean finished = latch.await(5, TimeUnit.SECONDS);

            if (finished) {
                int actualSize = map.size();
                boolean safe = (actualSize == THREAD_COUNT * OPERATIONS_PER_THREAD);
                System.out.println("  第 " + round + " 次运行: size = " + actualSize
                        + (safe ? " (恰好正确)" : " (数据丢失!)"));
            } else {
                // 超时 → 大概率触发了 HashMap 并发扩容导致的死循环
                for (Thread thread : threads) {
                    thread.interrupt();
                }
                System.out.println("  第 " + round + " 次运行: ⚠ 超时！线程卡死（HashMap 并发扩容导致链表成环/死循环）");
                System.out.println("    → 这正是 HashMap 不安全的最严重后果：CPU 100%，程序无法继续");
                // 等待中断后的线程退出
                for (Thread thread : threads) {
                    thread.join(1000);
                }
            }
        }

        System.out.println();
        System.out.println("  ⚠ HashMap 并发写入常见问题：");
        System.out.println("    - 数据丢失（put 覆盖）");
        System.out.println("    - JDK 7 链表成环导致 CPU 100%（死循环）");
        System.out.println("    - JDK 8 可能抛出 ConcurrentModificationException");
        System.out.println();
    }

    /**
     * 二、ConcurrentHashMap 线程安全验证
     * 同样的测试，ConcurrentHashMap 的 size 始终精确。
     */
    private static void demonstrateCHMSafe() throws InterruptedException {
        System.out.println("=== 二、ConcurrentHashMap 线程安全验证 ===\n");
        System.out.println("  同样 " + THREAD_COUNT + " 个线程，每个线程 put " + OPERATIONS_PER_THREAD + " 个不同 key");
        System.out.println("  预期 size = " + (THREAD_COUNT * OPERATIONS_PER_THREAD) + "\n");

        for (int round = 1; round <= 3; round++) {
            Map<Integer, Integer> map = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            Thread[] threads = new Thread[THREAD_COUNT];

            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                        map.put(threadId * OPERATIONS_PER_THREAD + i, 1);
                    }
                    latch.countDown();
                }, "CHM-Thread-" + t);
            }

            for (Thread thread : threads) {
                thread.start();
            }
            latch.await();

            int actualSize = map.size();
            System.out.println("  第 " + round + " 次运行: size = " + actualSize + " (始终正确)");
        }
        System.out.println();
    }

    /**
     * 三、ConcurrentHashMap 1.7 原理 — 分段锁（Segment）
     */
    private static void showCHM7Principle() {
        System.out.println("=== 三、ConcurrentHashMap 1.7 原理 ===\n");

        System.out.println("  核心思想：分段锁（Segment Lock）");
        System.out.println("  Segment extends ReentrantLock，每个 Segment 守护一段 HashEntry 数组");
        System.out.println("  不同 Segment 之间可以并发写入，互不干扰");
        System.out.println("  默认 16 个 Segment → 并发度 = 16\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │              ConcurrentHashMap (JDK 1.7)                    │");
        System.out.println("  ├─────────────────────────────────────────────────────────────┤");
        System.out.println("  │  Segment[] 数组 (默认长度 16)                               │");
        System.out.println("  │                                                             │");
        System.out.println("  │  ┌──────────┐  ┌──────────┐       ┌──────────┐              │");
        System.out.println("  │  │Segment[0]│  │Segment[1]│  ...  │Segment[15]│             │");
        System.out.println("  │  │(Lock)    │  │(Lock)    │       │(Lock)     │             │");
        System.out.println("  │  └────┬─────┘  └────┬─────┘       └────┬──────┘             │");
        System.out.println("  │       │             │                   │                    │");
        System.out.println("  │       ▼             ▼                   ▼                    │");
        System.out.println("  │  ┌─────────┐  ┌─────────┐        ┌─────────┐               │");
        System.out.println("  │  │HashEntry│  │HashEntry│        │HashEntry│               │");
        System.out.println("  │  │  数组   │  │  数组   │        │  数组   │               │");
        System.out.println("  │  └──┬──┬───┘  └──┬──┬───┘        └──┬──┬───┘               │");
        System.out.println("  │     │  │         │  │                │  │                    │");
        System.out.println("  │     ▼  ▼         ▼  ▼                ▼  ▼                    │");
        System.out.println("  │   链表 链表    链表 链表           链表 链表                 │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  put 流程（1.7）：");
        System.out.println("    1. 根据 key 的 hash 定位到 Segment");
        System.out.println("    2. 对该 Segment 加 ReentrantLock");
        System.out.println("    3. 在 Segment 内部的 HashEntry 数组中定位桶");
        System.out.println("    4. 头插法插入链表");
        System.out.println("    5. 释放锁");
        System.out.println();
    }

    /**
     * 四、ConcurrentHashMap 1.8 原理 — CAS + synchronized
     */
    private static void showCHM8Principle() {
        System.out.println("=== 四、ConcurrentHashMap 1.8 原理 ===\n");

        System.out.println("  核心变化：废弃 Segment，采用 Node 数组 + 链表/红黑树");
        System.out.println("  锁粒度从 Segment（多个桶）细化到 单个桶的头节点\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │              ConcurrentHashMap (JDK 1.8)                    │");
        System.out.println("  ├─────────────────────────────────────────────────────────────┤");
        System.out.println("  │  Node[] table (数组)                                        │");
        System.out.println("  │                                                             │");
        System.out.println("  │  [0]       [1]       [2]       [3]       ...   [n-1]        │");
        System.out.println("  │   │         │         │         │                │           │");
        System.out.println("  │   ▼         ▼         │         ▼                │           │");
        System.out.println("  │ ┌───┐    ┌───┐       null    ┌───┐             null         │");
        System.out.println("  │ │ A │    │ D │               │ G │                          │");
        System.out.println("  │ └─┬─┘    └─┬─┘               └─┬─┘                          │");
        System.out.println("  │   ▼        ▼                    ▼                            │");
        System.out.println("  │ ┌───┐    ┌───┐              ┌──────┐                        │");
        System.out.println("  │ │ B │    │ E │              │红黑树│                        │");
        System.out.println("  │ └─┬─┘    └───┘              │(>=8) │                        │");
        System.out.println("  │   ▼                         └──────┘                        │");
        System.out.println("  │ ┌───┐                                                       │");
        System.out.println("  │ │ C │  ← 链表                                              │");
        System.out.println("  │ └───┘                                                       │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  put 流程（1.8）：");
        System.out.println("    1. 计算 key 的 hash，定位到数组下标");
        System.out.println("    2. 如果桶为空 → CAS 写入新 Node（无锁）");
        System.out.println("    3. 如果正在扩容（hash == MOVED）→ 协助扩容");
        System.out.println("    4. 否则 → synchronized 锁住桶的头节点，遍历链表/红黑树插入");
        System.out.println("    5. 链表长度 >= 8 且数组长度 >= 64 → 转红黑树");
        System.out.println("    6. 链表长度 >= 8 但数组长度 < 64 → 优先扩容");
        System.out.println();

        // 1.7 vs 1.8 对比表
        System.out.println("  ┌──────────────┬───────────────────────┬───────────────────────────┐");
        System.out.println("  │     特性     │       JDK 1.7         │         JDK 1.8           │");
        System.out.println("  ├──────────────┼───────────────────────┼───────────────────────────┤");
        System.out.println("  │ 数据结构     │ Segment[] + HashEntry │ Node[] + 链表/红黑树      │");
        System.out.println("  │              │ + 链表                │                           │");
        System.out.println("  ├──────────────┼───────────────────────┼───────────────────────────┤");
        System.out.println("  │ 锁粒度       │ Segment（多个桶）     │ 桶头节点（单个桶）        │");
        System.out.println("  ├──────────────┼───────────────────────┼───────────────────────────┤");
        System.out.println("  │ 并发度       │ 默认16（Segment数）   │ 数组长度（更细粒度）      │");
        System.out.println("  ├──────────────┼───────────────────────┼───────────────────────────┤");
        System.out.println("  │ 查询复杂度   │ O(n) 链表遍历         │ O(log n) 红黑树           │");
        System.out.println("  ├──────────────┼───────────────────────┼───────────────────────────┤");
        System.out.println("  │ hash冲突     │ 链表（头插法）        │ 链表(尾插法)→红黑树       │");
        System.out.println("  ├──────────────┼───────────────────────┼───────────────────────────┤");
        System.out.println("  │ 锁实现       │ ReentrantLock         │ CAS + synchronized        │");
        System.out.println("  └──────────────┴───────────────────────┴───────────────────────────┘");
        System.out.println();
    }

    /**
     * 五、size() 的实现演变
     */
    private static void showSizeEvolution() {
        System.out.println("=== 五、size() 的实现演变 ===\n");

        System.out.println("  【JDK 1.7 的 size() 实现】");
        System.out.println("    1. 先不加锁，连续尝试 2 次统计所有 Segment 的 count 之和");
        System.out.println("    2. 每次统计前后对比 modCount，若一致说明期间无修改，返回结果");
        System.out.println("    3. 若 2 次尝试都不一致 → 对所有 Segment 加锁后再统计");
        System.out.println("    4. 这种 \"乐观 → 悲观\" 的策略避免了不必要的加锁开销");
        System.out.println();

        System.out.println("  【JDK 1.8 的 size() 实现】");
        System.out.println("    采用 baseCount + CounterCell[] 的方式（类似 LongAdder）");
        System.out.println();
        System.out.println("    ┌──────────────────────────────────────────────────┐");
        System.out.println("    │  addCount() 计数流程                             │");
        System.out.println("    │                                                  │");
        System.out.println("    │  CAS 更新 baseCount                              │");
        System.out.println("    │       │                                          │");
        System.out.println("    │       ├── 成功 → 结束                            │");
        System.out.println("    │       │                                          │");
        System.out.println("    │       └── 失败（竞争激烈）                       │");
        System.out.println("    │              │                                   │");
        System.out.println("    │              ▼                                   │");
        System.out.println("    │         CAS 更新 CounterCell[threadHash]         │");
        System.out.println("    │         (分散竞争到不同 Cell)                    │");
        System.out.println("    └──────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("    sumCount() = baseCount + sum(CounterCell[])");
        System.out.println("    最终 size = (int) sumCount()");
        System.out.println();

        // 实际验证
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put("key-" + i, i);
        }
        System.out.println("  实际验证: 放入 100 个元素, size() = " + map.size());
        System.out.println("  mappingCount() = " + map.mappingCount() + " (推荐使用，返回 long)");
        System.out.println();
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  ConcurrentHashMap 原理 — 1.7 vs 1.8");
        System.out.println("========================================\n");

        demonstrateHashMapUnsafe();
        demonstrateCHMSafe();
        showCHM7Principle();
        showCHM8Principle();
        showSizeEvolution();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. HashMap 线程不安全的原因：");
        System.out.println("     - 并发 put 导致数据丢失（覆盖写入）");
        System.out.println("     - JDK 7 并发扩容导致链表成环 → 死循环");
        System.out.println("     - JDK 8 改为尾插法解决了成环问题，但仍不安全");
        System.out.println();
        System.out.println("  2. CHM 1.7 — Segment 分段锁：");
        System.out.println("     - Segment extends ReentrantLock");
        System.out.println("     - 默认 16 个 Segment，并发度 = 16");
        System.out.println("     - 不同 Segment 之间的操作完全并行");
        System.out.println();
        System.out.println("  3. CHM 1.8 — CAS + synchronized：");
        System.out.println("     - 废弃 Segment，锁粒度细化到桶头节点");
        System.out.println("     - 空桶用 CAS 无锁写入，非空桶用 synchronized");
        System.out.println("     - 链表长度 >= 8 且数组 >= 64 → 红黑树");
        System.out.println();
        System.out.println("  4. size() 实现差异：");
        System.out.println("     - 1.7: 先乐观不加锁统计 2 次，不一致则悲观全锁");
        System.out.println("     - 1.8: baseCount + CounterCell[]（类似 LongAdder 思路）");
        System.out.println();
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * ConcurrentHashMap 是并发编程中最重要的容器：
 * → 它综合运用了 CAS、synchronized、volatile 等并发原语
 * → 理解它的演进有助于掌握并发设计的思路
 * → 至此 W02 并发编程核心模块完成，下一步进入 W03 Spring 框架
 */
