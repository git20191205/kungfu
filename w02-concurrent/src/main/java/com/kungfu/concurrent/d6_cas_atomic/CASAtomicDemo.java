package com.kungfu.concurrent.d6_cas_atomic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 【Demo】CAS 与原子类 — 无锁并发的基石
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>CAS（Compare And Swap）的原理与自旋机制</li>
 *   <li>CAS 三大问题：ABA、自旋开销、单变量限制</li>
 *   <li>Atomic 原子类族的使用（AtomicInteger、AtomicReference、FieldUpdater）</li>
 *   <li>LongAdder vs AtomicLong 高并发性能对比</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * CAS 是无锁并发的基础，是 synchronized 锁优化、AQS、并发容器的底层支撑。
 * 理解 CAS 原理和原子类的正确使用，是掌握 Java 并发编程的关键。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可：
 * <pre>
 *   java com.kungfu.concurrent.d6_cas_atomic.CASAtomicDemo
 * </pre>
 *
 * @author kungfu
 * @since D14 - 并发编程
 */
public class CASAtomicDemo {

    // ========== 内部辅助类 ==========

    /**
     * 用于 AtomicReference 演示的用户对象
     */
    static class User {
        final String name;
        final int age;

        User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "User{name='" + name + "', age=" + age + "}";
        }
    }

    /**
     * 用于 AtomicIntegerFieldUpdater 演示的实体类
     * 注意：字段必须是 volatile int，不能是 private
     */
    static class Counter {
        volatile int count;

        Counter(int count) {
            this.count = count;
        }
    }

    // ========== 线程数与每线程操作次数 ==========
    private static final int THREAD_COUNT = 10;
    private static final int OPERATIONS_PER_THREAD = 1_000_000;

    /**
     * 一、CAS 原理 — Compare And Swap
     */
    private static void demonstrateCASPrinciple() {
        System.out.println("=== 一、CAS 原理 — Compare And Swap ===\n");

        // CAS 原理图
        System.out.println("  ┌────────────────────────────────────────────────────┐");
        System.out.println("  │         compareAndSwap(V, Expected, New)          │");
        System.out.println("  │                                                    │");
        System.out.println("  │  V = 内存地址上的当前值                             │");
        System.out.println("  │  Expected = 预期值（线程之前读到的值）              │");
        System.out.println("  │  New = 要更新的新值                                │");
        System.out.println("  │                                                    │");
        System.out.println("  │  if (V == Expected) {                              │");
        System.out.println("  │      V = New;         // 原子更新                  │");
        System.out.println("  │      return true;     // 成功                      │");
        System.out.println("  │  } else {                                          │");
        System.out.println("  │      return false;    // 失败，需自旋重试           │");
        System.out.println("  │  }                                                 │");
        System.out.println("  └────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  底层实现链路：");
        System.out.println("    AtomicInteger.compareAndSet()");
        System.out.println("      → Unsafe.compareAndSwapInt()");
        System.out.println("        → JNI 调用");
        System.out.println("          → CPU cmpxchg 指令（硬件级原子操作）");
        System.out.println();

        // 演示 CAS 自旋概念
        System.out.println("  【演示】CAS 自旋更新：");
        AtomicInteger spinValue = new AtomicInteger(0);
        System.out.println("    初始值: " + spinValue.get());

        // 模拟自旋 CAS
        int oldValue;
        int newValue;
        int spinCount = 0;
        do {
            oldValue = spinValue.get();
            newValue = oldValue + 1;
            spinCount++;
            // 第一次一定成功，这里演示自旋的写法
        } while (!spinValue.compareAndSet(oldValue, newValue));

        System.out.println("    CAS 自旋更新: " + oldValue + " → " + newValue + " (自旋次数: " + spinCount + ")");
        System.out.println("    当前值: " + spinValue.get());
        System.out.println();

        // 演示 CAS 失败场景
        System.out.println("  【演示】CAS 失败场景：");
        AtomicInteger casDemo = new AtomicInteger(100);
        boolean success1 = casDemo.compareAndSet(100, 200);
        System.out.println("    compareAndSet(100, 200) → " + success1 + ", 当前值: " + casDemo.get());

        boolean success2 = casDemo.compareAndSet(100, 300);
        System.out.println("    compareAndSet(100, 300) → " + success2 + ", 当前值: " + casDemo.get()
                + "  (预期值100与实际值200不符，CAS 失败)");
        System.out.println();
    }

    /**
     * 二、CAS 三大问题
     */
    private static void demonstrateCASProblems() {
        System.out.println("=== 二、CAS 三大问题 ===\n");

        // ---------- 问题1：ABA ----------
        System.out.println("  【问题1】ABA 问题\n");
        System.out.println("    场景：线程1读到值 A，被挂起；线程2将 A→B→A；");
        System.out.println("          线程1恢复，CAS 比较 A==A 成功，但数据已被篡改过！\n");

        // 普通 AtomicInteger 无法检测 ABA
        AtomicInteger atomicInt = new AtomicInteger(1);
        System.out.println("    --- 普通 AtomicInteger（无法检测 ABA）---");
        System.out.println("    初始值: " + atomicInt.get());
        atomicInt.compareAndSet(1, 2);  // A → B
        System.out.println("    线程2: 1 → 2 (A→B)");
        atomicInt.compareAndSet(2, 1);  // B → A
        System.out.println("    线程2: 2 → 1 (B→A)");
        boolean abaSuccess = atomicInt.compareAndSet(1, 3);
        System.out.println("    线程1: compareAndSet(1, 3) → " + abaSuccess + " (ABA 未被发现!)\n");

        // AtomicStampedReference 解决 ABA
        System.out.println("    --- AtomicStampedReference（版本号解决 ABA）---");
        AtomicStampedReference<Integer> stampRef = new AtomicStampedReference<>(1, 1);
        int[] stampHolder = new int[1];
        Integer ref = stampRef.get(stampHolder);
        int originalStamp = stampHolder[0];
        System.out.println("    初始值: " + ref + ", 版本号: " + originalStamp);

        // 线程2 执行 A→B→A，版本号递增
        stampRef.compareAndSet(1, 2, 1, 2);  // A→B, stamp 1→2
        System.out.println("    线程2: 1→2, 版本号: 1→2");
        stampRef.compareAndSet(2, 1, 2, 3);  // B→A, stamp 2→3
        System.out.println("    线程2: 2→1, 版本号: 2→3");

        // 线程1 用旧版本号尝试 CAS
        boolean stampSuccess = stampRef.compareAndSet(1, 3, originalStamp, originalStamp + 1);
        System.out.println("    线程1: compareAndSet(1, 3, stamp=" + originalStamp + ") → " + stampSuccess
                + " (ABA 被检测到! 版本号不匹配)");
        System.out.println();

        // ---------- 问题2：自旋开销 ----------
        System.out.println("  【问题2】自旋开销");
        System.out.println("    - CAS 失败后不断重试（自旋），竞争激烈时 CPU 空转");
        System.out.println("    - 解决思路：LongAdder 分段减少竞争（见第四节）");
        System.out.println();

        // ---------- 问题3：单变量限制 ----------
        System.out.println("  【问题3】只能保证单个变量的原子性");
        System.out.println("    - CAS 只能对一个变量做原子操作");
        System.out.println("    - 多变量原子更新：用 AtomicReference 包装成对象");
        System.out.println("    - 或使用锁（synchronized / ReentrantLock）");
        System.out.println();
    }

    /**
     * 三、Atomic 原子类族
     */
    private static void demonstrateAtomicClasses() {
        System.out.println("=== 三、Atomic 原子类族 ===\n");

        // ---------- 3.1 AtomicInteger ----------
        System.out.println("  【3.1】AtomicInteger 基本操作\n");
        AtomicInteger ai = new AtomicInteger(0);
        System.out.println("    初始值: " + ai.get());
        System.out.println("    incrementAndGet(): " + ai.incrementAndGet());       // 1
        System.out.println("    incrementAndGet(): " + ai.incrementAndGet());       // 2
        System.out.println("    getAndAdd(5): " + ai.getAndAdd(5) + " → 当前值: " + ai.get());  // 返回2, 值=7
        System.out.println("    compareAndSet(7, 10): " + ai.compareAndSet(7, 10) + " → 当前值: " + ai.get());
        System.out.println("    compareAndSet(7, 20): " + ai.compareAndSet(7, 20) + " → 当前值: " + ai.get()
                + " (预期值不匹配)");
        System.out.println();

        // ---------- 3.2 AtomicReference ----------
        System.out.println("  【3.2】AtomicReference — 引用类型原子更新\n");
        User user1 = new User("Alice", 25);
        User user2 = new User("Bob", 30);
        AtomicReference<User> atomicUser = new AtomicReference<>(user1);

        System.out.println("    初始值: " + atomicUser.get());
        boolean updated = atomicUser.compareAndSet(user1, user2);
        System.out.println("    compareAndSet(Alice→Bob): " + updated + " → " + atomicUser.get());
        boolean updated2 = atomicUser.compareAndSet(user1, new User("Charlie", 35));
        System.out.println("    compareAndSet(Alice→Charlie): " + updated2 + " → " + atomicUser.get()
                + " (引用已变，CAS 失败)");
        System.out.println();

        // ---------- 3.3 AtomicIntegerFieldUpdater ----------
        System.out.println("  【3.3】AtomicIntegerFieldUpdater — 字段级原子更新\n");
        System.out.println("    适用场景：已有类不想改为 AtomicInteger，但需要原子更新某个字段");
        System.out.println("    要求：字段必须是 volatile int，且不能是 private\n");

        AtomicIntegerFieldUpdater<Counter> updater =
                AtomicIntegerFieldUpdater.newUpdater(Counter.class, "count");
        Counter counter = new Counter(0);
        System.out.println("    初始值: " + counter.count);
        updater.incrementAndGet(counter);
        System.out.println("    incrementAndGet(): " + counter.count);
        updater.addAndGet(counter, 10);
        System.out.println("    addAndGet(10): " + counter.count);
        boolean casResult = updater.compareAndSet(counter, 11, 100);
        System.out.println("    compareAndSet(11, 100): " + casResult + " → " + counter.count);
        System.out.println();
    }

    /**
     * 四、LongAdder vs AtomicLong 性能对比
     */
    private static void compareLongAdderVsAtomicLong() throws InterruptedException {
        System.out.println("=== 四、LongAdder vs AtomicLong 性能对比 ===\n");
        System.out.println("  测试条件: " + THREAD_COUNT + " 个线程，每线程累加 "
                + String.format("%,d", OPERATIONS_PER_THREAD) + " 次\n");

        // ---------- AtomicLong 测试 ----------
        AtomicLong atomicLong = new AtomicLong(0);
        CountDownLatch atomicLatch = new CountDownLatch(THREAD_COUNT);

        long atomicStart = System.nanoTime();
        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(() -> {
                for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                    atomicLong.incrementAndGet();
                }
                atomicLatch.countDown();
            }, "AtomicLong-" + i).start();
        }
        atomicLatch.await();
        long atomicTime = System.nanoTime() - atomicStart;

        // ---------- LongAdder 测试 ----------
        LongAdder longAdder = new LongAdder();
        CountDownLatch adderLatch = new CountDownLatch(THREAD_COUNT);

        long adderStart = System.nanoTime();
        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(() -> {
                for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                    longAdder.increment();
                }
                adderLatch.countDown();
            }, "LongAdder-" + i).start();
        }
        adderLatch.await();
        long adderTime = System.nanoTime() - adderStart;

        // ---------- 结果对比表 ----------
        long expectedTotal = (long) THREAD_COUNT * OPERATIONS_PER_THREAD;
        String atomicTimeStr = String.format("%,d ms", atomicTime / 1_000_000);
        String adderTimeStr = String.format("%,d ms", adderTime / 1_000_000);

        System.out.println("  ┌──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │                  │   AtomicLong     │   LongAdder      │");
        System.out.println("  ├──────────────────┼──────────────────┼──────────────────┤");
        System.out.printf("  │ 最终结果         │ %16d │ %16d │%n", atomicLong.get(), longAdder.sum());
        System.out.printf("  │ 预期值           │ %16d │ %16d │%n", expectedTotal, expectedTotal);
        System.out.println("  ├──────────────────┼──────────────────┼──────────────────┤");
        System.out.printf("  │ 耗时             │ %16s │ %16s │%n", atomicTimeStr, adderTimeStr);
        System.out.println("  └──────────────────┴──────────────────┴──────────────────┘");
        System.out.println();

        // 原理说明
        System.out.println("  【原理】LongAdder 为何更快？");
        System.out.println("    AtomicLong：所有线程 CAS 竞争同一个 value → 大量自旋失败");
        System.out.println("    LongAdder：分段思想，将竞争分散到 Cell 数组");
        System.out.println();
        System.out.println("    ┌──────────┐");
        System.out.println("    │  base    │ ←── 无竞争时直接 CAS 更新");
        System.out.println("    └──────────┘");
        System.out.println("    ┌────────┬────────┬────────┬────────┐");
        System.out.println("    │Cell[0] │Cell[1] │Cell[2] │Cell[3] │ ←── 竞争时线程散列到不同 Cell");
        System.out.println("    └────────┴────────┴────────┴────────┘");
        System.out.println("    sum() = base + Cell[0] + Cell[1] + ... + Cell[n]");
        System.out.println();
        System.out.println("    注意：sum() 非实时精确值（统计过程中可能有并发修改）");
        System.out.println("          适合统计场景（如计数器），不适合需要精确值的场景");
        System.out.println();
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  CAS 与原子类 — 无锁并发的基石");
        System.out.println("========================================\n");

        demonstrateCASPrinciple();
        demonstrateCASProblems();
        demonstrateAtomicClasses();
        compareLongAdderVsAtomicLong();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. CAS = Compare And Swap，CPU 级原子操作（cmpxchg 指令）");
        System.out.println();
        System.out.println("  2. ABA 问题用 AtomicStampedReference 解决（加版本号）");
        System.out.println();
        System.out.println("  3. AtomicInteger 底层是 Unsafe.compareAndSwapInt + 自旋");
        System.out.println();
        System.out.println("  4. LongAdder 高并发下优于 AtomicLong（分段思想：base + Cell[]）");
        System.out.println();
        System.out.println("  5. CAS 是 synchronized 优化和并发容器的基础");
        System.out.println();
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * CAS 是无锁并发的核心，贯穿整个并发体系：
 * → CAS → AtomicInteger（原子类）
 * → CAS → AQS（AbstractQueuedSynchronizer 的核心）
 * → AQS → ReentrantLock（基于 AQS 实现）
 * → CAS + synchronized → 并发容器（ConcurrentHashMap 等）
 *
 * 串联路线：CAS → AtomicInteger → AQS → ReentrantLock → 并发容器
 */
