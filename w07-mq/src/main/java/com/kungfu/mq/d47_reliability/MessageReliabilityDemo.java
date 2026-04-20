package com.kungfu.mq.d47_reliability;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * 【Demo】消息可靠性 — 不丢失配置
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>消息丢失的 3 个环节：Producer→Broker、Broker存储、Broker→Consumer</li>
 *   <li>Producer 可靠配置：acks=all + retries + idempotence</li>
 *   <li>Broker 可靠配置：min.insync.replicas + unclean.leader.election</li>
 *   <li>Consumer 可靠配置：手动提交 offset</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Kafka 运行在 localhost:9092
 *
 * @author kungfu
 * @since D47 - 消息可靠性
 */
public class MessageReliabilityDemo {

    private static final String BOOTSTRAP = "localhost:9092";
    private static final String TOPIC = "demo-d47-reliability";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  消息可靠性 — 不丢失配置");
        System.out.println("========================================\n");

        showLossScenarios();
        demoReliableProducer();
        showBrokerConfig();
        demoManualCommitConsumer();
        showDeliverySemantics();
    }

    private static void showLossScenarios() {
        System.out.println("=== 一、消息丢失的 3 个环节 ===\n");

        System.out.println("  Producer ──────→ Broker ──────→ Consumer");
        System.out.println("       ①              ②              ③\n");

        System.out.println("  ┌─────┬──────────────────────────────┬──────────────────────────────┐");
        System.out.println("  │ 环节│ 丢失原因                     │ 解决方案                      │");
        System.out.println("  ├─────┼──────────────────────────────┼──────────────────────────────┤");
        System.out.println("  │ ①  │ 网络故障/Broker 未确认        │ acks=all + retries + 回调确认 │");
        System.out.println("  │ ②  │ Leader 宕机，数据未同步到副本 │ min.insync.replicas=2        │");
        System.out.println("  │ ③  │ Consumer 消费后未提交 offset  │ 手动提交 + 幂等处理          │");
        System.out.println("  └─────┴──────────────────────────────┴──────────────────────────────┘\n");
    }

    private static void demoReliableProducer() {
        System.out.println("=== 二、可靠 Producer 配置 ===\n");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // ★ 可靠性配置
        props.put(ProducerConfig.ACKS_CONFIG, "all");           // 所有 ISR 副本确认
        props.put(ProducerConfig.RETRIES_CONFIG, "3");          // 失败重试 3 次
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // 幂等生产者（防重复）
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5"); // 幂等要求 ≤5

        System.out.println("  Producer 可靠性配置：");
        System.out.println("    acks=all          → 等待所有 ISR 副本确认");
        System.out.println("    retries=3         → 发送失败自动重试");
        System.out.println("    enable.idempotence=true → 幂等生产者（PID+SeqNum 去重）");
        System.out.println("    max.in.flight.requests.per.connection=5 → 幂等要求\n");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < 5; i++) {
                String value = "reliable_msg_" + i;
                ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "key_" + i, value);

                // 同步发送（等待确认）
                try {
                    RecordMetadata metadata = producer.send(record).get();
                    System.out.println("    ✓ 消息已确认: partition=" + metadata.partition()
                            + " offset=" + metadata.offset());
                } catch (Exception e) {
                    System.out.println("    ✗ 发送失败: " + e.getMessage());
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("  ✗ Kafka 连接失败: " + e.getMessage());
            System.out.println("  → 以下为理论内容\n");
        }
    }

    private static void showBrokerConfig() {
        System.out.println("=== 三、Broker 可靠性配置 ===\n");

        System.out.println("  server.properties（Broker 端）：");
        System.out.println("  ┌────────────────────────────────────────────────────────────┐");
        System.out.println("  │ # 最小同步副本数（acks=all 时生效）                        │");
        System.out.println("  │ min.insync.replicas=2                                      │");
        System.out.println("  │                                                            │");
        System.out.println("  │ # 禁止非 ISR 副本成为 Leader（防数据丢失）                │");
        System.out.println("  │ unclean.leader.election.enable=false                       │");
        System.out.println("  │                                                            │");
        System.out.println("  │ # 副本数（创建 Topic 时指定）                              │");
        System.out.println("  │ default.replication.factor=3                               │");
        System.out.println("  └────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 组合效果：");
        System.out.println("    acks=all + min.insync.replicas=2 + replication.factor=3");
        System.out.println("    → 消息必须写入至少 2 个副本才算成功");
        System.out.println("    → 即使 1 个 Broker 宕机，数据也不丢失\n");
    }

    private static void demoManualCommitConsumer() {
        System.out.println("=== 四、手动提交 Offset（Consumer 端）===\n");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "demo-d47-reliable-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // ★ 关闭自动提交
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        System.out.println("  enable.auto.commit=false → 手动控制 offset 提交时机\n");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
            System.out.println("  拉取到 " + records.count() + " 条消息");

            for (ConsumerRecord<String, String> record : records) {
                // 1. 处理消息
                System.out.println("    处理: " + record.value());
                // 2. 处理成功后才提交 offset
            }

            if (!records.isEmpty()) {
                consumer.commitSync(); // 同步提交
                System.out.println("  ✓ 手动提交 offset 成功\n");
            }

        } catch (Exception e) {
            System.out.println("  ✗ Kafka 连接失败: " + e.getMessage() + "\n");
        }

        System.out.println("  手动提交的好处：");
        System.out.println("    - 处理失败 → 不提交 → 下次重新消费（at-least-once）");
        System.out.println("    - 自动提交 → 处理失败但 offset 已提交 → 消息丢失（at-most-once）\n");
    }

    private static void showDeliverySemantics() {
        System.out.println("=== 五、投递语义对比 ===\n");

        System.out.println("  ┌──────────────────┬──────────────────────────────────────────────────┐");
        System.out.println("  │ 语义             │ 实现方式                                          │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────────────────┤");
        System.out.println("  │ At-Most-Once     │ 自动提交 offset → 可能丢消息                     │");
        System.out.println("  │ At-Least-Once ★ │ 手动提交 + 处理后提交 → 可能重复                 │");
        System.out.println("  │ Exactly-Once     │ 幂等 Producer + 事务 + 手动提交 → 最强但最慢     │");
        System.out.println("  └──────────────────┴──────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 生产推荐：At-Least-Once + 消费端幂等（D48 详解）");
        System.out.println("    原因：Exactly-Once 性能开销大，大多数场景用幂等兜底更实际");
        System.out.println();
    }
}
