/**
 * D4 — 类加载机制
 *
 * <h2>知识体系</h2>
 * <pre>
 *   类加载机制
 *   ├── 1. 类加载过程（ClassLoadingProcessDemo）
 *   │   ├── 五个阶段: 加载 → 验证 → 准备 → 解析 → 初始化
 *   │   ├── 准备阶段: static 变量设零值
 *   │   ├── 初始化阶段: 执行 &lt;clinit&gt;()
 *   │   ├── 主动引用 vs 被动引用
 *   │   └── clinit 执行顺序 + 线程安全
 *   │
 *   ├── 2. 双亲委派模型（ParentDelegationDemo）
 *   │   ├── Bootstrap → Extension → Application
 *   │   ├── 工作流程: 先父后己，逐层向上委派
 *   │   ├── 意义: 安全性 + 唯一性
 *   │   └── Class.forName() vs ClassLoader.loadClass()
 *   │
 *   └── 3. 打破双亲委派（BreakParentDelegationDemo）
 *       ├── SPI: TCCL 线程上下文类加载器（JDBC 驱动加载）
 *       ├── Tomcat: WebAppClassLoader 优先加载 WEB-INF
 *       ├── 热部署: 丢弃旧 ClassLoader + 创建新 ClassLoader
 *       └── 自定义 ClassLoader: 重写 findClass vs loadClass
 * </pre>
 *
 * <h2>面试高频考点</h2>
 * <pre>
 *   Q: 类加载的过程？
 *   A: 加载 → 验证 → 准备 → 解析 → 初始化
 *
 *   Q: 什么是双亲委派？为什么需要？
 *   A: 先委派父加载器，安全+唯一
 *
 *   Q: 怎么打破双亲委派？
 *   A: 重写 loadClass()，或用 TCCL（SPI 场景）
 *
 *   Q: Tomcat 类加载器？
 *   A: 每个 WebApp 独立 ClassLoader，优先加载 WEB-INF
 * </pre>
 *
 * <h2>W01-JVM 完整路线</h2>
 * <pre>
 *   D1（内存模型）→ D2（GC）→ D3（调优）→ D4（类加载）→ 完成
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.jvm.d4_classloading;


/*
*
● d4_classloading 有三个 Demo，按顺序：

  1. ClassLoadingProcessDemo — 类加载五阶段 + 主动/被动引用 + clinit 执行顺序
  2. ParentDelegationDemo — 双亲委派模型
  3. BreakParentDelegationDemo — 打破双亲委派

  直接在 IDEA 运行 ClassLoadingProcessDemo.main() 即可，无需特殊参数（可选加 -verbose:class 观察类加载顺序）。运行完把日志保存到 d4_classloading/docs/ 下。
* */