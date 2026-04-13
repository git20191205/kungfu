package com.kungfu.concurrent.d1_thread_basis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】volatile 原理与内存可见性
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>volatile 关键字如何保证内存可见性</li>
 *   <li>volatile 不保证原子性的经典陷阱</li>
 *   <li>volatile 在双重检查锁（DCL）单例中防止指令重排序</li>
 *   <li>volatile 与 synchronized 的核心区别</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>volatile 是 Java 并发中最轻量级的同步机制，理解它是掌握 JMM 的基础</li>
 *   <li>面试高频考点：可见性、原子性、指令重排序、happens-before</li>
 *   <li>实际开发中 DCL 单例、状态标志位等场景广泛使用 volatile</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，建议添加 JVM 参数以确保实验效果：
 * <pre>
 *   -server                      # 使用 Server VM（C2 编译器），JIT 优化更激进
 *   -Djava.compiler=server       # IDEA 中等效设置
 * </pre>
 * 如果实验一中「普通变量」的线程也能感知到变化，说明 JIT 还没来得及优化，
 * 加 {@code -server} 后可稳定复现不可见性问题。
 *
 * @author kungfu
 * @since D8 - 并发编程
 */
public class VolatileDemo {

    // ==================== 共享变量 ====================

    /** 普通变量 —— 不保证可见性 */
    private static boolean flag = false;

    /** volatile 变量 —— 保证可见性 */
    private static volatile boolean volatileFlag = false;

    /** volatile 变量 —— 不保证原子性 */
    private static volatile int count = 0;

    // ==================== main ====================

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   volatile 原理与内存可见性 Demo");
        System.out.println("========================================\n");

        // JVM 参数提示
        String vmName = System.getProperty("java.vm.name", "");
        System.out.println("  当前 VM: " + vmName);
        if (!vmName.contains("Server")) {
            System.out.println("  ⚠ 建议添加 JVM 参数 -server 以确保 JIT 优化效果");
            System.out.println("    IDEA: Run → Edit Configurations → VM options: -server");
        }
        System.out.println();

        // 一、内存可见性问题
        demonstrateVisibility();

        // 二、volatile 不保证原子性
        demonstrateNonAtomic();

        // 三、指令重排序 — 双重检查锁单例
        demonstrateDoubleCheckLock();

        // 四、volatile vs synchronized 对比
        showComparisonTable();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. volatile 保证可见性和有序性，但不保证原子性");
        System.out.println("  2. volatile 写操作会插入 StoreStore + StoreLoad 屏障");
        System.out.println("  3. volatile 读操作会插入 LoadLoad + LoadStore 屏障");
        System.out.println("  4. volatile 适用于一写多读的状态标志场景");
        System.out.println("  5. 复合操作（如 i++）需要用 AtomicInteger 或 synchronized");
        System.out.println("  6. DCL 单例必须加 volatile，防止指令重排导致拿到未初始化对象");
        System.out.println("  7. volatile 的 happens-before 规则：");
        System.out.println("     对一个 volatile 变量的写 happens-before 后续对它的读");
        System.out.println("  8. volatile 底层通过 lock 前缀指令实现（x86 架构）：");
        System.out.println("     锁总线/缓存行 → 写回主存 → MESI 协议使其他缓存失效");
        System.out.println();
    }

    // ==================== 一、内存可见性问题 ====================

    /**
     * 演示 volatile 的内存可见性。
     * <p>
     * 不加 volatile 时，子线程可能永远看不到主线程对 flag 的修改（JIT 优化/缓存）；
     * 加了 volatile 后，写操作立即刷回主存，读操作直接从主存读取。
     */
    private static void demonstrateVisibility() throws InterruptedException {
        System.out.println("=== 一、内存可见性问题 ===\n");

        // ---------- 1) 不加 volatile 的情况 ----------
        System.out.println("  【实验 1】不加 volatile 的普通变量");
        System.out.println("  flag 初始值 = false，子线程循环检测 flag...\n");

        flag = false; // 重置

        Thread t1 = new Thread(() -> {
            long loopCount = 0;
            while (!flag) {
                loopCount++;
            }
            System.out.println("  [普通变量] 子线程检测到 flag 变化！循环了 " + loopCount + " 次");
        }, "non-volatile-thread");
        t1.setDaemon(true); // 设为守护线程，防止程序无法退出
        t1.start();

        Thread.sleep(100);
        flag = true;
        System.out.println("  主线程已将 flag 设为 true");

        // 给普通变量的线程一点时间看能不能感知到变化
        t1.join(2000); // 最多等 2 秒

        if (t1.isAlive()) {
            System.out.println("  [普通变量] ⚠ 子线程仍未感知到 flag 变化（JIT 优化导致不可见）");
        }
        System.out.println();

        // ---------- 2) 加 volatile 的情况 ----------
        System.out.println("  【实验 2】加 volatile 的变量");
        System.out.println("  volatileFlag 初始值 = false，子线程循环检测 volatileFlag...\n");

        volatileFlag = false; // 重置

        Thread t2 = new Thread(() -> {
            long loopCount = 0;
            while (!volatileFlag) {
                loopCount++;
            }
            System.out.println("  [volatile 变量] 子线程检测到 volatileFlag 变化！循环了 " + loopCount + " 次");
        }, "volatile-thread");
        t2.start();

        Thread.sleep(100);
        volatileFlag = true;
        System.out.println("  主线程已将 volatileFlag 设为 true");

        t2.join(2000);

        if (t2.isAlive()) {
            System.out.println("  [volatile 变量] 子线程仍未感知到变化（不应出现此情况）");
        } else {
            System.out.println("  [volatile 变量] 子线程正常退出 —— volatile 保证了可见性！");
        }

        System.out.println();
        System.out.println("  ┌──────────────────────────────────────────────────────┐");
        System.out.println("  │ 结论：volatile 修饰的变量，写操作会立即刷回主存，    │");
        System.out.println("  │ 读操作会从主存重新读取，从而保证多线程间的可见性。    │");
        System.out.println("  └──────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、volatile 不保证原子性 ====================

    /**
     * 演示 volatile 不保证原子性。
     * <p>
     * volatile int count++ 实际是 3 步操作：读 → 加 → 写，
     * 多线程并发时会丢失更新，最终结果 < 预期值。
     * 解决方案：使用 AtomicInteger。
     */
    private static void demonstrateNonAtomic() throws InterruptedException {
        System.out.println("=== 二、volatile 不保证原子性 ===\n");

        int threadCount = 20;
        int incrementPerThread = 10000;

        // ---------- 1) volatile int 的 ++ 操作 ----------
        System.out.println("  【实验 1】volatile int count++（" + threadCount + " 线程 x " + incrementPerThread + " 次）");

        count = 0; // 重置
        CountDownLatch latch1 = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    count++; // 非原子操作！
                }
                latch1.countDown();
            }).start();
        }

        latch1.await();
        int expected = threadCount * incrementPerThread;
        System.out.println("  volatile int 结果: " + count + "（期望 " + expected + "）");
        System.out.println("  结论: " + (count < expected
                ? "结果 < " + expected + "，volatile 不保证原子性！丢失了 " + (expected - count) + " 次更新"
                : "本次恰好等于 " + expected + "（概率极低），多运行几次会出现丢失"));
        System.out.println();

        // ---------- 2) AtomicInteger 的 ++ 操作 ----------
        System.out.println("  【实验 2】AtomicInteger（" + threadCount + " 线程 x " + incrementPerThread + " 次）");

        AtomicInteger atomicCount = new AtomicInteger(0);
        CountDownLatch latch2 = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    atomicCount.incrementAndGet(); // CAS 原子操作
                }
                latch2.countDown();
            }).start();
        }

        latch2.await();
        System.out.println("  AtomicInteger 结果: " + atomicCount.get() + "（期望 " + expected + "）");
        System.out.println("  结论: AtomicInteger 基于 CAS 保证原子性，结果一定是 " + expected);

        System.out.println();
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │ count++ 拆解为 3 步：                                    │");
        System.out.println("  │   ① 读取 count 的值（从主存读到工作内存）                │");
        System.out.println("  │   ② 执行 +1 操作                                        │");
        System.out.println("  │   ③ 写回 count 的新值（从工作内存刷回主存）              │");
        System.out.println("  │ volatile 只保证 ①③ 的可见性，不保证 ①②③ 作为整体的原子性 │");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 三、指令重排序 — 双重检查锁单例 ====================

    /**
     * 演示 volatile 在 DCL 单例模式中防止指令重排序的作用。
     */
    private static void demonstrateDoubleCheckLock() {
        System.out.println("=== 三、指令重排序 — 双重检查锁单例 ===\n");

        System.out.println("  双重检查锁（DCL）单例模式代码：");
        System.out.println("  ─────────────────────────────────────────────────");
        System.out.println("  public class Singleton {");
        System.out.println("      // 必须加 volatile！");
        System.out.println("      private static volatile Singleton instance;");
        System.out.println();
        System.out.println("      private Singleton() {}");
        System.out.println();
        System.out.println("      public static Singleton getInstance() {");
        System.out.println("          if (instance == null) {             // 第一次检查（无锁）");
        System.out.println("              synchronized (Singleton.class) {");
        System.out.println("                  if (instance == null) {     // 第二次检查（有锁）");
        System.out.println("                      instance = new Singleton();");
        System.out.println("                  }");
        System.out.println("              }");
        System.out.println("          }");
        System.out.println("          return instance;");
        System.out.println("      }");
        System.out.println("  }");
        System.out.println("  ─────────────────────────────────────────────────");
        System.out.println();

        System.out.println("  new Singleton() 在 JVM 中分为 3 步：");
        System.out.println("  ┌────┬──────────────────────────────────────────────────┐");
        System.out.println("  │ 步骤│ 操作                                             │");
        System.out.println("  ├────┼──────────────────────────────────────────────────┤");
        System.out.println("  │ ①  │ 分配内存空间                                     │");
        System.out.println("  │ ②  │ 初始化对象（调用构造方法）                        │");
        System.out.println("  │ ③  │ 将 instance 引用指向内存地址                      │");
        System.out.println("  └────┴──────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │ 指令重排序风险：JVM 可能将执行顺序优化为 ① → ③ → ②      │");
        System.out.println("  │                                                         │");
        System.out.println("  │ 线程 A 执行到 ③（instance 已非 null，但对象未初始化）    │");
        System.out.println("  │ 线程 B 第一次检查 instance != null，直接返回              │");
        System.out.println("  │ → 线程 B 拿到的是一个未完成初始化的对象！                │");
        System.out.println("  │                                                         │");
        System.out.println("  │ 加 volatile 后：禁止 ② 和 ③ 重排序，保证对象一定初始化   │");
        System.out.println("  │ 完毕后才将引用赋给 instance                              │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
        System.out.println();

        // 验证 DCL 单例
        Singleton s1 = Singleton.getInstance();
        Singleton s2 = Singleton.getInstance();
        System.out.println("  DCL 单例验证: s1 == s2 → " + (s1 == s2) + "（应为 true）");
        System.out.println();
    }

    /**
     * 双重检查锁单例（内部类，用于演示）
     */
    private static class Singleton {
        private static volatile Singleton instance;

        private Singleton() {}

        public static Singleton getInstance() {
            if (instance == null) {
                synchronized (Singleton.class) {
                    if (instance == null) {
                        instance = new Singleton();
                    }
                }
            }
            return instance;
        }
    }

    // ==================== 四、volatile vs synchronized 对比 ====================

    /**
     * 打印 volatile 与 synchronized 的对比表格。
     */
    private static void showComparisonTable() {
        System.out.println("=== 四、volatile vs synchronized 对比 ===\n");

        System.out.println("  ┌──────────┬─────────────────────┬─────────────────────────┐");
        System.out.println("  │ 特性     │ volatile            │ synchronized            │");
        System.out.println("  ├──────────┼─────────────────────┼─────────────────────────┤");
        System.out.println("  │ 原子性   │ 不保证              │ 保证                    │");
        System.out.println("  ├──────────┼─────────────────────┼─────────────────────────┤");
        System.out.println("  │ 可见性   │ 保证                │ 保证                    │");
        System.out.println("  ├──────────┼─────────────────────┼─────────────────────────┤");
        System.out.println("  │ 有序性   │ 保证（禁止重排序）  │ 保证（临界区串行执行）  │");
        System.out.println("  ├──────────┼─────────────────────┼─────────────────────────┤");
        System.out.println("  │ 阻塞     │ 不会阻塞线程        │ 可能阻塞线程            │");
        System.out.println("  ├──────────┼─────────────────────┼─────────────────────────┤");
        System.out.println("  │ 使用场景 │ 一写多读的状态标志  │ 多写、复合操作          │");
        System.out.println("  │          │ DCL 单例模式        │ 临界区代码保护          │");
        System.out.println("  │          │ 轻量级通知机制      │ 需要互斥的场景          │");
        System.out.println("  └──────────┴─────────────────────┴─────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了 volatile 的内存可见性后，下一步要关注：
 * → synchronized 的锁升级过程（见 SynchronizedDemo.java）
 * → Lock 与 AQS 的实现原理（见 ReentrantLockDemo.java）
 * → 并发工具类的使用（见 ConcurrentToolsDemo.java）
 */
