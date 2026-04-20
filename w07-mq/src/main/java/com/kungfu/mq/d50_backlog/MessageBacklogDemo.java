package com.kungfu.mq.d50_backlog;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * 【Demo】消息积压 — 排查和解决
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>什么是消息积压（Lag）</li>
 *   <li>用 AdminClient 检测消费组 Lag</li>
 *   <li>模拟积压：快速生产 + 慢速消费</li>
 *   <li>解决方案：扩容/提速/跳过/死信</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Kafka 运行在 localhost:9092
 *
 * @author kungfu
 * @since D50 - 消息积压
 */
public class MessageBacklogDemo {

    private static final String BOOTSTRAP = "localhost:9092";
    private static final String TOPIC = "demo-d50-backlog";
    private static final String GROUP = "demo-d50-group";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  消息积压 — 排查和解决");
        System.out.println("========================================\n");

        showConcept();
        demoProduceBacklog();
        demoCheckLag();
        showSolutions();
        showMonitoring();
    }

    private static void showConcept() {
        System.out.println("=== 一、什么是消息积压 ===\n");

        System.out.println("  Lag = 最新 Offset - 消费者已提交 Offset\n");

        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ Partition-0:                                                │");
        System.out.println("  │                                                            │");
        System.out.println("  │ [0][1][2][3][4][5][6][7][8][9][10][11][12][13][14][15]     │");
        System.out.println("  │              ↑                                    ↑        │");
        System.out.println("  │         Consumer Offset=4                   Latest Offset=15│");
        System.out.println("  │                                                            │");
        System.out.println("  │         Lag = 15 - 4 = 11 条消息积压                       │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");

        System.out.println("  积压原因：");
        System.out.println("    1. 消费者处理速度 < 生产者发送速度");
        System.out.println("    2. 消费者异常（OOM、死锁、外部依赖超时）");
        System.out.println("    3. 消费者数量不足（< Partition 数）");
        System.out.println("    4. 单条消息处理耗时过长（如调用慢接口）\n");
    }

    private static void demoProduceBacklog() {
        System.out.println("=== 二、模拟积压（快速生产 1000 条）===\n");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "5");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "key_" + i, "backlog_msg_" + i));
            }
            producer.flush();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  ✓ 发送 1000 条消息，耗时 " + elapsed + " ms");
            System.out.println("  → 如果消费者没有运行，这 1000 条就是积压\n");

        } catch (Exception e) {
            System.out.println("  ✗ Kafka 连接失败: " + e.getMessage());
            System.out.println("  → 模拟：假设已积压 1000 条消息\n");
        }
    }

    private static void demoCheckLag() {
        System.out.println("=== 三、检测消费组 Lag ===\n");

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000");

        try (AdminClient admin = AdminClient.create(props)) {
            // 获取消费组已提交的 offset
            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(GROUP)
                            .partitionsToOffsetAndMetadata().get();

            // 获取 Topic 最新 offset
            Set<TopicPartition> partitions = new HashSet<>();
            if (committed.isEmpty()) {
                // 消费组还没消费过，手动构造 partition 列表
                TopicDescription desc = admin.describeTopics(Collections.singleton(TOPIC))
                        .allTopicNames().get().get(TOPIC);
                if (desc != null) {
                    for (int i = 0; i < desc.partitions().size(); i++) {
                        partitions.add(new TopicPartition(TOPIC, i));
                    }
                }
            } else {
                partitions.addAll(committed.keySet());
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(buildLatestSpec(partitions)).all().get();

            // 计算 Lag
            long totalLag = 0;
            System.out.println("  ┌──────────────┬──────────────┬──────────────┬──────────────┐");
            System.out.println("  │ Partition    │ Committed    │ End Offset   │ Lag          │");
            System.out.println("  ├──────────────┼──────────────┼──────────────┼──────────────┤");

            for (TopicPartition tp : partitions) {
                long committedOffset = committed.containsKey(tp) ? committed.get(tp).offset() : 0;
                long endOffset = endOffsets.containsKey(tp) ? endOffsets.get(tp).offset() : 0;
                long lag = endOffset - committedOffset;
                totalLag += lag;
                System.out.printf("  │ %-12s│ %-12d│ %-12d│ %-12d│%n",
                        tp.partition(), committedOffset, endOffset, lag);
            }
            System.out.println("  └──────────────┴──────────────┴──────────────┴──────────────┘");
            System.out.println("  总 Lag: " + totalLag + " 条\n");

        } catch (Exception e) {
            System.out.println("  ✗ 检测失败: " + e.getMessage());
            System.out.println("  → 等效命令: kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group " + GROUP + "\n");
        }
    }

    private static Map<TopicPartition, OffsetSpec> buildLatestSpec(Set<TopicPartition> partitions) {
        Map<TopicPartition, OffsetSpec> spec = new HashMap<>();
        for (TopicPartition tp : partitions) {
            spec.put(tp, OffsetSpec.latest());
        }
        return spec;
    }

    private static void showSolutions() {
        System.out.println("=== 四、积压解决方案 ===\n");

        System.out.println("  ┌──────────────────────┬──────────────────────────────────────────────┐");
        System.out.println("  │ 方案                 │ 说明                                          │");
        System.out.println("  ├──────────────────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 1. 扩容 Consumer     │ 增加消费者数量（最多 = Partition 数）         │");
        System.out.println("  │ 2. 增加 Partition    │ 临时增加分区 + 对应增加 Consumer              │");
        System.out.println("  │ 3. 提高消费速度      │ 批量处理 / 异步处理 / 减少外部调用           │");
        System.out.println("  │ 4. 跳过问题消息      │ 记录到死信队列，跳过继续消费                 │");
        System.out.println("  │ 5. 临时转储          │ 消费者只做转储到 DB/文件，后续慢慢处理       │");
        System.out.println("  └──────────────────────┴──────────────────────────────────────────────┘\n");

        System.out.println("  紧急处理流程（线上积压 SOP）：");
        System.out.println("    1. 确认积压量和增长速度（Lag 是否还在增加）");
        System.out.println("    2. 排查消费者是否异常（日志、GC、外部依赖）");
        System.out.println("    3. 如果是消费者 bug → 修复后重启");
        System.out.println("    4. 如果是处理能力不足 → 扩容 Consumer");
        System.out.println("    5. 如果积压太多需要快速消化 → 临时转储方案\n");
    }

    private static void showMonitoring() {
        System.out.println("=== 五、监控告警 ===\n");

        System.out.println("  监控指标：");
        System.out.println("    - kafka_consumergroup_lag（Prometheus + kafka_exporter）");
        System.out.println("    - 消费速率 = (current_offset - offset_5min_ago) / 300");
        System.out.println("    - 积压消化时间 = lag / 消费速率\n");

        System.out.println("  告警规则：");
        System.out.println("    - Lag > 10000 → P3 告警（关注）");
        System.out.println("    - Lag > 100000 → P2 告警（处理）");
        System.out.println("    - Lag 持续增长 5 分钟 → P1 告警（紧急）");
        System.out.println("    - Consumer 无消费（offset 不变）→ P1 告警\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: 消息积压怎么处理？");
        System.out.println("    A: 1) 先排查消费者是否异常（bug/GC/依赖超时）");
        System.out.println("       2) 扩容 Consumer（最多等于 Partition 数）");
        System.out.println("       3) 紧急情况：临时转储到 DB，后续慢慢处理");
        System.out.println("       4) 长期：增加 Partition + Consumer，提高消费并行度");
        System.out.println();
    }
}
