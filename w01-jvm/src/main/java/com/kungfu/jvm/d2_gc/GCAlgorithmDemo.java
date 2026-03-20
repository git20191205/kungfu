package com.kungfu.jvm.d2_gc;

/**
 * 【Demo】GC 算法对比演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 通过代码模拟三种经典 GC 算法的核心思想：
 * <ol>
 *   <li>标记-清除（Mark-Sweep）— 最基础，有碎片</li>
 *   <li>标记-复制（Copying）— 新生代主流算法，无碎片但浪费空间</li>
 *   <li>标记-整理（Mark-Compact）— 老年代主流算法，无碎片但需移动对象</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 所有垃圾回收器（Serial、ParNew、CMS、G1、ZGC）都是基于这三种算法的组合实现。
 * 理解算法原理，才能理解各回收器的优劣和适用场景。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，观察三种算法的模拟过程。
 *
 * @author kungfu
 * @since D2 - GC算法与垃圾回收器
 */
public class GCAlgorithmDemo {

    /**
     * 模拟堆内存中的对象槽位
     * true = 存活对象, false = 空闲
     */
    private static void printHeap(String label, boolean[] heap) {
        StringBuilder sb = new StringBuilder();
        sb.append("  [").append(label).append("] |");
        for (boolean slot : heap) {
            sb.append(slot ? "■" : "□").append("|");
        }
        System.out.println(sb);
    }

    // ============================================================
    // 算法一：标记-清除（Mark-Sweep）
    // ============================================================
    private static void markSweep() {
        System.out.println("=== 算法一：标记-清除（Mark-Sweep）===");
        System.out.println("  原理：先标记所有存活对象，再清除未标记的对象");
        System.out.println();

        // 模拟堆：10 个槽位，部分被对象占用
        boolean[] heap = {true, false, true, true, false, true, false, false, true, false};
        //                 A     垃圾    B     C     垃圾    D     垃圾    垃圾    E     垃圾

        System.out.println("  Step 1 - 初始堆状态（■=存活 □=垃圾）:");
        printHeap("GC前", heap);

        // 标记阶段：从 GC Roots 出发，标记所有可达对象（这里已经用 true 表示）
        System.out.println("\n  Step 2 - 标记阶段：从 GC Roots 遍历，标记存活对象 A,B,C,D,E");

        // 清除阶段：回收未标记的对象
        // 存活对象位置不变，垃圾对象被清除
        System.out.println("  Step 3 - 清除阶段：回收所有未标记对象");
        printHeap("GC后", heap);  // 存活对象位置不变

        System.out.println();
        System.out.println("  ⚠ 问题：产生内存碎片！");
        System.out.println("    □ 是空闲的，但不连续，无法分配大对象");
        System.out.println("    比如要分配 3 个连续槽位的对象 → 放不下！虽然总空闲有 5 个");
        System.out.println();
    }

    // ============================================================
    // 算法二：标记-复制（Copying）
    // ============================================================
    private static void copying() {
        System.out.println("=== 算法二：标记-复制（Copying）===");
        System.out.println("  原理：将内存分为两半，存活对象复制到另一半，原区域整体清空");
        System.out.println("  → 新生代 Eden + S0 + S1 就是用这种算法（S0 和 S1 轮流复制）");
        System.out.println();

        // 模拟两个 Survivor 区
        boolean[] fromSpace = {true, false, true, true, false, true, false, false, true, false};
        boolean[] toSpace =   {false, false, false, false, false, false, false, false, false, false};

        System.out.println("  Step 1 - 初始状态:");
        printHeap("From", fromSpace);
        printHeap("To  ", toSpace);

        // 复制存活对象到 To 区（紧凑排列）
        int toIndex = 0;
        for (boolean alive : fromSpace) {
            if (alive) {
                toSpace[toIndex++] = true;
            }
        }
        // 清空 From 区
        java.util.Arrays.fill(fromSpace, false);

        System.out.println("\n  Step 2 - 复制存活对象到 To 区，From 区整体清空:");
        printHeap("From", fromSpace);
        printHeap("To  ", toSpace);

        System.out.println();
        System.out.println("  ✓ 优点：无碎片！存活对象紧凑排列");
        System.out.println("  ✓ 适合新生代：大部分对象朝生夕死，存活率低，复制少量对象很快");
        System.out.println("  ⚠ 缺点：浪费一半空间（所以 HotSpot 用 Eden:S0:S1 = 8:1:1 优化）");
        System.out.println();
    }

    // ============================================================
    // 算法三：标记-整理（Mark-Compact）
    // ============================================================
    private static void markCompact() {
        System.out.println("=== 算法三：标记-整理（Mark-Compact）===");
        System.out.println("  原理：标记存活对象，然后将它们向一端移动，清理边界外的空间");
        System.out.println("  → 老年代用这种算法（对象存活率高，复制成本太大）");
        System.out.println();

        boolean[] heap = {true, false, true, true, false, true, false, false, true, false};

        System.out.println("  Step 1 - 初始状态:");
        printHeap("整理前", heap);

        // 整理：存活对象向前移动，紧凑排列
        boolean[] compacted = new boolean[heap.length];
        int pos = 0;
        for (boolean alive : heap) {
            if (alive) {
                compacted[pos++] = true;
            }
        }

        System.out.println("\n  Step 2 - 存活对象向左端移动，紧凑排列:");
        printHeap("整理后", compacted);

        System.out.println();
        System.out.println("  ✓ 优点：无碎片，不浪费空间");
        System.out.println("  ⚠ 缺点：移动对象需要更新引用，STW（Stop-The-World）时间更长");
        System.out.println("  → 所以 CMS 用「标记-清除」减少停顿，但会有碎片，碎片严重时退化为 Serial Old 做整理");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  GC 算法对比演示");
        System.out.println("========================================\n");

        markSweep();
        copying();
        markCompact();

        System.out.println("========================================");
        System.out.println("  三种算法对比速记：");
        System.out.println("  ┌──────────────┬──────┬──────┬──────────┐");
        System.out.println("  │    算法       │ 碎片 │ 空间 │ 移动对象  │");
        System.out.println("  ├──────────────┼──────┼──────┼──────────┤");
        System.out.println("  │ 标记-清除     │  有  │ 不浪费│   不移动  │");
        System.out.println("  │ 标记-复制     │  无  │ 浪费半│   复制    │");
        System.out.println("  │ 标记-整理     │  无  │ 不浪费│   移动    │");
        System.out.println("  └──────────────┴──────┴──────┴──────────┘");
        System.out.println();
        System.out.println("  新生代 → 复制算法（对象存活率低，复制少）");
        System.out.println("  老年代 → 标记-整理 或 标记-清除");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 理解了三种 GC 算法后，下一步：
 * → 各种垃圾回收器（Serial、ParNew、CMS、G1）分别用了哪种算法？
 * → 请看 GarbageCollectorDemo.java
 */