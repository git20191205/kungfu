package com.kungfu.mq.d45_architecture;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * 【Demo】Kafka 架构 — 分区 + 副本 + 消费组
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Kafka 整体架构图</li>
 *   <li>用 AdminClient 探索集群：列出 Topic、描述分区和副本</li>
 *   <li>创建 Demo Topic（3 分区）</li>
 *   <li>Kafka vs RocketMQ vs RabbitMQ 对比</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Kafka 运行在 localhost:9092
 *
 * @author kungfu
 * @since D45 - Kafka架构
 */
public class KafkaArchitectureDemo {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEMO_TOPIC = "demo-d45-architecture";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Kafka 架构");
        System.out.println("========================================\n");

        showArchitecture();
        showPartitionAndReplica();
        exploreCluster();
        showConsumerGroup();
        showComparison();
    }

    private static void showArchitecture() {
        System.out.println("=== 一、Kafka 整体架构 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────────┐");
        System.out.println("  │                         Kafka Cluster                             │");
        System.out.println("  │  ┌──────────┐  ┌──────────┐  ┌──────────┐                       │");
        System.out.println("  │  │ Broker-0 │  │ Broker-1 │  │ Broker-2 │                       │");
        System.out.println("  │  │ P0(L)    │  │ P1(L)    │  │ P2(L)    │  ← Leader 分散       │");
        System.out.println("  │  │ P1(F)    │  │ P2(F)    │  │ P0(F)    │  ← Follower 副本     │");
        System.out.println("  │  └──────────┘  └──────────┘  └──────────┘                       │");
        System.out.println("  └──────────────────────────────────────────────────────────────────┘");
        System.out.println("         ↑ 写入                              ↓ 消费");
        System.out.println("  ┌──────────────┐                    ┌──────────────────────┐");
        System.out.println("  │  Producer    │                    │  Consumer Group      │");
        System.out.println("  │  (生产者)    │                    │  C0←P0  C1←P1  C2←P2│");
        System.out.println("  └──────────────┘                    └──────────────────────┘\n");

        System.out.println("  核心概念：");
        System.out.println("    Topic:     逻辑分类（如 order-topic, payment-topic）");
        System.out.println("    Partition: 物理分片，一个 Topic 分成 N 个 Partition");
        System.out.println("    Broker:    Kafka 服务器节点");
        System.out.println("    Replica:   副本（Leader 读写，Follower 同步备份）");
        System.out.println("    Offset:    消息在 Partition 中的唯一序号（递增）\n");
    }

    private static void showPartitionAndReplica() {
        System.out.println("=== 二、分区与副本 ===\n");

        System.out.println("  Partition（分区）— 并行度的基本单位：");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ Topic: order-topic (3 partitions)                          │");
        System.out.println("  │                                                            │");
        System.out.println("  │ Partition-0: [msg0, msg3, msg6, msg9, ...]  → Broker-0    │");
        System.out.println("  │ Partition-1: [msg1, msg4, msg7, msg10, ...] → Broker-1    │");
        System.out.println("  │ Partition-2: [msg2, msg5, msg8, msg11, ...] → Broker-2    │");
        System.out.println("  │                                                            │");
        System.out.println("  │ ★ 同一 Partition 内消息有序，跨 Partition 无序             │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");

        System.out.println("  Replica（副本）— 高可用保障：");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ Partition-0 (replication-factor=3):                        │");
        System.out.println("  │   Leader  (Broker-0): 处理所有读写请求                     │");
        System.out.println("  │   Follower(Broker-1): 从 Leader 同步数据                   │");
        System.out.println("  │   Follower(Broker-2): 从 Leader 同步数据                   │");
        System.out.println("  │                                                            │");
        System.out.println("  │ ISR (In-Sync Replicas): 与 Leader 保持同步的副本列表       │");
        System.out.println("  │   ISR = [Broker-0, Broker-1, Broker-2]                     │");
        System.out.println("  │   如果 Follower 落后太多 → 被踢出 ISR                     │");
        System.out.println("  │   Leader 宕机 → 从 ISR 中选新 Leader                      │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");
    }

    private static void exploreCluster() {
        System.out.println("=== 三、探索 Kafka 集群 ===\n");

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");

        try (AdminClient admin = AdminClient.create(props)) {
            // 描述集群
            DescribeClusterResult cluster = admin.describeCluster();
            Collection<Node> nodes = cluster.nodes().get();
            System.out.println("  集群节点：");
            for (Node node : nodes) {
                System.out.println("    " + node.idString() + " → " + node.host() + ":" + node.port());
            }
            System.out.println("  Controller: " + cluster.controller().get().idString());
            System.out.println();

            // 列出 Topic
            Set<String> topics = admin.listTopics().names().get();
            System.out.println("  现有 Topic (" + topics.size() + " 个)：");
            for (String t : topics) {
                System.out.println("    - " + t);
            }
            System.out.println();

            // 创建 Demo Topic
            NewTopic newTopic = new NewTopic(DEMO_TOPIC, 3, (short) 1);
            try {
                admin.createTopics(Collections.singleton(newTopic)).all().get();
                System.out.println("  ✓ 创建 Topic: " + DEMO_TOPIC + " (3 partitions, RF=1)");
            } catch (ExecutionException e) {
                if (e.getCause().getMessage().contains("already exists")) {
                    System.out.println("  Topic " + DEMO_TOPIC + " 已存在");
                } else {
                    throw e;
                }
            }

            // 描述 Topic
            Map<String, TopicDescription> desc = admin.describeTopics(Collections.singleton(DEMO_TOPIC)).allTopicNames().get();
            TopicDescription td = desc.get(DEMO_TOPIC);
            System.out.println("  Topic 详情：");
            System.out.println("    名称: " + td.name());
            System.out.println("    分区数: " + td.partitions().size());
            for (TopicPartitionInfo p : td.partitions()) {
                System.out.println("    Partition-" + p.partition()
                        + " Leader=" + p.leader().idString()
                        + " Replicas=" + p.replicas()
                        + " ISR=" + p.isr());
            }
            System.out.println();

        } catch (Exception e) {
            System.out.println("  ✗ Kafka 连接失败: " + e.getMessage());
            System.out.println("  → 请确保 Kafka 运行在 " + BOOTSTRAP_SERVERS);
            System.out.println("  → 以下为理论内容，跳过实际操作\n");
        }
    }

    private static void showConsumerGroup() {
        System.out.println("=== 四、消费组（Consumer Group）===\n");

        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ Topic: order-topic (3 partitions)                          │");
        System.out.println("  │                                                            │");
        System.out.println("  │ Consumer Group A (3 consumers):                            │");
        System.out.println("  │   Consumer-A0 ← Partition-0  (竞争消费)                   │");
        System.out.println("  │   Consumer-A1 ← Partition-1                                │");
        System.out.println("  │   Consumer-A2 ← Partition-2                                │");
        System.out.println("  │                                                            │");
        System.out.println("  │ Consumer Group B (2 consumers):                            │");
        System.out.println("  │   Consumer-B0 ← Partition-0, Partition-1  (一个消费多个)   │");
        System.out.println("  │   Consumer-B1 ← Partition-2                                │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");

        System.out.println("  规则：");
        System.out.println("    1. 同一 Group 内：一个 Partition 只能被一个 Consumer 消费");
        System.out.println("    2. 不同 Group 间：每个 Group 独立消费全部消息（广播）");
        System.out.println("    3. Consumer 数 > Partition 数 → 多余 Consumer 空闲");
        System.out.println("    4. Consumer 数 < Partition 数 → 一个 Consumer 消费多个 Partition\n");

        System.out.println("  ★ 面试：Consumer 数量最多等于 Partition 数量，再多没用\n");
    }

    private static void showComparison() {
        System.out.println("=== 五、消息队列对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │              │ Kafka ★         │ RocketMQ         │ RabbitMQ         │");
        System.out.println("  ├──────────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 吞吐量       │ 百万级/s         │ 十万级/s         │ 万级/s           │");
        System.out.println("  │ 延迟         │ ms 级            │ ms 级            │ us 级            │");
        System.out.println("  │ 可靠性       │ 高（副本机制）   │ 高（同步刷盘）   │ 高（镜像队列）   │");
        System.out.println("  │ 顺序消息     │ Partition 内有序 │ Queue 内有序     │ Queue 内有序     │");
        System.out.println("  │ 事务消息     │ ✓ (0.11+)       │ ✓ (原生支持)    │ ✗               │");
        System.out.println("  │ 延迟消息     │ ✗               │ ✓ (18个级别)    │ ✓ (TTL+DLX)     │");
        System.out.println("  │ 消息回溯     │ ✓ (按 offset)   │ ✓ (按时间)      │ ✗               │");
        System.out.println("  │ 适用场景     │ 大数据/日志/流   │ 电商/金融        │ 企业集成         │");
        System.out.println("  └──────────────┴──────────────────┴──────────────────┴──────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: 为什么 Kafka 吞吐量高？");
        System.out.println("    A: 1) 顺序写磁盘（比随机写快 1000 倍）");
        System.out.println("       2) Page Cache（利用 OS 缓存）");
        System.out.println("       3) 零拷贝（sendfile 系统调用）");
        System.out.println("       4) 批量发送 + 压缩");
        System.out.println("       5) 分区并行");
        System.out.println();
    }
}
