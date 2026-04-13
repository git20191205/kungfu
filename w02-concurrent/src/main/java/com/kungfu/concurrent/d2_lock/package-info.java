/**
 * D9 - Lock 与 AQS
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、ReentrantLock 使用与原理</h3>
 * <pre>
 * ReentrantLock lock = new ReentrantLock();
 * lock.lock();
 * try {
 *     // 临界区
 * } finally {
 *     lock.unlock();   // 必须在 finally 中释放
 * }
 *
 * 与 synchronized 对比：
 * ┌──────────────┬───────────────┬───────────────┐
 * │              │ synchronized  │ ReentrantLock  │
 * ├──────────────┼───────────────┼───────────────┤
 * │ 实现层面      │ JVM 关键字     │ Java API       │
 * │ 释放锁        │ 自动释放       │ 手动 unlock    │
 * │ 可中断        │ 不可中断       │ lockInterruptibly │
 * │ 超时获取      │ 不支持        │ tryLock(time)  │
 * │ 公平性        │ 非公平        │ 可选公平/非公平  │
 * │ 条件变量      │ 单一 wait/notify│ 多个 Condition │
 * └──────────────┴───────────────┴───────────────┘
 * </pre>
 *
 * <h3>二、AQS（AbstractQueuedSynchronizer）</h3>
 * <pre>
 * AQS 核心：
 *   volatile int state;   // 同步状态（0=未锁，≥1=已锁/重入次数）
 *   CLH 双向队列;          // 等待获取锁的线程排队
 *
 * 模板方法模式：
 * ┌─────────────────────────────────────────┐
 * │              AQS 框架                    │
 * │  acquire() / release()    ← 不可重写     │
 * │  tryAcquire() / tryRelease() ← 子类实现  │
 * └─────────────────────────────────────────┘
 *
 * 两种模式：
 *   独占模式 (Exclusive) → ReentrantLock
 *   共享模式 (Shared)    → Semaphore / CountDownLatch
 * </pre>
 *
 * <h3>三、CLH 队列</h3>
 * <pre>
 *        ┌──────┐    prev    ┌──────┐    prev    ┌──────┐
 * head ──→│ Node │←─────────│ Node │←─────────│ Node │← tail
 *        │(哨兵) │──────────→│ T1   │──────────→│ T2   │
 *        └──────┘    next    └──────┘    next    └──────┘
 *
 * waitStatus 状态值：
 *   0          → 初始状态
 *   SIGNAL(-1) → 后继节点需要唤醒
 *   CANCELLED(1) → 节点已取消
 *   CONDITION(-2) → 在条件队列中等待
 *
 * 入队流程：
 *   tryAcquire 失败 → addWaiter 入队 → acquireQueued 自旋/park
 * </pre>
 *
 * <h3>四、公平锁 vs 非公平锁</h3>
 * <pre>
 * ┌────────────────────────────────────────────────┐
 * │ 非公平锁（默认）                                 │
 * │   lock() → 直接 CAS 抢锁                        │
 * │         → 失败则 tryAcquire（再抢一次）           │
 * │         → 仍失败则入队排队                       │
 * │   优点：吞吐量高（减少线程切换）                   │
 * ├────────────────────────────────────────────────┤
 * │ 公平锁                                         │
 * │   lock() → hasQueuedPredecessors() 检查队列     │
 * │         → 有排队者则直接入队                     │
 * │   优点：不会饥饿                                 │
 * │   缺点：吞吐量低（频繁线程切换）                   │
 * └────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>五、Condition 条件变量</h3>
 * <pre>
 * Condition condition = lock.newCondition();
 *
 * 等待方：                     通知方：
 *   lock.lock();                lock.lock();
 *   condition.await();          condition.signal();
 *   lock.unlock();              lock.unlock();
 *
 * 底层：条件队列（单向链表）→ signal 时转移到 CLH 同步队列
 *
 * ┌──────────────┐  signal   ┌──────────────┐
 * │  条件队列      │ ────────→ │  CLH 同步队列  │
 * │ (await等待)   │           │ (竞争获取锁)   │
 * └──────────────┘           └──────────────┘
 *
 * 典型应用：生产者-消费者模式（用两个 Condition 分别控制）
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>并发工具类 —— 基于 AQS 的 CountDownLatch、CyclicBarrier、Semaphore。</p>
 *
 * @author kungfu
 * @since D9
 */
package com.kungfu.concurrent.d2_lock;
