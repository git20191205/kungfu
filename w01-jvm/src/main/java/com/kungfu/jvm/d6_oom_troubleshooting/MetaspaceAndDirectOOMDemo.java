package com.kungfu.jvm.d6_oom_troubleshooting;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 【Demo】非堆 OOM 排查 — Metaspace / Direct Memory / StackOverflow
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>场景一：Metaspace OOM — 动态代理生成大量类</li>
 *   <li>场景二：Direct Memory OOM — NIO ByteBuffer.allocateDirect 泄漏</li>
 *   <li>场景三：StackOverflowError — 深递归耗尽栈空间</li>
 *   <li>每种 OOM 展示：错误特征 → 排查流程 → 修复方案</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："除了堆 OOM 你还遇到过什么 OOM？"
 * 非堆 OOM 更隐蔽，不会被 -Xmx 限制，排查思路完全不同
 *
 * <h3>运行方式</h3>
 * 每次只运行一个场景（在 main 中取消注释）：
 * <pre>
 * 场景1: -XX:MaxMetaspaceSize=30m -XX:+PrintGCDetails
 * 场景2: -XX:MaxDirectMemorySize=10m -XX:+PrintGCDetails
 * 场景3: -Xss256k（或默认参数直接运行）
 * </pre>
 *
 * @author kungfu
 * @since D6 - OOM排查实战
 */
public class MetaspaceAndDirectOOMDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  非堆 OOM 排查实战");
        System.out.println("========================================\n");

        // OOM 类型速查表
        showOOMTypeGuide();

        // ★ 每次只取消注释一个场景来运行 ★
         metaspaceOOM();        // 场景一：Metaspace OOM
//        directMemoryOOM();     // 场景二：Direct Memory OOM
//        stackOverflow();       // 场景三：StackOverflowError
    }

    // =============================================================
    // OOM 类型速查表
    // =============================================================
    private static void showOOMTypeGuide() {
        System.out.println("=== OOM 类型速查表 ===\n");

        System.out.println("  ┌──────────────────────────────────────┬───────────────────────┬──────────────────────────┐");
        System.out.println("  │ 错误信息                             │ 内存区域              │ 常见根因                  │");
        System.out.println("  ├──────────────────────────────────────┼───────────────────────┼──────────────────────────┤");
        System.out.println("  │ java.lang.OutOfMemoryError:          │                       │                          │");
        System.out.println("  │   Java heap space                    │ 堆                    │ 对象泄漏/堆太小          │");
        System.out.println("  │   GC overhead limit exceeded         │ 堆                    │ GC 回收率 < 2%           │");
        System.out.println("  │   Metaspace                          │ 元空间（本地内存）    │ 类太多/ClassLoader泄漏   │");
        System.out.println("  │   Direct buffer memory               │ 直接内存（本地内存）  │ NIO ByteBuffer 泄漏      │");
        System.out.println("  │   unable to create new native thread │ 操作系统              │ 线程数超过 OS 限制        │");
        System.out.println("  ├──────────────────────────────────────┼───────────────────────┼──────────────────────────┤");
        System.out.println("  │ java.lang.StackOverflowError         │ 线程栈                │ 递归过深/栈帧过大        │");
        System.out.println("  └──────────────────────────────────────┴───────────────────────┴──────────────────────────┘\n");
    }

    // =============================================================
    // 场景一：Metaspace OOM
    // 真实案例：CGLIB/Groovy/JSP 动态生成大量代理类
    // =============================================================

    private static void metaspaceOOM() {
        System.out.println("--- 场景一：Metaspace OOM ---\n");
        System.out.println("  模拟：JDK 动态代理不断生成新类");
        System.out.println("  典型原因：CGLIB 增强 / Groovy 脚本编译 / JSP 热加载");
        System.out.println("  参数: -XX:MaxMetaspaceSize=30m\n");

        List<Object> proxies = new ArrayList<>();
        int count = 0;

        try {
            while (true) {
                // 每次创建一个新的 ClassLoader + 代理类，产生新的 Class 对象
                ClassLoader cl = new ClassLoader(MetaspaceAndDirectOOMDemo.class.getClassLoader()) {};
                Object proxy = Proxy.newProxyInstance(
                        cl,
                        new Class<?>[]{Runnable.class}, // 用 public 接口，避免跨 ClassLoader 访问问题
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object p, Method m, Object[] a) {
                                return null;
                            }
                        }
                );
                proxies.add(proxy); // 持有引用防止被 GC
                count++;

                if (count % 5000 == 0) {
                    System.out.println("  已生成 " + count + " 个代理类");
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ OOM！生成了 " + count + " 个代理类后 Metaspace 耗尽");
            System.out.println("  错误: " + e.getMessage());
            System.out.println();
            System.out.println("  排查流程：");
            System.out.println("  1. 看错误信息 → 包含 'Metaspace' → 是类相关问题");
            System.out.println("  2. jcmd <PID> GC.class_stats → 查看类统计信息");
            System.out.println("  3. jmap -clstats <PID> → 查看 ClassLoader 统计");
            System.out.println("  4. 找到加载类最多的 ClassLoader → 定位代码");
            System.out.println();
            System.out.println("  修复方案：");
            System.out.println("  1. 增大 Metaspace: -XX:MaxMetaspaceSize=256m");
            System.out.println("  2. 排查动态类生成（CGLIB、反射、Groovy、JSP）");
            System.out.println("  3. 检查 ClassLoader 是否正确释放（Tomcat 热部署场景）");
            System.out.println("  4. Spring AOP 代理类泄漏：检查 @Scope(proxyMode=ScopedProxyMode.TARGET_CLASS)");
            System.out.println();
            System.out.println("  ★ 面试加分项：");
            System.out.println("    JDK 8 把永久代(PermGen)替换为元空间(Metaspace)");
            System.out.println("    区别：PermGen 在堆内，Metaspace 在本地内存");
            System.out.println("    默认 Metaspace 无上限 → 生产必须设 -XX:MaxMetaspaceSize");
        }
    }

    // =============================================================
    // 场景二：Direct Memory OOM
    // 真实案例：Netty / NIO 直接内存分配后未释放
    // =============================================================
    private static void directMemoryOOM() {
        System.out.println("--- 场景二：Direct Memory OOM ---\n");
        System.out.println("  模拟：NIO ByteBuffer.allocateDirect() 不断分配直接内存");
        System.out.println("  典型原因：Netty ByteBuf 泄漏 / NIO Channel 未关闭");
        System.out.println("  参数: -XX:MaxDirectMemorySize=10m\n");

        List<ByteBuffer> buffers = new ArrayList<>();
        int count = 0;
        long totalAllocated = 0;

        try {
            while (true) {
                // 分配 1MB 直接内存
                ByteBuffer buf = ByteBuffer.allocateDirect(1024 * 1024);
                buffers.add(buf); // 持有引用，模拟泄漏
                count++;
                totalAllocated += 1024 * 1024;

                System.out.println("  分配第 " + count + " 块直接内存，累计 "
                        + formatMB(totalAllocated));
            }
        } catch (OutOfMemoryError e) {
            System.out.println("\n  ★ OOM！分配了 " + count + " 块（" + formatMB(totalAllocated)
                    + "）直接内存后耗尽");
            System.out.println("  错误: " + e.getMessage());
            System.out.println();
            System.out.println("  排查流程：");
            System.out.println("  1. 看错误信息 → 包含 'Direct buffer memory'");
            System.out.println("  2. 堆 dump 中看不到问题 → 因为直接内存不在堆中");
            System.out.println("  3. 检查 NIO/Netty 相关代码的 ByteBuffer 是否释放");
            System.out.println("  4. Netty 场景: 开启 -Dio.netty.leakDetection.level=PARANOID");
            System.out.println();
            System.out.println("  修复方案：");
            System.out.println("  1. 确保 ByteBuffer 用完后释放: ((DirectBuffer) buf).cleaner().clean()");
            System.out.println("  2. 设置上限: -XX:MaxDirectMemorySize=256m");
            System.out.println("  3. Netty: 使用 ReferenceCountUtil.release(buf) 或 try-finally");
            System.out.println("  4. 监控: MXBean BufferPoolMXBean 获取直接内存使用量");
            System.out.println();
            System.out.println("  ★ 面试加分项：");
            System.out.println("    - 直接内存不受 -Xmx 限制，受 -XX:MaxDirectMemorySize 限制");
            System.out.println("    - 直接内存的 GC 回收依赖 Cleaner 机制（PhantomReference）");
            System.out.println("    - 如果 Full GC 不频繁，Cleaner 可能来不及执行 → 泄漏");
        }
    }

    // =============================================================
    // 场景三：StackOverflowError
    // 真实案例：无限递归 / 非常深的调用链
    // =============================================================
    private static int recursionDepth = 0;

    private static void stackOverflow() {
        System.out.println("--- 场景三：StackOverflowError ---\n");
        System.out.println("  模拟：无限递归调用");
        System.out.println("  典型原因：递归缺少终止条件 / 循环依赖调用\n");

        try {
            infiniteRecursion();
        } catch (StackOverflowError e) {
            System.out.println("\n  ★ StackOverflow！递归深度达到 " + recursionDepth + " 层");
            System.out.println("  错误: " + e.getClass().getSimpleName());
            System.out.println();
            System.out.println("  排查流程：");
            System.out.println("  1. 看异常堆栈 → 相同方法反复出现 → 递归");
            System.out.println("  2. 检查递归终止条件是否正确");
            System.out.println("  3. 计算递归深度是否合理（默认栈大小约 512KB~1MB）\n");

            System.out.println("  每个栈帧的大小取决于：");
            System.out.println("    - 局部变量数量和类型");
            System.out.println("    - 操作数栈深度");
            System.out.println("    - 大约 128~256 字节/帧\n");

            System.out.println("  修复方案：");
            System.out.println("  1. 修复递归终止条件");
            System.out.println("  2. 递归改迭代（用显式栈代替调用栈）");
            System.out.println("  3. 调大栈空间: -Xss512k（不推荐，只是治标）");
            System.out.println("  4. 尾递归优化（Java 不原生支持，需手动改写）\n");

            System.out.println("  ★ 面试加分项：");
            System.out.println("    -Xss 影响每个线程的栈大小，不是总栈大小");
            System.out.println("    -Xss 越大 → 每个线程占用内存越多 → 可创建线程数越少");
            System.out.println("    默认 -Xss: Linux 64位 = 1MB, Windows = 320KB~1MB");
        }
    }

    private static void infiniteRecursion() {
        recursionDepth++;
        if (recursionDepth % 1000 == 0) {
            System.out.println("  递归深度: " + recursionDepth);
        }
        infiniteRecursion(); // 无终止条件
    }

    private static String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}

/*
 * 【知识串联】
 * D6 全部知识点：
 *   1. HeapDumpAnalysisDemo        — MAT 堆转储分析方法论
 *   2. MetaspaceAndDirectOOMDemo   — 非堆 OOM 排查（本类）
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
