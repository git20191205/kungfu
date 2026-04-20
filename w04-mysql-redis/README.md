# W04: MySQL+Redis 实战 (D22-D29)

> 对应学习路线 D22-D29（D30 为总结日）

## 学习目标

- MySQL 索引原理（B+Tree、聚簇/二级索引、回表、覆盖索引）
- EXPLAIN 执行计划逐字段解读 + 慢查询优化
- 事务隔离级别 + MVCC 原理
- InnoDB 锁机制（行锁、间隙锁、死锁排查）
- Redis 5 种数据结构 + 使用场景
- Redis 持久化（RDB vs AOF vs 混合）
- 缓存三大问题（穿透/击穿/雪崩）+ 缓存一致性
- 综合实战：完整缓存架构设计

## 模块结构

| Day | 目录 | 主题 | Demo 数 |
|-----|------|------|---------|
| D22 | d22_index | MySQL 索引原理 | 2 |
| D23 | d23_explain | EXPLAIN 执行计划 | 2 |
| D24 | d24_transaction | 事务隔离 + MVCC | 2 |
| D25 | d25_lock | 锁机制 + 死锁 | 2 |
| D26 | d26_redis_datastructure | Redis 数据结构 | 1 |
| D27 | d27_redis_persistence | Redis 持久化 | 1 |
| D28 | d28_cache | 缓存问题 + 一致性 | 2 |
| D29 | d29_integration | 综合实战 | 1 |

## 运行前提

- MySQL 8.0+ 运行在 localhost:3306（用户 root/root）
- Redis 运行在 localhost:6379（无密码）
- D22 BTreeIndexSimulationDemo 和 D24 MiniMVCCDemo 为纯 Java，不需要外部服务

## 面试核心问题

- Q: 为什么 MySQL 用 B+Tree 而不是 B-Tree / Hash？
- Q: 什么是回表？怎么用覆盖索引避免？
- Q: 联合索引的最左前缀原则？
- Q: EXPLAIN 的 type 字段从好到差排列？
- Q: MVCC 的 ReadView 可见性判断算法？
- Q: InnoDB 的行锁加在哪里？什么时候退化为表锁？
- Q: 死锁怎么排查？怎么预防？
- Q: Redis 5 种数据结构各适合什么场景？
- Q: RDB 和 AOF 的区别？生产怎么配？
- Q: 缓存穿透/击穿/雪崩分别怎么解决？
- Q: 缓存和 DB 怎么保证一致性？

## 技术栈

- MySQL 8.0 + mysql-connector-java 8.0.33
- Redis + Jedis 4.4.6
- 纯 JDBC（不依赖 Spring）
