package com.kungfu.jvm.d3_tuning;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 【Demo】JVM 参数调优实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>JVM 核心参数分类与含义</li>
 *   <li>不同参数组合对 GC 行为的影响</li>
 *   <li>通过实际对比理解调优效果</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你在生产环境中配过哪些 JVM 参数？为什么这样配？"
 *
 * <h3>运行方式</h3>
 * 用不同参数组合运行，对比 GC 次数和耗时：
 * <pre>
 * 场景1（默认）:    -Xms100m -Xmx100m -XX:+PrintGCDetails -XX:+UseSerialGC
 * 场景2（大新生代）: -Xms100m -Xmx100m -Xmn60m -XX:+PrintGCDetails -XX:+UseSerialGC
 * 场景3（大Survivor）: -Xms100m -Xmx100m -XX:SurvivorRatio=2 -XX:+PrintGCDetails -XX:+UseSerialGC
 * 场景4（G1）:      -Xms100m -Xmx100m -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:+PrintGCDetails
 * </pre>
 *
 * @author kungfu
 * @since D3 - JVM调优实战
 */
public class JvmParameterTuningDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  JVM 参数调优实战");
        System.out.println("========================================\n");

        showParameterGuide();
        showCurrentConfig();

        System.out.println("=== 开始负载测试 ===\n");
        long start = System.currentTimeMillis();
        runWorkload();
        long elapsed = System.currentTimeMillis() - start;

        showResult(elapsed);
        showProductionTemplate();
    }

    // =============================================================
    // 一、JVM 参数大全
    // =============================================================
    private static void showParameterGuide() {
        System.out.println("=== 一、JVM 核心参数分类 ===\n");

        System.out.println("  1. 堆内存参数:");
        System.out.println("  ┌───────────────────────────┬──────────────────────────────────┐");
        System.out.println("  │ 参数                       │ 含义                             │");
        System.out.println("  ├───────────────────────────┼──────────────────────────────────┤");
        System.out.println("  │ -Xms                      │ 堆初始大小（建议与 Xmx 相等）     │");
        System.out.println("  │ -Xmx                      │ 堆最大大小                       │");
        System.out.println("  │ -Xmn                      │ 新生代大小                       │");
        System.out.println("  │ -XX:NewRatio=2             │ 老年代:新生代 = 2:1              │");
        System.out.println("  │ -XX:SurvivorRatio=8        │ Eden:S0:S1 = 8:1:1              │");
        System.out.println("  │ -XX:MaxTenuringThreshold=15│ 晋升老年代的年龄阈值              │");
        System.out.println("  │ -XX:PretenureSizeThreshold │ 大对象直接进老年代的阈值           │");
        System.out.println("  └───────────────────────────┴──────────────────────────────────┘\n");

        System.out.println("  2. 收集器参数:");
        System.out.println("  ┌───────────────────────────┬──────────────────────────────────┐");
        System.out.println("  │ 参数                       │ 含义                             │");
        System.out.println("  ├───────────────────────────┼──────────────────────────────────┤");
        System.out.println("  │ -XX:+UseSerialGC          │ Serial（单线程，客户端/小堆）      │");
        System.out.println("  │ -XX:+UseParallelGC        │ Parallel（吞吐量优先）            │");
        System.out.println("  │ -XX:+UseConcMarkSweepGC   │ CMS（低延迟，JDK 9 废弃）        │");
        System.out.println("  │ -XX:+UseG1GC              │ G1（均衡，JDK 9+ 默认）          │");
        System.out.println("  │ -XX:+UseZGC               │ ZGC（超低延迟，JDK 15+）         │");
        System.out.println("  │ -XX:MaxGCPauseMillis=200  │ G1 目标最大停顿时间（ms）         │");
        System.out.println("  │ -XX:ParallelGCThreads=N   │ GC 并行线程数                    │");
        System.out.println("  │ -XX:ConcGCThreads=N       │ GC 并发线程数                    │");
        System.out.println("  └───────────────────────────┴──────────────────────────────────┘\n");

        System.out.println("  3. 其他重要参数:");
        System.out.println("  ┌──────────────────────────────────┬────────────────────────────┐");
        System.out.println("  │ 参数                              │ 含义                       │");
        System.out.println("  ├──────────────────────────────────┼────────────────────────────┤");
        System.out.println("  │ -Xss256k                         │ 线程栈大小                  │");
        System.out.println("  │ -XX:MetaspaceSize=256m           │ 元空间初始大小               │");
        System.out.println("  │ -XX:MaxMetaspaceSize=256m        │ 元空间最大大小               │");
        System.out.println("  │ -XX:MaxDirectMemorySize=256m     │ 直接内存最大值               │");
        System.out.println("  │ -XX:+HeapDumpOnOutOfMemoryError  │ OOM 时自动 dump             │");
        System.out.println("  │ -XX:HeapDumpPath=/path/dump.hprof│ dump 文件路径               │");
        System.out.println("  │ -XX:+DisableExplicitGC           │ 禁用 System.gc()           │");
        System.out.println("  │ -XX:+AlwaysPreTouch              │ 启动时预分配所有内存页       │");
        System.out.println("  └──────────────────────────────────┴────────────────────────────┘\n");

        System.out.println("  ★ 调优核心公式:");
        System.out.println("    堆大小 = 3~4 倍 Full GC 后老年代存活大小");
        System.out.println("    新生代 = 1~1.5 倍 Full GC 后老年代存活大小");
        System.out.println("    老年代 = 2~3 倍 Full GC 后老年代存活大小");
        System.out.println("    元空间 = 1.2~1.5 倍 Full GC 后元空间大小\n");
    }

    // =============================================================
    // 二、当前配置
    // =============================================================
    private static void showCurrentConfig() {
        System.out.println("=== 二、当前 JVM 配置 ===\n");

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();

        System.out.println("  堆内存:");
        System.out.println("    初始: " + formatMB(heap.getInit()));
        System.out.println("    最大: " + formatMB(heap.getMax()));
        System.out.println("    已用: " + formatMB(heap.getUsed()));
        System.out.println();

        System.out.println("  启动参数:");
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            System.out.println("    " + arg);
        }
        System.out.println();
    }

    // =============================================================
    // 三、负载测试
    // =============================================================
    private static void runWorkload() {
        Random random = new Random(42); // 固定种子，保证每次运行一致
        List<byte[]> longLived = new ArrayList<>(); // 模拟长期存活对象

        for (int round = 0; round < 5; round++) {
            System.out.println("  --- 第 " + (round + 1) + " 轮 ---");

            // 创建大量短命对象（触发 Young GC）
            for (int i = 0; i < 200; i++) {
                byte[] shortLived = new byte[1024 * (10 + random.nextInt(90))]; // 10~100KB
            }

            // 创建少量长命对象（逐步填充老年代）
            for (int i = 0; i < 3; i++) {
                longLived.add(new byte[1024 * 512]); // 512KB
            }

            // 模拟部分缓存过期
            if (longLived.size() > 15) {
                for (int i = 0; i < 5; i++) {
                    longLived.remove(0);
                }
            }

            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            System.out.println("    堆使用: " + formatMB(heap.getUsed()) + " / " + formatMB(heap.getMax())
                    + "  长期对象: " + longLived.size() + " 个");
        }
        System.out.println();
    }

    // =============================================================
    // 四、结果展示
    // =============================================================
    private static void showResult(long elapsed) {
        System.out.println("=== 三、运行结果 ===\n");

        System.out.println("  总耗时: " + elapsed + " ms");

        List<java.lang.management.GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        long totalGCCount = 0;
        long totalGCTime = 0;
        for (java.lang.management.GarbageCollectorMXBean gc : gcBeans) {
            System.out.println("  " + gc.getName() + ": " + gc.getCollectionCount()
                    + " 次, 耗时 " + gc.getCollectionTime() + " ms");
            totalGCCount += gc.getCollectionCount();
            totalGCTime += gc.getCollectionTime();
        }

        System.out.println("  GC 总计: " + totalGCCount + " 次, 总耗时 " + totalGCTime + " ms");
        System.out.println("  GC 时间占比: " + String.format("%.1f%%", totalGCTime * 100.0 / elapsed));
        System.out.println();

        System.out.println("  ★ 对比实验建议:");
        System.out.println("    记录每种配置下的: GC次数 / GC耗时 / GC占比 / 总耗时");
        System.out.println("    然后比较哪种配置最优\n");
    }

    // =============================================================
    // 五、生产模板
    // =============================================================
    private static void showProductionTemplate() {
        System.out.println("========================================");
        System.out.println("  生产环境 JVM 参数模板");
        System.out.println("========================================\n");

        System.out.println("  ★ 4C8G 服务器（常见配置）:");
        System.out.println("  ┌──────────────────────────────────────────────┐");
        System.out.println("  │ -Xms4g -Xmx4g                   # 堆 4GB   │");
        System.out.println("  │ -Xmn2g                           # 新生代 2G│");
        System.out.println("  │ -XX:MetaspaceSize=256m                      │");
        System.out.println("  │ -XX:MaxMetaspaceSize=256m                   │");
        System.out.println("  │ -XX:+UseG1GC                     # G1 收集器│");
        System.out.println("  │ -XX:MaxGCPauseMillis=200          # 目标停顿│");
        System.out.println("  │ -XX:+HeapDumpOnOutOfMemoryError             │");
        System.out.println("  │ -XX:HeapDumpPath=/data/logs/dump.hprof      │");
        System.out.println("  │ -XX:+PrintGCDetails                         │");
        System.out.println("  │ -XX:+PrintGCDateStamps                      │");
        System.out.println("  │ -Xloggc:/data/logs/gc.log                   │");
        System.out.println("  └──────────────────────────────────────────────┘\n");

        System.out.println("  ★ 参数设置原则:");
        System.out.println("  ┌──────────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 原则                  │ 说明                                  │");
        System.out.println("  ├──────────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ Xms = Xmx            │ 避免堆动态扩缩的开销                   │");
        System.out.println("  │ 新生代占堆 1/3~1/2    │ 太小→频繁 YGC，太大→老年代不够         │");
        System.out.println("  │ 别设太大堆            │ GC 停顿和堆大小正相关                  │");
        System.out.println("  │ 必开 HeapDump        │ OOM 时有据可查                        │");
        System.out.println("  │ 必开 GC 日志          │ 调优的唯一数据来源                     │");
        System.out.println("  │ 优先用 G1             │ JDK 8+ 通用选择，平衡吞吐和延迟        │");
        System.out.println("  │ 别随意调 NewRatio     │ G1 会自动调节新生代老年代比例            │");
        System.out.println("  └──────────────────────┴──────────────────────────────────────┘\n");

        System.out.println("  ★ 不同场景的收集器选择:");
        System.out.println("  ┌──────────────────────┬──────────────────┬─────────────────────┐");
        System.out.println("  │ 场景                  │ 收集器            │ 原因                 │");
        System.out.println("  ├──────────────────────┼──────────────────┼─────────────────────┤");
        System.out.println("  │ 普通 Web 服务         │ G1               │ 平衡吞吐和延迟        │");
        System.out.println("  │ 大数据批处理          │ Parallel          │ 吞吐量最大化          │");
        System.out.println("  │ 交易/支付（低延迟）   │ ZGC / G1          │ 亚毫秒级停顿          │");
        System.out.println("  │ 小堆（< 256MB）      │ Serial            │ 简单高效              │");
        System.out.println("  │ 容器环境             │ G1 + 容器感知      │ -XX:+UseContainerSupport │");
        System.out.println("  └──────────────────────┴──────────────────┴─────────────────────┘");
        System.out.println("========================================");
    }

    private static String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * JVM 参数调优的前提：
 *   1. 先用 jstat 看 GC 现状（D3 Demo1）
 *   2. 再分析 GC 日志找问题（D3 Demo2）
 *   3. 调参数验证效果（本 Demo）
 *   4. 遇到 OOM 用 dump 排查（D3 Demo4）
 *
 * 接下来看 OOM 排查实战 → OOMTroubleshootingDemo.java
 */