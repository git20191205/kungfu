package com.kungfu.jvm.d2_gc;

/**
 * 【Demo】SafePoint（安全点）演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * GC 发生时需要 Stop-The-World（STW），但线程不能在任意位置停下来，
 * 必须跑到「安全点」才能暂停。本 Demo 演示安全点对 GC 停顿的影响。
 *
 * <h3>什么是 SafePoint？</h3>
 * 安全点是代码中的特定位置，在这些位置上 JVM 可以安全地暂停线程做 GC：
 * <ul>
 *   <li>方法调用（call 指令）</li>
 *   <li>循环末尾的回跳（backedge）</li>
 *   <li>异常跳转</li>
 *   <li>方法返回前</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * - 可数循环（int i, 固定次数）可能不会在循环内放置安全点
 * - 这意味着一个长时间的 int 循环可能让 GC 等很久 → STW 时间异常长
 * - 实际案例：线上服务 GC 停顿异常，排查发现是大量 int 循环导致
 *
 * <h3>运行方式</h3>
 * <pre>
 *   java -XX:+PrintGCApplicationStoppedTime
 *        com.kungfu.jvm.d2_gc.SafePointDemo
 * </pre>
 *
 * @author kungfu
 * @since D2 - GC算法与垃圾回收器
 */
public class SafePointDemo {

    // 模拟耗时计算（可数循环 — int 类型）
    // JIT 编译后，可数循环内部可能不放安全点
    private static void countableLoop() {
        long sum = 0;
        // int 循环：JIT 可能认为这是「可数循环」，不在循环内设安全点
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            sum += i;
        }
        System.out.println("  [int 循环] 完成，sum = " + sum);
    }

    // 同样的循环，但用 long 类型
    // long 循环不被视为「可数循环」，会在循环内设安全点
    private static void uncountableLoop() {
        long sum = 0;
        // long 循环：不是「可数循环」，循环内有安全点
        for (long i = 0; i < Integer.MAX_VALUE; i++) {
            sum += i;
        }
        System.out.println("  [long 循环] 完成，sum = " + sum);
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  SafePoint（安全点）演示");
        System.out.println("========================================\n");

        System.out.println("  SafePoint 是什么？");
        System.out.println("  → GC 需要 STW，但线程不能在任意位置暂停");
        System.out.println("  → 必须跑到「安全点」才能停下来配合 GC");
        System.out.println();

        System.out.println("  安全点的位置：");
        System.out.println("  1. 方法调用处");
        System.out.println("  2. 循环末尾的回跳");
        System.out.println("  3. 异常跳转处");
        System.out.println("  4. 方法返回前");
        System.out.println();

        System.out.println("  ⚠ 可数循环的坑：");
        System.out.println("  → for (int i=0; i<n; i++) — JIT 认为是可数循环");
        System.out.println("  → 可能不在循环体内放安全点");
        System.out.println("  → 如果循环时间很长，GC 要等这个线程跑完才能 STW");
        System.out.println("  → 改用 long 或拆分循环可以避免这个问题");
        System.out.println();

        // 演示两种循环
        System.out.println("--- 运行两种循环对比（观察耗时）---\n");

        long start1 = System.currentTimeMillis();
        uncountableLoop();
        long time1 = System.currentTimeMillis() - start1;
        System.out.println("  耗时: " + time1 + " ms\n");

        long start2 = System.currentTimeMillis();
        countableLoop();
        long time2 = System.currentTimeMillis() - start2;
        System.out.println("  耗时: " + time2 + " ms\n");

        System.out.println("========================================");
        System.out.println("  SafePoint 速记：");
        System.out.println("  1. GC STW 前，所有线程必须到达安全点");
        System.out.println("  2. 两种方式让线程到达安全点：");
        System.out.println("     抢先式中断 — 先停所有线程，不在安全点的恢复跑到安全点（几乎不用）");
        System.out.println("     主动式中断 — 设标志位，线程轮询到标志时自己停（主流方式）");
        System.out.println("  3. int 可数循环可能没有安全点 → 线上排查 GC 停顿异常的一个方向");
        System.out.println("  4. SafeRegion（安全区域）：线程 sleep/blocked 时在安全区域中，");
        System.out.println("     不需要跑到安全点，GC 可以直接忽略这些线程");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * SafePoint 是 GC 的底层机制，理解后可以解释：
 * → 为什么 GC 停顿时间有时比预期长？（某个线程迟迟到不了安全点）
 * → 为什么 Thread.sleep(0) 有时能改善 GC 表现？（它是一个安全点）
 *
 * D2 GC 全部知识点到此结束，完整目录：
 *   1. GCAlgorithmDemo          — 三种 GC 算法
 *   2. GarbageCollectorDemo     — 四大垃圾回收器
 *   3. GCRootsDemo              — GC Roots 可达性分析
 *   4. ReferenceTypeDemo        — 四种引用类型
 *   5. ObjectFinalizationDemo   — 对象死亡过程
 *   6. SafePointDemo            — 安全点机制
 */