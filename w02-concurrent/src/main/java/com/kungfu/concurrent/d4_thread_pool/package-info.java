/**
 * D11 - 线程池
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、ThreadPoolExecutor 7大参数</h3>
 * <pre>
 * new ThreadPoolExecutor(
 *     corePoolSize,      // 核心线程数（常驻线程）
 *     maximumPoolSize,   // 最大线程数
 *     keepAliveTime,     // 非核心线程空闲存活时间
 *     TimeUnit,          // 存活时间单位
 *     workQueue,         // 阻塞队列（存放待执行任务）
 *     threadFactory,     // 线程工厂（自定义线程名称等）
 *     rejectedHandler    // 拒绝策略
 * );
 *
 * 常用队列类型：
 * ┌────────────────────┬─────────────────────────────┐
 * │ LinkedBlockingQueue │ 无界队列（默认 Integer.MAX） │
 * │ ArrayBlockingQueue  │ 有界队列（推荐生产使用）     │
 * │ SynchronousQueue    │ 不存储，直接交接给线程       │
 * │ PriorityBlockingQueue│ 优先级队列                 │
 * └────────────────────┴─────────────────────────────┘
 * </pre>
 *
 * <h3>二、4种拒绝策略</h3>
 * <pre>
 * ┌──────────────────────┬──────────────────────────────┐
 * │ AbortPolicy（默认）    │ 抛出 RejectedExecutionException│
 * │ CallerRunsPolicy     │ 调用者线程执行该任务           │
 * │ DiscardPolicy        │ 静默丢弃，不抛异常            │
 * │ DiscardOldestPolicy  │ 丢弃队列头部最旧任务，重新提交 │
 * └──────────────────────┴──────────────────────────────┘
 *
 * 生产建议：
 *   - 不要用默认的 AbortPolicy（异常容易被吞）
 *   - CallerRunsPolicy 可实现反压（back-pressure）
 *   - 也可自定义策略：记录日志 + 持久化 + 告警
 * </pre>
 *
 * <h3>三、线程池执行流程</h3>
 * <pre>
 *                     提交任务 execute(task)
 *                            │
 *                            ▼
 *               ┌─── 核心线程数已满？───┐
 *               │ 否                   │ 是
 *               ▼                      ▼
 *         创建核心线程            ┌── 队列已满？──┐
 *         执行任务               │ 否            │ 是
 *                               ▼               ▼
 *                          任务入队        ┌── 最大线程数已满？──┐
 *                                         │ 否                │ 是
 *                                         ▼                   ▼
 *                                    创建非核心线程         执行拒绝策略
 *                                    执行任务
 * </pre>
 *
 * <h3>四、常见线程池陷阱</h3>
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │ 1. newFixedThreadPool / newSingleThreadExecutor      │
 * │    → LinkedBlockingQueue 无界队列 → OOM              │
 * │                                                      │
 * │ 2. newCachedThreadPool                               │
 * │    → maximumPoolSize = Integer.MAX_VALUE → OOM       │
 * │                                                      │
 * │ 3. newScheduledThreadPool                            │
 * │    → 任务异常被吞，后续调度静默停止                     │
 * │                                                      │
 * │ 阿里巴巴规约：线程池不允许使用 Executors 创建          │
 * │              必须通过 ThreadPoolExecutor 手动创建      │
 * └──────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>五、手写线程池核心思路</h3>
 * <pre>
 * ┌──────────────────────────────────────┐
 * │           MiniThreadPool             │
 * │                                      │
 * │  BlockingQueue&lt;Runnable&gt; taskQueue   │
 * │  List&lt;Worker&gt; workers               │
 * │                                      │
 * │  execute(task):                      │
 * │    if workers.size &lt; coreSize        │
 * │      → new Worker(task).start()      │
 * │    else                              │
 * │      → taskQueue.offer(task)         │
 * │                                      │
 * │  Worker extends Thread:              │
 * │    run():                            │
 * │      while (task = taskQueue.take()) │
 * │        task.run()                    │
 * └──────────────────────────────────────┘
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>并发容器 —— ConcurrentHashMap 的分段锁与 CAS 机制，理解高并发下的容器设计。</p>
 *
 * @author kungfu
 * @since D11
 */
package com.kungfu.concurrent.d4_thread_pool;
