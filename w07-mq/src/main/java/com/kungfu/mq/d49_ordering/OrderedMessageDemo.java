package com.kungfu.mq.d49_ordering;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;

/**
 * 【Demo】顺序消息 — 保证消费顺序
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>为什么多 Partition 无法保证全局顺序</li>
 *   <li>解决方案：相同 key → 相同 Partition → 局部有序</li>
 *   <li>模拟订单生命周期消息的顺序消费</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Kafka 运行在 localhost:9092
 *
 * @author kungfu
 * @since D49 - 顺序消息
 */
public class OrderedMessageDemo {

    private static final String BOOTSTRAP = "localhost:9092";
    private static final String TOPIC = "demo-d49-ordering";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  顺序消息 — 保证消费顺序");
        System.out.println("========================================\n");

        showProblem();
        showSolution();
        demoOrderedProduce();
        demoOrderedConsume();
        showTradeoff();
    }

    private static void showProblem() {
        System.out.println("=== 一、问题：多 Partition 无全局顺序 ===\n");

        System.out.println("  场景：订单状态变更消息");
        System.out.println("    msg1: order_001 → CREATED");
        System.out.println("    msg2: order_001 → PAID");
        System.out.println("    msg3: order_001 → SHIPPED\n");

        System.out.println("  如果 3 条消息分散到不同 Partition：");
        System.out.println("    Partition-0: [SHIPPED]   ← Consumer-0 先消费到 SHIPPED");
        System.out.println("    Partition-1: [CREATED]   ← Consumer-1 后消费到 CREATED");
        System.out.println("    Partition-2: [PAID]      ← Consumer-2 最后消费到 PAID");
        System.out.println("    → 消费顺序: SHIPPED → CREATED → PAID → 乱序！\n");
    }

    private static void showSolution() {
        System.out.println("=== 二、解决方案：相同 Key → 相同 Partition ===\n");

        System.out.println("  Kafka 分区策略：hash(key) % partitionCount");
        System.out.println("  → 相同 key 的消息一定进入相同 Partition");
        System.out.println("  → 同一 Partition 内消息严格有序\n");

        System.out.println("  用 orderId 作为 key：");
        System.out.println("    key=order_001 → hash → Partition-1");
        System.out.println("    所有 order_001 的消息都在 Partition-1 中：");
        System.out.println("    Partition-1: [CREATED, PAID, SHIPPED] → 有序！\n");
    }

    private static void demoOrderedProduce() {
        System.out.println("=== 三、发送顺序消息 ===\n");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 模拟 3 个订单的生命周期消息
        String[][] orderMessages = {
            {"order_001", "CREATED"}, {"order_001", "PAID"}, {"order_001", "SHIPPED"},
            {"order_002", "CREATED"}, {"order_002", "PAID"},
            {"order_003", "CREATED"}, {"order_003", "PAID"}, {"order_003", "SHIPPED"}, {"order_003", "DELIVERED"}
        };

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            System.out.println("  发送 3 个订单的生命周期消息（key=orderId）：\n");

            for (String[] msg : orderMessages) {
                String key = msg[0];
                String value = msg[1];
                RecordMetadata metadata = producer.send(
                        new ProducerRecord<>(TOPIC, key, value)).get();
                System.out.println("    key=" + key + " value=" + value
                        + " → partition=" + metadata.partition());
            }
            System.out.println();
            System.out.println("  ★ 观察：相同 orderId 的消息都在同一个 partition\n");

        } catch (Exception e) {
            System.out.println("  ✗ Kafka 连接失败: " + e.getMessage());
            System.out.println("  → 模拟输出：");
            System.out.println("    key=order_001 value=CREATED  → partition=1");
            System.out.println("    key=order_001 value=PAID     → partition=1");
            System.out.println("    key=order_001 value=SHIPPED  → partition=1");
            System.out.println("    key=order_002 value=CREATED  → partition=0");
            System.out.println("    key=order_002 value=PAID     → partition=0");
            System.out.println("    → 相同 key 进入相同 partition ✓\n");
        }
    }

    private static void demoOrderedConsume() {
        System.out.println("=== 四、顺序消费 ===\n");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "demo-d49-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            System.out.println("  按 Partition 分组消费：\n");
            Map<Integer, List<String>> partitionMessages = new TreeMap<>();

            for (int i = 0; i < 3; i++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, String> record : records) {
                    partitionMessages.computeIfAbsent(record.partition(), k -> new ArrayList<>())
                            .add(record.key() + ":" + record.value());
                }
            }

            for (Map.Entry<Integer, List<String>> entry : partitionMessages.entrySet()) {
                System.out.println("  Partition-" + entry.getKey() + ": " + entry.getValue());
            }
            System.out.println("\n  → 每个 Partition 内的消息顺序与发送顺序一致 ✓\n");

        } catch (Exception e) {
            System.out.println("  ✗ Kafka 连接失败: " + e.getMessage() + "\n");
        }
    }

    private static void showTradeoff() {
        System.out.println("=== 五、顺序 vs 并行度的权衡 ===\n");

        System.out.println("  ┌──────────────────────┬──────────────────────────────────────────┐");
        System.out.println("  │ 方案                 │ 特点                                      │");
        System.out.println("  ├──────────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ 单 Partition         │ 全局有序，但无并行度（吞吐低）            │");
        System.out.println("  │ 多 Partition + Key ★│ 局部有序（同 Key），高并行度              │");
        System.out.println("  │ 多 Partition 无 Key  │ 无序，最高并行度                          │");
        System.out.println("  └──────────────────────┴──────────────────────────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Kafka 怎么保证消息顺序？");
        System.out.println("    A: 1) 同一 Partition 内消息严格有序");
        System.out.println("       2) 用业务 key（如 orderId）做分区路由");
        System.out.println("       3) 同一 key 的消息进入同一 Partition → 局部有序");
        System.out.println("       4) Consumer 单线程消费一个 Partition → 消费有序");
        System.out.println("    Q: 全局有序怎么做？");
        System.out.println("    A: 只用 1 个 Partition（牺牲并行度，不推荐）");
        System.out.println();
    }
}
