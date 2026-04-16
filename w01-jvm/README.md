# W01: JVM实战调优 (D1-D6)

## 学习目标

- JVM 内存模型（五大区域、对象布局、OOM 类型、常量池）
- GC 算法与垃圾收集器（GC Roots、引用类型、终结机制、安全点）
- JVM 调优工具（jps/jstat/jinfo/jstack/jmap + GC日志分析 + 参数调优）
- 类加载机制（加载流程、双亲委派、打破双亲委派）
- 内存调优方法论（度量 → 分析 → 调优 → 验证）+ 内存泄漏检测
- OOM 排查实战（MAT 堆转储分析 + 非堆 OOM）

## 模块结构

| Day | 主题 | 核心内容 | Demo 数 |
|-----|------|---------|---------|
| D1 | JVM 内存模型 | 内存区域、对象分配、对象布局、常量池、OOM 类型 | 5 |
| D2 | GC 算法与收集器 | 算法原理、Serial/Parallel/G1、GC Roots、引用类型、安全点 | 6 |
| D3 | JVM 调优工具 | jps/jstat/jstack/jmap、GC 日志分析、参数调优、OOM 案例、线程问题 | 6 |
| D4 | 类加载机制 | 五阶段、双亲委派、SPI/Tomcat/热部署打破双亲委派 | 3 |
| D5 | 内存调优实战 | 调优四步法、GC 指标采集、内存泄漏 3 大模式检测 | 2 |
| D6 | OOM 排查 | MAT 堆转储分析 SOP、Metaspace/Direct Memory/StackOverflow | 2 |

## 运行方式

每个 Demo 的 Javadoc 头部都有 `运行方式` 和 `VM 参数`。
日志和分析文件保存在各自的 `docs/` 目录下。

## 面试核心问题

- Q: JVM 内存分为几个区？哪些线程共享？
- Q: GC Roots 有哪些？
- Q: CMS 和 G1 的区别？什么场景选哪个？
- Q: 双亲委派模型？为什么要打破？
- Q: 生产环境遇到过 OOM 吗？怎么排查的？
- Q: JVM 参数调优怎么做？有什么方法论？

## 技术栈

- JDK 1.8+
- JOL (Java Object Layout) — 对象布局分析
- MAT (Eclipse Memory Analyzer) — heap dump 分析
