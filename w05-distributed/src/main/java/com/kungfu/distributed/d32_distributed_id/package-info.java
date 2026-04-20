/**
 * D32 — 分布式 ID
 *
 * <h2>知识体系</h2>
 * <pre>
 *   分布式 ID
 *   └── SnowflakeDemo
 *       ├── ID 结构: 1位符号 + 41位时间戳 + 5位数据中心 + 5位机器 + 12位序列号
 *       ├── 时钟回拨处理: 小幅等待 / 大幅拒绝
 *       ├── 并发安全: synchronized + 序列号溢出等待
 *       └── 方案对比: UUID / DB自增 / Redis INCR / Snowflake / Leaf
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.distributed.d32_distributed_id;
