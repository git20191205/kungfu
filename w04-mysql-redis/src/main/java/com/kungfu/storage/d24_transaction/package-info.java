/**
 * D24 — 事务隔离
 *
 * <h2>知识体系</h2>
 * <pre>
 *   事务隔离
 *   ├── 1. MVCC 多版本并发控制（MiniMVCCDemo）
 *   │   ├── 隐藏列: DB_TRX_ID（事务ID）、DB_ROLL_PTR（回滚指针）
 *   │   ├── Undo Log 版本链: 每次修改产生新版本，旧版本串成链表
 *   │   ├── ReadView 一致性读视图
 *   │   │   ├── m_ids — 活跃事务列表
 *   │   │   ├── min_trx_id — 最小活跃事务 ID
 *   │   │   ├── max_trx_id — 下一个待分配的事务 ID
 *   │   │   └── creator_trx_id — 创建者事务 ID
 *   │   ├── 可见性判断算法: 自己→可见, 已提交→可见, 活跃→不可见
 *   │   └── RC vs RR 区别: ReadView 创建时机不同
 *   │
 *   └── 2. 4 种隔离级别实战（IsolationLevelDemo）
 *       ├── READ UNCOMMITTED — 脏读（读到未提交数据）
 *       ├── READ COMMITTED — 不可重复读（同事务两次读不同）
 *       ├── REPEATABLE READ ★ — InnoDB 默认，MVCC+间隙锁防幻读
 *       └── SERIALIZABLE — 串行化，读加锁，性能最低
 * </pre>
 *
 * <h2>面试高频考点</h2>
 * <pre>
 *   Q: MVCC 的原理是什么？
 *   A: 三大组件: 隐藏列(DB_TRX_ID, DB_ROLL_PTR) + Undo Log 版本链 + ReadView
 *      读操作沿版本链找到对当前 ReadView 可见的版本，实现非锁定读
 *
 *   Q: 4 种隔离级别分别解决什么问题？
 *   A: RU=无保护  RC=防脏读  RR=防脏读+不可重复读+幻读(InnoDB)  S=全防
 *      InnoDB 默认 RR，通过 MVCC 实现快照读，间隙锁防止幻读
 *
 *   Q: RC 和 RR 在 MVCC 层面的区别？
 *   A: RC 每次 SELECT 创建新 ReadView → 能看到最新已提交 → 不可重复读
 *      RR 第一次 SELECT 创建 ReadView 后复用 → 看不到后续提交 → 可重复读
 *
 *   Q: InnoDB 的 RR 真的能防止幻读吗？
 *   A: 快照读(普通SELECT)通过 MVCC 防止; 当前读(SELECT FOR UPDATE)通过间隙锁防止
 *      但特殊场景下仍可能出现幻读(先快照读再当前读)
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.storage.d24_transaction;

/*
 *  D24 有两个 Demo：
 *
 *  1. MiniMVCCDemo — 手写 MVCC 模拟（纯 Java，不需要 MySQL）
 *     直接运行 main 即可
 *     演示版本链结构、ReadView 可见性判断、RC vs RR 对比
 *
 *  2. IsolationLevelDemo — 真实 MySQL 4 种隔离级别实战
 *     需要先启动 MySQL，确保 kungfu 数据库存在
 *     然后运行 main，Demo 会自动建表、演示 4 种隔离级别并清理
 */
