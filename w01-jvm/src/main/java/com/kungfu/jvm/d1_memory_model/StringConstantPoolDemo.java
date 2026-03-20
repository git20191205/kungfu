package com.kungfu.jvm.d1_memory_model;

/**
 * 【Demo】字符串常量池演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>字符串常量池的位置（JDK 6 永久代 vs JDK 7+ 堆中）</li>
 *   <li>字面量 vs new String() 的区别</li>
 *   <li>String.intern() 的行为（JDK 6 vs JDK 7+ 的关键差异）</li>
 *   <li>"new String("abc") 创建了几个对象？" — 面试经典题</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试必考："new String("abc") 创建了几个对象？"
 * - 理解 intern() 才能理解字符串去重优化
 * - JDK 6→7 的行为变化是经典的版本差异考点
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法。
 *
 * @author kungfu
 * @since D1 - JVM内存模型
 */
public class StringConstantPoolDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  字符串常量池演示");
        System.out.println("========================================\n");

        testLiteralVsNew();
        testIntern();
        testInternDifference();
        testConcatenation();
        testHowManyObjects();

        System.out.println("========================================");
        System.out.println("  字符串常量池速记：");
        System.out.println();
        System.out.println("  位置变迁：");
        System.out.println("    JDK 6   → 永久代（PermGen）中");
        System.out.println("    JDK 7+  → 堆（Heap）中");
        System.out.println("    → 移到堆中后可以被 GC 回收，不容易 OOM");
        System.out.println();
        System.out.println("  核心规则：");
        System.out.println("    1. 字面量 \"abc\" → 自动进常量池");
        System.out.println("    2. new String(\"abc\") → 堆中新对象，不在常量池");
        System.out.println("    3. intern() → 有就返回池中的，没有就放进去");
        System.out.println("    4. 编译期能确定的拼接 → 优化为一个常量");
        System.out.println("    5. 运行期拼接（含变量）→ StringBuilder.toString() → 新对象");
        System.out.println("========================================");
    }

    // =============================================================
    // 一、字面量 vs new String()
    // =============================================================
    private static void testLiteralVsNew() {
        System.out.println("=== 一、字面量 vs new String() ===\n");

        // 字面量：直接使用常量池中的对象
        String s1 = "abc";
        String s2 = "abc";

        // new String()：在堆中创建新对象，值相同但地址不同
        String s3 = new String("abc");

        System.out.println("  String s1 = \"abc\";");
        System.out.println("  String s2 = \"abc\";");
        System.out.println("  String s3 = new String(\"abc\");\n");

        System.out.println("  s1 == s2 : " + (s1 == s2) + "   ← 都指向常量池同一个对象");
        System.out.println("  s1 == s3 : " + (s1 == s3) + "  ← s3 是堆中新对象，地址不同");
        System.out.println("  s1.equals(s3) : " + s1.equals(s3) + "   ← 值相同");
        System.out.println();

        System.out.println("  内存分布：");
        System.out.println("  ┌─────────────────────────────────┐");
        System.out.println("  │ 常量池:  \"abc\" ←── s1, s2 都指向这里 │");
        System.out.println("  │ 堆:     new String(\"abc\") ←── s3  │");
        System.out.println("  └─────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、String.intern()
    // =============================================================
    private static void testIntern() {
        System.out.println("=== 二、String.intern() ===\n");

        String s1 = new String("hello");
        String s2 = s1.intern(); // 常量池已有 "hello"（类加载时放入），返回池中引用
        String s3 = "hello";

        System.out.println("  String s1 = new String(\"hello\");");
        System.out.println("  String s2 = s1.intern();");
        System.out.println("  String s3 = \"hello\";\n");

        System.out.println("  s1 == s2 : " + (s1 == s2) + "  ← s1 是堆对象，s2 是常量池引用");
        System.out.println("  s2 == s3 : " + (s2 == s3) + "   ← 都是常量池中的 \"hello\"");
        System.out.println();

        System.out.println("  intern() 的逻辑：");
        System.out.println("    常量池已有该字符串 → 返回池中的引用");
        System.out.println("    常量池没有 → (JDK 7+) 把堆对象的引用放入池中，返回该引用");
        System.out.println("              → (JDK 6)  复制一份到永久代常量池中，返回新引用\n");
    }

    // =============================================================
    // 三、intern() 在 JDK 6 vs JDK 7+ 的关键差异
    // =============================================================
    private static void testInternDifference() {
        System.out.println("=== 三、intern() 版本差异（经典面试题）===\n");

        // 拼接生成一个常量池中不存在的新字符串
        String s1 = new String("ja") + new String("va");
        // "java" 这个字符串比较特殊，JVM 启动时 sun.misc.Version 类已经将它放入常量池
        // 所以 intern() 返回的是已有的常量池引用，不是 s1

        String s2 = new String("kun") + new String("gfu");
        // "kungfu" 之前不在常量池中
        String s3 = s2.intern();

        System.out.println("  String s2 = new String(\"kun\") + new String(\"gfu\");");
        System.out.println("  // 此时 \"kungfu\" 只在堆中，不在常量池");
        System.out.println("  String s3 = s2.intern();");
        System.out.println("  // intern() 发现常量池没有 \"kungfu\"\n");

        System.out.println("  s2 == s3 : " + (s2 == s3));
        System.out.println();

        if (s2 == s3) {
            System.out.println("  ✓ 当前是 JDK 7+ 行为：");
            System.out.println("    intern() 直接把堆对象 s2 的引用放入常量池");
            System.out.println("    所以 s3 和 s2 是同一个对象 → true");
        } else {
            System.out.println("  ✓ 当前是 JDK 6 行为：");
            System.out.println("    intern() 在永久代中复制了一份新的 \"kungfu\"");
            System.out.println("    s3 指向永久代副本，s2 指向堆对象 → false");
        }

        System.out.println();
        System.out.println("  对比图示：");
        System.out.println("  JDK 6:                         JDK 7+:");
        System.out.println("  ┌─────────┐ ┌─────────┐       ┌──────────┐");
        System.out.println("  │堆:\"kungfu\"│ │永久代:    │       │堆: \"kungfu\"│←── s2");
        System.out.println("  │  ↑ s2   │ │\"kungfu\" │       │     ↑     │");
        System.out.println("  │         │ │  ↑ s3   │       │常量池存引用│←── s3");
        System.out.println("  └─────────┘ └─────────┘       └──────────┘");
        System.out.println("  s2 != s3 (不同对象)             s2 == s3 (同一个对象)\n");
    }

    // =============================================================
    // 四、字符串拼接
    // =============================================================
    private static void testConcatenation() {
        System.out.println("=== 四、字符串拼接原理 ===\n");

        // 编译期常量拼接 → 编译器直接优化为一个字符串
        String s1 = "a" + "b" + "c";  // 编译后等价于 String s1 = "abc"
        String s2 = "abc";
        System.out.println("  \"a\" + \"b\" + \"c\" == \"abc\" : " + (s1 == s2) + " ← 编译器优化为同一个常量");

        // final 常量也可以优化
        final String a = "a";
        final String b = "b";
        String s3 = a + b;  // 编译期可确定 → 优化为 "ab"
        String s4 = "ab";
        System.out.println("  final a + final b == \"ab\" : " + (s3 == s4) + " ← final 变量编译期可确定");

        // 含变量的拼接 → 运行期用 StringBuilder
        String x = "a";  // 非 final
        String s5 = x + "b";   // 实际是 new StringBuilder().append(x).append("b").toString()
        String s6 = "ab";
        System.out.println("  x + \"b\" == \"ab\" : " + (s5 == s6) + "  ← 运行期拼接，new 了新对象");

        System.out.println();
        System.out.println("  规则总结：");
        System.out.println("  \"a\" + \"b\"         → 编译期优化为 \"ab\"（常量折叠）");
        System.out.println("  final a + final b → 编译期优化为 \"ab\"（常量传播）");
        System.out.println("  变量 + \"b\"         → StringBuilder.toString() → 堆中新对象");
        System.out.println();
    }

    // =============================================================
    // 五、经典面试题："new String("abc") 创建了几个对象？"
    // =============================================================
    private static void testHowManyObjects() {
        System.out.println("=== 五、面试题：创建了几个对象？===\n");

        System.out.println("  Q1: String s = new String(\"abc\"); 创建了几个对象？");
        System.out.println("  A: 最多 2 个");
        System.out.println("     对象1: 常量池中的 \"abc\"（如果之前不存在的话）");
        System.out.println("     对象2: 堆中 new 出来的 String 对象");
        System.out.println("     如果常量池已有 \"abc\" → 只创建 1 个（堆中的）");
        System.out.println();

        System.out.println("  Q2: String s = new String(\"a\") + new String(\"b\"); 创建了几个对象？");
        System.out.println("  A: 最多 6 个");
        System.out.println("     对象1: 常量池 \"a\"");
        System.out.println("     对象2: 堆中 new String(\"a\")");
        System.out.println("     对象3: 常量池 \"b\"");
        System.out.println("     对象4: 堆中 new String(\"b\")");
        System.out.println("     对象5: StringBuilder 对象（编译器生成的拼接工具）");
        System.out.println("     对象6: StringBuilder.toString() 生成的 \"ab\"（在堆中）");
        System.out.println("     注意: 常量池中不会有 \"ab\"（拼接结果不自动入池）");
        System.out.println();

        // 验证
        String s = new String("a") + new String("b");
        String pooled = "ab";
        System.out.println("  验证: (new String(\"a\") + new String(\"b\")) == \"ab\" : " + (s == pooled));
        System.out.println("  → false，拼接结果在堆中，\"ab\" 在常量池中，不是同一个对象\n");
    }
}

/*
 * 【知识串联】
 * 字符串常量池是方法区（运行时常量池）的一部分
 * JDK 7 后移到堆中 → 可以被 GC 回收
 *
 * D1 完整知识点：
 *   1. MemoryAreaDemo             — 五大内存区域
 *   2. ObjectAllocationDemo       — 对象分配策略
 *   3. ObjectMemoryLayoutDemo     — 对象内存布局
 *   4. OOMDemo                    — 各区域 OOM 实战
 *   5. StringConstantPoolDemo     — 字符串常量池（本类）
 */