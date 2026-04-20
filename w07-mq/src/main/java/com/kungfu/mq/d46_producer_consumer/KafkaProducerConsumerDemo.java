package com.kungfu.mq.d46_producer_consumer;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * 【Demo】Kafka 生产者消费者 — 完整收发流程
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>KafkaProducer 发送消息（同步/异步 + 回调）</li>
 *   <li>KafkaConsumer 订阅消费（poll 模型）</li>
 *   <li>消息路由：相同 key → 相同 partition</li>
 *   <li>Offset 管理：自动提交 vs 手动提交</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Kafka 运行在 localhost:9092
 *
 * @author kungfu
 * @since D46 - Kafka生产消费
 */
public class KafkaProducerConsumerDemo {

    private static final String BOOTSTRAP = "localhost:9092";
    private static final String TOPIC = "demo-d46-producer-consumer";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Kafka 生产者消费者");
        System.out.println("========================================\n");

        showProducerConfig();
        demoProduce();
        showConsumerConfig();
        demoConsume();
    }

    private static void showProducerConfig() {
        System.out.println("=== 一、Producer 核心配置 ===\n");
        System.out.println("  ┌──────────────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 配置项                   │ 说明                                  │");
        System.out.println("  ├──────────────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ bootstrap.servers        │ Kafka 集群地址                        │");
        System.out.println("  │ key.serializer           │ Key 序列化器                          │");
        System.out.println("  │ value.serializer         │ Value 序列化器                        │");
        System.out.println("  │ acks                     │ 0/1/all 确认级别                      │");
        System.out.println("  │ retries                  │ 发送失败重试次数                      │");
        System.out.println("  │ batch.size               │ 批量发送大小（默认 16KB）             │");
        System.out.println("  │ linger.ms                │ 等待凑批时间（默认 0）                │");
        System.out.println("  │ buffer.memory            │ 发送缓冲区大小（默认 32MB）           │");
        System.out.println("  └──────────────────────────┴──────────────────────────────────────┘\n");
    }

    private static void demoProduce() {
        System.out.println("=== 二、发送消息 ===\n");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            // 发送 10 条消息，用 orderId 作为 key
            for (int i = 0; i < 10; i++) {
                String key = "order_" + (i % 3); // 3 个不同的 key
                String value = "{\"orderId\":\"" + key + "\",\"item\":\"product_" + i + "\",\"amount\":" + (100 + i * 10) + "}";

                producer.send(new ProducerRecord<>(TOPIC, key, value), (metadata, exception) -> {
                    if (exception == null) {
                        System.out.println("    ✓ 发送成功: partition=" + metadata.partition()
                                + " offset=" + metadata.offset() + " key=" + key);
                    } else {
                        System.out.println("    ✗ 发送失败: " + exception.getMessage());
                    }
                });
            }
            producer.flush();
            System.out.println();
            System.out.println("  ★ 观察：相同 key 的消息进入相同 partition（保证局部有序）\n");

        } catch (Exception e) {
            System.out.println("  ✗ Producer 创建失败: " + e.getMessage());
            System.out.println("  → 请确保 Kafka 运行在 " + BOOTSTRAP + "\n");
        }
    }

    private static void showConsumerConfig() {
        System.out.println("=== 三、Consumer 核心配置 ===\n");
        System.out.println("  ┌──────────────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 配置项                   │ 说明                                  │");
        System.out.println("  ├──────────────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ group.id                 │ 消费组 ID（同组竞争消费）             │");
        System.out.println("  │ auto.offset.reset        │ earliest/latest（无 offset 时从哪开始）│");
        System.out.println("  │ enable.auto.commit       │ 是否自动提交 offset（默认 true）      │");
        System.out.println("  │ auto.commit.interval.ms  │ 自动提交间隔（默认 5000ms）           │");
        System.out.println("  │ max.poll.records          │ 单次 poll 最大消息数（默认 500）      │");
        System.out.println("  │ session.timeout.ms       │ 心跳超时（默认 45s）                  │");
        System.out.println("  └──────────────────────────┴──────────────────────────────────────┘\n");
    }

    private static void demoConsume() {
        System.out.println("=== 四、消费消息 ===\n");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "demo-d46-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "20");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            System.out.println("  订阅 Topic: " + TOPIC);
            System.out.println("  消费组: demo-d46-group\n");

            // poll 3 次
            int totalMessages = 0;
            for (int round = 0; round < 3; round++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("    partition=" + record.partition()
                            + " offset=" + record.offset()
                            + " key=" + record.key()
                            + " value=" + record.value());
                    totalMessages++;
                }
            }
            System.out.println("\n  共消费 " + totalMessages + " 条消息\n");

        } catch (Exception e) {
            System.out.println("  ✗ Consumer 创建失败: " + e.getMessage());
            System.out.println("  → 请确保 Kafka 运行在 " + BOOTSTRAP + "\n");
        }

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Kafka 消费者 poll 模型 vs 推模型？");
        System.out.println("    A: Kafka 用 pull（消费者主动拉取），好处：");
        System.out.println("       1) 消费者控制消费速率，不会被压垮");
        System.out.println("       2) 可以批量拉取，提高吞吐");
        System.out.println("       3) 消费者可以回溯消费（重置 offset）");
        System.out.println();
    }
}
