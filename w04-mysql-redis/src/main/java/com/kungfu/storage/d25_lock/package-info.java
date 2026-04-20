/**
 * D25 — 锁机制（InnoDB Lock Mechanism）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   InnoDB 锁机制
 *   ├── 1. 锁的粒度
 *   │   ├── 表锁（Table Lock）     — 开销小，并发低
 *   │   ├── 页锁（Page Lock）      — MyISAM 用，InnoDB 不用
 *   │   └── 行锁（Row Lock）       — 开销大，并发高 ★ InnoDB 默认
 *   │
 *   ├── 2. 行锁的三种形态（LockMechanismDemo）
 *   │   ├── Record Lock（记录锁）    — 锁住索引上的一条记录
 *   │   ├── Gap Lock（间隙锁）       — 锁住索引记录之间的"间隙"，防幻读
 *   │   └── Next-Key Lock（临键锁）  — Record + Gap，RR 级别默认行为
 *   │
 *   ├── 3. 锁的模式
 *   │   ├── 共享锁 S（SELECT ... LOCK IN SHARE MODE）
 *   │   ├── 排他锁 X（SELECT ... FOR UPDATE / UPDATE / DELETE）
 *   │   ├── 意向共享锁 IS   — 表级，自动加
 *   │   └── 意向排他锁 IX   — 表级，自动加
 *   │
 *   ├── 4. 死锁（DeadlockDemo）
 *   │   ├── 死锁四要素：互斥、占有并等待、不可剥夺、循环等待
 *   │   ├── InnoDB 自动检测（wait-for graph）→ 回滚代价小的事务
 *   │   ├── 排查工具: SHOW ENGINE INNODB STATUS
 *   │   └── 错误码: 1213 (Deadlock found)
 *   │
 *   └── 5. 锁相关视图（information_schema / performance_schema）
 *       ├── data_locks        — 当前持有/等待的锁
 *       ├── data_lock_waits   — 锁等待关系
 *       └── innodb_trx        — 当前事务
 * </pre>
 *
 * <h2>面试高频考点</h2>
 * <pre>
 *   Q: InnoDB 和 MyISAM 的锁有什么区别？
 *   A: InnoDB 支持行锁（默认）和表锁；MyISAM 只支持表锁。
 *      InnoDB 行锁加在"索引"上，不是数据行本身。
 *
 *   Q: 行锁什么情况会升级为表锁？
 *   A: WHERE 条件没用索引 → InnoDB 只能全表扫描 → 给所有行加锁
 *      = 事实上的表锁。所以写 SQL 一定要让条件走索引。
 *
 *   Q: 什么是间隙锁？为什么需要？
 *   A: 间隙锁锁住索引记录之间的"间隙"（开区间），阻止其他事务在间隙里 INSERT。
 *      目的：在 RR 隔离级别防止幻读。
 *      副作用：并发下容易死锁，需要谨慎使用。
 *
 *   Q: 什么是 Next-Key Lock？
 *   A: Record Lock + Gap Lock 的组合（左开右闭区间）。
 *      RR 级别下范围查询默认加 Next-Key Lock。
 *      RC 级别下只加 Record Lock（所以 RC 不能防幻读）。
 *
 *   Q: 如何排查死锁？
 *   A: 1. SHOW ENGINE INNODB STATUS 看 LATEST DETECTED DEADLOCK
 *      2. 拿到两个事务的 SQL、持锁情况、等待锁情况
 *      3. 画"锁等待图"找循环
 *      4. 看应用日志定位业务场景
 *
 *   Q: 如何避免死锁？
 *   A: 1. 按固定顺序访问资源（如按主键从小到大更新）
 *      2. 事务尽量短，减少持锁时间
 *      3. 用合理的索引，避免锁范围过大
 *      4. 降低隔离级别到 RC（如果业务允许）
 *      5. 热点行用乐观锁（version 字段）代替行锁
 * </pre>
 *
 * <h2>运行方式</h2>
 * <pre>
 * 1. 启动 MySQL 8.x，创建 kungfu 数据库（默认 RR 隔离级别）
 * 2. 确认 DBUtil 中的连接信息正确
 * 3. 分别运行：
 *    - LockMechanismDemo.main()  — 演示 Record Lock / Gap Lock / 表锁升级
 *    - DeadlockDemo.main()        — 主动制造死锁，展示排查方法
 * 4. 每个 Demo 运行完会自动清理测试表
 * </pre>
 *
 * @author kungfu
 * @since D25 - 锁机制
 */
package com.kungfu.storage.d25_lock;

/*
 *  D25 有两个 Demo：
 *
 *  1. LockMechanismDemo — InnoDB 锁类型全景演示
 *     - Record Lock: SELECT ... FOR UPDATE 锁住一行，其他事务 UPDATE 同行会阻塞
 *     - Gap Lock:    范围 SELECT FOR UPDATE 锁住间隙，其他事务 INSERT 到间隙会阻塞
 *     - 表锁升级:    WHERE 没走索引时，行锁退化为表锁
 *
 *  2. DeadlockDemo — 主动制造死锁
 *     - 两个线程以相反顺序申请两行的锁 → InnoDB 检测到循环 → 回滚其中一个
 *     - 打印 Error 1213 并展示 SHOW ENGINE INNODB STATUS 的 SOP
 *
 *  说明：两个 Demo 都依赖真实 MySQL 连接（com.kungfu.storage.common.DBUtil），
 *       无法用 H2 等内存库代替，因为锁行为依赖 InnoDB 引擎实现。
 */
