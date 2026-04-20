/**
 * D45 — Kafka 架构
 *
 * <h2>知识体系</h2>
 * <pre>
 *   Kafka 架构
 *   └── KafkaArchitectureDemo
 *       ├── 核心组件: Producer / Broker / Consumer / ZooKeeper
 *       ├── Topic: 逻辑分类，一个 Topic 可有多个 Partition
 *       ├── Partition: 并行度单位，有序，分布在不同 Broker
 *       ├── Replica: 副本机制，Leader + Follower，ISR 列表
 *       ├── Consumer Group: 组内竞争消费，组间广播
 *       └── 对比: Kafka vs RocketMQ vs RabbitMQ
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.mq.d45_architecture;
