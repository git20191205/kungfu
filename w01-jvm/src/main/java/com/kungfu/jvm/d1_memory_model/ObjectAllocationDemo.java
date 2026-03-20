package com.kungfu.jvm.d1_memory_model;

/**
 * 【Demo】对象分配策略演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 演示 JVM 堆中对象的三大分配策略：
 * <ol>
 *   <li>对象优先在 Eden 区分配</li>
 *   <li>大对象直接进入老年代（避免在 Eden 和 Survivor 之间来回复制）</li>
 *   <li>长期存活的对象晋升到老年代（年龄达到阈值）</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 了解分配策略，才能理解：
 * - 为什么频繁创建大对象会导致 Full GC
 * - 为什么对象池/缓存能减少 GC 压力
 * - 如何通过 JVM 参数调整分配行为
 *
 * <h3>运行方式</h3>
 * 建议使用以下参数运行，方便观察 GC 行为：
 * <pre>
 *   java -Xms40m -Xmx40m -Xmn20m
 *        -XX:+PrintGCDetails -XX:+UseSerialGC
 *        -XX:SurvivorRatio=8
 *        -XX:PretenureSizeThreshold=3145728
 *        -XX:MaxTenuringThreshold=3
 *        com.kungfu.jvm.d1_memory_model.ObjectAllocationDemo
 * </pre>
 * 参数说明：
 * -Xms40m -Xmx40m : 堆大小固定 40MB（避免动态扩展干扰观察）
 * -Xmn20m          : 新生代 20MB（Eden:S0:S1 = 8:1:1 → Eden=16MB, S0=S1=2MB）
 * -XX:+UseSerialGC  : 使用 Serial 收集器，GC 日志最简洁易读
 * -XX:PretenureSizeThreshold=3145728 : 大于 3MB 的对象直接进老年代
 * -XX:MaxTenuringThreshold=3 : 年龄达到 3 即晋升老年代
 *
 * @author kungfu
 * @since D1 - JVM内存模型
 */
public class ObjectAllocationDemo {

    /** 1MB 常量，方便分配指定大小的内存 */
    private static final int _1MB = 1024 * 1024;

    /**
     * 策略一：对象优先在 Eden 区分配
     *
     * 大多数情况下，对象在新生代 Eden 区分配。
     * 当 Eden 区没有足够空间时，触发一次 Minor GC。
     */
    private static void testEdenAllocation() {
        System.out.println("--- 策略一：对象优先在 Eden 区分配 ---");
        System.out.println("分配 4 个 1MB 的对象到 Eden 区...\n");

        // 这些对象优先分配在 Eden 区
        // 如果 Eden 放不下，会触发 Minor GC
        byte[] allocation1 = new byte[1 * _1MB];
        byte[] allocation2 = new byte[1 * _1MB];
        byte[] allocation3 = new byte[1 * _1MB];

        System.out.println("  前 3 个对象已分配在 Eden 区");
        System.out.println("  Eden 区已使用约 3MB");

        // 此时如果 Eden 区空间不足，分配 allocation4 会触发 Minor GC
        // GC 日志中可以看到新生代的空间变化
        byte[] allocation4 = new byte[1 * _1MB];
        System.out.println("  第 4 个对象分配完成");
        System.out.println("  → 观察点：如果配合 -XX:+PrintGCDetails，可以看到 Minor GC 日志\n");
    }

    /**
     * 策略二：大对象直接进入老年代
     *
     * 需要大量连续内存的对象（如长字符串、大数组）被称为「大对象」。
     * -XX:PretenureSizeThreshold 参数可以设置阈值（仅对 Serial 和 ParNew 有效）。
     * 超过阈值的对象直接在老年代分配，避免在新生代来回复制。
     */
    private static void testBigObjectDirectToOldGen() {
        System.out.println("--- 策略二：大对象直接进入老年代 ---");
        System.out.println("分配一个 4MB 的大对象...\n");

        // 如果设置了 -XX:PretenureSizeThreshold=3145728（3MB）
        // 这个 4MB 的对象会直接分配在老年代，跳过 Eden 区
        byte[] bigObject = new byte[4 * _1MB];

        System.out.println("  4MB 大对象已分配");
        System.out.println("  → 如果 PretenureSizeThreshold=3MB，此对象直接进入老年代");
        System.out.println("  → 为什么？避免大对象在 Eden → S0 → S1 之间来回复制，浪费性能\n");

        // 防止被 GC 回收
        System.out.println("  大对象引用: " + bigObject.length + " bytes");
    }

    /**
     * 策略三：长期存活的对象晋升到老年代
     *
     * 对象在 Survivor 区每熬过一次 Minor GC，年龄（Age）+1。
     * 当年龄达到 -XX:MaxTenuringThreshold（默认 15），晋升到老年代。
     *
     * 注意：JVM 还有「动态年龄判定」机制 —
     * 如果 Survivor 区中相同年龄的对象总大小超过 Survivor 空间的一半，
     * 年龄 >= 该年龄的对象直接晋升，不必等到 MaxTenuringThreshold。
     */
    private static void testTenuringThreshold() {
        System.out.println("--- 策略三：长期存活的对象晋升到老年代 ---\n");

        // 模拟「长期存活」的对象
        // 这个对象一直被引用，每次 Minor GC 都存活，年龄不断增加
        byte[] longLivedObject = new byte[_1MB / 4];
        System.out.println("  创建一个 256KB 的长期存活对象");

        // 制造几次 Minor GC，让 longLivedObject 的年龄增长
        for (int i = 1; i <= 4; i++) {
            System.out.println("  第 " + i + " 次分配临时对象，可能触发 Minor GC...");
            // 临时对象，分配后即失去引用，下次 GC 会被回收
            byte[] temp = new byte[4 * _1MB];
            temp = null;
        }

        System.out.println();
        System.out.println("  → longLivedObject 在多次 GC 中存活，年龄不断增长");
        System.out.println("  → 当年龄达到 MaxTenuringThreshold 时，晋升到老年代");
        System.out.println("  → 动态年龄判定：Survivor 区同龄对象总大小 > Survivor/2 时，也会提前晋升");

        // 确保 longLivedObject 不被回收
        System.out.println("  长期对象大小: " + longLivedObject.length + " bytes\n");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  JVM 对象分配策略演示");
        System.out.println("========================================\n");

        // 打印当前 JVM 内存信息，对比观察
        Runtime runtime = Runtime.getRuntime();
        System.out.println("当前 JVM 内存信息：");
        System.out.println("  最大堆内存: " + runtime.maxMemory() / _1MB + " MB");
        System.out.println("  已分配堆内存: " + runtime.totalMemory() / _1MB + " MB");
        System.out.println("  空闲堆内存: " + runtime.freeMemory() / _1MB + " MB");
        System.out.println();

        // 逐一演示三大分配策略
        testEdenAllocation();
        System.out.println();
        testBigObjectDirectToOldGen();
        System.out.println();
        testTenuringThreshold();

        System.out.println("========================================");
        System.out.println("  分配策略速记：");
        System.out.println("  1. 普通对象 → Eden 区（满了触发 Minor GC）");
        System.out.println("  2. 大对象   → 直接进老年代（避免复制开销）");
        System.out.println("  3. 老对象   → 晋升到老年代（年龄达标或动态判定）");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 理解了对象的分配策略后，自然要问：
 * → 对象什么时候会被回收？（D2 - GC 算法与垃圾回收器）
 * → 怎么判断对象是否「还活着」？（可达性分析 vs 引用计数）
 * → Minor GC / Major GC / Full GC 有什么区别？
 */
