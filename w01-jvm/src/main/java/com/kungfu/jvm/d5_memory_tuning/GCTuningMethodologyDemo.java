package com.kungfu.jvm.d5_memory_tuning;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 【Demo】GC 调优方法论 — 度量 → 分析 → 调优 → 验证
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>GC 调优四步法的完整流程</li>
 *   <li>场景一：高频 Young GC — Eden 过小导致频繁 Minor GC</li>
 *   <li>场景二：频繁 Full GC — 大对象直接进老年代 + 晋升阈值过低</li>
 *   <li>用 MXBean API 实时采集 GC 指标，量化调优效果</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你做过 JVM 调优吗？具体怎么做的？"
 * 调优不是瞎调参数，而是 度量 → 分析 → 调优 → 验证 的科学流程
 *
 * <h3>运行方式</h3>
 * 对比运行两次，观察 GC 行为变化：
 * <pre>
 * 场景1（调优前）: -Xms30m -Xmx30m -XX:NewRatio=2 -XX:+PrintGCDetails -XX:+UseSerialGC
 * 场景2（调优后）: -Xms100m -Xmx100m -XX:NewRatio=1 -XX:+PrintGCDetails -XX:+UseSerialGC
 * </pre>
 *
 * @author kungfu
 * @since D5 - 内存调优实战
 */
public class GCTuningMethodologyDemo {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  GC 调优方法论");
        System.out.println("========================================\n");

        // 一、调优四步法
        showTuningMethodology();

        // 二、采集当前 GC 基线指标
        System.out.println("=== 二、采集 GC 基线指标 ===\n");
        showCurrentJvmConfig();
        GCMetrics baseline = collectGCMetrics();
        System.out.println("  基线 GC 指标：");
        printMetrics(baseline);
        System.out.println();

        // 三、模拟业务负载（高频短对象 + 长生命周期对象）
        System.out.println("=== 三、模拟业务负载 ===\n");
        System.out.println("  模拟场景：电商下单接口");
        System.out.println("  - 每次请求产生大量临时对象（订单DTO、校验结果等）");
        System.out.println("  - 部分数据缓存到内存（热点商品信息）\n");

        long start = System.currentTimeMillis();
        simulateWorkload();
        long elapsed = System.currentTimeMillis() - start;

        // 四、采集负载后指标，与基线对比
        System.out.println("\n=== 四、负载后 GC 指标对比 ===\n");
        GCMetrics afterLoad = collectGCMetrics();
        showComparison(baseline, afterLoad, elapsed);

        // 五、调优建议
        showTuningAdvice(baseline, afterLoad, elapsed);

        // 六、生产调优模板
        showProductionTemplate();
    }

    // =============================================================
    // 一、调优方法论
    // =============================================================
    private static void showTuningMethodology() {
        System.out.println("=== 一、GC 调优四步法 ===\n");

        System.out.println("  ┌──────────┬──────────────────────────────────────────────┐");
        System.out.println("  │ 步骤     │ 说明                                         │");
        System.out.println("  ├──────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 1. 度量  │ 采集 GC 次数、耗时、堆使用率（jstat/MXBean） │");
        System.out.println("  │ 2. 分析  │ 判断瓶颈：YGC频繁？FGC频繁？停顿过长？      │");
        System.out.println("  │ 3. 调优  │ 针对性调参：堆大小/新生代比例/收集器/晋升阈值│");
        System.out.println("  │ 4. 验证  │ 重新度量，对比调优前后指标，确认改善效果     │");
        System.out.println("  └──────────┴──────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ★ 核心原则：没有数据支撑的调优就是瞎调！\n");

        System.out.println("  常见调优场景：");
        System.out.println("  ┌──────────────────┬─────────────────────┬──────────────────────────┐");
        System.out.println("  │ 症状             │ 可能原因             │ 调优方向                  │");
        System.out.println("  ├──────────────────┼─────────────────────┼──────────────────────────┤");
        System.out.println("  │ Young GC 过于频繁│ Eden 区太小          │ 增大 -Xmn 或降低 NewRatio│");
        System.out.println("  │ Full GC 频繁     │ 老年代空间不足       │ 增大 -Xmx 或优化对象生命│");
        System.out.println("  │ GC 停顿过长      │ 堆太大 / 用了Serial  │ 换 G1/ZGC + 设停顿目标  │");
        System.out.println("  │ 对象过早晋升     │ Survivor 太小        │ 调 SurvivorRatio/晋升阈值│");
        System.out.println("  │ 堆使用率持续上升 │ 内存泄漏             │ dump 分析 → 修复代码     │");
        System.out.println("  └──────────────────┴─────────────────────┴──────────────────────────┘\n");
    }

    // =============================================================
    // 二、JVM 配置信息
    // =============================================================
    private static void showCurrentJvmConfig() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.println("  当前 JVM 配置：");
        System.out.println("    -Xms（初始堆）: " + formatMB(heap.getInit()));
        System.out.println("    -Xmx（最大堆）: " + formatMB(heap.getMax()));
        System.out.println("    已使用:         " + formatMB(heap.getUsed()));
        System.out.println("    已提交:         " + formatMB(heap.getCommitted()));
        System.out.println();

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        System.out.println("  垃圾收集器：");
        for (GarbageCollectorMXBean gc : gcBeans) {
            System.out.println("    " + gc.getName() + " → 管理区域: "
                    + String.join(", ", gc.getMemoryPoolNames()));
        }
        System.out.println();
    }

    // =============================================================
    // 三、模拟业务负载
    // =============================================================
    private static void simulateWorkload() {
        // 模拟热点缓存（长生命周期对象，会进入老年代）
        List<byte[]> hotCache = new ArrayList<>();
        int cacheCount = 0;

        // 模拟 2000 次请求
        for (int req = 0; req < 2000; req++) {
            // 每次请求产生临时对象（短生命周期，应在 Young GC 回收）
            List<byte[]> tempObjects = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                tempObjects.add(new byte[1024]); // 1KB 临时对象
            }

            // 每 100 次请求缓存一个热点数据（长生命周期）
            if (req % 100 == 0) {
                hotCache.add(new byte[10 * 1024]); // 10KB 缓存数据
                cacheCount++;
            }

            // 每 500 次请求打印进度
            if (req % 500 == 0 && req > 0) {
                MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                System.out.println("  已处理 " + req + " 次请求，缓存 " + cacheCount
                        + " 个热点，堆使用 " + formatMB(heap.getUsed()) + " / " + formatMB(heap.getMax()));
            }

            // 模拟请求间隔（让 GC 有机会触发）
            if (req % 100 == 0) {
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println("  负载完成：2000 次请求，缓存 " + cacheCount + " 个热点数据");
    }

    // =============================================================
    // 四、GC 指标采集与对比
    // =============================================================
    static class GCMetrics {
        long youngGCCount;
        long youngGCTime;   // ms
        long fullGCCount;
        long fullGCTime;    // ms
        long heapUsed;
        long heapMax;
    }

    private static GCMetrics collectGCMetrics() {
        GCMetrics m = new GCMetrics();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gc.getName().toLowerCase();
            if (name.contains("young") || name.contains("minor")
                    || name.contains("copy") || name.contains("scavenge")
                    || name.contains("parnew") || name.contains("g1 young")) {
                m.youngGCCount = gc.getCollectionCount();
                m.youngGCTime = gc.getCollectionTime();
            } else {
                m.fullGCCount = gc.getCollectionCount();
                m.fullGCTime = gc.getCollectionTime();
            }
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        m.heapUsed = heap.getUsed();
        m.heapMax = heap.getMax();
        return m;
    }

    private static void printMetrics(GCMetrics m) {
        System.out.println("    Young GC: " + m.youngGCCount + " 次, 总耗时 " + m.youngGCTime + " ms");
        System.out.println("    Full  GC: " + m.fullGCCount + " 次, 总耗时 " + m.fullGCTime + " ms");
        System.out.println("    堆使用率: " + formatMB(m.heapUsed) + " / " + formatMB(m.heapMax)
                + " (" + String.format("%.1f%%", m.heapUsed * 100.0 / m.heapMax) + ")");
    }

    private static void showComparison(GCMetrics before, GCMetrics after, long elapsedMs) {
        long ygcDelta = after.youngGCCount - before.youngGCCount;
        long ygcTimeDelta = after.youngGCTime - before.youngGCTime;
        long fgcDelta = after.fullGCCount - before.fullGCCount;
        long fgcTimeDelta = after.fullGCTime - before.fullGCTime;

        System.out.println("  ┌───────────────┬──────────┬──────────┬──────────┐");
        System.out.println("  │ 指标          │ 基线     │ 负载后   │ 增量     │");
        System.out.println("  ├───────────────┼──────────┼──────────┼──────────┤");
        System.out.printf("  │ Young GC 次数 │ %6d   │ %6d   │ %+6d   │%n",
                before.youngGCCount, after.youngGCCount, ygcDelta);
        System.out.printf("  │ Young GC 耗时 │ %5dms  │ %5dms  │ %+5dms  │%n",
                before.youngGCTime, after.youngGCTime, ygcTimeDelta);
        System.out.printf("  │ Full GC  次数 │ %6d   │ %6d   │ %+6d   │%n",
                before.fullGCCount, after.fullGCCount, fgcDelta);
        System.out.printf("  │ Full GC  耗时 │ %5dms  │ %5dms  │ %+5dms  │%n",
                before.fullGCTime, after.fullGCTime, fgcTimeDelta);
        System.out.println("  └───────────────┴──────────┴──────────┴──────────┘");
        System.out.println();

        double ygcFreq = elapsedMs > 0 ? ygcDelta * 60000.0 / elapsedMs : 0;
        double avgYgcPause = ygcDelta > 0 ? (double) ygcTimeDelta / ygcDelta : 0;
        double gcOverhead = elapsedMs > 0 ? (ygcTimeDelta + fgcTimeDelta) * 100.0 / elapsedMs : 0;

        System.out.println("  关键指标：");
        System.out.printf("    Young GC 频率:    %.1f 次/分钟%s%n", ygcFreq,
                ygcFreq > 10 ? " ← ★ 过高！建议扩大新生代" : " ✓");
        System.out.printf("    Young GC 平均耗时: %.1f ms/次%s%n", avgYgcPause,
                avgYgcPause > 50 ? " ← ★ 过长！" : " ✓");
        System.out.printf("    Full GC 次数:      %d 次%s%n", fgcDelta,
                fgcDelta > 0 ? " ← ★ 有 Full GC！需关注" : " ✓ 无 Full GC");
        System.out.printf("    GC 总开销:         %.2f%%%s%n", gcOverhead,
                gcOverhead > 5 ? " ← ★ GC 开销过大！" : " ✓");
        System.out.printf("    业务耗时:          %d ms%n", elapsedMs);
        System.out.println();
    }

    // =============================================================
    // 五、调优建议
    // =============================================================
    private static void showTuningAdvice(GCMetrics before, GCMetrics after, long elapsed) {
        System.out.println("=== 五、调优建议 ===\n");

        long ygcDelta = after.youngGCCount - before.youngGCCount;
        long fgcDelta = after.fullGCCount - before.fullGCCount;
        double ygcFreq = elapsed > 0 ? ygcDelta * 60000.0 / elapsed : 0;

        if (ygcFreq > 10) {
            System.out.println("  问题1: Young GC 频率过高");
            System.out.println("  原因:  Eden 区太小，频繁被填满");
            System.out.println("  方案:  增大新生代");
            System.out.println("    调优前: -Xms30m -Xmx30m -XX:NewRatio=2  （新生代约 10m）");
            System.out.println("    调优后: -Xms100m -Xmx100m -XX:NewRatio=1 （新生代约 50m）");
            System.out.println("    预期效果: Young GC 频率下降 60%+ \n");
        }

        if (fgcDelta > 0) {
            System.out.println("  问题2: 出现 Full GC");
            System.out.println("  原因:  老年代空间不足 / 对象过早晋升");
            System.out.println("  方案:");
            System.out.println("    1. 增大堆总大小（-Xmx）");
            System.out.println("    2. 调大晋升阈值: -XX:MaxTenuringThreshold=15");
            System.out.println("    3. 检查是否有大对象直接进老年代\n");
        }

        if (ygcFreq <= 10 && fgcDelta == 0) {
            System.out.println("  ✓ GC 表现健康，无需调优");
            System.out.println("  当前配置适合此业务负载\n");
        }

        System.out.println("  ★ 调优验证流程：");
        System.out.println("  1. 用调优后的参数重新运行本 Demo");
        System.out.println("  2. 对比 GC 指标表格，确认各项指标改善");
        System.out.println("  3. 灰度发布到生产环境，观察 Grafana 监控");
        System.out.println("  4. 确认没有副作用（如停顿时间增加）后全量发布\n");
    }

    // =============================================================
    // 六、生产调优模板
    // =============================================================
    private static void showProductionTemplate() {
        System.out.println("========================================");
        System.out.println("  生产环境 JVM 调优模板");
        System.out.println("========================================\n");

        System.out.println("  ★ 4C8G 服务器（G1 收集器）：");
        System.out.println("  ┌────────────────────────────────────────────────────┐");
        System.out.println("  │ -Xms4g -Xmx4g                                     │");
        System.out.println("  │ -XX:+UseG1GC                                      │");
        System.out.println("  │ -XX:MaxGCPauseMillis=200                           │");
        System.out.println("  │ -XX:+HeapDumpOnOutOfMemoryError                    │");
        System.out.println("  │ -XX:HeapDumpPath=/data/logs/heap_dump.hprof        │");
        System.out.println("  │ -Xlog:gc*:file=/data/logs/gc.log:time,uptime:filecount=5,filesize=20m │");
        System.out.println("  └────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 2C4G 服务器（G1 收集器）：");
        System.out.println("  ┌────────────────────────────────────────────────────┐");
        System.out.println("  │ -Xms2g -Xmx2g                                     │");
        System.out.println("  │ -XX:+UseG1GC                                      │");
        System.out.println("  │ -XX:MaxGCPauseMillis=100                           │");
        System.out.println("  │ -XX:+HeapDumpOnOutOfMemoryError                    │");
        System.out.println("  │ -XX:HeapDumpPath=/data/logs/heap_dump.hprof        │");
        System.out.println("  └────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 监控指标（接入 Prometheus + Grafana）：");
        System.out.println("    1. jvm_gc_pause_seconds_sum — GC 停顿总时间");
        System.out.println("    2. jvm_gc_pause_seconds_count — GC 次数");
        System.out.println("    3. jvm_memory_used_bytes — 堆使用量");
        System.out.println("    4. jvm_memory_max_bytes — 堆上限");
        System.out.println("    5. 告警规则: Full GC > 2次/小时 → P2 告警");
        System.out.println("========================================");
    }

    private static String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * D5 全部知识点：
 *   1. GCTuningMethodologyDemo    — GC 调优方法论（本类）
 *   2. MemoryLeakDetectionDemo    — 内存泄漏检测实战
 *
 * W01-JVM 完整路线：
 *   D1 → 内存模型（五大区域、对象布局、OOM类型、常量池）
 *   D2 → GC（算法、收集器、GC Roots、引用类型、终结机制、安全点）
 *   D3 → 调优工具（监控命令、GC日志、参数调优、OOM排查）
 *   D4 → 类加载（加载过程、双亲委派、打破双亲委派）
 *   D5 → 内存调优（调优方法论、内存泄漏检测）
 *   D6 → OOM排查（MAT分析、非堆OOM）
 */
