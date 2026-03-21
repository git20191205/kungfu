package com.kungfu.jvm.d3_tuning;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * 【Demo】JVM 监控命令 + MXBean 实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>用 Java MXBean API 获取 JVM 运行时信息（等价于命令行工具）</li>
 *   <li>对应关系：jps → RuntimeMXBean, jstat → MemoryMXBean, jstack → ThreadMXBean</li>
 *   <li>同时教你用命令行工具排查线上问题</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试必问："线上CPU飙高怎么排查？" "线上OOM怎么排查？"
 * 答案的核心就是这几个命令：jps → jstat → jstack → jmap
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法。
 * 运行后程序会暂停 60 秒，期间可以用命令行工具观察：
 * <pre>
 * jps -l                          → 找到进程 PID
 * jstat -gc &lt;PID&gt; 1000 5         → 每秒打印一次 GC 统计，共 5 次
 * jstat -gcutil &lt;PID&gt; 1000 5     → 百分比形式
 * jinfo -flags &lt;PID&gt;             → 查看 JVM 参数
 * jstack &lt;PID&gt;                   → 打印所有线程堆栈
 * jmap -heap &lt;PID&gt;               → 堆概况
 * jmap -histo &lt;PID&gt; | head -20   → 对象统计 Top 20
 * </pre>
 *
 * @author kungfu
 * @since D3 - JVM调优实战
 */
public class JvmMonitorCommandDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  JVM 监控命令实战");
        System.out.println("========================================\n");

        showProcessInfo();
        showMemoryInfo();
        showGCInfo();
        showThreadInfo();
        showMemoryPoolInfo();
        showCommandCheatSheet();

        // 制造一些内存压力，方便用命令行工具观察
        System.out.println("\n=== 制造内存压力，等待 60 秒 ===");
        System.out.println("  现在可以打开另一个终端，用以下命令观察：");
        System.out.println("  1. jps -l");
        System.out.println("  2. jstat -gc <PID> 1000 5");
        System.out.println("  3. jstack <PID>");
        System.out.println("  4. jmap -histo <PID> | head -20\n");

        List<byte[]> pressure = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pressure.add(new byte[1024 * 1024]); // 每次 1MB
            Thread.sleep(2000); // 每 2 秒分配一次
            System.out.println("  已分配 " + (i + 1) + " MB，可以用 jstat 观察内存变化");
        }
    }

    // =============================================================
    // 一、进程信息（对应 jps）
    // =============================================================
    private static void showProcessInfo() {
        System.out.println("=== 一、进程信息（对应 jps）===\n");

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        System.out.println("  进程名:    " + runtime.getName());
        // getName() 返回 "PID@hostname" 格式
        String pid = runtime.getName().split("@")[0];
        System.out.println("  PID:       " + pid);
        System.out.println("  JVM 名称:  " + runtime.getVmName());
        System.out.println("  JVM 版本:  " + runtime.getVmVersion());
        System.out.println("  启动时间:  " + runtime.getUptime() + " ms");
        System.out.println();

        System.out.println("  JVM 启动参数:");
        for (String arg : runtime.getInputArguments()) {
            System.out.println("    " + arg);
        }
        System.out.println();

        System.out.println("  命令行等价:");
        System.out.println("  ┌──────────────────────────────────────────────┐");
        System.out.println("  │ jps -l         → 列出所有 Java 进程          │");
        System.out.println("  │ jps -v         → 显示 JVM 启动参数           │");
        System.out.println("  │ jinfo <PID>    → 查看进程的系统属性和 VM 参数 │");
        System.out.println("  │ jinfo -flags <PID> → 只看 VM 参数           │");
        System.out.println("  └──────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、内存信息（对应 jstat -gc / jmap -heap）
    // =============================================================
    private static void showMemoryInfo() {
        System.out.println("=== 二、内存信息（对应 jstat / jmap）===\n");

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        System.out.println("  堆内存（Heap）:");
        System.out.println("    初始(init):     " + formatBytes(heap.getInit()));
        System.out.println("    已用(used):     " + formatBytes(heap.getUsed()));
        System.out.println("    已提交(committed): " + formatBytes(heap.getCommitted()));
        System.out.println("    最大(max):      " + formatBytes(heap.getMax()));
        System.out.println("    使用率:         " + percent(heap.getUsed(), heap.getMax()));
        System.out.println();

        System.out.println("  非堆内存（Non-Heap = 元空间 + 代码缓存等）:");
        System.out.println("    已用(used):     " + formatBytes(nonHeap.getUsed()));
        System.out.println("    已提交(committed): " + formatBytes(nonHeap.getCommitted()));
        System.out.println();

        System.out.println("  各参数含义:");
        System.out.println("  ┌─────────────┬───────────────────────────────────┐");
        System.out.println("  │ init        │ JVM 启动时从 OS 申请的初始内存     │");
        System.out.println("  │ used        │ 当前实际使用的内存                 │");
        System.out.println("  │ committed   │ 已向 OS 申请到的内存（已保证可用）  │");
        System.out.println("  │ max         │ 最大可用内存（-Xmx 设置的值）      │");
        System.out.println("  └─────────────┴───────────────────────────────────┘");
        System.out.println("  关系: init ≤ used ≤ committed ≤ max\n");

        System.out.println("  命令行等价:");
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │ jstat -gc <PID> 1000 5     → 每秒打印一次 GC 统计，共 5 次│");
        System.out.println("  │ jstat -gcutil <PID> 1000   → 百分比形式，更直观            │");
        System.out.println("  │ jmap -heap <PID>           → 堆内存概况                   │");
        System.out.println("  │ jmap -histo <PID>          → 对象统计（哪种对象最多）       │");
        System.out.println("  │ jmap -dump:format=b,file=dump.hprof <PID> → 导出堆转储    │");
        System.out.println("  └──────────────────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 三、GC 信息（对应 jstat -gc）
    // =============================================================
    private static void showGCInfo() {
        System.out.println("=== 三、GC 信息（对应 jstat -gc）===\n");

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println("  当前使用的垃圾收集器:");
        for (GarbageCollectorMXBean gc : gcBeans) {
            System.out.println("    收集器名称: " + gc.getName());
            System.out.println("    收集次数:   " + gc.getCollectionCount());
            System.out.println("    收集总耗时: " + gc.getCollectionTime() + " ms");
            System.out.print("    管理的内存池: ");
            for (String pool : gc.getMemoryPoolNames()) {
                System.out.print(pool + " ");
            }
            System.out.println("\n");
        }

        System.out.println("  jstat -gc 输出列名对照表:");
        System.out.println("  ┌──────┬────────────────────────────────┐");
        System.out.println("  │ 列名  │ 含义                           │");
        System.out.println("  ├──────┼────────────────────────────────┤");
        System.out.println("  │ S0C  │ Survivor 0 容量（KB）           │");
        System.out.println("  │ S1C  │ Survivor 1 容量（KB）           │");
        System.out.println("  │ S0U  │ Survivor 0 已用（KB）           │");
        System.out.println("  │ S1U  │ Survivor 1 已用（KB）           │");
        System.out.println("  │ EC   │ Eden 容量（KB）                 │");
        System.out.println("  │ EU   │ Eden 已用（KB）                 │");
        System.out.println("  │ OC   │ Old 容量（KB）                  │");
        System.out.println("  │ OU   │ Old 已用（KB）                  │");
        System.out.println("  │ MC   │ Metaspace 容量（KB）            │");
        System.out.println("  │ MU   │ Metaspace 已用（KB）            │");
        System.out.println("  │ YGC  │ Young GC 次数                  │");
        System.out.println("  │ YGCT │ Young GC 总耗时（秒）           │");
        System.out.println("  │ FGC  │ Full GC 次数                   │");
        System.out.println("  │ FGCT │ Full GC 总耗时（秒）            │");
        System.out.println("  │ GCT  │ GC 总耗时（秒）                 │");
        System.out.println("  └──────┴────────────────────────────────┘\n");
    }

    // =============================================================
    // 四、线程信息（对应 jstack）
    // =============================================================
    private static void showThreadInfo() {
        System.out.println("=== 四、线程信息（对应 jstack）===\n");

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        System.out.println("  当前线程数:       " + threadBean.getThreadCount());
        System.out.println("  守护线程数:       " + threadBean.getDaemonThreadCount());
        System.out.println("  历史峰值线程数:    " + threadBean.getPeakThreadCount());
        System.out.println("  累计创建线程数:    " + threadBean.getTotalStartedThreadCount());
        System.out.println();

        // 死锁检测
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        if (deadlockedThreads != null) {
            System.out.println("  ⚠ 检测到死锁！涉及 " + deadlockedThreads.length + " 个线程");
        } else {
            System.out.println("  ✓ 未检测到死锁");
        }
        System.out.println();

        // 打印部分线程信息
        System.out.println("  当前所有线程:");
        ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);
        for (ThreadInfo thread : threads) {
            System.out.printf("    [%s] %s - %s%n",
                    thread.getThreadId(), thread.getThreadName(), thread.getThreadState());
        }
        System.out.println();

        System.out.println("  命令行等价:");
        System.out.println("  ┌──────────────────────────────────────────────────────┐");
        System.out.println("  │ jstack <PID>            → 打印所有线程堆栈           │");
        System.out.println("  │ jstack -l <PID>         → 包含锁信息                │");
        System.out.println("  │ jstack -F <PID>         → 强制 dump（进程无响应时用） │");
        System.out.println("  └──────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ CPU 飙高排查三板斧（面试必背）：");
        System.out.println("    1. top → 找到 CPU 高的 Java 进程 PID");
        System.out.println("    2. top -Hp <PID> → 找到 CPU 高的线程 TID");
        System.out.println("    3. printf '%x' <TID> → 转成 16 进制");
        System.out.println("    4. jstack <PID> | grep <16进制TID> -A 30 → 看这个线程在干什么\n");
    }

    // =============================================================
    // 五、内存池详情
    // =============================================================
    private static void showMemoryPoolInfo() {
        System.out.println("=== 五、各内存池详情 ===\n");

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            System.out.printf("  %-30s  类型: %-8s  已用: %-10s  最大: %s%n",
                    pool.getName(),
                    pool.getType(),
                    formatBytes(usage.getUsed()),
                    usage.getMax() == -1 ? "无限制" : formatBytes(usage.getMax()));
        }
        System.out.println();
    }

    // =============================================================
    // 六、命令速查表
    // =============================================================
    private static void showCommandCheatSheet() {
        System.out.println("========================================");
        System.out.println("  JVM 排查命令速查表");
        System.out.println("========================================\n");

        System.out.println("  一、日常监控:");
        System.out.println("  ┌────────────────────────────────────┬─────────────────────────┐");
        System.out.println("  │ 命令                               │ 场景                     │");
        System.out.println("  ├────────────────────────────────────┼─────────────────────────┤");
        System.out.println("  │ jps -l                             │ 找 Java 进程             │");
        System.out.println("  │ jstat -gcutil <PID> 1000           │ 持续监控 GC 状态          │");
        System.out.println("  │ jinfo -flags <PID>                 │ 看 JVM 参数              │");
        System.out.println("  │ jmap -heap <PID>                   │ 堆概况                   │");
        System.out.println("  └────────────────────────────────────┴─────────────────────────┘\n");

        System.out.println("  二、故障排查:");
        System.out.println("  ┌────────────────────────────────────┬─────────────────────────┐");
        System.out.println("  │ 命令                               │ 场景                     │");
        System.out.println("  ├────────────────────────────────────┼─────────────────────────┤");
        System.out.println("  │ jstack <PID>                       │ CPU 飙高 / 死锁排查      │");
        System.out.println("  │ jmap -histo <PID> | head -20       │ 快速看哪种对象最多        │");
        System.out.println("  │ jmap -dump:format=b,file=dump.hprof│ 导出堆转储（OOM 排查）   │");
        System.out.println("  │ jhat dump.hprof / MAT              │ 分析堆转储               │");
        System.out.println("  └────────────────────────────────────┴─────────────────────────┘\n");

        System.out.println("  三、线上排查流程图:");
        System.out.println("  ┌──────────┐     ┌──────────┐     ┌──────────┐");
        System.out.println("  │ CPU 飙高  │────→│ top -Hp  │────→│ jstack   │");
        System.out.println("  └──────────┘     └──────────┘     └──────────┘");
        System.out.println("                                    找到热点线程在做什么");
        System.out.println();
        System.out.println("  ┌──────────┐     ┌──────────┐     ┌──────────┐");
        System.out.println("  │ OOM      │────→│ jmap dump│────→│ MAT 分析  │");
        System.out.println("  └──────────┘     └──────────┘     └──────────┘");
        System.out.println("                                    找到大对象是谁持有");
        System.out.println();
        System.out.println("  ┌──────────┐     ┌──────────┐     ┌──────────┐");
        System.out.println("  │ GC 频繁   │────→│ jstat -gc│────→│ 调参数    │");
        System.out.println("  └──────────┘     └──────────┘     └──────────┘");
        System.out.println("                                    调堆大小/换收集器");
    }

    // =============================================================
    // 工具方法
    // =============================================================
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String percent(long used, long max) {
        if (max <= 0) return "N/A";
        return String.format("%.1f%%", used * 100.0 / max);
    }
}

/*
 * 【知识串联】
 * D3 全部知识点：
 *   1. JvmMonitorCommandDemo      — 监控命令实战（本类）
 *   2. GCLogAnalysisDemo          — GC 日志分析
 *   3. JvmParameterTuningDemo     — JVM 参数调优
 *   4. OOMTroubleshootingDemo     — OOM 案例排查
 *
 * 上一步 D2 → GC 算法与垃圾回收器
 */