/**
 * D3 — JVM 调优实战
 *
 * <h2>知识体系</h2>
 * <pre>
 *   JVM 调优
 *   ├── 1. 监控命令（JvmMonitorCommandDemo）
 *   │   ├── jps  → 找 Java 进程
 *   │   ├── jstat → 监控 GC 状态
 *   │   ├── jinfo → 查看 JVM 参数
 *   │   ├── jstack → 线程堆栈（CPU 飙高排查）
 *   │   ├── jmap → 堆转储（OOM 排查）
 *   │   └── MXBean API → 代码中获取运行时信息
 *   │
 *   ├── 2. GC 日志分析（GCLogAnalysisDemo）
 *   │   ├── JDK 8 参数: -XX:+PrintGCDetails
 *   │   ├── JDK 9+ 参数: -Xlog:gc*
 *   │   ├── 日志格式逐字段解读
 *   │   ├── 关键计算: 回收量、晋升量
 *   │   └── 调优警戒线: YGC频率/FGC频率/停顿时间
 *   │
 *   ├── 3. JVM 参数调优（JvmParameterTuningDemo）
 *   │   ├── 堆参数: -Xms/-Xmx/-Xmn/SurvivorRatio
 *   │   ├── 收集器参数: UseG1GC/MaxGCPauseMillis
 *   │   ├── 其他: Xss/MetaspaceSize/HeapDump
 *   │   ├── 调优公式: 堆 = 3~4倍 FGC后存活
 *   │   └── 生产模板: 4C8G 服务器推荐配置
 *   │
 *   └── 4. OOM 排查实战（OOMTroubleshootingDemo）
 *       ├── 场景1: 缓存无限增长（HashMap 无淘汰）
 *       ├── 场景2: 大查询全量加载（SQL 无 LIMIT）
 *       ├── 场景3: 任务队列无界堆积（线程池无界队列）
 *       └── 排查 SOP: dump → MAT → GC Roots → 修复
 * </pre>
 *
 * <h2>线上排查三大场景（面试必备）</h2>
 * <pre>
 *   CPU 飙高:  top → top -Hp → printf '%x' → jstack grep
 *   OOM:       HeapDump → MAT → Dominator Tree → GC Roots
 *   GC 频繁:   jstat -gcutil → 分析 GC 日志 → 调参数
 * </pre>
 *
 * <h2>学习路线</h2>
 * <pre>
 *   D1（内存模型）→ D2（GC）→ D3（调优）→ 完成 W01-JVM
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.jvm.d3_tuning;


/*
*   1. JvmParameterTuningDemo — 参数调优对比
  场景1: -Xms100m -Xmx100m -XX:+PrintGCDetails -XX:+UseSerialGC
  场景2（可选对比）: -Xms100m -Xmx100m -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:+PrintGCDetails

  2. OOMTroubleshootingDemo — 3 种 OOM 场景（每次跑一个）
  -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails
  - cacheOOM() — 默认开启
  - bigQueryOOM() — 取消注释
  - taskQueueOOM() — 取消注释
  *  OOMTroubleshootingDemo 运行指南：

  场景1: cacheOOM() (默认开启)
  VM参数: -Xms30m -Xmx30m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails
  直接运行即可，程序会不断往 HashMap 塞数据直到 OOM。

  场景2: bigQueryOOM()
  需要在代码中注释掉 cacheOOM()，取消注释 bigQueryOOM()，同样的 VM 参数运行。

  场景3: taskQueueOOM()
  注释掉前面的，取消注释 taskQueueOOM()，同样参数运行。

  每跑完一个场景，把日志保存到 docs 目录发给我分析。先跑 cacheOOM 吧。


  3. ThreadTroubleshootingDemo — 3 种线程问题（每次跑一个）
  直接运行，不需要特殊 VM 参数
  - cpuSpike() — 默认开启
  - deadlock() — 取消注释
  - threadPoolFull() — 取消注释

  4. JvmMonitorCommandDemo — 监控命令
  直接运行，程序暂停 60 秒，期间用 jstack/jstat 观察
* */
