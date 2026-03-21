package com.kungfu.jvm.d3_tuning;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【Demo】OOM 真实案例排查实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 模拟 3 种线上常见的 OOM 场景，展示完整的排查流程：
 * <ol>
 *   <li>场景1 — 缓存无限增长（static Map 没有淘汰策略）</li>
 *   <li>场景2 — 大查询一次加载（模拟数据库全表扫描）</li>
 *   <li>场景3 — 线程池任务堆积（队列无界导致 OOM）</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："说一下你排查过的线上 OOM 案例"
 * 这 3 个场景覆盖了 90% 的堆 OOM 根因
 *
 * <h3>运行方式</h3>
 * 每次只运行一个场景（在 main 中取消注释）：
 * <pre>
 * -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError
 * -XX:HeapDumpPath=./oom_dump.hprof -XX:+PrintGCDetails
 * </pre>
 *
 * @author kungfu
 * @since D3 - JVM调优实战
 */
public class OOMTroubleshootingDemo {

    // =============================================================
    // 场景一：缓存无限增长
    // 真实案例：本地缓存用 HashMap 实现，没有 maxSize 和淘汰策略
    // =============================================================
    /** 模拟一个没有淘汰策略的本地缓存 */
    private static final Map<String, byte[]> LOCAL_CACHE = new HashMap<>();

    /**
     * 场景：每次请求都往缓存写，但从不清理
     * 典型代码：Cache.put(userId, userData) 但没有 expire / maxSize
     */
    private static void cacheOOM() {
        System.out.println("--- 场景一：缓存无限增长 ---\n");
        System.out.println("  模拟：本地 HashMap 缓存，只写不删");
        System.out.println("  典型原因：缓存没有 maxSize / TTL / LRU 淘汰\n");

        int count = 0;
        try {
            while (true) {
                // 模拟每次请求都缓存用户数据（1KB）
                String key = "user_" + count;
                byte[] userData = new byte[1024]; // 1KB 用户数据
                LOCAL_CACHE.put(key, userData);
                count++;

                if (count % 1000 == 0) {
                    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                    System.out.println("  缓存 " + count + " 个用户，堆使用 "
                            + formatMB(heap.getUsed()) + " / " + formatMB(heap.getMax()));
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ OOM！缓存了 " + count + " 个用户后堆耗尽");
            System.out.println("  错误: " + e.getMessage());
            System.out.println();
            System.out.println("  排查流程：");
            System.out.println("  1. jmap -histo <PID> → 发现大量 byte[] 和 HashMap$Node");
            System.out.println("  2. MAT 打开 dump → Dominator Tree → HashMap 占了 90% 内存");
            System.out.println("  3. 找到持有 HashMap 的变量 → LOCAL_CACHE（static 字段）");
            System.out.println("  4. 代码审查 → 只有 put 没有 remove，也没有 size 限制");
            System.out.println();
            System.out.println("  修复方案：");
            System.out.println("  1. 用 Caffeine / Guava Cache 替代 HashMap（自带淘汰）");
            System.out.println("  2. 设置 maximumSize + expireAfterWrite");
            System.out.println("  3. 或者用 Redis 做分布式缓存（不占 JVM 堆内存）");
            System.out.println();
            System.out.println("  正确写法示例：");
            System.out.println("    Cache<String, byte[]> cache = Caffeine.newBuilder()");
            System.out.println("        .maximumSize(10_000)              // 最多 1 万条");
            System.out.println("        .expireAfterWrite(10, MINUTES)    // 10 分钟过期");
            System.out.println("        .build();");
        }
    }

    // =============================================================
    // 场景二：大查询一次加载
    // 真实案例：SQL 没有 LIMIT，一次查出百万行数据
    // =============================================================
    /**
     * 场景：一次性加载大量数据到内存
     * 典型代码：SELECT * FROM orders（没有分页）
     */
    private static void bigQueryOOM() {
        System.out.println("--- 场景二：大查询一次加载 ---\n");
        System.out.println("  模拟：SELECT * FROM orders（无 LIMIT，百万行）");
        System.out.println("  典型原因：SQL 忘记分页 / 导出功能没有流式处理\n");

        List<Map<String, Object>> resultSet = new ArrayList<>();
        int rowCount = 0;

        try {
            while (true) {
                // 模拟一行数据库记录（约 500 bytes）
                Map<String, Object> row = new HashMap<>();
                row.put("id", rowCount);
                row.put("order_no", "ORD" + String.format("%010d", rowCount));
                row.put("user_name", "用户" + rowCount);
                row.put("amount", 99.99);
                row.put("address", "某某省某某市某某区某某路" + rowCount + "号，这是一个很长的地址用来模拟真实数据");
                row.put("data", new byte[256]); // 模拟其他字段

                resultSet.add(row);
                rowCount++;

                if (rowCount % 5000 == 0) {
                    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                    System.out.println("  已加载 " + rowCount + " 行，堆使用 "
                            + formatMB(heap.getUsed()) + " / " + formatMB(heap.getMax()));
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ OOM！加载了 " + rowCount + " 行后堆耗尽");
            System.out.println("  错误: " + e.getMessage());
            System.out.println();
            System.out.println("  排查流程：");
            System.out.println("  1. jmap -histo → 大量 HashMap / ArrayList 对象");
            System.out.println("  2. MAT → GC Roots 分析 → 找到持有 resultSet 的方法");
            System.out.println("  3. 对应代码 → SQL 查询没有 LIMIT");
            System.out.println();
            System.out.println("  修复方案：");
            System.out.println("  1. SQL 加 LIMIT 分页: SELECT * FROM orders LIMIT 100 OFFSET 0");
            System.out.println("  2. 大量数据用流式处理: MyBatis 的 Cursor / JDBC fetchSize");
            System.out.println("  3. 导出场景用 CSV 流式写出，不全量加载到内存");
            System.out.println("  4. 加 SQL 审核：禁止没有 WHERE 和 LIMIT 的全表查询");
        }
    }

    // =============================================================
    // 场景三：任务队列无界堆积
    // 真实案例：线程池用了无界队列，任务提交速度 > 消费速度
    // =============================================================
    /**
     * 场景：模拟线程池任务堆积
     * 典型代码：Executors.newFixedThreadPool() 默认用 LinkedBlockingQueue（无界）
     */
    private static void taskQueueOOM() {
        System.out.println("--- 场景三：任务队列无界堆积 ---\n");
        System.out.println("  模拟：线程池任务提交速度 > 消费速度，队列无限增长");
        System.out.println("  典型原因：Executors.newFixedThreadPool() 内部用无界队列\n");

        // 模拟无界任务队列
        List<Runnable> taskQueue = new ArrayList<>();
        int taskCount = 0;

        try {
            while (true) {
                // 模拟每个任务携带的数据（如请求体、消息体）
                final byte[] taskData = new byte[10 * 1024]; // 10KB 任务数据
                final int taskId = taskCount;

                Runnable task = () -> {
                    // 模拟任务处理（引用 taskData 使其被队列持有，不被 GC 回收）
                    if (taskData.length < 0) System.out.println("处理任务 " + taskId);
                };

                taskQueue.add(task);
                taskCount++;

                if (taskCount % 500 == 0) {
                    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                    System.out.println("  任务队列: " + taskCount + " 个任务待处理，堆使用 "
                            + formatMB(heap.getUsed()) + " / " + formatMB(heap.getMax()));
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ OOM！" + taskCount + " 个任务堆积后堆耗尽");
            System.out.println("  错误: " + e.getMessage());
            System.out.println();
            System.out.println("  排查流程：");
            System.out.println("  1. jstack <PID> → 线程池中线程都在忙 / 队列很长");
            System.out.println("  2. jmap -histo → 大量 Runnable / Lambda 对象");
            System.out.println("  3. MAT → 找到任务队列 → 发现是无界 LinkedBlockingQueue");
            System.out.println();
            System.out.println("  修复方案：");
            System.out.println("  1. ❌ Executors.newFixedThreadPool()  → 无界队列！");
            System.out.println("  2. ✓ new ThreadPoolExecutor(");
            System.out.println("         coreSize, maxSize, keepAlive, TimeUnit,");
            System.out.println("         new ArrayBlockingQueue<>(1000),  // 有界队列！");
            System.out.println("         new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略");
            System.out.println("       )");
            System.out.println("  3. 监控队列大小，超过阈值告警");
            System.out.println("  4. 《阿里巴巴Java开发手册》明确禁止使用 Executors 创建线程池");
        }
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  OOM 真实案例排查实战");
        System.out.println("========================================\n");

        System.out.println("  参数: -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError");
        System.out.println("        -XX:HeapDumpPath=./oom_dump.hprof -XX:+PrintGCDetails\n");
        System.out.println("  每次只取消注释一个场景来运行\n");

        // ★ 每次只取消注释一个来运行 ★
//        cacheOOM();          // 场景一：缓存无限增长
//         bigQueryOOM();       // 场景二：大查询一次加载
         taskQueueOOM();      // 场景三：任务队列无界堆积

        System.out.println();
        System.out.println("========================================");
        System.out.println("  OOM 排查 SOP（标准流程）");
        System.out.println("========================================\n");

        System.out.println("  Step 1: 收集信息");
        System.out.println("    - 确认 -XX:+HeapDumpOnOutOfMemoryError 已开启");
        System.out.println("    - 收集 GC 日志、heap dump、异常堆栈\n");

        System.out.println("  Step 2: 分析 dump");
        System.out.println("    - MAT → Open Heap Dump → Leak Suspects Report");
        System.out.println("    - Dominator Tree → 找占内存最大的对象");
        System.out.println("    - 右键 → Path to GC Roots → 找到谁持有引用\n");

        System.out.println("  Step 3: 定位代码");
        System.out.println("    - 根据 GC Roots 路径找到代码位置");
        System.out.println("    - 判断是「泄漏」还是「确实不够」\n");

        System.out.println("  Step 4: 修复");
        System.out.println("    ┌───────────────────┬──────────────────────────────┐");
        System.out.println("    │ 问题               │ 修复                          │");
        System.out.println("    ├───────────────────┼──────────────────────────────┤");
        System.out.println("    │ 缓存无限增长       │ 用 Caffeine + maxSize + TTL   │");
        System.out.println("    │ 大查询全量加载     │ 分页 / 流式处理                │");
        System.out.println("    │ 队列无界堆积       │ 有界队列 + 拒绝策略            │");
        System.out.println("    │ 连接池泄漏         │ try-with-resources 确保关闭    │");
        System.out.println("    │ 确实内存不足       │ 加大 -Xmx / 优化数据结构       │");
        System.out.println("    └───────────────────┴──────────────────────────────┘");
        System.out.println("========================================");
    }

    private static String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * D3 全部知识点：
 *   1. JvmMonitorCommandDemo      — 监控命令实战
 *   2. GCLogAnalysisDemo          — GC 日志分析
 *   3. JvmParameterTuningDemo     — JVM 参数调优
 *   4. OOMTroubleshootingDemo     — OOM 案例排查（本类）
 *
 * W01-JVM 完整路线：
 *   D1 → 内存模型（五大区域、对象布局、OOM类型、常量池）
 *   D2 → GC（算法、收集器、GC Roots、引用类型、终结机制、安全点）
 *   D3 → 调优（监控命令、GC日志、参数调优、OOM排查）
 */

/*
*  OOMTroubleshootingDemo 运行指南：

  场景1: cacheOOM() (默认开启)
  VM参数: -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails
  直接运行即可，程序会不断往 HashMap 塞数据直到 OOM。

  场景2: bigQueryOOM()
  需要在代码中注释掉 cacheOOM()，取消注释 bigQueryOOM()，同样的 VM 参数运行。

  场景3: taskQueueOOM()
  注释掉前面的，取消注释 taskQueueOOM()，同样参数运行。

  每跑完一个场景，把日志保存到 docs 目录发给我分析。先跑 cacheOOM 吧。
  * */