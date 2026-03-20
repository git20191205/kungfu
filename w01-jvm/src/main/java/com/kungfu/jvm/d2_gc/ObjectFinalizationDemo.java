package com.kungfu.jvm.d2_gc;

/**
 * 【Demo】对象的死亡过程 — 两次标记与 finalize 自救
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 一个对象从「被判死刑」到「真正死亡」要经历两次标记：
 * <ol>
 *   <li>第一次标记：可达性分析发现不可达</li>
 *   <li>筛选：是否有必要执行 finalize()？</li>
 *   <li>如果有 finalize() → 放入 F-Queue → Finalizer 线程执行</li>
 *   <li>第二次标记：如果 finalize() 中重新建立了引用 → 对象复活！</li>
 *   <li>但 finalize() 只会被调用一次，第二次不会再给机会</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试会考"对象一定会被回收吗？有没有自救的机会？"
 * - 理解为什么 finalize() 被废弃：不可靠、有性能开销、只能自救一次
 * - 实际开发中应该用 try-with-resources 替代
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可。
 *
 * @author kungfu
 * @since D2 - GC算法与垃圾回收器
 */
public class ObjectFinalizationDemo {

    // 用于演示自救：对象可以在 finalize() 中把自己重新挂到一个 GC Root 上
    private static ObjectFinalizationDemo SAVE_HOOK = null;

    private String name;

    public ObjectFinalizationDemo(String name) {
        this.name = name;
    }

    /**
     * 重写 finalize() — 对象的「临终遗言」
     *
     * 当 GC 第一次标记对象不可达后，会检查是否重写了 finalize()：
     * - 没重写 / 已调用过 → 直接回收
     * - 重写了且未调用过 → 放入 F-Queue，由 Finalizer 线程执行
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        System.out.println("  [finalize] " + name + " 的 finalize() 被调用了！");
        System.out.println("  [finalize] 正在自救 — 把自己重新赋给类变量 SAVE_HOOK...");

        // 自救！重新建立 GC Root 可达的引用
        SAVE_HOOK = this;
    }

    @Override
    public String toString() {
        return "ObjectFinalizationDemo{" + name + "}";
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  对象死亡过程 — 两次标记与 finalize 自救");
        System.out.println("========================================\n");

        // ============ 第一次：自救成功 ============
        System.out.println("--- 第一次 GC：finalize() 自救 ---\n");

        SAVE_HOOK = new ObjectFinalizationDemo("小明");
        System.out.println("  1. 创建对象: " + SAVE_HOOK);

        // 断开引用 → 对象不可达
        SAVE_HOOK = null;
        System.out.println("  2. SAVE_HOOK = null → 对象不可达");
        System.out.println("  3. 触发 GC...");
        System.gc();

        // finalize() 由 Finalizer 线程执行，优先级低，需要等待
        // 注意：JVM 不保证 finalize() 一定会执行完！
        Thread.sleep(500);

        if (SAVE_HOOK != null) {
            System.out.println("  4. ✓ 自救成功！对象还活着: " + SAVE_HOOK);
        } else {
            System.out.println("  4. × 自救失败，对象已被回收");
        }

        // ============ 第二次：自救失败 ============
        System.out.println("\n--- 第二次 GC：finalize() 不会再被调用 ---\n");

        // 再次断开引用
        SAVE_HOOK = null;
        System.out.println("  1. 再次 SAVE_HOOK = null → 对象又不可达了");
        System.out.println("  2. 触发 GC...");
        System.gc();

        Thread.sleep(500);

        if (SAVE_HOOK != null) {
            System.out.println("  3. ✓ 自救成功！");
        } else {
            System.out.println("  3. × 自救失败！对象被回收了");
            System.out.println("     → 因为 finalize() 只会被 JVM 调用一次");
            System.out.println("     → 第二次 GC 不会再执行 finalize()，直接回收");
        }

        // ============ 总结 ============
        System.out.println();
        System.out.println("========================================");
        System.out.println("  对象死亡流程：");
        System.out.println();
        System.out.println("  第一次标记：可达性分析 → 不可达");
        System.out.println("      ↓");
        System.out.println("  检查 finalize():");
        System.out.println("    没重写 / 已调用过 → 直接回收（死刑立即执行）");
        System.out.println("    重写了且未调用过 → 放入 F-Queue");
        System.out.println("      ↓");
        System.out.println("  Finalizer 线程执行 finalize()");
        System.out.println("      ↓");
        System.out.println("  第二次标记：");
        System.out.println("    finalize() 中重建了引用 → 移出回收集合（自救成功）");
        System.out.println("    没有重建引用 → 回收（死刑执行）");
        System.out.println();
        System.out.println("  ⚠ finalize() 的问题（为什么不推荐使用）：");
        System.out.println("    1. 只能自救一次，第二次不会再调用");
        System.out.println("    2. Finalizer 线程优先级低，不保证及时执行");
        System.out.println("    3. 如果 finalize() 执行慢，会拖慢 GC");
        System.out.println("    4. JVM 甚至不保证 finalize() 一定会执行完");
        System.out.println("    → Java 9 已标记 @Deprecated");
        System.out.println("    → 用 try-with-resources + AutoCloseable 替代");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 到这里，D2 GC 相关知识全部覆盖：
 *   1. 三种 GC 算法     → GCAlgorithmDemo
 *   2. 四大垃圾回收器    → GarbageCollectorDemo
 *   3. GC Roots 可达性  → GCRootsDemo
 *   4. 四种引用类型      → ReferenceTypeDemo
 *   5. 对象死亡过程      → ObjectFinalizationDemo（本类）
 *
 * 下一步 D3 → JVM 调优实战
 * → 怎么用 jstat、jmap、jstack 排查问题？
 * → 常见的 JVM 参数怎么调？
 * → OOM 案例分析
 */