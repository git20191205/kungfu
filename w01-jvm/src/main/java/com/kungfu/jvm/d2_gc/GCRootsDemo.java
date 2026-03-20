package com.kungfu.jvm.d2_gc;

/**
 * 【Demo】GC Roots 与可达性分析演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 演示 JVM 如何通过「可达性分析」判断对象是否存活：
 * 从 GC Roots 出发，沿引用链遍历，不可达的对象即为垃圾。
 *
 * <h3>GC Roots 包括哪些？（面试必背）</h3>
 * <ol>
 *   <li>虚拟机栈中引用的对象（局部变量）</li>
 *   <li>方法区中静态变量引用的对象</li>
 *   <li>方法区中常量引用的对象</li>
 *   <li>本地方法栈中 JNI 引用的对象</li>
 *   <li>被同步锁（synchronized）持有的对象</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * <pre>
 *   java -Xms20m -Xmx20m -XX:+PrintGCDetails -XX:+UseSerialGC
 *        com.kungfu.jvm.d2_gc.GCRootsDemo
 * </pre>
 *
 * @author kungfu
 * @since D2 - GC算法与垃圾回收器
 */
public class GCRootsDemo {

    // ====== GC Root 类型 2：静态变量引用 ======
    // 静态变量属于类，类由方法区中的 ClassLoader 持有 → 是 GC Root
    private static Object staticRef;

    // ====== GC Root 类型 3：常量引用 ======
    private static final String CONSTANT = "我是常量，方法区中的常量引用也是 GC Root";

    private static final int _1MB = 1024 * 1024;

    /**
     * 演示 1：局部变量作为 GC Root
     * 局部变量在栈帧中，是 GC Root → 引用的对象不会被回收
     * 局部变量失效（出作用域）→ 对象变为不可达 → 可被回收
     */
    private static void testLocalVarAsGCRoot() {
        System.out.println("--- 演示 1：局部变量作为 GC Root ---\n");

        {
            // obj 是局部变量（栈帧中），引用一个 2MB 对象
            // 此时 obj 是 GC Root → 对象不可回收
            byte[] obj = new byte[2 * _1MB];
            System.out.println("  [作用域内] obj 引用 2MB 对象，obj 是 GC Root → 对象存活");
            System.gc();
            System.out.println("  [GC 后] 对象仍存活（obj 还在栈帧中）");
        }

        // 出了作用域，但 obj 的槽位可能还没被复用
        // 此时 GC 不一定能回收（取决于栈帧槽位是否被覆盖）
        System.out.println("\n  [出作用域] obj 超出作用域");
        System.gc();
        System.out.println("  [GC 后] 注意：槽位未被复用时，对象可能仍未被回收！");

        // 用一个新变量覆盖栈帧中 obj 原来的槽位
        int placeholder = 0;
        System.gc();
        System.out.println("  [覆盖槽位后 GC] 现在 obj 的槽位被 placeholder 覆盖 → 对象可被回收");
        System.out.println("  → 这就是为什么有时需要 obj = null 来帮助 GC\n");
    }

    /**
     * 演示 2：静态变量作为 GC Root
     */
    private static void testStaticVarAsGCRoot() {
        System.out.println("--- 演示 2：静态变量作为 GC Root ---\n");

        // 将对象赋给静态变量 → 变为 GC Root 可达
        staticRef = new byte[2 * _1MB];
        System.out.println("  staticRef 指向 2MB 对象 → GC Root 可达 → 不会被回收");
        System.gc();

        // 断开引用
        staticRef = null;
        System.out.println("  staticRef = null → 对象不可达 → 可被回收");
        System.gc();
        System.out.println("  [GC 后] 对象已被回收\n");
    }

    /**
     * 演示 3：引用链断裂 → 对象不可达
     * A → B → C，如果断开 A→B 的引用，B 和 C 都变成不可达
     */
    private static void testReferenceChain() {
        System.out.println("--- 演示 3：引用链断裂 ---\n");

        // 构建引用链：node1 → node2 → node3
        int[][] node1 = new int[1][];
        int[][] node2 = new int[1][];
        int[] node3 = new int[256 * 1024]; // 1MB

        node1[0] = new int[]{1};  // node1 → 某数据
        node2[0] = node3;         // node2 → node3

        System.out.println("  引用链: 栈变量 node1, node2 → 各自的对象");
        System.out.println("  所有对象通过栈变量（GC Root）可达 → 都存活");

        // 断开 node2 的引用
        node2 = null;
        System.out.println("\n  node2 = null → node2 原来指向的对象不可达");
        System.out.println("  但 node3 仍被栈变量直接引用 → 仍然存活");

        System.gc();
        System.out.println("  [GC 后] node3 数据仍然可访问: " + node3.length + " 个 int\n");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  GC Roots 与可达性分析演示");
        System.out.println("========================================\n");

        testLocalVarAsGCRoot();
        testStaticVarAsGCRoot();
        testReferenceChain();

        System.out.println("========================================");
        System.out.println("  GC Roots 速记（面试必背）：");
        System.out.println("  1. 虚拟机栈局部变量引用的对象");
        System.out.println("  2. 方法区静态变量引用的对象");
        System.out.println("  3. 方法区常量引用的对象");
        System.out.println("  4. 本地方法栈 JNI 引用的对象");
        System.out.println("  5. synchronized 持有的对象");
        System.out.println();
        System.out.println("  判断对象存活的两种方式：");
        System.out.println("  × 引用计数法 → 有循环引用问题，JVM 不用");
        System.out.println("  ✓ 可达性分析 → 从 GC Roots 沿引用链遍历");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * D2 学完了三件事：
 * 1. 三种 GC 算法（标记-清除 / 复制 / 标记-整理）
 * 2. 七种垃圾回收器及其搭配
 * 3. GC Roots 与可达性分析
 *
 * 下一步 D3 → JVM 调优实战
 * → 怎么看 GC 日志？
 * → 怎么用 jstat、jmap、jstack 排查问题？
 * → 常见的 JVM 参数怎么调？
 */
