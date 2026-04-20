package com.kungfu.storage.d24_transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 【Demo】手写 MVCC 模拟 — 理解 MySQL InnoDB 多版本并发控制
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>手写 Undo Log 版本链：每次修改产生新版本，旧版本通过 next 指针串联</li>
 *   <li>手写 ReadView：活跃事务列表 m_ids、min_trx_id、max_trx_id、creator_trx_id</li>
 *   <li>可见性判断算法：逐版本遍历，判断当前事务能看到哪个版本</li>
 *   <li>RC vs RR 的核心区别：RC 每次读创建新 ReadView，RR 只用第一次的 ReadView</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："MVCC 的原理是什么？RC 和 RR 的区别在哪？"
 * MVCC 是 InnoDB 实现非锁定读的核心机制，理解版本链 + ReadView 才能回答清楚
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main 方法，不需要 MySQL 连接
 *
 * @author kungfu
 * @since D24 - 事务隔离
 */
public class MiniMVCCDemo {

    // =============================================================
    // 一、Undo Log 版本链节点
    // =============================================================

    /**
     * 模拟 InnoDB 的 Undo Log 版本链中的一个节点。
     * 每次事务修改一行数据，都会在版本链头部插入一个新节点，
     * 旧版本通过 next 指针连接，形成单向链表。
     */
    static class UndoLogEntry {
        long trxId;          // 修改这行的事务 ID
        String value;        // 该版本的数据值
        UndoLogEntry next;   // 指向更旧的版本

        UndoLogEntry(long trxId, String value, UndoLogEntry next) {
            this.trxId = trxId;
            this.value = value;
            this.next = next;
        }
    }

    // =============================================================
    // 二、ReadView（一致性读视图）
    // =============================================================

    /**
     * 模拟 InnoDB 的 ReadView。
     * 事务执行 SELECT 时创建 ReadView，用于判断版本链中哪个版本对自己可见。
     *
     * <pre>
     * 4 个关键字段：
     *   m_ids          — 创建 ReadView 时所有「活跃」（未提交）的事务 ID 列表
     *   min_trx_id     — m_ids 中的最小值
     *   max_trx_id     — 系统将要分配的下一个事务 ID（当前最大 + 1）
     *   creator_trx_id — 创建这个 ReadView 的事务自身的 ID
     * </pre>
     */
    static class ReadView {
        List<Long> mIds;      // 活跃事务列表
        long minTrxId;        // 最小活跃事务 ID
        long maxTrxId;        // 下一个待分配的事务 ID
        long creatorTrxId;    // 创建 ReadView 的事务 ID

        ReadView(long creatorTrxId, List<Long> activeTrxIds, long nextTrxId) {
            this.creatorTrxId = creatorTrxId;
            this.mIds = new ArrayList<>(activeTrxIds);
            this.minTrxId = activeTrxIds.isEmpty() ? nextTrxId : activeTrxIds.stream().min(Long::compareTo).get();
            this.maxTrxId = nextTrxId;
        }

        @Override
        public String toString() {
            return String.format("ReadView{creator=%d, m_ids=%s, min=%d, max=%d}",
                    creatorTrxId, mIds, minTrxId, maxTrxId);
        }
    }

    // =============================================================
    // 三、可见性判断算法
    // =============================================================

    /**
     * InnoDB 可见性判断规则：
     * <pre>
     * 遍历版本链，对每个版本的 trx_id 做如下判断：
     *
     * 1. trx_id == creator_trx_id          → 自己修改的，可见 ✓
     * 2. trx_id < min_trx_id               → 事务已提交（在 ReadView 创建前），可见 ✓
     * 3. trx_id >= max_trx_id              → 事务在 ReadView 之后开启，不可见 ✗
     * 4. min_trx_id <= trx_id < max_trx_id
     *    4a. trx_id 在 m_ids 中            → 事务还未提交，不可见 ✗
     *    4b. trx_id 不在 m_ids 中          → 事务已提交，可见 ✓
     * </pre>
     */
    static boolean isVisible(long trxId, ReadView view) {
        // 规则1: 自己的修改
        if (trxId == view.creatorTrxId) return true;
        // 规则2: 比最小活跃事务还小 → 已提交
        if (trxId < view.minTrxId) return true;
        // 规则3: 大于等于下一个待分配 ID → ReadView 创建后才开启的事务
        if (trxId >= view.maxTrxId) return false;
        // 规则4: 在 [min, max) 范围内，看是否在活跃列表中
        return !view.mIds.contains(trxId);
    }

    /**
     * 沿版本链查找对给定 ReadView 可见的第一个版本
     */
    static String findVisibleVersion(UndoLogEntry head, ReadView view) {
        UndoLogEntry current = head;
        while (current != null) {
            boolean visible = isVisible(current.trxId, view);
            System.out.printf("      版本 [trx_id=%d, value='%s'] → %s%n",
                    current.trxId, current.value, visible ? "可见 ✓" : "不可见 ✗");
            if (visible) {
                return current.value;
            }
            current = current.next;
        }
        return null; // 所有版本都不可见
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  手写 MVCC 模拟（版本链 + ReadView）");
        System.out.println("========================================\n");

        // 一、MVCC 原理图解
        showMVCCPrinciple();

        // 二、构建版本链
        UndoLogEntry versionChain = buildVersionChain();

        // 三、ReadView 可见性算法
        showVisibilityAlgorithm();

        // 四、RC vs RR 对比
        demonstrateRCvsRR(versionChain);

        // 面试速记
        showInterviewTips();
    }

    // =============================================================
    // 一、MVCC 原理图解
    // =============================================================

    private static void showMVCCPrinciple() {
        System.out.println("=== 一、MVCC 核心原理 ===\n");

        System.out.println("  MVCC = Multi-Version Concurrency Control（多版本并发控制）");
        System.out.println("  核心思想：读不加锁，读写不冲突，通过版本链实现一致性读\n");

        System.out.println("  三大组件：");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │  1. 隐藏列                                                   │");
        System.out.println("  │     DB_TRX_ID  — 最后修改该行的事务 ID                       │");
        System.out.println("  │     DB_ROLL_PTR — 回滚指针，指向 Undo Log 中的旧版本          │");
        System.out.println("  │                                                              │");
        System.out.println("  │  2. Undo Log 版本链                                          │");
        System.out.println("  │     每次修改，旧数据写入 Undo Log，通过指针串成链表            │");
        System.out.println("  │     最新版本 → 次新版本 → ... → 最老版本                     │");
        System.out.println("  │                                                              │");
        System.out.println("  │  3. ReadView（一致性读视图）                                  │");
        System.out.println("  │     事务做快照读时创建，记录当时的活跃事务列表                  │");
        System.out.println("  │     用于判断版本链中哪个版本对自己可见                         │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、构建版本链
    // =============================================================

    private static UndoLogEntry buildVersionChain() {
        System.out.println("=== 二、模拟 3 个事务修改同一行 ===\n");

        System.out.println("  场景：t_account 表中 Alice 的 balance 字段\n");
        System.out.println("  时间线：");
        System.out.println("  ──────────────────────────────────────────────────────────────");
        System.out.println("  T1(trx=100)    T2(trx=200)    T3(trx=300)    时间 ↓");
        System.out.println("  ──────────────────────────────────────────────────────────────");
        System.out.println("  BEGIN");
        System.out.println("  UPDATE → 1000                                  t1");
        System.out.println("  COMMIT                                         t2");
        System.out.println("                  BEGIN                           t3");
        System.out.println("                  UPDATE → 2000                   t4");
        System.out.println("                                  BEGIN           t5");
        System.out.println("                                  SELECT(?)       t6 ← 这里读到什么？");
        System.out.println("                  COMMIT                          t7");
        System.out.println("                                  SELECT(?)       t8 ← 再读一次呢？");
        System.out.println("  ──────────────────────────────────────────────────────────────\n");

        // 构建版本链（从旧到新）
        // 初始值：trx=100 写入 '1000'（已提交）
        UndoLogEntry v1 = new UndoLogEntry(100, "1000", null);
        // trx=200 修改为 '2000'（未提交 → 后来提交）
        UndoLogEntry v2 = new UndoLogEntry(200, "2000", v1);

        System.out.println("  版本链（头部是最新版本）：");
        System.out.println("  ┌──────────────────┐     ┌──────────────────┐");
        System.out.println("  │ trx_id = 200     │     │ trx_id = 100     │");
        System.out.println("  │ value  = '2000'  │────>│ value  = '1000'  │───> NULL");
        System.out.println("  │ （T2 未提交）    │     │ （T1 已提交）    │");
        System.out.println("  └──────────────────┘     └──────────────────┘");
        System.out.println("        ↑ 最新版本                ↑ 旧版本\n");

        return v2;
    }

    // =============================================================
    // 三、可见性算法图解
    // =============================================================

    private static void showVisibilityAlgorithm() {
        System.out.println("=== 三、ReadView 可见性判断算法 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │              ReadView 可见性判断流程                          │");
        System.out.println("  │                                                              │");
        System.out.println("  │  对版本链中每个版本的 trx_id 做判断：                         │");
        System.out.println("  │                                                              │");
        System.out.println("  │  trx_id == creator_trx_id ?  ──── YES ──→ 可见 ✓（自己改的） │");
        System.out.println("  │         │ NO                                                 │");
        System.out.println("  │         ↓                                                    │");
        System.out.println("  │  trx_id < min_trx_id ?       ──── YES ──→ 可见 ✓（已提交）  │");
        System.out.println("  │         │ NO                                                 │");
        System.out.println("  │         ↓                                                    │");
        System.out.println("  │  trx_id >= max_trx_id ?      ──── YES ──→ 不可见 ✗（未来的） │");
        System.out.println("  │         │ NO                                                 │");
        System.out.println("  │         ↓                                                    │");
        System.out.println("  │  trx_id 在 m_ids 中 ?        ──── YES ──→ 不可见 ✗（未提交） │");
        System.out.println("  │         │ NO                                                 │");
        System.out.println("  │         ↓                                                    │");
        System.out.println("  │  可见 ✓（已提交）                                            │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 四、RC vs RR 对比演示
    // =============================================================

    private static void demonstrateRCvsRR(UndoLogEntry versionChain) {
        System.out.println("=== 四、RC vs RR 隔离级别对比 ===\n");

        System.out.println("  核心区别：");
        System.out.println("  ┌────────────────────┬──────────────────────────────────────────┐");
        System.out.println("  │ 隔离级别           │ ReadView 创建时机                        │");
        System.out.println("  ├────────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ READ COMMITTED     │ 每次 SELECT 都创建新的 ReadView           │");
        System.out.println("  │ REPEATABLE READ    │ 事务第一次 SELECT 时创建，之后一直复用    │");
        System.out.println("  └────────────────────┴──────────────────────────────────────────┘\n");

        // -------------------------------------------------------
        // 场景：T3(trx=300) 在 T2(trx=200) 提交前后各读一次
        //   T1(trx=100) 已提交
        //   T2(trx=200) 先未提交，后提交
        //   T3(trx=300) 做两次 SELECT
        // -------------------------------------------------------

        System.out.println("  ───────────────────────────────────────────────────────");
        System.out.println("  ★ READ COMMITTED（RC）：每次读都创建新 ReadView");
        System.out.println("  ───────────────────────────────────────────────────────\n");

        // 第一次读：T2(200) 未提交，活跃列表 = [200, 300]
        System.out.println("  【第一次 SELECT】T2 未提交时");
        ReadView rcView1 = new ReadView(300, Arrays.asList(200L, 300L), 301);
        System.out.println("    创建 " + rcView1);
        System.out.println("    遍历版本链：");
        String rcResult1 = findVisibleVersion(versionChain, rcView1);
        System.out.println("    → T3 读到: balance = " + rcResult1 + "\n");

        // 第二次读：T2(200) 已提交，活跃列表 = [300]
        System.out.println("  【第二次 SELECT】T2 已提交后");
        ReadView rcView2 = new ReadView(300, Arrays.asList(300L), 301);
        System.out.println("    创建 " + rcView2);
        System.out.println("    遍历版本链：");
        String rcResult2 = findVisibleVersion(versionChain, rcView2);
        System.out.println("    → T3 读到: balance = " + rcResult2 + "\n");

        System.out.println("    ★ RC 结果: 第一次读 " + rcResult1 + "，第二次读 " + rcResult2);
        System.out.println("      两次读到不同值 → 不可重复读！\n");

        System.out.println("  ───────────────────────────────────────────────────────");
        System.out.println("  ★ REPEATABLE READ（RR）：始终用第一次的 ReadView");
        System.out.println("  ───────────────────────────────────────────────────────\n");

        // 第一次读：同 RC，T2(200) 未提交
        System.out.println("  【第一次 SELECT】T2 未提交时");
        ReadView rrView = new ReadView(300, Arrays.asList(200L, 300L), 301);
        System.out.println("    创建 " + rrView);
        System.out.println("    遍历版本链：");
        String rrResult1 = findVisibleVersion(versionChain, rrView);
        System.out.println("    → T3 读到: balance = " + rrResult1 + "\n");

        // 第二次读：T2(200) 已经提交了，但 RR 复用第一次的 ReadView
        System.out.println("  【第二次 SELECT】T2 已提交，但 RR 复用第一次 ReadView！");
        System.out.println("    复用 " + rrView);
        System.out.println("    遍历版本链：");
        String rrResult2 = findVisibleVersion(versionChain, rrView);
        System.out.println("    → T3 读到: balance = " + rrResult2 + "\n");

        System.out.println("    ★ RR 结果: 第一次读 " + rrResult1 + "，第二次读 " + rrResult2);
        System.out.println("      两次读到相同值 → 可重复读！\n");

        // 总结对比
        System.out.println("  ┌─────────────┬──────────────┬──────────────┬────────────────────────┐");
        System.out.println("  │ 隔离级别    │ 第一次读     │ 第二次读     │ 原因                   │");
        System.out.println("  ├─────────────┼──────────────┼──────────────┼────────────────────────┤");
        System.out.println("  │ RC          │ 1000         │ 2000         │ 每次读新建 ReadView    │");
        System.out.println("  │ RR ★       │ 1000         │ 1000         │ 复用第一次 ReadView    │");
        System.out.println("  └─────────────┴──────────────┴──────────────┴────────────────────────┘\n");
    }

    // =============================================================
    // 面试速记
    // =============================================================

    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  1. MVCC = 隐藏列(DB_TRX_ID, DB_ROLL_PTR) + Undo Log 版本链 + ReadView");
        System.out.println("  2. ReadView 4 个字段: m_ids, min_trx_id, max_trx_id, creator_trx_id");
        System.out.println("  3. 可见性判断: 自己的→可见, 已提交→可见, 未来的→不可见, 活跃的→不可见");
        System.out.println("  4. RC vs RR 的唯一区别: ReadView 创建时机");
        System.out.println("     RC: 每次 SELECT 新建 ReadView → 能看到其他事务的最新提交 → 不可重复读");
        System.out.println("     RR: 第一次 SELECT 创建后复用 → 看不到后续提交 → 可重复读");
        System.out.println("  5. MVCC 只对 SELECT（快照读）生效，UPDATE/DELETE 是当前读（加锁）");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * D24 全部知识点：
 *   1. MiniMVCCDemo          — 手写 MVCC 模拟（本类）
 *   2. IsolationLevelDemo    — 真实 MySQL 4 种隔离级别实战
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
