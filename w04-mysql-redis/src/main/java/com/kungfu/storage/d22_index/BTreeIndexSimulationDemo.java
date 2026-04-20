package com.kungfu.storage.d22_index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 【Demo】手写 B+Tree 模拟 — 理解 MySQL 索引底层结构
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>手写简化版 B+Tree（阶=4），演示插入和查找过程</li>
 *   <li>等值查找：从根到叶，O(log N) 复杂度</li>
 *   <li>范围查找：定位到起始叶子后沿链表遍历</li>
 *   <li>对比 B+Tree 与其他数据结构的优劣</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："为什么 MySQL 选择 B+Tree 做索引？"
 * 不理解 B+Tree 结构就无法理解索引失效、回表、覆盖索引等核心概念
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main 方法，不需要 MySQL 连接
 *
 * @author kungfu
 * @since D22 - MySQL索引原理
 */
public class BTreeIndexSimulationDemo {

    // =============================================================
    // 简化版 B+Tree 实现（阶 ORDER=4，即每个节点最多 3 个 key）
    // =============================================================

    static final int ORDER = 4; // 阶数：每个节点最多 ORDER-1 个 key
    static final int MAX_KEYS = ORDER - 1;
    static final int MIN_KEYS = (ORDER + 1) / 2 - 1;

    /** B+Tree 节点 */
    static class BPlusNode {
        List<Integer> keys = new ArrayList<>();
        List<BPlusNode> children = new ArrayList<>(); // 内部节点的子节点
        List<String> values = new ArrayList<>();       // 叶子节点的数据
        BPlusNode next;    // 叶子节点的链表指针
        boolean isLeaf;

        BPlusNode(boolean isLeaf) {
            this.isLeaf = isLeaf;
        }
    }

    /** B+Tree */
    static class BPlusTree {
        BPlusNode root;
        int height = 1;
        int nodeCount = 1;

        BPlusTree() {
            root = new BPlusNode(true);
        }

        /** 等值查找 */
        String search(int key) {
            BPlusNode node = root;
            int level = 0;
            System.out.println("    查找 key=" + key + "：");
            while (!node.isLeaf) {
                level++;
                int i = Collections.binarySearch(node.keys, key);
                if (i < 0) i = -i - 1;
                else i = i + 1;
                System.out.println("      第" + level + "层（内部节点）keys=" + node.keys + " → 走子节点[" + i + "]");
                node = node.children.get(i);
            }
            level++;
            int idx = node.keys.indexOf(key);
            if (idx >= 0) {
                System.out.println("      第" + level + "层（叶子节点）keys=" + node.keys + " → 命中！值=" + node.values.get(idx));
                return node.values.get(idx);
            }
            System.out.println("      第" + level + "层（叶子节点）keys=" + node.keys + " → 未找到");
            return null;
        }

        /** 范围查找 [low, high] */
        List<String> rangeSearch(int low, int high) {
            List<String> result = new ArrayList<>();
            // 先找到 low 所在的叶子节点
            BPlusNode node = root;
            while (!node.isLeaf) {
                int i = Collections.binarySearch(node.keys, low);
                if (i < 0) i = -i - 1;
                else i = i + 1;
                node = node.children.get(i);
            }
            // 沿叶子链表遍历
            while (node != null) {
                for (int i = 0; i < node.keys.size(); i++) {
                    int k = node.keys.get(i);
                    if (k > high) return result;
                    if (k >= low) result.add(k + "→" + node.values.get(i));
                }
                node = node.next;
            }
            return result;
        }

        /** 插入 key-value */
        void insert(int key, String value) {
            BPlusNode r = root;
            if (r.keys.size() == MAX_KEYS) {
                // 根节点满了，需要分裂
                BPlusNode s = new BPlusNode(false);
                s.children.add(r);
                splitChild(s, 0, r);
                root = s;
                height++;
                nodeCount++;
                insertNonFull(s, key, value);
            } else {
                insertNonFull(r, key, value);
            }
        }

        private void insertNonFull(BPlusNode node, int key, String value) {
            if (node.isLeaf) {
                int i = Collections.binarySearch(node.keys, key);
                if (i >= 0) {
                    node.values.set(i, value); // key 已存在，更新
                } else {
                    i = -i - 1;
                    node.keys.add(i, key);
                    node.values.add(i, value);
                }
            } else {
                int i = Collections.binarySearch(node.keys, key);
                if (i < 0) i = -i - 1;
                else i = i + 1;
                BPlusNode child = node.children.get(i);
                if (child.keys.size() == MAX_KEYS) {
                    splitChild(node, i, child);
                    if (key > node.keys.get(i)) i++;
                }
                insertNonFull(node.children.get(i), key, value);
            }
        }

        private void splitChild(BPlusNode parent, int index, BPlusNode full) {
            BPlusNode right = new BPlusNode(full.isLeaf);
            int mid = MAX_KEYS / 2;
            nodeCount++;

            if (full.isLeaf) {
                // 叶子分裂：右节点包含 [mid, end]，左节点保留 [0, mid)
                for (int i = mid; i < full.keys.size(); i++) {
                    right.keys.add(full.keys.get(i));
                    right.values.add(full.values.get(i));
                }
                // 维护叶子链表
                right.next = full.next;
                full.next = right;
                // 清理左节点
                full.keys.subList(mid, full.keys.size()).clear();
                full.values.subList(mid, full.values.size()).clear();
                // 上提的 key 是右节点的第一个 key（B+Tree 叶子保留）
                parent.keys.add(index, right.keys.get(0));
            } else {
                // 内部节点分裂
                int midKey = full.keys.get(mid);
                for (int i = mid + 1; i < full.keys.size(); i++) {
                    right.keys.add(full.keys.get(i));
                }
                for (int i = mid + 1; i <= full.children.size() - 1; i++) {
                    right.children.add(full.children.get(i));
                }
                full.keys.subList(mid, full.keys.size()).clear();
                full.children.subList(mid + 1, full.children.size()).clear();
                parent.keys.add(index, midKey);
            }
            parent.children.add(index + 1, right);
        }

        /** 打印树结构 */
        void printTree() {
            System.out.println("    B+Tree（高度=" + height + "，节点数=" + nodeCount + "）：");
            printNode(root, "    ", true);
            // 打印叶子链表
            System.out.print("    叶子链表: ");
            BPlusNode leaf = root;
            while (!leaf.isLeaf) leaf = leaf.children.get(0);
            while (leaf != null) {
                System.out.print(leaf.keys + " → ");
                leaf = leaf.next;
            }
            System.out.println("NULL\n");
        }

        private void printNode(BPlusNode node, String indent, boolean last) {
            String branch = last ? "└── " : "├── ";
            String type = node.isLeaf ? "[叶子]" : "[内部]";
            System.out.println(indent + branch + type + " keys=" + node.keys);
            if (!node.isLeaf) {
                for (int i = 0; i < node.children.size(); i++) {
                    printNode(node.children.get(i), indent + (last ? "    " : "│   "), i == node.children.size() - 1);
                }
            }
        }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  手写 B+Tree 索引模拟");
        System.out.println("========================================\n");

        // 一、为什么是 B+Tree
        showWhyBPlusTree();

        // 二、构建 B+Tree
        BPlusTree tree = new BPlusTree();
        System.out.println("=== 二、构建 B+Tree（阶=" + ORDER + "）===\n");
        int[] data = {10, 20, 5, 15, 25, 30, 35, 40, 3, 7, 12, 18, 22, 28, 33, 38};
        for (int key : data) {
            tree.insert(key, "row_" + key);
        }
        System.out.println("  插入 " + data.length + " 个 key 后的树结构：\n");
        tree.printTree();

        // 三、等值查找
        System.out.println("=== 三、等值查找（O(log N)）===\n");
        tree.search(22);
        System.out.println();
        tree.search(99);
        System.out.println();

        // 四、范围查找
        System.out.println("=== 四、范围查找（叶子链表遍历）===\n");
        System.out.println("  查找 key ∈ [10, 25]：");
        List<String> result = tree.rangeSearch(10, 25);
        System.out.println("    结果: " + result);
        System.out.println("    → 定位到 10 所在叶子，沿链表遍历到 25 为止\n");

        // 五、InnoDB 索引结构
        showInnoDBIndex();

        // 面试速记
        showInterviewTips();
    }

    // =============================================================
    // 一、为什么是 B+Tree
    // =============================================================
    private static void showWhyBPlusTree() {
        System.out.println("=== 一、为什么 MySQL 选择 B+Tree ===\n");

        System.out.println("  ┌────────────┬───────────────────────────┬──────────────────────────────┐");
        System.out.println("  │ 数据结构   │ 优点                      │ 缺点                          │");
        System.out.println("  ├────────────┼───────────────────────────┼──────────────────────────────┤");
        System.out.println("  │ Hash       │ O(1) 等值查找              │ ❌ 不支持范围查询             │");
        System.out.println("  │ 红黑树     │ O(log N) 查找              │ ❌ 树太高，磁盘 IO 多         │");
        System.out.println("  │ B-Tree     │ 矮胖，IO 少               │ ❌ 数据分散在所有节点，范围慢 │");
        System.out.println("  │ B+Tree ★  │ 非叶子不存数据 → 更矮     │ ✓ 叶子有链表 → 范围查询快    │");
        System.out.println("  └────────────┴───────────────────────────┴──────────────────────────────┘\n");

        System.out.println("  B+Tree 核心优势：");
        System.out.println("    1. 非叶子节点只存 key → 一个 16KB 页能存更多索引项 → 树更矮 → IO 更少");
        System.out.println("    2. 叶子节点用双向链表连接 → 范围查询只需顺序遍历");
        System.out.println("    3. 所有数据都在叶子 → 查询路径长度一致 → 性能稳定");
        System.out.println();

        System.out.println("  高度计算（假设 key=8B, 指针=6B, 行数据=1KB, 页=16KB）：");
        System.out.println("    非叶子每页: 16KB / (8+6)B ≈ 1170 个指针");
        System.out.println("    叶子每页:   16KB / 1KB ≈ 16 行");
        System.out.println("    高度=2: 1170 × 16 ≈ 1.8万行");
        System.out.println("    高度=3: 1170 × 1170 × 16 ≈ 2190万行");
        System.out.println("    → 2000 万行数据，只需 3 次磁盘 IO！\n");
    }

    // =============================================================
    // 五、InnoDB 索引结构
    // =============================================================
    private static void showInnoDBIndex() {
        System.out.println("=== 五、InnoDB 聚簇索引 vs 二级索引 ===\n");

        System.out.println("  ┌─────────────────┬─────────────────────────────┬──────────────────────────┐");
        System.out.println("  │                 │ 聚簇索引（主键索引）        │ 二级索引（非主键索引）   │");
        System.out.println("  ├─────────────────┼─────────────────────────────┼──────────────────────────┤");
        System.out.println("  │ 叶子节点存什么  │ 整行数据                    │ 主键值                   │");
        System.out.println("  │ 每表几个        │ 1 个（主键决定）            │ 多个                     │");
        System.out.println("  │ 查询方式        │ 直接取数据                  │ 先查主键，再回表取数据   │");
        System.out.println("  │ 排序方式        │ 按主键物理排序              │ 按索引列逻辑排序         │");
        System.out.println("  └─────────────────┴─────────────────────────────┴──────────────────────────┘\n");

        System.out.println("  回表过程：");
        System.out.println("    SELECT * FROM user WHERE name='Alice'");
        System.out.println("    1. 在 idx_name（二级索引）中查找 'Alice' → 得到主键 id=5");
        System.out.println("    2. 用 id=5 回到聚簇索引查找 → 得到整行数据");
        System.out.println("    3. 这就是「回表」—— 二级索引查了两棵树\n");

        System.out.println("  覆盖索引（避免回表）：");
        System.out.println("    SELECT id, name FROM user WHERE name='Alice'");
        System.out.println("    → 查询列 id+name 都在索引 idx_name 中 → 不需要回表\n");
    }

    // =============================================================
    // 面试速记
    // =============================================================
    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. B+Tree vs B-Tree: B+Tree 非叶子不存数据 → 更矮 + 叶子链表 → 范围快");
        System.out.println("  2. 聚簇索引: 叶子存整行，每表只有一个，按主键排序");
        System.out.println("  3. 二级索引: 叶子存主键值，查完还要回表");
        System.out.println("  4. 覆盖索引: 查询列都在索引里，不需要回表");
        System.out.println("  5. 2000万行只需 3 次 IO（B+Tree 高度=3）");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * D22 全部知识点：
 *   1. BTreeIndexSimulationDemo   — B+Tree 结构模拟（本类）
 *   2. IndexOptimizationDemo      — 真实 MySQL 索引优化实战
 *
 * W04 完整路线：
 *   D22 → 索引原理（B+Tree、聚簇/二级索引、回表、覆盖索引）
 *   D23 → 执行计划（EXPLAIN 逐字段解读、慢查询优化）
 *   D24 → 事务隔离（MVCC、4种隔离级别）
 *   D25 → 锁机制（行锁、间隙锁、死锁排查）
 *   D26 → Redis数据结构（5种结构+场景）
 *   D27 → Redis持久化（RDB vs AOF）
 *   D28 → 缓存问题（穿透/击穿/雪崩 + 一致性）
 *   D29 → 综合实战（缓存架构设计）
 */
