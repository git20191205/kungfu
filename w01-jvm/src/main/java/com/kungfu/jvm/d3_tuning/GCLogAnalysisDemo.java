package com.kungfu.jvm.d3_tuning;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 【Demo】GC 日志分析实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>如何开启和读懂 GC 日志</li>
 *   <li>JDK 8 vs JDK 9+ 的 GC 日志参数差异</li>
 *   <li>通过模拟真实负载，生成不同收集器的 GC 日志</li>
 *   <li>教你分析 GC 日志中的关键指标</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试：看一段 GC 日志，分析 GC 类型、耗时、内存回收效果
 * - 调优：GC 日志是判断是否需要调优的第一手数据
 *
 * <h3>运行方式</h3>
 * 使用不同的 GC 参数运行，对比日志差异：
 * <pre>
 * JDK 8 GC 日志参数:
 *   -Xms100m -Xmx100m -XX:+PrintGCDetails -XX:+PrintGCDateStamps
 *   -XX:+PrintGCTimeStamps -Xloggc:gc.log
 *
 * JDK 9+ 统一日志框架（推荐）:
 *   -Xms100m -Xmx100m -Xlog:gc*:file=gc.log:time,uptime,level,tags
 *
 * 分别用不同收集器跑:
 *   -XX:+UseSerialGC
 *   -XX:+UseParallelGC
 *   -XX:+UseConcMarkSweepGC
 *   -XX:+UseG1GC
 * </pre>
 *
 * @author kungfu
 * @since D3 - JVM调优实战
 */
public class GCLogAnalysisDemo {

    /** 模拟的缓存，保持部分对象存活 */
    private static final List<byte[]> CACHE = new ArrayList<>();
    private static final int MAX_CACHE_SIZE = 20; // 最多保留 20 个对象

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  GC 日志分析实战");
        System.out.println("========================================\n");

        showGCLogParams();
        showGCLogFormat();

        System.out.println("=== 开始模拟负载，观察 GC 日志 ===\n");
        simulateWorkload();

        showAnalysisGuide();
    }

    // =============================================================
    // 一、GC 日志参数
    // =============================================================
    private static void showGCLogParams() {
        System.out.println("=== 一、GC 日志参数 ===\n");

        System.out.println("  JDK 8 参数（你大概率在用这套）:");
        System.out.println("  ┌─────────────────────────────┬─────────────────────┐");
        System.out.println("  │ 参数                         │ 作用                 │");
        System.out.println("  ├─────────────────────────────┼─────────────────────┤");
        System.out.println("  │ -XX:+PrintGC                │ 简单 GC 日志          │");
        System.out.println("  │ -XX:+PrintGCDetails         │ 详细 GC 日志（推荐）   │");
        System.out.println("  │ -XX:+PrintGCDateStamps      │ 添加日期时间戳         │");
        System.out.println("  │ -XX:+PrintGCTimeStamps      │ 添加 JVM 运行时间戳    │");
        System.out.println("  │ -Xloggc:gc.log              │ 输出到文件             │");
        System.out.println("  │ -XX:+PrintHeapAtGC          │ GC 前后打印堆详情      │");
        System.out.println("  │ -XX:+PrintGCApplicationStoppedTime │ 打印 STW 时间  │");
        System.out.println("  └─────────────────────────────┴─────────────────────┘\n");

        System.out.println("  JDK 9+ 统一日志（Unified Logging）:");
        System.out.println("  ┌──────────────────────────────────────────────────────┐");
        System.out.println("  │ -Xlog:gc*:file=gc.log:time,uptime,level,tags        │");
        System.out.println("  │                                                      │");
        System.out.println("  │ 格式: -Xlog:标签:输出:装饰器                           │");
        System.out.println("  │   标签: gc, gc+heap, gc+phases 等                    │");
        System.out.println("  │   输出: stdout, file=gc.log                          │");
        System.out.println("  │   装饰器: time, uptime, level, tags, pid, tid        │");
        System.out.println("  └──────────────────────────────────────────────────────┘\n");

        System.out.println("  生产环境推荐组合（JDK 8）:");
        System.out.println("    -XX:+PrintGCDetails");
        System.out.println("    -XX:+PrintGCDateStamps");
        System.out.println("    -Xloggc:/var/log/app/gc.log");
        System.out.println("    -XX:+UseGCLogFileRotation");
        System.out.println("    -XX:NumberOfGCLogFiles=5");
        System.out.println("    -XX:GCLogFileSize=20M");
        System.out.println("    → 5 个日志文件轮转，每个最大 20MB\n");
    }

    // =============================================================
    // 二、GC 日志格式解读
    // =============================================================
    private static void showGCLogFormat() {
        System.out.println("=== 二、GC 日志格式解读 ===\n");

        System.out.println("  Young GC 日志格式（Serial 收集器为例）:");
        System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("  │ [GC (Allocation Failure)                                       │");
        System.out.println("  │   [DefNew: 8128K->1024K(9216K), 0.0052341 secs]                │");
        System.out.println("  │   12345K->5678K(29696K), 0.0053217 secs]                       │");
        System.out.println("  │   [Times: user=0.01 sys=0.00, real=0.01 secs]                  │");
        System.out.println("  └─────────────────────────────────────────────────────────────────┘\n");

        System.out.println("  逐字段解读:");
        System.out.println("  ┌──────────────────────┬─────────────────────────────────────┐");
        System.out.println("  │ 字段                  │ 含义                                │");
        System.out.println("  ├──────────────────────┼─────────────────────────────────────┤");
        System.out.println("  │ GC                   │ Minor GC（Young GC）                │");
        System.out.println("  │ Full GC              │ Full GC（整堆回收）                  │");
        System.out.println("  │ Allocation Failure   │ GC 触发原因（分配失败）               │");
        System.out.println("  │ DefNew / ParNew      │ 新生代收集器名称                     │");
        System.out.println("  │ PSYoungGen           │ Parallel 的新生代名称                │");
        System.out.println("  │ 8128K->1024K(9216K)  │ 新生代: GC前→GC后(总容量)            │");
        System.out.println("  │ 12345K->5678K(29696K)│ 整堆: GC前→GC后(总容量)              │");
        System.out.println("  │ 0.0052341 secs       │ 本次 GC 耗时                        │");
        System.out.println("  │ user=0.01            │ 用户态 CPU 时间                     │");
        System.out.println("  │ sys=0.00             │ 内核态 CPU 时间                     │");
        System.out.println("  │ real=0.01            │ 实际墙钟时间（STW 时间）              │");
        System.out.println("  └──────────────────────┴─────────────────────────────────────┘\n");

        System.out.println("  ★ 关键计算:");
        System.out.println("    回收了多少 = GC前 - GC后");
        System.out.println("    晋升到老年代 = 整堆GC后 - 新生代GC后");
        System.out.println("    例: 新生代 8128K→1024K，整堆 12345K→5678K");
        System.out.println("      新生代回收: 8128-1024 = 7104K");
        System.out.println("      整堆减少:   12345-5678 = 6667K");
        System.out.println("      晋升老年代: 7104-6667 = 437K\n");
    }

    // =============================================================
    // 三、模拟负载
    // =============================================================
    private static void simulateWorkload() {
        Random random = new Random();
        int totalAllocated = 0;

        for (int i = 0; i < 100; i++) {
            // 随机分配 50KB ~ 500KB 的对象
            int size = 50 * 1024 + random.nextInt(450 * 1024);
            byte[] data = new byte[size];
            totalAllocated += size;

            // 模拟缓存：随机保留部分对象（制造老年代压力）
            if (random.nextInt(3) == 0) {
                if (CACHE.size() >= MAX_CACHE_SIZE) {
                    // 随机淘汰一个（模拟 LRU）
                    CACHE.remove(random.nextInt(CACHE.size()));
                }
                CACHE.add(data);
            }
            // 其他对象不引用 → 下次 GC 就回收（短命对象）

            if ((i + 1) % 20 == 0) {
                System.out.println("  已处理 " + (i + 1) + " 个请求，累计分配 "
                        + formatBytes(totalAllocated) + "，缓存 " + CACHE.size() + " 个对象");
            }
        }

        System.out.println("  模拟完成，总计分配 " + formatBytes(totalAllocated) + "\n");
    }

    // =============================================================
    // 四、分析指南
    // =============================================================
    private static void showAnalysisGuide() {
        System.out.println("========================================");
        System.out.println("  GC 日志分析指南");
        System.out.println("========================================\n");

        System.out.println("  一、快速判断是否需要调优:");
        System.out.println("  ┌──────────────────────────┬──────────────────┐");
        System.out.println("  │ 指标                      │ 警戒线            │");
        System.out.println("  ├──────────────────────────┼──────────────────┤");
        System.out.println("  │ Young GC 频率             │ >10次/秒 需关注   │");
        System.out.println("  │ Young GC 耗时             │ >50ms 需关注      │");
        System.out.println("  │ Full GC 频率              │ >1次/小时 需关注   │");
        System.out.println("  │ Full GC 耗时              │ >1秒 需关注       │");
        System.out.println("  │ 老年代使用率（GC后）        │ >70% 需关注       │");
        System.out.println("  │ GC 总时间占比             │ >5% 需关注        │");
        System.out.println("  └──────────────────────────┴──────────────────┘\n");

        System.out.println("  二、常见问题与调优方向:");
        System.out.println("  ┌────────────────────┬──────────────────────────────────┐");
        System.out.println("  │ 问题                │ 调优方向                          │");
        System.out.println("  ├────────────────────┼──────────────────────────────────┤");
        System.out.println("  │ Young GC 太频繁     │ 加大新生代 -Xmn                   │");
        System.out.println("  │ 大量对象晋升老年代   │ 加大 Survivor / 调晋升阈值         │");
        System.out.println("  │ Full GC 太频繁      │ 加大老年代 / 检查内存泄漏           │");
        System.out.println("  │ GC 停顿太长         │ 换低延迟收集器（G1/ZGC）           │");
        System.out.println("  │ 堆内存持续增长不回收  │ 内存泄漏，用 MAT 分析             │");
        System.out.println("  │ Metaspace 持续增长  │ 类加载泄漏，查动态代理              │");
        System.out.println("  └────────────────────┴──────────────────────────────────┘\n");

        System.out.println("  三、GC 日志在线分析工具:");
        System.out.println("    GCEasy: https://gceasy.io （上传 gc.log 自动分析）");
        System.out.println("    GCViewer: 本地 Java 工具，图形化展示 GC 趋势");
        System.out.println("========================================");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * GC 日志分析是调优的基础：
 * → D2 的各种收集器日志对比（Serial vs CMS vs Parallel vs G1）
 * → D3 的参数调优和 OOM 排查都依赖 GC 日志
 *
 * 接下来看 JVM 参数调优 → JvmParameterTuningDemo.java
 */