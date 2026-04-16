# W02: 并发编程核心 (D8-D13 + 扩展)

> 对应学习路线 D8-D13（6 个必修主题 + 4 个扩展主题）

## 学习目标

- JMM 内存模型 + volatile/synchronized 原理
- AQS 框架 + ReentrantLock 源码
- 并发工具类（CountDownLatch/CyclicBarrier/Semaphore）
- 线程池原理 + 手写简化版 ThreadPoolExecutor
- ConcurrentHashMap 1.7/1.8 实现差异
- CAS/Atomic 原子类 + ABA 问题
- ThreadLocal + 线程池场景下的泄漏防护
- BlockingQueue 阻塞队列家族
- CompletableFuture 异步编排
- ReadWriteLock 读写锁 + StampedLock

## 模块结构

| 目录 | 主题 | 核心内容 | Demo 数 |
|------|------|---------|---------|
| d1_thread_basis | volatile/synchronized | JMM、happens-before、锁升级、MESI | 2 |
| d2_lock | ReentrantLock / AQS | AQS 原理、CLH 队列、公平/非公平锁、Condition | 1 |
| d3_concurrent_util | 并发工具类 | CountDownLatch、CyclicBarrier、Semaphore | 1 |
| d4_thread_pool | 线程池 | 7 参数、拒绝策略、执行流程、手写线程池 | 2 |
| d5_concurrent_container | ConcurrentHashMap | JDK7 Segment vs JDK8 CAS+synchronized | 1 |
| d6_cas_atomic | CAS 原子类 | CAS 原理、ABA 问题、AtomicXXX 家族、LongAdder | 1 |
| d7_threadlocal | ThreadLocal | ThreadLocalMap、内存泄漏、InheritableThreadLocal | 2 |
| d8_blockingqueue | 阻塞队列 | ArrayBlockingQueue、LinkedBlockingQueue、SynchronousQueue、DelayQueue | 1 |
| d9_completablefuture | CompletableFuture | 异步编排、组合、异常处理、生产场景 | 1 |
| d10_readwritelock | 读写锁 | ReentrantReadWriteLock、锁降级、StampedLock | 1 |

## 运行方式

每个 Demo 的 Javadoc 头部都有 `运行方式`。
每个子目录下都有 `package-info.java` 文档和 `docs/` 日志目录。

## 面试核心问题

- Q: volatile 能保证原子性吗？为什么？
- Q: synchronized 锁升级过程？
- Q: AQS 框架如何实现锁？CLH 队列是什么？
- Q: 线程池 7 个参数分别是什么？拒绝策略有几种？
- Q: 为什么不推荐用 Executors 创建线程池？
- Q: ConcurrentHashMap 1.7 和 1.8 的区别？
- Q: ThreadLocal 为什么会内存泄漏？怎么避免？
- Q: CAS 的 ABA 问题怎么解决？
- Q: CompletableFuture 和 Future 的区别？

## 技术栈

- JDK 1.8+
- java.util.concurrent 全家桶
