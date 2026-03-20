package com.kungfu.jvm.d2_gc;

import java.lang.ref.*;
import java.util.*;

/**
 * 【Demo】四种引用类型实战演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * Java 中四种引用类型对 GC 行为的影响：
 * <ol>
 *   <li>强引用（Strong）— 宁可 OOM 也不回收</li>
 *   <li>软引用（Soft）— 内存不够时才回收，适合做缓存</li>
 *   <li>弱引用（Weak）— GC 一来就回收，WeakHashMap 用的就是这个</li>
 *   <li>虚引用（Phantom）— 最弱，只能用来收 GC 通知</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试必考："软引用和弱引用的区别？各自的使用场景？"
 * - 实战常用：本地缓存（Soft）、ThreadLocal 防泄漏（Weak）、堆外内存回收（Phantom）
 * - 理解引用类型才能理解 WeakHashMap、ThreadLocal、DirectByteBuffer 的设计
 *
 * <h3>运行方式</h3>
 * <pre>
 *   java -Xms20m -Xmx20m -XX:+PrintGCDetails
 *        com.kungfu.jvm.d2_gc.ReferenceTypeDemo
 * </pre>
 *
 * @author kungfu
 * @since D2 - GC算法与垃圾回收器
 */
public class ReferenceTypeDemo {

    private static final int _1MB = 1024 * 1024;

    // =============================================================
    // 一、强引用（Strong Reference）— 最常见，不回收
    // =============================================================
    private static void testStrongReference() {
        System.out.println("=== 一、强引用（Strong Reference）===");
        System.out.println("  场景：日常写的 Object obj = new Object() 就是强引用\n");

        // 这就是强引用，只要引用在，GC 绝不回收
        byte[] strongRef = new byte[2 * _1MB];
        System.out.println("  创建 2MB 强引用对象");

        System.gc();
        // 强引用在，对象一定活着
        System.out.println("  GC 后对象是否存活: " + (strongRef != null));

        // 只有手动断开引用，对象才能被回收
        strongRef = null;
        System.gc();
        System.out.println("  置 null + GC 后才被回收");
        System.out.println();
        System.out.println("  → 强引用的风险：忘记置 null → 内存泄漏");
        System.out.println("    经典场景：static List 不断 add 却不 remove\n");
    }

    // =============================================================
    // 二、软引用（Soft Reference）— 内存不够才回收，天然的缓存
    // =============================================================
    private static void testSoftReference() {
        System.out.println("=== 二、软引用（Soft Reference）===");
        System.out.println("  场景：图片缓存、计算结果缓存 — 内存够就留着，不够就扔\n");

        // 模拟一个图片缓存
        SoftReference<byte[]> imageCache = new SoftReference<>(new byte[2 * _1MB]);
        System.out.println("  创建 2MB 软引用对象（模拟缓存图片）");
        System.out.println("  缓存是否可用: " + (imageCache.get() != null));

        // 内存充足时，GC 不会回收软引用
        System.gc();
        System.out.println("  内存充足 + GC 后，缓存是否还在: " + (imageCache.get() != null));

        // 模拟内存紧张：疯狂分配对象挤压内存
        System.out.println("\n  开始疯狂分配内存，制造内存压力...");
        try {
            List<byte[]> pressure = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                pressure.add(new byte[_1MB]);
            }
        } catch (OutOfMemoryError e) {
            System.out.println("  [触发 OOM 边缘]");
        }

        // 内存紧张时，软引用对象被回收
        System.out.println("  内存紧张后，缓存是否还在: " + (imageCache.get() != null));
        System.out.println();
        System.out.println("  → 软引用 = 「内存够就缓存，不够就丢弃」");
        System.out.println("  → 比手动管理缓存过期更优雅，JVM 自动帮你决定何时清理");
        System.out.println("  → 实战：Android 图片加载框架、本地计算缓存\n");
    }

    // =============================================================
    // 三、弱引用（Weak Reference）— GC 一来就回收
    // =============================================================
    private static void testWeakReference() {
        System.out.println("=== 三、弱引用（Weak Reference）===");
        System.out.println("  场景：WeakHashMap、ThreadLocal 的 key\n");

        // 弱引用对象
        WeakReference<byte[]> weakRef = new WeakReference<>(new byte[_1MB]);
        System.out.println("  创建 1MB 弱引用对象");
        System.out.println("  GC 前是否存活: " + (weakRef.get() != null));

        // 只要 GC 发生，无论内存是否充足，弱引用都会被回收
        System.gc();
        System.out.println("  GC 后是否存活: " + (weakRef.get() != null) + " ← 一定是 false！");

        // WeakHashMap 实战场景
        System.out.println("\n  --- WeakHashMap 实战场景 ---");
        WeakHashMap<Object, String> weakMap = new WeakHashMap<>();
        Object key = new Object();
        weakMap.put(key, "我是缓存数据");
        System.out.println("  WeakHashMap 存入数据，size: " + weakMap.size());

        // key 还在，数据就在
        System.gc();
        System.out.println("  key 还在 + GC 后，size: " + weakMap.size());

        // key 断开引用后，GC 会自动清理 WeakHashMap 的条目
        key = null;
        System.gc();
        // 需要短暂等待 ReferenceHandler 线程处理
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        System.out.println("  key = null + GC 后，size: " + weakMap.size() + " ← 自动清理了！");

        System.out.println();
        System.out.println("  → 弱引用 vs 软引用：软引用「内存不够才回收」，弱引用「GC 就回收」");
        System.out.println("  → WeakHashMap：key 没有外部强引用时，条目自动清理，防止内存泄漏");
        System.out.println("  → ThreadLocal 的 key 也是弱引用，但 value 不是 → 所以要手动 remove()！\n");
    }

    // =============================================================
    // 四、虚引用（Phantom Reference）— 最弱，只能收通知
    // =============================================================
    private static void testPhantomReference() {
        System.out.println("=== 四、虚引用（Phantom Reference）===");
        System.out.println("  场景：追踪对象被 GC 回收的时机，DirectByteBuffer 堆外内存回收\n");

        // 虚引用必须配合 ReferenceQueue 使用
        ReferenceQueue<byte[]> queue = new ReferenceQueue<>();
        PhantomReference<byte[]> phantomRef = new PhantomReference<>(new byte[_1MB], queue);

        // 虚引用的 get() 永远返回 null — 不能通过虚引用获取对象
        System.out.println("  phantomRef.get(): " + phantomRef.get() + " ← 永远是 null");

        // GC 后，虚引用会被放入 ReferenceQueue
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        Reference<? extends byte[]> ref = queue.poll();
        System.out.println("  GC 后 ReferenceQueue 中是否收到通知: " + (ref != null));
        System.out.println("  收到的引用是不是 phantomRef: " + (ref == phantomRef));

        System.out.println();
        System.out.println("  → 虚引用唯一的用途：对象被回收时收到通知");
        System.out.println("  → 经典应用：DirectByteBuffer 的 Cleaner 机制");
        System.out.println("    NIO 分配堆外内存 → 用虚引用追踪 → 对象被 GC 时触发堆外内存释放");
        System.out.println("  → 日常开发很少直接用，但面试会考原理\n");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  四种引用类型实战演示");
        System.out.println("========================================\n");

        testStrongReference();
        testWeakReference();       // 弱引用放前面，避免软引用挤爆内存影响后续
        testPhantomReference();
        testSoftReference();       // 软引用放最后，因为会制造内存压力

        System.out.println("========================================");
        System.out.println("  四种引用对比速记：");
        System.out.println("  ┌──────────┬────────────┬──────────────────────┐");
        System.out.println("  │ 引用类型  │ GC 回收时机  │ 典型场景              │");
        System.out.println("  ├──────────┼────────────┼──────────────────────┤");
        System.out.println("  │ 强引用    │ 不回收       │ Object o = new O()   │");
        System.out.println("  │ 软引用    │ 内存不够时   │ 图片缓存、本地缓存     │");
        System.out.println("  │ 弱引用    │ GC 就回收    │ WeakHashMap/ThreadLocal│");
        System.out.println("  │ 虚引用    │ GC 就回收    │ 堆外内存回收通知       │");
        System.out.println("  └──────────┴────────────┴──────────────────────┘");
        System.out.println();
        System.out.println("  面试追问：ThreadLocal 为什么会内存泄漏？");
        System.out.println("  → ThreadLocalMap 的 key 是弱引用（GC 会回收）");
        System.out.println("  → 但 value 是强引用（GC 不会回收）");
        System.out.println("  → key 被回收后 value 变成孤儿 → 内存泄漏");
        System.out.println("  → 解决方案：用完必须调用 threadLocal.remove()");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 四种引用类型掌握后，还有一个 GC 相关知识点：
 * → 对象的死亡过程：两次标记 + finalize() 自救
 * → 请看 ObjectFinalizationDemo.java
 */