/**
 * D22 — MySQL 索引原理
 *
 * <h2>知识体系</h2>
 * <pre>
 *   MySQL 索引
 *   ├── 1. B+Tree 索引结构（BTreeIndexSimulationDemo）
 *   │   ├── B+Tree vs B-Tree vs Hash vs 红黑树
 *   │   ├── 非叶子节点只存 key → 一页能放更多索引项 → 树更矮
 *   │   ├── 叶子节点用链表连接 → 范围查询高效
 *   │   ├── InnoDB 聚簇索引: 叶子节点存整行数据
 *   │   └── InnoDB 二级索引: 叶子节点存主键值 → 回表
 *   │
 *   └── 2. 索引优化实战（IndexOptimizationDemo）
 *       ├── 案例1: 无索引 → 全表扫描 → 添加索引
 *       ├── 案例2: 联合索引最左前缀匹配
 *       ├── 案例3: 回表 → 覆盖索引优化
 *       └── 用 EXPLAIN 验证每个优化
 * </pre>
 *
 * <h2>面试高频考点</h2>
 * <pre>
 *   Q: 为什么 MySQL 用 B+Tree 而不是 B-Tree？
 *   A: B+Tree 非叶子不存数据 → 一页放更多key → 树更矮 → IO更少
 *      叶子有链表 → 范围查询只需遍历链表
 *
 *   Q: 什么是回表？怎么避免？
 *   A: 二级索引查到主键后还要回聚簇索引取整行 = 回表
 *      用覆盖索引（查询列都在索引中）可避免回表
 *
 *   Q: 联合索引的最左前缀原则？
 *   A: 索引(a,b,c)只有查询条件从a开始才能命中
 *      WHERE a=1 AND b=2 ✓  WHERE b=2 ✗
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.storage.d22_index;

/*
 *  D22 有两个 Demo：
 *
 *  1. BTreeIndexSimulationDemo — 手写 B+Tree 模拟（纯 Java，不需要 MySQL）
 *  直接运行 main 即可
 *
 *  2. IndexOptimizationDemo — 真实 MySQL 索引实战
 *  需要先启动 MySQL，执行 sql/schema-d22.sql 建表
 *  然后运行 main，Demo 会自动插入测试数据并演示索引优化
 */
