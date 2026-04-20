# W05: 分布式基础 (D31-D36)

> 对应学习路线 D31-D36（D37 为休息日）

## 学习目标

- CAP/BASE 理论 — 分布式系统设计的第一性原理
- 分布式 ID — 雪花算法原理 + 手写实现
- 分布式锁 — Redis SET NX EX + Lua 解锁
- 分布式锁进阶 — Redisson 看门狗 + 可重入 + 公平锁
- 分布式事务1 — 本地消息表方案
- 分布式事务2 — Seata AT 模式原理 + 手写模拟

## 模块结构

| Day | 目录 | 主题 | Demo | 需要外部服务 |
|-----|------|------|------|-------------|
| D31 | d31_cap | CAP/BASE 理论 | CAPAndBASEDemo | 无 |
| D32 | d32_distributed_id | 分布式 ID | SnowflakeDemo | 无 |
| D33 | d33_distributed_lock | Redis 分布式锁 | RedisDistributedLockDemo | Redis |
| D34 | d34_redisson | Redisson 看门狗 | RedissonWatchDogDemo | Redis |
| D35 | d35_local_msg | 本地消息表 | LocalMessageTableDemo | MySQL |
| D36 | d36_seata | Seata AT 模式 | SeataATDemo | MySQL |

## 运行前提

- D31/D32: 纯 Java，直接运行
- D33/D34: Redis 运行在 localhost:6379
- D35/D36: MySQL 运行在 localhost:3306（root/123456）

## 面试核心问题

- Q: CAP 定理是什么？为什么只能三选二？
- Q: 雪花算法原理？时钟回拨怎么处理？
- Q: Redis 分布式锁怎么实现？怎么防误删？
- Q: Redisson 看门狗怎么实现的？
- Q: 分布式事务有哪些方案？各自优缺点？
- Q: Seata AT 模式原理？undo_log 是什么？
- Q: 本地消息表怎么保证最终一致性？

## 技术栈

- Redis + Jedis 4.4.6
- Redisson 3.23.5
- MySQL 8.0 + mysql-connector-java 8.0.33
- Seata 1.7.1（依赖引入，D36 用纯 JDBC 模拟 AT 原理）
