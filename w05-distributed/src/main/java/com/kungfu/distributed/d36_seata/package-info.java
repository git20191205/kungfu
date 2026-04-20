/**
 * D36 — 分布式事务2（Seata AT 模式）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   Seata AT 模式
 *   └── SeataATDemo
 *       ├── Seata 架构: TC(事务协调器) + TM(事务管理器) + RM(资源管理器)
 *       ├── AT 模式原理: 自动生成 undo_log → 两阶段提交
 *       │   ├── 一阶段: 拦截 SQL → 记录 before/after image → 提交本地事务
 *       │   └── 二阶段: 提交 → 删 undo_log / 回滚 → 用 undo_log 恢复
 *       ├── 手写 Mini-Seata: 模拟 AT 模式核心流程
 *       └── 生产配置模板
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.distributed.d36_seata;
