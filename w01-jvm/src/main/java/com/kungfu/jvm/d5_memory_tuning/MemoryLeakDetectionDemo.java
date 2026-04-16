package com.kungfu.jvm.d5_memory_tuning;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 【Demo】内存泄漏检测实战 — 3 种典型泄漏模式
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>模式一：static 集合只增不删 — 最常见的泄漏模式</li>
 *   <li>模式二：监听器/回调注册不注销 — EventListener 未 remove</li>
 *   <li>模式三：ThreadLocal 使用后未 remove — 线程池场景下的隐蔽泄漏</li>
 *   <li>每种模式展示：泄漏特征 → 检测方法 → 修复方案</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："你遇到过内存泄漏吗？怎么排查的？"
 * 内存泄漏 ≠ OOM，泄漏是渐进过程，OOM 是最终结果
 *
 * <h3>运行方式</h3>
 * <pre>
 * -Xms50m -Xmx50m -XX:+PrintGCDetails
 * </pre>
 *
 * @author kungfu
 * @since D5 - 内存调优实战
 */
public class MemoryLeakDetectionDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  内存泄漏检测实战");
        System.out.println("========================================\n");

        showLeakOverview();

        // 模式一：static 集合泄漏
        staticCollectionLeak();

        // 模式二：监听器未注销泄漏
        listenerLeak();

        // 模式三：ThreadLocal 泄漏
        threadLocalLeak();

        // 泄漏检测 SOP
        showDetectionSOP();
    }

    // =============================================================
    // 总览：内存泄漏 vs 内存溢出
    // =============================================================
    private static void showLeakOverview() {
        System.out.println("=== 内存泄漏 vs 内存溢出 ===\n");

        System.out.println("  ┌──────────────┬──────────────────────┬──────────────────────┐");
        System.out.println("  │              │ 内存泄漏 (Leak)      │ 内存溢出 (OOM)       │");
        System.out.println("  ├──────────────┼──────────────────────┼──────────────────────┤");
        System.out.println("  │ 定义         │ 对象不再使用但无法回收│ 申请内存超过最大限制 │");
        System.out.println("  │ 特征         │ 堆使用率缓慢上升     │ 突然崩溃             │");
        System.out.println("  │ 发现时机     │ 运行数小时/天后      │ 立即或短时间内       │");
        System.out.println("  │ 因果关系     │ 泄漏是因             │ OOM 是果             │");
        System.out.println("  └──────────────┴──────────────────────┴──────────────────────┘\n");
    }

    // =============================================================
    // 模式一：static 集合只增不删
    // 真实案例：本地缓存用 static HashMap，只 put 不 remove
    // =============================================================
    /** 模拟有泄漏的缓存 */
    private static final Map<String, byte[]> LEAKY_CACHE = new HashMap<>();
    /** 修复方案：使用 WeakHashMap */
    private static final Map<String, byte[]> SAFE_CACHE = new WeakHashMap<>();

    private static void staticCollectionLeak() {
        System.out.println("--- 模式一：static 集合只增不删 ---\n");
        System.out.println("  场景：本地 HashMap 缓存用户会话数据，只 put 不 remove");
        System.out.println("  典型代码：SessionCache.put(sessionId, data)\n");

        // 演示泄漏过程
        System.out.println("  ★ 模拟泄漏过程（往 static HashMap 不断写入）：");
        for (int round = 1; round <= 5; round++) {
            for (int i = 0; i < 200; i++) {
                String key = "session_" + round + "_" + i;
                LEAKY_CACHE.put(key, new byte[512]); // 0.5KB
            }
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            System.out.println("    第 " + round + " 轮: 缓存 " + LEAKY_CACHE.size()
                    + " 条, 堆使用 " + formatMB(heap.getUsed()));
        }
        System.out.println();

        // 手动 GC 后观察
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        MemoryUsage afterGC = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.println("  手动 GC 后: 缓存仍有 " + LEAKY_CACHE.size()
                + " 条, 堆使用 " + formatMB(afterGC.getUsed()));
        System.out.println("  → GC 无法回收！因为 static 字段是 GC Root，持有所有 entry\n");

        // 泄漏特征
        System.out.println("  泄漏特征：");
        System.out.println("    1. jmap -histo 连续执行两次，HashMap$Node 数量只增不减");
        System.out.println("    2. Full GC 后堆使用率不回落");
        System.out.println("    3. MAT → Dominator Tree → HashMap 占比越来越高\n");

        // 修复方案
        System.out.println("  修复方案：");
        System.out.println("    方案1: WeakHashMap — 当 key 无强引用时自动回收");
        System.out.println("    方案2: Caffeine Cache — maximumSize + expireAfterWrite");
        System.out.println("    方案3: Redis — 缓存放到 JVM 外\n");

        // 演示 WeakHashMap 对比
        System.out.println("  ★ WeakHashMap 对比演示：");
        for (int i = 0; i < 1000; i++) {
            String key = new String("weak_" + i); // 必须用 new String，不能用字面量
            SAFE_CACHE.put(key, new byte[512]);
        }
        System.out.println("    写入 1000 条后: WeakHashMap size = " + SAFE_CACHE.size());
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        System.out.println("    GC 后: WeakHashMap size = " + SAFE_CACHE.size()
                + " ← key 无强引用时自动回收");
        System.out.println();

        // 清理
        LEAKY_CACHE.clear();
    }

    // =============================================================
    // 模式二：监听器/回调注册不注销
    // 真实案例：注册 EventListener 但忘记在 destroy 时移除
    // =============================================================
    /** 模拟事件中心 */
    private static final List<EventListener> LISTENERS = new ArrayList<>();

    interface EventListener {
        void onEvent(String event);
    }

    /** 模拟一个持有大量数据的组件 */
    static class HeavyComponent implements EventListener {
        private final String name;
        private final byte[] data = new byte[50 * 1024]; // 50KB

        HeavyComponent(String name) {
            this.name = name;
        }

        @Override
        public void onEvent(String event) {
            // 处理事件
        }

        /** 只注册，没有对应的 unregister！ */
        void register() {
            LISTENERS.add(this);
        }
    }

    private static void listenerLeak() {
        System.out.println("--- 模式二：监听器注册不注销 ---\n");
        System.out.println("  场景：组件注册 EventListener，销毁时忘记 removeListener");
        System.out.println("  典型代码：eventBus.register(this)  // 但 destroy() 没有 unregister\n");

        System.out.println("  ★ 模拟泄漏过程：");
        for (int i = 0; i < 5; i++) {
            // 模拟创建和 "销毁" 组件，但 listener 引用还在
            for (int j = 0; j < 20; j++) {
                HeavyComponent comp = new HeavyComponent("comp_" + i + "_" + j);
                comp.register();  // 注册了
                // comp 变量离开作用域，但 LISTENERS 仍持有引用！
            }
            System.gc();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            System.out.println("    第 " + (i + 1) + " 批: 注册 " + LISTENERS.size()
                    + " 个监听器, 堆使用 " + formatMB(heap.getUsed()));
        }
        System.out.println();

        System.out.println("  → 虽然组件 \"销毁\" 了，但 LISTENERS 列表仍持有引用");
        System.out.println("    GC 无法回收这些 HeavyComponent（每个 50KB）\n");

        System.out.println("  泄漏特征：");
        System.out.println("    1. LISTENERS 列表不断增长");
        System.out.println("    2. MAT → Path to GC Roots → 发现 static List 持有引用");
        System.out.println("    3. 组件实例数与创建次数成正比（应该被回收但没有）\n");

        System.out.println("  修复方案：");
        System.out.println("    1. 在 @PreDestroy / close() / finally 中调用 unregister()");
        System.out.println("    2. 使用 WeakReference 包装 listener");
        System.out.println("    3. Spring 的 @EventListener 自动管理生命周期\n");

        // 清理
        LISTENERS.clear();
    }

    // =============================================================
    // 模式三：ThreadLocal 使用后未 remove
    // 真实案例：线程池中使用 ThreadLocal，任务结束不 remove
    // =============================================================
    private static final ThreadLocal<byte[]> CONTEXT = new ThreadLocal<>();

    private static void threadLocalLeak() {
        System.out.println("--- 模式三：ThreadLocal 使用后未 remove ---\n");
        System.out.println("  场景：线程池 + ThreadLocal，任务结束不 remove");
        System.out.println("  典型代码：threadLocal.set(data)  // 但 finally 没有 remove\n");

        System.out.println("  ★ 为什么线程池 + ThreadLocal 会泄漏？");
        System.out.println("  ┌────────────────────────────────────────────────────────┐");
        System.out.println("  │ 线程池的线程不会被销毁 → Thread 对象一直存活           │");
        System.out.println("  │ Thread 持有 ThreadLocalMap → Map 中的 Entry 不会回收  │");
        System.out.println("  │ 即使 ThreadLocal 变量本身被 GC（WeakReference），    │");
        System.out.println("  │ value 仍然存活 → 造成 value 泄漏                      │");
        System.out.println("  └────────────────────────────────────────────────────────┘\n");

        System.out.println("  模拟：在主线程中演示 set 后不 remove 的问题\n");

        // 错误用法
        System.out.println("  ❌ 错误用法（不 remove）：");
        CONTEXT.set(new byte[10 * 1024]); // 10KB
        System.out.println("    ThreadLocal.set(10KB data)");
        System.out.println("    任务执行中...");
        System.out.println("    任务结束，但 ThreadLocal value 仍被 Thread 持有");
        // 故意不 remove
        System.out.println("    → 如果是线程池，这 10KB 会一直挂在线程上，直到线程被销毁\n");

        // 正确用法
        System.out.println("  ✓ 正确用法（try-finally remove）：");
        System.out.println("    try {");
        System.out.println("        CONTEXT.set(new byte[10 * 1024]);");
        System.out.println("        // 业务逻辑...");
        System.out.println("    } finally {");
        System.out.println("        CONTEXT.remove();  // ★ 必须 remove！");
        System.out.println("    }");
        CONTEXT.remove(); // 清理
        System.out.println();

        System.out.println("  泄漏特征：");
        System.out.println("    1. 堆中大量 ThreadLocal$ThreadLocalMap$Entry 对象");
        System.out.println("    2. Entry 的 referent（key）为 null，但 value 不为 null");
        System.out.println("    3. MAT → Thread 对象 → threadLocals 字段 → 大量废弃 Entry\n");

        System.out.println("  修复方案：");
        System.out.println("    1. ★ 永远在 finally 中调用 ThreadLocal.remove()");
        System.out.println("    2. 使用 TransmittableThreadLocal（阿里开源，线程池场景增强）");
        System.out.println("    3. 代码审查：grep -r 'ThreadLocal.*set' 检查有无对应 remove");
        System.out.println();
    }

    // =============================================================
    // 泄漏检测 SOP
    // =============================================================
    private static void showDetectionSOP() {
        System.out.println("========================================");
        System.out.println("  内存泄漏检测 SOP");
        System.out.println("========================================\n");

        System.out.println("  Step 1: 监控发现异常");
        System.out.println("    - Grafana 堆使用率图表呈锯齿上升趋势");
        System.out.println("    - Full GC 频率逐渐增加（每天多一次 → 每小时多一次）\n");

        System.out.println("  Step 2: jmap -histo 对比");
        System.out.println("    - 间隔 10 分钟执行两次 jmap -histo:live <PID>");
        System.out.println("    - 对比哪些类的实例数和占用内存持续增长");
        System.out.println("    - 重点关注自定义类、HashMap$Node、byte[] 等\n");

        System.out.println("  Step 3: dump 分析");
        System.out.println("    - jmap -dump:live,format=b,file=heap.hprof <PID>");
        System.out.println("    - MAT 打开 → Leak Suspects Report → 自动定位可疑对象");
        System.out.println("    - Dominator Tree → 找内存占比最大的对象\n");

        System.out.println("  Step 4: 定位根因");
        System.out.println("    - 右键可疑对象 → Path to GC Roots → exclude weak references");
        System.out.println("    - 找到持有引用的代码位置（static 字段 / 集合 / 监听器）");
        System.out.println("    - 判断是「只增不删」还是「生命周期管理缺失」\n");

        System.out.println("  Step 5: 修复验证");
        System.out.println("    ┌───────────────────┬────────────────────────────────────┐");
        System.out.println("    │ 泄漏模式          │ 修复方案                            │");
        System.out.println("    ├───────────────────┼────────────────────────────────────┤");
        System.out.println("    │ static 集合       │ Caffeine/WeakHashMap + 容量限制     │");
        System.out.println("    │ 监听器未注销      │ @PreDestroy / close() 中 unregister │");
        System.out.println("    │ ThreadLocal       │ finally { threadLocal.remove() }    │");
        System.out.println("    │ 连接/流未关闭     │ try-with-resources 自动关闭         │");
        System.out.println("    │ 内部类持有外部类  │ 改为 static 内部类                  │");
        System.out.println("    └───────────────────┴────────────────────────────────────┘");
        System.out.println("========================================");
    }

    private static String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * D5 全部知识点：
 *   1. GCTuningMethodologyDemo    — GC 调优方法论
 *   2. MemoryLeakDetectionDemo    — 内存泄漏检测实战（本类）
 *
 * W01-JVM 完整路线：
 *   D1 → 内存模型（五大区域、对象布局、OOM类型、常量池）
 *   D2 → GC（算法、收集器、GC Roots、引用类型、终结机制、安全点）
 *   D3 → 调优工具（监控命令、GC日志、参数调优、OOM排查）
 *   D4 → 类加载（加载过程、双亲委派、打破双亲委派）
 *   D5 → 内存调优（调优方法论、内存泄漏检测）
 *   D6 → OOM排查（MAT分析、非堆OOM）
 */
