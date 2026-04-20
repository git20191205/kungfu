/**
 * D35 — 分布式事务1（本地消息表）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   分布式事务 — 本地消息表
 *   └── LocalMessageTableDemo
 *       ├── 原理: 业务操作 + 消息写入同一个本地事务
 *       ├── 流程: 下单 → 写消息表 → 定时扫描 → 发送 → 消费 → 确认
 *       ├── 优点: 不依赖外部组件，实现简单
 *       └── 缺点: 定时扫描有延迟，消息表会膨胀
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.distributed.d35_local_msg;
