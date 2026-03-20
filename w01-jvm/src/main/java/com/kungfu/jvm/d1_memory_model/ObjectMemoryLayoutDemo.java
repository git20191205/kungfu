package com.kungfu.jvm.d1_memory_model;

/**
 * 【Demo】对象内存布局演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 一个 Java 对象在堆中的内存布局由三部分组成：
 * <ol>
 *   <li>对象头（Header）— Mark Word + 类型指针（+ 数组长度）</li>
 *   <li>实例数据（Instance Data）— 字段值</li>
 *   <li>对齐填充（Padding）— 补齐到 8 字节的倍数</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试必问："new Object() 占多少字节？"
 * - 理解内存布局才能理解：synchronized 锁信息存哪里、hashCode 存哪里、GC 年龄存哪里
 * - 性能优化：了解对象大小才能估算内存占用
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法。
 * 如果想看精确的对象大小，可以用 JOL（Java Object Layout）工具，
 * 但本 Demo 通过手动计算来理解原理。
 *
 * @author kungfu
 * @since D1 - JVM内存模型
 */
public class ObjectMemoryLayoutDemo {

    // ============================================================
    // 用于演示的各种类
    // ============================================================

    /** 空对象 — 只有对象头 */
    static class EmptyObject {
    }

    /** 有一个 int 字段的对象 */
    static class OneIntObject {
        int value; // 4 bytes
    }

    /** 有一个 long 字段的对象 */
    static class OneLongObject {
        long value; // 8 bytes
    }

    /** 有多个字段的对象 */
    static class MultiFieldObject {
        int a;       // 4 bytes
        long b;      // 8 bytes
        boolean c;   // 1 byte
        Object d;    // 4 bytes（开启压缩指针）/ 8 bytes（关闭）
    }

    /** 继承关系的对象 */
    static class Parent {
        int parentField; // 4 bytes
    }
    static class Child extends Parent {
        int childField;  // 4 bytes
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  对象内存布局演示");
        System.out.println("========================================\n");

        // ============================================================
        // 一、对象头（Header）
        // ============================================================
        System.out.println("=== 一、对象头（Header）===\n");
        System.out.println("  对象头由两部分组成（数组对象多一个）：");
        System.out.println();
        System.out.println("  1. Mark Word（标记字）:");
        System.out.println("     32 位 JVM → 4 bytes");
        System.out.println("     64 位 JVM → 8 bytes");
        System.out.println();
        System.out.println("     Mark Word 存储内容（64 位，不同状态下复用）：");
        System.out.println("     ┌─────────────────────────────────────────────────┐");
        System.out.println("     │ 状态        │ 存储内容                           │");
        System.out.println("     ├─────────────┼────────────────────────────────────┤");
        System.out.println("     │ 无锁        │ hashCode(31) + GC年龄(4) + 偏向位  │");
        System.out.println("     │ 偏向锁      │ 线程ID(54) + epoch(2) + GC年龄(4)  │");
        System.out.println("     │ 轻量级锁    │ 指向栈中锁记录的指针(62)             │");
        System.out.println("     │ 重量级锁    │ 指向 Monitor 对象的指针(62)          │");
        System.out.println("     │ GC 标记     │ 空（标记为可回收）                   │");
        System.out.println("     └─────────────┴────────────────────────────────────┘");
        System.out.println();
        System.out.println("     → hashCode 在无锁状态时存在 Mark Word 中");
        System.out.println("     → 加了 synchronized 后，hashCode 被挤走（存到 Monitor 里）");
        System.out.println("     → GC 年龄只有 4 bit → 最大值 15 → 所以 MaxTenuringThreshold 最大 15");
        System.out.println();

        System.out.println("  2. 类型指针（Class Pointer / Klass Pointer）:");
        System.out.println("     指向方法区中该类的 Klass 元数据");
        System.out.println("     开启压缩指针（-XX:+UseCompressedOops，默认开启）→ 4 bytes");
        System.out.println("     关闭压缩指针 → 8 bytes");
        System.out.println();

        System.out.println("  3. 数组长度（仅数组对象）:");
        System.out.println("     4 bytes，记录数组的 length");
        System.out.println();

        // ============================================================
        // 二、实例数据（Instance Data）
        // ============================================================
        System.out.println("=== 二、实例数据（Instance Data）===\n");
        System.out.println("  各基本类型占用大小：");
        System.out.println("  ┌───────────┬────────┐");
        System.out.println("  │ 类型       │ 大小    │");
        System.out.println("  ├───────────┼────────┤");
        System.out.println("  │ boolean   │ 1 byte │");
        System.out.println("  │ byte      │ 1 byte │");
        System.out.println("  │ short     │ 2 bytes│");
        System.out.println("  │ char      │ 2 bytes│");
        System.out.println("  │ int       │ 4 bytes│");
        System.out.println("  │ float     │ 4 bytes│");
        System.out.println("  │ long      │ 8 bytes│");
        System.out.println("  │ double    │ 8 bytes│");
        System.out.println("  │ reference │ 4 bytes│ （压缩指针开启时）");
        System.out.println("  └───────────┴────────┘");
        System.out.println();

        // ============================================================
        // 三、对齐填充（Padding）
        // ============================================================
        System.out.println("=== 三、对齐填充（Padding）===\n");
        System.out.println("  HotSpot 要求对象大小必须是 8 字节的倍数");
        System.out.println("  不够的部分用 0 填充（纯粹浪费，但 CPU 访问对齐内存更快）");
        System.out.println();

        // ============================================================
        // 四、实际计算各种对象的大小
        // ============================================================
        System.out.println("=== 四、对象大小计算（64 位 JVM，开启压缩指针）===\n");

        // 1. new Object()
        System.out.println("  1. new Object():");
        System.out.println("     Mark Word:    8 bytes");
        System.out.println("     Class Pointer: 4 bytes");
        System.out.println("     实例数据:       0 bytes（没有字段）");
        System.out.println("     小计:          12 bytes");
        System.out.println("     对齐填充:       4 bytes（补到 16）");
        System.out.println("     ★ 总计:        16 bytes");
        System.out.println();
        // 验证
        System.out.println("     验证: new Object() 的 hashCode = " + new Object().hashCode());
        System.out.println("     (hashCode 存在 Mark Word 的高 31 位中)");
        System.out.println();

        // 2. EmptyObject
        System.out.println("  2. new EmptyObject():");
        System.out.println("     和 Object 一样 = 16 bytes（空类也有对象头）");
        new EmptyObject(); // 防止类被优化掉
        System.out.println();

        // 3. OneIntObject
        System.out.println("  3. new OneIntObject():");
        System.out.println("     Mark Word:    8 bytes");
        System.out.println("     Class Pointer: 4 bytes");
        System.out.println("     int value:     4 bytes");
        System.out.println("     小计:          16 bytes（刚好 8 的倍数）");
        System.out.println("     对齐填充:       0 bytes");
        System.out.println("     ★ 总计:        16 bytes");
        new OneIntObject();
        System.out.println();

        // 4. OneLongObject
        System.out.println("  4. new OneLongObject():");
        System.out.println("     Mark Word:    8 bytes");
        System.out.println("     Class Pointer: 4 bytes");
        System.out.println("     对齐 gap:      4 bytes（long 要 8 字节对齐）");
        System.out.println("     long value:    8 bytes");
        System.out.println("     ★ 总计:        24 bytes");
        new OneLongObject();
        System.out.println();

        // 5. MultiFieldObject
        System.out.println("  5. new MultiFieldObject():");
        System.out.println("     Mark Word:     8 bytes");
        System.out.println("     Class Pointer:  4 bytes");
        System.out.println("     int a:          4 bytes");
        System.out.println("     long b:         8 bytes");
        System.out.println("     boolean c:      1 byte");
        System.out.println("     Object d(ref):  4 bytes（压缩指针）");
        System.out.println("     小计:           29 bytes");
        System.out.println("     对齐填充:        3 bytes（补到 32）");
        System.out.println("     ★ 总计:         32 bytes");
        System.out.println("     注意: JVM 可能重排字段顺序以减少 padding");
        new MultiFieldObject();
        System.out.println();

        // 6. 数组对象
        System.out.println("  6. new int[10]:");
        System.out.println("     Mark Word:      8 bytes");
        System.out.println("     Class Pointer:   4 bytes");
        System.out.println("     数组长度:         4 bytes（数组特有）");
        System.out.println("     实例数据:         10 × 4 = 40 bytes");
        System.out.println("     小计:            56 bytes（刚好 8 的倍数）");
        System.out.println("     ★ 总计:          56 bytes");
        int[] arr = new int[10];
        System.out.println("     验证: arr.length = " + arr.length);
        System.out.println();

        // 7. 继承对象
        System.out.println("  7. new Child():");
        System.out.println("     Mark Word:       8 bytes");
        System.out.println("     Class Pointer:    4 bytes");
        System.out.println("     parentField(int): 4 bytes（父类字段先排）");
        System.out.println("     childField(int):  4 bytes（子类字段后排）");
        System.out.println("     小计:             20 bytes");
        System.out.println("     对齐填充:          4 bytes（补到 24）");
        System.out.println("     ★ 总计:           24 bytes");
        System.out.println("     → 父类字段在前，子类字段在后，不会混在一起");
        new Child();
        System.out.println();

        // ============================================================
        // 五、面试经典问题
        // ============================================================
        System.out.println("========================================");
        System.out.println("  面试经典问题：");
        System.out.println();
        System.out.println("  Q: new Object() 占多少内存？");
        System.out.println("  A: 16 bytes（64位JVM，开启压缩指针）");
        System.out.println("     = Mark Word(8) + Klass Pointer(4) + Padding(4)");
        System.out.println();
        System.out.println("  Q: GC 年龄最大为什么是 15？");
        System.out.println("  A: Mark Word 中 GC 年龄只有 4 bit，最大值 = 2^4 - 1 = 15");
        System.out.println();
        System.out.println("  Q: synchronized 的锁信息存在哪里？");
        System.out.println("  A: 对象头的 Mark Word 中");
        System.out.println("     偏向锁 → 存线程ID");
        System.out.println("     轻量级锁 → 存指向栈中 Lock Record 的指针");
        System.out.println("     重量级锁 → 存指向 Monitor 对象的指针");
        System.out.println();
        System.out.println("  Q: 为什么要对齐填充？");
        System.out.println("  A: CPU 按 8 字节块读内存，对齐后避免跨块访问，提升性能");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 对象内存布局 → 引出两个关联知识：
 * → Mark Word 与 synchronized 锁升级（偏向锁→轻量级锁→重量级锁）→ W02 并发编程
 * → 对象大小估算 → JVM 调优中计算堆内存需求
 *
 * 接下来看 OOM 实战 → OOMDemo.java
 */