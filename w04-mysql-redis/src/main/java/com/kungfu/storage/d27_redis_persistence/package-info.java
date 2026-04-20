/**
 * D27 — Redis 持久化
 *
 * <h2>知识体系</h2>
 * <pre>
 *   Redis 持久化
 *   └── RedisPersistenceDemo
 *       ├── RDB: fork 子进程生成快照（BGSAVE）
 *       │   ├── 优点: 恢复速度快、文件紧凑
 *       │   └── 缺点: 可能丢失最后一次快照后的数据
 *       ├── AOF: 追加写命令日志
 *       │   ├── always/everysec/no 三种 fsync 策略
 *       │   └── BGREWRITEAOF 重写压缩
 *       └── 混合持久化（Redis 4.0+）
 *           └── RDB 头 + AOF 尾 → 兼顾恢复速度和数据安全
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.storage.d27_redis_persistence;
