/**
 * D5 — 内存调优实战
 *
 * <h2>知识体系</h2>
 * <pre>
 *   内存调优
 *   ├── 1. GC 调优方法论（GCTuningMethodologyDemo）
 *   │   ├── 调优四步法: 度量 → 分析 → 调优 → 验证
 *   │   ├── 高频 Young GC: Eden 过小 → 扩大新生代
 *   │   ├── 频繁 Full GC: 老年代不足 / 对象过早晋升
 *   │   ├── GC 指标采集: MXBean API 获取 GC 次数+耗时
 *   │   └── 调优前后关键指标对比表
 *   │
 *   └── 2. 内存泄漏检测（MemoryLeakDetectionDemo）
 *       ├── 泄漏模式1: static 集合只增不删
 *       ├── 泄漏模式2: 监听器/回调注册不注销
 *       ├── 泄漏模式3: 非静态内部类 + ThreadLocal
 *       ├── 检测方法: jmap -histo 对比 + 堆增长曲线
 *       └── 修复方案: WeakHashMap / WeakReference / try-finally
 * </pre>
 *
 * <h2>调优核心公式（面试必备）</h2>
 * <pre>
 *   堆大小 = 3~4 倍 Full GC 后存活数据量
 *   新生代 = 1~1.5 倍 Full GC 后存活数据量
 *   老年代 = 2~3 倍 Full GC 后存活数据量
 *
 *   GC 调优目标（生产环境参考）:
 *   ┌──────────────┬──────────────────────┐
 *   │ 指标          │ 健康阈值              │
 *   ├──────────────┼──────────────────────┤
 *   │ Young GC 频率 │ ≤ 10次/分钟           │
 *   │ Young GC 耗时 │ ≤ 50ms/次             │
 *   │ Full GC 频率  │ ≤ 1次/小时            │
 *   │ Full GC 耗时  │ ≤ 500ms/次            │
 *   │ 堆使用率      │ Full GC 后 ≤ 70%      │
 *   └──────────────┴──────────────────────┘
 * </pre>
 *
 * <h2>内存泄漏四大特征</h2>
 * <pre>
 *   1. 堆使用率持续上升，Full GC 后不回落
 *   2. Full GC 频率逐渐增加
 *   3. jmap -histo 对比：某类实例数只增不减
 *   4. 应用响应时间逐渐变慢（GC 停顿越来越长）
 * </pre>
 *
 * <h2>W01-JVM 完整路线</h2>
 * <pre>
 *   D1（内存模型）→ D2（GC）→ D3（调优工具）→ D4（类加载）→ D5（内存调优）→ D6（OOM排查）
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.jvm.d5_memory_tuning;


/*
 *  D5 有两个 Demo，按顺序：
 *
 *  1. GCTuningMethodologyDemo — GC 调优方法论
 *  VM参数（场景1 - 小堆）: -Xms30m -Xmx30m -XX:NewRatio=2 -XX:+PrintGCDetails -XX:+UseSerialGC
 *  VM参数（场景2 - 大堆）: -Xms100m -Xmx100m -XX:NewRatio=1 -XX:+PrintGCDetails -XX:+UseSerialGC
 *  对比两组参数下的 GC 次数和耗时
 *
 *  2. MemoryLeakDetectionDemo — 内存泄漏检测
 *  VM参数: -Xms50m -Xmx50m -XX:+PrintGCDetails
 *  直接运行 main，观察 3 种泄漏模式
 */
