/**
 * D6 — OOM 排查实战（MAT 分析 + 非堆 OOM）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   OOM 排查
 *   ├── 1. MAT 堆转储分析（HeapDumpAnalysisDemo）
 *   │   ├── Heap Dump 获取: jmap / HeapDumpOnOutOfMemoryError
 *   │   ├── MAT 核心视图: Dominator Tree / Leak Suspects
 *   │   ├── GC Roots 分析: Path to GC Roots → 定位持有者
 *   │   ├── Shallow Size vs Retained Size
 *   │   └── 完整排查 SOP: dump → MAT → 定位 → 修复
 *   │
 *   └── 2. 非堆 OOM 排查（MetaspaceAndDirectOOMDemo）
 *       ├── Metaspace OOM: 动态类生成过多（CGLIB/反射/Groovy）
 *       ├── Direct Memory OOM: NIO 直接内存泄漏
 *       ├── StackOverflowError: 深递归 / 线程栈过大
 *       └── OOM 类型速查表: 错误信息 → 根因 → 修复
 * </pre>
 *
 * <h2>OOM 类型速查表（面试必备）</h2>
 * <pre>
 *   ┌──────────────────────────────────────┬───────────────────────┐
 *   │ 错误信息                             │ 排查方向               │
 *   ├──────────────────────────────────────┼───────────────────────┤
 *   │ Java heap space                      │ 堆内存不足/泄漏       │
 *   │ GC overhead limit exceeded           │ GC 回收效率极低       │
 *   │ Metaspace                            │ 类太多/ClassLoader泄漏│
 *   │ Direct buffer memory                 │ NIO 直接内存泄漏      │
 *   │ unable to create new native thread   │ 线程数超限            │
 *   │ StackOverflowError                   │ 递归过深/栈帧过大     │
 *   └──────────────────────────────────────┴───────────────────────┘
 * </pre>
 *
 * <h2>W01-JVM 完整路线</h2>
 * <pre>
 *   D1（内存模型）→ D2（GC）→ D3（调优工具）→ D4（类加载）→ D5（内存调优）→ D6（OOM排查）→ 完成
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.jvm.d6_oom_troubleshooting;


/*
 *  D6 有两个 Demo，按顺序：
 *
 *  1. HeapDumpAnalysisDemo — MAT 堆转储分析方法论
 *  VM参数: -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError
 *          -XX:HeapDumpPath=./oom_dump.hprof -XX:+PrintGCDetails
 *  运行后用 MAT (Eclipse Memory Analyzer) 打开 hprof 文件分析
 *
 *  2. MetaspaceAndDirectOOMDemo — 非堆 OOM 排查
 *  每次运行一个场景（在 main 中取消注释）：
 *  场景1: -XX:MaxMetaspaceSize=30m -XX:+PrintGCDetails
 *  场景2: -XX:MaxDirectMemorySize=10m -XX:+PrintGCDetails
 *  场景3: -Xss256k（默认即可运行）
 */
