package com.kungfu.jvm.d6_oom_troubleshooting;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【Demo】MAT 堆转储分析方法论
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>如何获取 Heap Dump（jmap / JVM 参数自动生成）</li>
 *   <li>模拟真实内存泄漏场景，生成可分析的 heap dump</li>
 *   <li>MAT 分析完整 SOP：Leak Suspects → Dominator Tree → GC Roots</li>
 *   <li>Shallow Size vs Retained Size 的区别</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你用过 MAT 吗？怎么分析 heap dump 的？"
 * MAT 是 Java 内存问题排查的终极武器，没有之一
 *
 * <h3>运行方式</h3>
 * <pre>
 * -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError
 * -XX:HeapDumpPath=./oom_dump.hprof -XX:+PrintGCDetails
 * </pre>
 * 运行后用 MAT (Eclipse Memory Analyzer) 打开 hprof 文件
 *
 * @author kungfu
 * @since D6 - OOM排查实战
 */
public class HeapDumpAnalysisDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  MAT 堆转储分析方法论");
        System.out.println("========================================\n");

        // 一、Heap Dump 获取方式
        showDumpMethods();

        // 二、MAT 分析 SOP
        showMATWorkflow();

        // 三、Shallow vs Retained Size
        showSizeComparison();

        // 四、模拟真实泄漏场景（会触发 OOM）
        simulateRealWorldLeak();
    }

    // =============================================================
    // 一、Heap Dump 获取方式
    // =============================================================
    private static void showDumpMethods() {
        System.out.println("=== 一、Heap Dump 获取方式 ===\n");

        System.out.println("  ┌─────┬────────────────────────────────────────────────────────┐");
        System.out.println("  │ 方式│ 命令 / 配置                                            │");
        System.out.println("  ├─────┼────────────────────────────────────────────────────────┤");
        System.out.println("  │  1  │ jmap -dump:live,format=b,file=heap.hprof <PID>         │");
        System.out.println("  │     │ → 手动获取，live 表示只 dump 存活对象                  │");
        System.out.println("  ├─────┼────────────────────────────────────────────────────────┤");
        System.out.println("  │  2  │ -XX:+HeapDumpOnOutOfMemoryError                        │");
        System.out.println("  │     │ -XX:HeapDumpPath=/data/logs/heap_dump.hprof             │");
        System.out.println("  │     │ → OOM 时自动生成（★ 生产必配）                        │");
        System.out.println("  ├─────┼────────────────────────────────────────────────────────┤");
        System.out.println("  │  3  │ jcmd <PID> GC.heap_dump /tmp/heap.hprof                │");
        System.out.println("  │     │ → JDK 9+ 推荐方式                                     │");
        System.out.println("  └─────┴────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ★ 生产建议：始终开启 HeapDumpOnOutOfMemoryError");
        System.out.println("    OOM 是一次性事件，错过 dump 就无法复现\n");
    }

    // =============================================================
    // 二、MAT 分析 SOP
    // =============================================================
    private static void showMATWorkflow() {
        System.out.println("=== 二、MAT 分析 SOP（标准操作流程）===\n");

        System.out.println("  Step 1: 打开 Dump");
        System.out.println("    File → Open Heap Dump → 选择 .hprof 文件");
        System.out.println("    MAT 自动计算支配树，首次打开较慢\n");

        System.out.println("  Step 2: Leak Suspects Report（自动分析）");
        System.out.println("    MAT 自动检测可疑泄漏点，生成报告");
        System.out.println("    通常能直接定位到问题代码\n");

        System.out.println("  Step 3: Dominator Tree（支配树）");
        System.out.println("    按 Retained Size 降序排列");
        System.out.println("    找到占内存最大的对象（通常就是泄漏点）");
        System.out.println("    ┌──────────────────────────────────────────────────┐");
        System.out.println("    │ Dominator Tree 示例：                            │");
        System.out.println("    │                                                  │");
        System.out.println("    │   java.util.HashMap         Retained: 25.6 MB   │");
        System.out.println("    │   ├── Node[0]               Retained: 1.2 KB    │");
        System.out.println("    │   ├── Node[1]               Retained: 1.2 KB    │");
        System.out.println("    │   └── ... (21000 nodes)                          │");
        System.out.println("    │                                                  │");
        System.out.println("    │   → 一个 HashMap 占了 25.6 MB ≈ 堆的 85%        │");
        System.out.println("    └──────────────────────────────────────────────────┘\n");

        System.out.println("  Step 4: Path to GC Roots");
        System.out.println("    右键可疑对象 → Path to GC Roots → exclude weak/soft references");
        System.out.println("    找到是谁持有了这个对象的引用");
        System.out.println("    ┌──────────────────────────────────────────────────┐");
        System.out.println("    │ GC Roots 路径示例：                              │");
        System.out.println("    │                                                  │");
        System.out.println("    │   Thread main                                    │");
        System.out.println("    │   └── static OrderService.ORDER_CACHE            │");
        System.out.println("    │       └── java.util.HashMap                      │");
        System.out.println("    │           └── Node[] table (21000 entries)        │");
        System.out.println("    │                                                  │");
        System.out.println("    │   → 根因：OrderService.ORDER_CACHE 是 static     │");
        System.out.println("    │     HashMap，只 put 不 remove                    │");
        System.out.println("    └──────────────────────────────────────────────────┘\n");

        System.out.println("  Step 5: 定位代码并修复");
        System.out.println("    根据 GC Roots 路径找到代码中的具体位置");
        System.out.println("    判断是「泄漏」（该回收没回收）还是「确实不够」（业务需要这么多内存）\n");
    }

    // =============================================================
    // 三、Shallow Size vs Retained Size
    // =============================================================
    private static void showSizeComparison() {
        System.out.println("=== 三、Shallow Size vs Retained Size ===\n");

        System.out.println("  ┌────────────────┬──────────────────────────────────────────┐");
        System.out.println("  │ 概念           │ 说明                                     │");
        System.out.println("  ├────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ Shallow Size   │ 对象本身占用的内存（不含引用的其他对象） │");
        System.out.println("  │                │ 例：HashMap 对象本身只有几十字节         │");
        System.out.println("  ├────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ Retained Size  │ 对象被 GC 后能释放的总内存               │");
        System.out.println("  │                │ 例：HashMap 被回收后，内部所有 Node +    │");
        System.out.println("  │                │ Key + Value 都会被释放 → 可能几十 MB     │");
        System.out.println("  └────────────────┴──────────────────────────────────────────┘\n");

        System.out.println("  示意图：");
        System.out.println("    HashMap (Shallow: 48B, Retained: 25MB)");
        System.out.println("    ├── Node[] table (Shallow: 64KB)");
        System.out.println("    │   ├── Node → Key(String) + Value(byte[1024])");
        System.out.println("    │   ├── Node → Key(String) + Value(byte[1024])");
        System.out.println("    │   └── ... × 21000");
        System.out.println("    └── 回收 HashMap → 释放 25MB（Retained Size）\n");

        System.out.println("  ★ 面试要点：");
        System.out.println("    - 排查泄漏时看 Retained Size，不是 Shallow Size");
        System.out.println("    - Retained Size 最大的对象最可能是泄漏根因");
        System.out.println("    - Shallow Size 主要用于理解单个对象的内存布局\n");
    }

    // =============================================================
    // 四、模拟真实泄漏场景
    // 场景：订单服务缓存订单数据，只缓存不清理
    // =============================================================

    /** 模拟订单缓存（多层引用链：Map → OrderInfo → List<Item>） */
    private static final Map<String, OrderInfo> ORDER_CACHE = new HashMap<>();

    static class OrderInfo {
        String orderId;
        String userName;
        List<OrderItem> items;
        byte[] extraData; // 模拟订单附加数据

        OrderInfo(String orderId, int itemCount) {
            this.orderId = orderId;
            this.userName = "user_" + orderId;
            this.items = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                items.add(new OrderItem("item_" + i, 99.9));
            }
            this.extraData = new byte[512]; // 0.5KB 附加数据
        }
    }

    static class OrderItem {
        String name;
        double price;
        byte[] detail = new byte[128]; // 128B 商品详情

        OrderItem(String name, double price) {
            this.name = name;
            this.price = price;
        }
    }

    private static void simulateRealWorldLeak() {
        System.out.println("=== 四、模拟真实泄漏场景 ===\n");
        System.out.println("  场景：订单服务本地缓存，只缓存不清理");
        System.out.println("  引用链：ORDER_CACHE(static) → HashMap → OrderInfo → List<OrderItem>");
        System.out.println("  每个订单约 1.5KB（含 3 个商品项）\n");
        System.out.println("  参数: -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError\n");

        int count = 0;
        try {
            while (true) {
                String orderId = "ORD_" + String.format("%06d", count);
                ORDER_CACHE.put(orderId, new OrderInfo(orderId, 3));
                count++;

                if (count % 2000 == 0) {
                    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                    System.out.println("  缓存 " + count + " 个订单，堆使用 "
                            + formatMB(heap.getUsed()) + " / " + formatMB(heap.getMax()));
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ OOM！缓存了 " + count + " 个订单后堆耗尽");
            System.out.println("  错误: " + e.getMessage());
            System.out.println();
            System.out.println("  MAT 分析此 dump 的预期结果：");
            System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
            System.out.println("  │ 1. Leak Suspects → Problem Suspect 1:                      │");
            System.out.println("  │    \"One instance of java.util.HashMap loaded by             │");
            System.out.println("  │     <system class loader> occupies 25,165,824 bytes\"        │");
            System.out.println("  │                                                             │");
            System.out.println("  │ 2. Dominator Tree:                                          │");
            System.out.println("  │    HashMap           Retained: ~25 MB (85%)                 │");
            System.out.println("  │    ├── Node[0]       → OrderInfo → List<OrderItem>          │");
            System.out.println("  │    ├── Node[1]       → OrderInfo → List<OrderItem>          │");
            System.out.println("  │    └── ... (" + count + " nodes)                            │");
            System.out.println("  │                                                             │");
            System.out.println("  │ 3. Path to GC Roots:                                        │");
            System.out.println("  │    static HeapDumpAnalysisDemo.ORDER_CACHE                  │");
            System.out.println("  │    └── HashMap → Node[] → OrderInfo → OrderItem[]           │");
            System.out.println("  │                                                             │");
            System.out.println("  │ 4. 定位：ORDER_CACHE 是 static 字段，只 put 不 remove       │");
            System.out.println("  └─────────────────────────────────────────────────────────────┘");
            System.out.println();
            System.out.println("  修复方案：");
            System.out.println("  1. 替换为 Caffeine Cache: maximumSize(10000) + expireAfterWrite(30min)");
            System.out.println("  2. 或使用 Redis 做分布式缓存");
            System.out.println("  3. 定期清理过期订单缓存");
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("  MAT 使用速查");
        System.out.println("========================================\n");
        System.out.println("  下载: https://eclipse.dev/mat/downloads.php");
        System.out.println("  打开: File → Open Heap Dump → 选 .hprof 文件");
        System.out.println("  分析: Leak Suspects → Dominator Tree → GC Roots");
        System.out.println("  技巧: 用 OQL 查询（类似 SQL）: SELECT * FROM java.util.HashMap WHERE size > 1000");
        System.out.println("========================================");
    }

    private static String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * D6 全部知识点：
 *   1. HeapDumpAnalysisDemo        — MAT 堆转储分析方法论（本类）
 *   2. MetaspaceAndDirectOOMDemo   — 非堆 OOM 排查
 *
 * W01-JVM 完整路线：
 *   D1 → 内存模型（五大区域、对象布局、OOM类型、常量池）
 *   D2 → GC（算法、收集器、GC Roots、引用类型、终结机制、安全点）
 *   D3 → 调优工具（监控命令、GC日志、参数调优、OOM排查）
 *   D4 → 类加载（加载过程、双亲委派、打破双亲委派）
 *   D5 → 内存调优（调优方法论、内存泄漏检测）
 *   D6 → OOM排查（MAT分析、非堆OOM）← 你在这里
 *
 * ★ W01-JVM 完成！下一站：W02-并发编程
 */
