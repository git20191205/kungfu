package com.kungfu.distributed.d31_cap;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】CAP/BASE 理论 — 分布式系统的基石
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>CAP 定理的三个维度 + 为什么只能三选二</li>
 *   <li>模拟 CP 系统：写入时要求所有节点确认（牺牲可用性）</li>
 *   <li>模拟 AP 系统：写入只要一个节点成功就返回（牺牲一致性）</li>
 *   <li>BASE 理论 + 最终一致性模拟</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："说说你对 CAP 定理的理解？"
 * 这是分布式系统设计的第一性原理，所有架构决策都基于此
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D31 - CAP/BASE理论
 */
public class CAPAndBASEDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  CAP/BASE 理论");
        System.out.println("========================================\n");

        showCAPTheory();
        simulateCPSystem();
        simulateAPSystem();
        showBASETheory();
        simulateEventualConsistency();
        showInterviewTips();
    }

    // =============================================================
    // CAP 定理
    // =============================================================
    private static void showCAPTheory() {
        System.out.println("=== 一、CAP 定理 ===\n");

        System.out.println("  CAP = Consistency + Availability + Partition tolerance\n");

        System.out.println("  ┌──────────────────┬──────────────────────────────────────────────────┐");
        System.out.println("  │ 维度             │ 含义                                              │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────────────────┤");
        System.out.println("  │ C (一致性)       │ 所有节点在同一时刻看到相同的数据                  │");
        System.out.println("  │ A (可用性)       │ 每个请求都能在合理时间内收到非错误响应            │");
        System.out.println("  │ P (分区容错)     │ 网络分区发生时系统仍能继续运行                    │");
        System.out.println("  └──────────────────┴──────────────────────────────────────────────────┘\n");

        System.out.println("  为什么只能三选二？");
        System.out.println("    网络分区（P）在分布式系统中必然发生 → P 必须保证");
        System.out.println("    → 实际选择是 CP 还是 AP\n");

        System.out.println("  ┌──────┬──────────────────────────┬──────────────────────────────────┐");
        System.out.println("  │ 选择 │ 代表系统                 │ 特点                              │");
        System.out.println("  ├──────┼──────────────────────────┼──────────────────────────────────┤");
        System.out.println("  │ CP   │ ZooKeeper, Etcd, HBase   │ 分区时拒绝写入，保证数据一致     │");
        System.out.println("  │ AP   │ Eureka, Cassandra, DNS   │ 分区时继续服务，允许数据不一致   │");
        System.out.println("  │ CA   │ 单机 MySQL（非分布式）   │ 不存在网络分区 → 不是分布式系统  │");
        System.out.println("  └──────┴──────────────────────────┴──────────────────────────────────┘\n");
    }

    // =============================================================
    // 模拟 CP 系统
    // =============================================================
    private static void simulateCPSystem() throws Exception {
        System.out.println("=== 二、模拟 CP 系统（写入需所有节点确认）===\n");

        String[] nodes = {"Node-A", "Node-B", "Node-C"};
        boolean nodeB_partitioned = true; // 模拟 Node-B 网络分区

        System.out.println("  场景：3 节点集群，Node-B 发生网络分区");
        System.out.println("  CP 策略：写入需要所有节点确认\n");

        System.out.println("  写入 key=order_001, value=created...");
        boolean allAcked = true;
        for (String node : nodes) {
            if (node.equals("Node-B") && nodeB_partitioned) {
                System.out.println("    " + node + ": ✗ 网络超时（分区中）");
                allAcked = false;
            } else {
                System.out.println("    " + node + ": ✓ 写入成功");
            }
        }

        if (!allAcked) {
            System.out.println("\n  → CP 决策：写入失败！回滚所有节点");
            System.out.println("  → 保证了一致性（C），但牺牲了可用性（A）");
            System.out.println("  → 客户端收到错误响应，需要重试\n");
        }
    }

    // =============================================================
    // 模拟 AP 系统
    // =============================================================
    private static void simulateAPSystem() throws Exception {
        System.out.println("=== 三、模拟 AP 系统（写入只需一个节点成功）===\n");

        String[] nodes = {"Node-A", "Node-B", "Node-C"};
        boolean nodeB_partitioned = true;

        System.out.println("  场景：同样 3 节点集群，Node-B 网络分区");
        System.out.println("  AP 策略：写入只需一个节点成功即返回\n");

        System.out.println("  写入 key=order_001, value=created...");
        boolean anyAcked = false;
        for (String node : nodes) {
            if (node.equals("Node-B") && nodeB_partitioned) {
                System.out.println("    " + node + ": ✗ 网络超时（分区中，稍后异步同步）");
            } else {
                System.out.println("    " + node + ": ✓ 写入成功");
                anyAcked = true;
            }
        }

        if (anyAcked) {
            System.out.println("\n  → AP 决策：写入成功！立即返回客户端");
            System.out.println("  → 保证了可用性（A），但 Node-B 数据暂时不一致");
            System.out.println("  → 分区恢复后，Node-B 通过异步同步达到最终一致\n");
        }

        System.out.println("  读取对比：");
        System.out.println("    读 Node-A: order_001 = created  ✓");
        System.out.println("    读 Node-B: order_001 = (不存在) ✗ ← 数据不一致！");
        System.out.println("    → 这就是 AP 系统的代价\n");
    }

    // =============================================================
    // BASE 理论
    // =============================================================
    private static void showBASETheory() {
        System.out.println("=== 四、BASE 理论 ===\n");

        System.out.println("  BASE = Basically Available + Soft state + Eventually consistent\n");

        System.out.println("  ┌──────────────────┬──────────────────────────────────────────────────┐");
        System.out.println("  │ 维度             │ 含义                                              │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────────────────┤");
        System.out.println("  │ 基本可用         │ 允许部分功能降级（如返回缓存数据、排队等待）      │");
        System.out.println("  │ 软状态           │ 允许中间状态存在（如订单「支付中」状态）          │");
        System.out.println("  │ 最终一致         │ 经过一段时间后，所有节点数据最终一致              │");
        System.out.println("  └──────────────────┴──────────────────────────────────────────────────┘\n");

        System.out.println("  ACID vs BASE：");
        System.out.println("  ┌──────────┬──────────────────────┬──────────────────────┐");
        System.out.println("  │          │ ACID                 │ BASE                 │");
        System.out.println("  ├──────────┼──────────────────────┼──────────────────────┤");
        System.out.println("  │ 一致性   │ 强一致               │ 最终一致             │");
        System.out.println("  │ 可用性   │ 可能阻塞             │ 基本可用             │");
        System.out.println("  │ 适用场景 │ 银行转账             │ 电商下单             │");
        System.out.println("  │ 代表     │ MySQL 事务           │ 分布式消息队列       │");
        System.out.println("  └──────────┴──────────────────────┴──────────────────────┘\n");
    }

    // =============================================================
    // 模拟最终一致性
    // =============================================================
    private static void simulateEventualConsistency() throws Exception {
        System.out.println("=== 五、模拟最终一致性 ===\n");

        // 3 个节点，初始值都是 0
        AtomicInteger nodeA = new AtomicInteger(0);
        AtomicInteger nodeB = new AtomicInteger(0);
        AtomicInteger nodeC = new AtomicInteger(0);

        System.out.println("  初始状态: A=0, B=0, C=0");

        // 写入 Node-A
        nodeA.set(42);
        System.out.println("  T1: 写入 Node-A = 42");
        System.out.println("      状态: A=42, B=0, C=0 ← 不一致！\n");

        // 异步同步（模拟延迟）
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        CountDownLatch latch = new CountDownLatch(2);
        scheduler.schedule(() -> {
            nodeB.set(42);
            System.out.println("  T2: 异步同步 → Node-B = 42");
            System.out.println("      状态: A=42, B=42, C=0 ← 仍不一致");
            latch.countDown();
        }, 200, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> {
            nodeC.set(42);
            System.out.println("  T3: 异步同步 → Node-C = 42");
            System.out.println("      状态: A=42, B=42, C=42 ← 最终一致！✓");
            latch.countDown();
        }, 400, TimeUnit.MILLISECONDS);

        latch.await(2, TimeUnit.SECONDS);
        scheduler.shutdown();

        System.out.println("\n  → 经过短暂的不一致窗口，所有节点最终达到一致");
        System.out.println("  → 这就是「最终一致性」的含义\n");
    }

    // =============================================================
    // 面试速记
    // =============================================================
    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. CAP 三选二，但 P 必须保证 → 实际选 CP 或 AP");
        System.out.println("  2. CP 代表：ZooKeeper（分区时拒绝写入）");
        System.out.println("  3. AP 代表：Eureka（分区时继续服务，数据可能不一致）");
        System.out.println("  4. BASE 是对 ACID 的妥协：基本可用 + 软状态 + 最终一致");
        System.out.println("  5. 大多数互联网系统选 AP + 最终一致（如电商、社交）");
        System.out.println("  6. 金融系统选 CP + 强一致（如银行转账）");
        System.out.println();
    }
}
