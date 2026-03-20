package com.kungfu.jvm.d2_gc;

/**
 * 【Demo】垃圾回收器实战对比
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 通过实际触发 GC，对比不同垃圾回收器的行为差异。
 * 切换 JVM 参数即可观察不同回收器的 GC 日志。
 *
 * <h3>为什么重要？</h3>
 * 面试必考，生产调优必用。选错回收器可能导致：
 * - 响应延迟飙升（STW 时间过长）
 * - 吞吐量下降
 * - Full GC 频繁
 *
 * <h3>运行方式 — 分别使用以下参数运行，对比 GC 日志</h3>
 *
 * <pre>
 * 【1】Serial 收集器（单线程，Client 模式默认）
 * -Xms20m -Xmx20m -Xmn10m -XX:+PrintGCDetails -XX:+UseSerialGC
 *
 * 【2】ParNew + CMS（低延迟组合，JDK 8 常用）
 * -Xms20m -Xmx20m -Xmn10m -XX:+PrintGCDetails -XX:+UseConcMarkSweepGC
 *
 * 【3】Parallel Scavenge + Parallel Old（高吞吐组合，JDK 8 Server 默认）
 * -Xms20m -Xmx20m -Xmn10m -XX:+PrintGCDetails -XX:+UseParallelGC
 *
 * 【4】G1（JDK 9+ 默认，兼顾吞吐和延迟）
 * -Xms20m -Xmx20m -XX:+PrintGCDetails -XX:+UseG1GC
 * </pre>
 *
 * 对比要点：观察 GC 日志中 [GC 和 [Full GC 的耗时、回收效果
 *
 * @author kungfu
 * @since D2 - GC算法与垃圾回收器
 */
public class GarbageCollectorDemo {

    private static final int _1MB = 1024 * 1024;

    /**
     * 制造 GC 压力：不断分配对象，让新生代和老年代都参与回收
     */
    private static void generateGCPressure() {
        System.out.println("开始制造 GC 压力...\n");

        // 保留一些长期存活对象（模拟缓存），防止全被回收
        byte[][] longLived = new byte[3][];

        for (int i = 0; i < 20; i++) {
            // 分配 1MB 临时对象 → 触发 Minor GC
            byte[] temp = new byte[_1MB];

            // 每隔几次，替换一个长期存活对象 → 模拟老年代增长
            if (i % 5 == 0 && i > 0) {
                longLived[i / 5 % 3] = new byte[2 * _1MB];
                System.out.println("  [第" + i + "轮] 分配 2MB 长期对象，可能触发老年代 GC");
            }
        }

        // 确保 longLived 不被优化掉
        int totalSize = 0;
        for (byte[] arr : longLived) {
            if (arr != null) totalSize += arr.length;
        }
        System.out.println("\n长期存活对象总大小: " + totalSize / 1024 + " KB");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  垃圾回收器实战对比");
        System.out.println("========================================");

        // 打印当前使用的 GC 信息
        System.out.println("\n当前 JVM 信息：");
        java.lang.management.MemoryMXBean memBean =
                java.lang.management.ManagementFactory.getMemoryMXBean();
        System.out.println("  堆内存: " + memBean.getHeapMemoryUsage());

        // 打印垃圾回收器名称
        System.out.println("  当前使用的垃圾回收器:");
        for (java.lang.management.GarbageCollectorMXBean gc :
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("    - " + gc.getName());
        }
        System.out.println();

        // 制造 GC 压力，观察 GC 日志
        generateGCPressure();

        // 打印 GC 统计
        System.out.println("\nGC 统计：");
        for (java.lang.management.GarbageCollectorMXBean gc :
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName()
                    + ": 回收 " + gc.getCollectionCount() + " 次"
                    + ", 总耗时 " + gc.getCollectionTime() + " ms");
        }

        System.out.println("\n========================================");
        System.out.println("  回收器速记：");
        System.out.println("  ┌─────────────┬──────────┬──────────┬───────────────┐");
        System.out.println("  │   回收器      │  算法    │  线程    │ 适用场景       │");
        System.out.println("  ├─────────────┼──────────┼──────────┼───────────────┤");
        System.out.println("  │ Serial       │ 复制     │ 单线程   │ Client/小堆    │");
        System.out.println("  │ ParNew       │ 复制     │ 多线程   │ 配合CMS       │");
        System.out.println("  │ Parallel Scav│ 复制     │ 多线程   │ 高吞吐(默认)   │");
        System.out.println("  │ Serial Old   │ 标记-整理│ 单线程   │ CMS后备       │");
        System.out.println("  │ Parallel Old │ 标记-整理│ 多线程   │ 配合PS        │");
        System.out.println("  │ CMS          │ 标记-清除│ 并发     │ 低延迟        │");
        System.out.println("  │ G1           │ 分区+复制│ 并发     │ 大堆/均衡     │");
        System.out.println("  └─────────────┴──────────┴──────────┴───────────────┘");
        System.out.println();
        System.out.println("  面试黄金搭配：");
        System.out.println("  JDK 8 默认  → Parallel Scavenge + Parallel Old（吞吐优先）");
        System.out.println("  JDK 8 低延迟 → ParNew + CMS");
        System.out.println("  JDK 9+      → G1（默认，推荐）");
        System.out.println("  JDK 11+     → 可选 ZGC（超低延迟，实验性）");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 理解了回收器之后，接下来要问：
 * → 怎么判断对象是否可回收？（可达性分析、GC Roots、四种引用）
 * → 请看 GCRootsDemo.java
 */