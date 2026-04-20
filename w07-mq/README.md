# W07: 消息队列 (D45-D50)

> 对应学习路线 D45-D50（D51 为休息日）

## 学习目标

- Kafka 架构 — 分区、副本、ISR、消费组
- Kafka 生产消费 — Producer/Consumer API + Offset 管理
- 消息可靠性 — 不丢失配置（acks=all + 手动提交 + min.insync.replicas）
- 消息重复消费 — 幂等性设计（唯一键去重 + 状态机）
- 顺序消息 — 相同 Key → 相同 Partition → 局部有序
- 消息积压 — Lag 检测 + 扩容/提速/转储方案

## 模块结构

| Day | 目录 | 主题 | Demo | 需要外部服务 |
|-----|------|------|------|-------------|
| D45 | d45_architecture | Kafka 架构 | KafkaArchitectureDemo | Kafka |
| D46 | d46_producer_consumer | 生产消费 | KafkaProducerConsumerDemo | Kafka |
| D47 | d47_reliability | 消息可靠性 | MessageReliabilityDemo | Kafka |
| D48 | d48_idempotent | 幂等消费 | IdempotentConsumerDemo | MySQL |
| D49 | d49_ordering | 顺序消息 | OrderedMessageDemo | Kafka |
| D50 | d50_backlog | 消息积压 | MessageBacklogDemo | Kafka |

## 运行前提

- D48: 只需 MySQL（localhost:3306, root/123456）
- D45-D47, D49-D50: 需要 Kafka（localhost:9092）
- Kafka 未启动时，Demo 会输出理论内容并优雅跳过实际操作

## 面试核心问题

- Q: Kafka 为什么吞吐量高？（顺序写 + Page Cache + 零拷贝 + 批量 + 分区并行）
- Q: Kafka 怎么保证消息不丢失？（acks=all + min.insync.replicas + 手动提交）
- Q: 如何保证消息不被重复消费？（消费端幂等：唯一键/状态机/Redis SETNX）
- Q: Kafka 怎么保证消息顺序？（相同 key → 相同 partition → 局部有序）
- Q: 消息积压怎么处理？（扩容 Consumer / 提高消费速度 / 临时转储）
- Q: Kafka vs RocketMQ vs RabbitMQ 区别？

## 技术栈

- Kafka 3.5.1 (kafka-clients)
- MySQL 8.0 (mysql-connector-java 8.0.33)
