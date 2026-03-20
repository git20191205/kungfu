package com.kungfu.jvm.d1_memory_model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 【Demo】各内存区域 OOM 实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 演示四种常见的 OOM / SOF 错误，对应不同的内存区域：
 * <ol>
 *   <li>堆溢出 — OutOfMemoryError: Java heap space</li>
 *   <li>栈溢出 — StackOverflowError</li>
 *   <li>元空间溢出 — OutOfMemoryError: Metaspace</li>
 *   <li>直接内存溢出 — OutOfMemoryError: Direct buffer memory</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试必问："说说你遇到过哪些 OOM？怎么排查？"
 * 每种 OOM 对应不同的内存区域，排查方向完全不同。
 *
 * <h3>运行方式</h3>
 * 每次只运行一种 OOM（在 main 方法中取消注释对应的方法）：
 * <pre>
 * 堆溢出:     java -Xms10m -Xmx10m -XX:+HeapDumpOnOutOfMemoryError OOMDemo
 * 栈溢出:     java -Xss256k OOMDemo
 * 元空间溢出:  java -XX:MaxMetaspaceSize=10m OOMDemo
 * 直接内存:    java -XX:MaxDirectMemorySize=5m OOMDemo
 * </pre>
 *
 * @author kungfu
 * @since D1 - JVM内存模型
 */
public class OOMDemo {

    // =============================================================
    // 一、堆溢出 — OutOfMemoryError: Java heap space
    // =============================================================
    /**
     * 场景：不断创建对象且不释放引用
     * 运行参数：-Xms10m -Xmx10m -XX:+HeapDumpOnOutOfMemoryError
     *
     * 线上常见原因：
     * - 内存泄漏：对象被 static 集合持有，忘记 remove
     * - 大查询：一次查了百万行数据到内存
     * - 缓存无限增长：本地缓存没有淘汰策略
     */
    private static void heapOOM() {
        System.out.println("--- 堆溢出演示 ---");
        System.out.println("参数: -Xms10m -Xmx10m -XX:+HeapDumpOnOutOfMemoryError\n");

        List<byte[]> list = new ArrayList<>();
        int count = 0;
        try {
            while (true) {
                list.add(new byte[1024 * 1024]); // 每次 1MB
                count++;
                System.out.println("  已分配 " + count + " MB");
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ 崩溃！" + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  已分配 " + count + " MB 后堆空间耗尽");
            System.out.println();
            System.out.println("  排查思路：");
            System.out.println("  1. -XX:+HeapDumpOnOutOfMemoryError 自动生成堆转储文件");
            System.out.println("  2. 用 MAT / VisualVM 分析 dump 文件");
            System.out.println("  3. 找到占用最大的对象 → 分析是「内存泄漏」还是「内存不足」");
            System.out.println("     内存泄漏：对象不该活但还活着 → 找到泄漏点修代码");
            System.out.println("     内存不足：对象确实都需要 → 加大堆 -Xmx 或优化数据结构");
        }
    }

    // =============================================================
    // 二、栈溢出 — StackOverflowError
    // =============================================================
    private static int stackDepth = 0;

    /**
     * 场景：递归太深或方法调用链太长
     * 运行参数：-Xss256k（减小栈大小，更快触发）
     *
     * 线上常见原因：
     * - 递归没有正确的终止条件
     * - 循环依赖导致的无限调用
     * - 框架嵌套层级太深（如拦截器链）
     */
    private static void stackOverflow() {
        System.out.println("--- 栈溢出演示 ---");
        System.out.println("参数: -Xss256k（默认 512k-1m）\n");

        try {
            recursiveCall();
        } catch (StackOverflowError e) {
            System.out.println("\n  ★ 崩溃！StackOverflowError");
            System.out.println("  递归深度达到 " + stackDepth + " 层时栈空间耗尽");
            System.out.println();
            System.out.println("  每个栈帧包含：局部变量表 + 操作数栈 + 动态链接 + 返回地址");
            System.out.println("  栈帧越大（局部变量越多），能递归的层数越少");
            System.out.println();
            System.out.println("  排查思路：");
            System.out.println("  1. 看异常堆栈 → 找到递归调用的方法");
            System.out.println("  2. 检查递归终止条件是否正确");
            System.out.println("  3. 考虑用迭代替代递归");
            System.out.println("  4. 确实需要深递归 → 加大 -Xss（但会减少可创建的线程数）");
        }
    }

    private static void recursiveCall() {
        stackDepth++;
        if (stackDepth % 100 == 0) {
            System.out.println("  递归深度: " + stackDepth);
        }
        recursiveCall(); // 无限递归
    }

    // =============================================================
    // 三、元空间溢出 — OutOfMemoryError: Metaspace
    // =============================================================
    /**
     * 场景：加载过多的类导致元空间满
     * 运行参数：-XX:MaxMetaspaceSize=10m
     *
     * 线上常见原因：
     * - 动态代理（CGLib、Javassist）生成大量类
     * - JSP 热部署产生新类
     * - OSGi / 自定义 ClassLoader 频繁加载
     *
     * 注意：这个演示需要 CGLib 等字节码库才能动态生成类，
     * 这里用简化的方式说明原理。
     */
    private static void metaspaceOOM() {
        System.out.println("--- 元空间溢出演示 ---");
        System.out.println("参数: -XX:MaxMetaspaceSize=10m\n");

        System.out.println("  元空间存储：类的结构信息（方法、字段、常量池等）");
        System.out.println("  JDK 8 前叫「永久代」(PermGen)，固定大小容易溢出");
        System.out.println("  JDK 8 改为「元空间」(Metaspace)，默认用本地内存不设上限");
        System.out.println();
        System.out.println("  触发方式：动态生成大量类（如 CGLib 代理）");
        System.out.println("  → Spring AOP 就是用 CGLib/JDK Proxy 动态生成代理类");
        System.out.println("  → 每个代理类都要在元空间中存储类信息");
        System.out.println("  → 如果不断创建新的代理类，元空间会被撑满");
        System.out.println();

        // 用字符串常量池间接触发（JDK 7+ intern 的字符串在堆中，但相关类信息在元空间）
        // 真正触发需要 CGLib，这里说明原理
        System.out.println("  简化演示：用大量不同的类名模拟");
        try {
            List<Class<?>> classes = new ArrayList<>();
            int count = 0;
            // 通过不断创建新的 ClassLoader 加载类来撑满元空间
            // 简化版：直接说明原理
            System.out.println("  (完整演示需要 CGLib 依赖，这里说明排查思路)");
            System.out.println();
            System.out.println("  排查思路：");
            System.out.println("  1. 错误信息: OutOfMemoryError: Metaspace");
            System.out.println("  2. jstat -gc <pid> 查看 MC/MU（Metaspace Capacity/Used）");
            System.out.println("  3. 如果持续增长 → 有类在不断被加载");
            System.out.println("  4. -verbose:class 或 -XX:+TraceClassLoading 查看加载了哪些类");
            System.out.println("  5. 常见原因: CGLib 代理无限创建、反射 inflate 生成 accessor 类");
            System.out.println("  6. 临时解决: 加大 -XX:MaxMetaspaceSize");
            System.out.println("  7. 根本解决: 找到持续加载类的代码，加缓存或限制代理创建");
        } catch (Exception e) {
            System.out.println("  ★ " + e.getMessage());
        }
    }

    // =============================================================
    // 四、直接内存溢出 — OutOfMemoryError: Direct buffer memory
    // =============================================================
    /**
     * 场景：NIO 分配过多的直接内存
     * 运行参数：-XX:MaxDirectMemorySize=5m
     *
     * 线上常见原因：
     * - Netty 的 DirectByteBuf 分配过多
     * - NIO 文件通道的 MappedByteBuffer
     * - 手动 ByteBuffer.allocateDirect() 没有释放
     */
    private static void directMemoryOOM() {
        System.out.println("--- 直接内存溢出演示 ---");
        System.out.println("参数: -XX:MaxDirectMemorySize=5m\n");

        System.out.println("  直接内存（Direct Memory）不在 JVM 堆中，在操作系统本地内存中");
        System.out.println("  NIO 的 ByteBuffer.allocateDirect() 会分配直接内存");
        System.out.println("  优点: 减少一次内存复制（零拷贝），I/O 性能更好");
        System.out.println("  缺点: 分配/释放比堆内存慢，不受 -Xmx 控制\n");

        List<ByteBuffer> buffers = new ArrayList<>();
        int count = 0;
        try {
            while (true) {
                // 每次分配 1MB 直接内存
                ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
                buffers.add(buffer);
                count++;
                System.out.println("  已分配直接内存 " + count + " MB");
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ 崩溃！" + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  已分配 " + count + " MB 后直接内存耗尽");
            System.out.println();
            System.out.println("  排查思路：");
            System.out.println("  1. 错误信息: OutOfMemoryError: Direct buffer memory");
            System.out.println("  2. 堆内存 dump 中看不到大对象（因为不在堆中！）");
            System.out.println("  3. 这是容易误判的地方 — 堆 dump 正常但进程内存很大");
            System.out.println("  4. 用 NMT (Native Memory Tracking): -XX:NativeMemoryTracking=detail");
            System.out.println("  5. 检查 NIO / Netty 是否有直接内存泄漏");
            System.out.println("  6. 确保 DirectByteBuffer 用完后被 GC（或手动 Cleaner 释放）");
        }
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  各内存区域 OOM 实战");
        System.out.println("========================================\n");

        System.out.println("  运行方式：每次取消注释一个方法来测试");
        System.out.println("  (同时运行多个会互相干扰)\n");

        // ★ 每次只取消注释一个来运行 ★

//        heapOOM();          // 参数: -Xms10m -Xmx10m
//         stackOverflow();    // 参数: -Xss256k
//         metaspaceOOM();     // 参数: -XX:MaxMetaspaceSize=10m
         directMemoryOOM();  // 参数: -XX:MaxDirectMemorySize=5m

        System.out.println();
        System.out.println("========================================");
        System.out.println("  OOM 排查速记：");
        System.out.println("  ┌───────────────────────┬─────────────────────┬─────────────────────┐");
        System.out.println("  │ 错误                   │ 对应区域             │ 排查工具             │");
        System.out.println("  ├───────────────────────┼─────────────────────┼─────────────────────┤");
        System.out.println("  │ Java heap space       │ 堆                  │ MAT 分析 heap dump  │");
        System.out.println("  │ StackOverflowError    │ 虚拟机栈             │ 看异常堆栈找递归     │");
        System.out.println("  │ Metaspace             │ 元空间（方法区）      │ jstat + TraceClass  │");
        System.out.println("  │ Direct buffer memory  │ 直接内存（堆外）      │ NMT + 检查 NIO      │");
        System.out.println("  └───────────────────────┴─────────────────────┴─────────────────────┘");
        System.out.println();
        System.out.println("  生产环境必备参数：");
        System.out.println("  -XX:+HeapDumpOnOutOfMemoryError");
        System.out.println("  -XX:HeapDumpPath=/path/to/dump.hprof");
        System.out.println("  → OOM 时自动生成堆转储，事后分析用");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * D1 全部知识点：
 *   1. MemoryAreaDemo          — 五大内存区域
 *   2. ObjectAllocationDemo    — 对象分配策略
 *   3. ObjectMemoryLayoutDemo  — 对象内存布局（本类）
 *   4. OOMDemo                 — 各区域 OOM 实战（本类）
 *
 * 下一步 D2 → GC 算法与垃圾回收器
 */