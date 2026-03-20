package com.kungfu.jvm.d1_memory_model;

/**
 * 【Demo】JVM 内存区域全景演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 通过简单的代码，直观感受 JVM 五大内存区域各自存放的内容：
 * <ul>
 *   <li>堆（Heap）         — 对象实例</li>
 *   <li>虚拟机栈（VM Stack） — 局部变量、方法调用帧</li>
 *   <li>方法区（Method Area）— 类信息、常量、静态变量</li>
 *   <li>本地方法栈（Native） — native 方法调用</li>
 *   <li>程序计数器（PC）     — 当前线程执行的字节码行号</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * 理解内存区域是排查 OOM、调优 GC 的基础。只有知道对象在哪里分配、何时回收，
 * 才能在生产环境中精准定位问题。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可，建议加上 JVM 参数观察效果：
 * <pre>
 *   java -Xms20m -Xmx20m -XX:+PrintGCDetails
 *        -XX:+PrintCommandLineFlags
 *        com.kungfu.jvm.d1_memory_model.MemoryAreaDemo
 * </pre>
 *
 * @author kungfu
 * @since D1 - JVM内存模型
 */
public class MemoryAreaDemo {

    // ==================== 方法区（Method Area） ====================
    // 静态变量存储在方法区（JDK 8+ 即元空间 Metaspace）
    // 类信息、常量池也在这里
    private static final String CONSTANT = "我是常量，存在方法区的运行时常量池";
    private static int staticCounter = 0;

    // ==================== 堆（Heap） ====================
    // 成员变量跟随对象实例分配在堆上
    private String instanceField = "我是实例字段，跟随对象分配在堆上";

    /**
     * 演示虚拟机栈（VM Stack）：
     * 每调用一次方法，就会在当前线程的栈上创建一个栈帧（Stack Frame），
     * 栈帧包含：局部变量表、操作数栈、动态链接、方法返回地址。
     */
    private static void demonstrateStack(int depth) {
        // localVar 是局部变量，存在栈帧的局部变量表中
        int localVar = depth;
        System.out.println("  [虚拟机栈] 第 " + localVar + " 层栈帧，局部变量 localVar 存在栈帧中");

        if (depth < 3) {
            // 递归调用 → 产生新的栈帧，直观体现「栈」的含义
            demonstrateStack(depth + 1);
        }
    }

    /**
     * 演示本地方法栈（Native Method Stack）：
     * 当 Java 代码调用 native 方法时，使用的是本地方法栈。
     * System.currentTimeMillis() 就是一个典型的 native 方法。
     */
    private static void demonstrateNativeMethodStack() {
        // currentTimeMillis() 是 native 方法，底层由 C/C++ 实现
        // 调用时会在本地方法栈中创建栈帧
        long timestamp = System.currentTimeMillis();
        System.out.println("  [本地方法栈] System.currentTimeMillis() 是 native 方法，返回: " + timestamp);

        // hashCode() 默认也是 native 方法
        int hash = new Object().hashCode();
        System.out.println("  [本地方法栈] Object.hashCode() 也是 native 方法，返回: " + hash);
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  JVM 内存区域全景演示");
        System.out.println("========================================\n");

        // ---------- 1. 堆（Heap） ----------
        System.out.println("【1. 堆 Heap】— 存放对象实例");
        // new 出来的对象实例分配在堆上，demo 只是引用（存在栈上）
        MemoryAreaDemo demo = new MemoryAreaDemo();
        System.out.println("  [堆] 创建对象 demo，实例字段: " + demo.instanceField);

        // 数组也是对象，分配在堆上
        int[] array = new int[]{1, 2, 3};
        System.out.println("  [堆] 数组对象也在堆上，长度: " + array.length);
        System.out.println();

        // ---------- 2. 虚拟机栈（VM Stack） ----------
        System.out.println("【2. 虚拟机栈 VM Stack】— 存放栈帧（局部变量表、操作数栈等）");
        demonstrateStack(1);
        System.out.println();

        // ---------- 3. 方法区（Method Area / Metaspace） ----------
        System.out.println("【3. 方法区 Method Area】— 存放类信息、常量、静态变量");
        System.out.println("  [方法区] 静态常量 CONSTANT = " + CONSTANT);
        staticCounter++;
        System.out.println("  [方法区] 静态变量 staticCounter = " + staticCounter);
        // 类信息也在方法区
        System.out.println("  [方法区] 类信息: " + MemoryAreaDemo.class.getName());
        System.out.println();

        // ---------- 4. 本地方法栈（Native Method Stack） ----------
        System.out.println("【4. 本地方法栈 Native Method Stack】— 为 native 方法服务");
        demonstrateNativeMethodStack();
        System.out.println();

        // ---------- 5. 程序计数器（PC Register） ----------
        System.out.println("【5. 程序计数器 PC Register】— 记录当前线程执行的字节码行号");
        System.out.println("  [PC] 程序计数器是线程私有的，无法直接打印");
        System.out.println("  [PC] 每个线程都有独立的 PC，用于线程切换后恢复执行位置");
        System.out.println("  [PC] 执行 Java 方法时记录字节码地址，执行 native 方法时为空（Undefined）");
        System.out.println();

        // ---------- 总结 ----------
        System.out.println("========================================");
        System.out.println("  内存区域速记：");
        System.out.println("  线程私有：虚拟机栈、本地方法栈、程序计数器");
        System.out.println("  线程共享：堆、方法区");
        System.out.println("  堆是 GC 的主战场，方法区回收的是类卸载");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 理解了内存区域的划分后，下一步要关注的是：
 * → 对象在堆中是如何分配的？（见 ObjectAllocationDemo.java）
 * → Eden 区、Survivor 区、老年代各自的角色是什么？
 * → 什么样的对象会直接进入老年代？
 */
