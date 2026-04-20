/**
 * D23 — 执行计划
 *
 * <h2>知识体系</h2>
 * <pre>
 *   执行计划 (EXPLAIN)
 *   ├── 1. EXPLAIN 逐字段解读（ExplainAnalysisDemo）
 *   │   ├── EXPLAIN 输出 11 个字段含义
 *   │   ├── type 从最优到最差：system > const > eq_ref > ref > range > index > ALL
 *   │   ├── const — 主键等值匹配（最多1行）
 *   │   ├── ref — 非唯一索引等值匹配
 *   │   ├── range — 索引范围扫描（BETWEEN / IN / > / <）
 *   │   ├── index — 全索引扫描（覆盖索引，不回表）
 *   │   ├── ALL — 全表扫描（必须优化！）
 *   │   └── Extra 字段：Using index / Using filesort / Using temporary
 *   │
 *   └── 2. 慢查询优化 3 大案例（SlowQueryOptimizationDemo）
 *       ├── 案例1: 对索引列使用函数 → 索引失效 → 改写为范围查询
 *       ├── 案例2: 隐式类型转换 → 索引失效 → 保证类型一致
 *       └── 案例3: 前导通配符 LIKE '%xx%' → 全表扫描 → 覆盖索引 / ES
 * </pre>
 *
 * <h2>面试高频考点</h2>
 * <pre>
 *   Q: EXPLAIN 中 type 字段的各值代表什么？从好到差怎么排？
 *   A: system > const > eq_ref > ref > range > index > ALL
 *      const = 主键/唯一索引等值（1行），ref = 普通索引等值（多行）
 *      range = 索引范围扫描，ALL = 全表扫描（最差）
 *      优化目标：至少达到 range，杜绝 ALL
 *
 *   Q: 哪些情况会导致索引失效？
 *   A: 1. 对索引列使用函数/计算：YEAR(col), col+1
 *      2. 隐式类型转换：VARCHAR 列与 INT 比较
 *      3. 前导通配符：LIKE '%keyword%'
 *      4. 不满足最左前缀：联合索引 (a,b,c) 查 WHERE b=1
 *      5. OR 连接中有无索引列
 *
 *   Q: 排查慢查询的标准流程？
 *   A: 开启慢查询日志 → EXPLAIN 分析 → 看 type/key/rows/Extra
 *      → 加索引/改写SQL/覆盖索引 → 再次 EXPLAIN 验证
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.storage.d23_explain;

/*
 *  D23 有两个 Demo：
 *
 *  1. ExplainAnalysisDemo — EXPLAIN 逐字段解读 + type 实战演示
 *  需要先启动 MySQL，确保 kungfu 数据库存在
 *  直接运行 main 方法（自动建表、插数据、演示、清理）
 *
 *  2. SlowQueryOptimizationDemo — 慢查询优化 3 大经典案例
 *  需要先启动 MySQL，确保 kungfu 数据库存在
 *  直接运行 main 方法（自动建表、插数据、演示、清理）
 */
