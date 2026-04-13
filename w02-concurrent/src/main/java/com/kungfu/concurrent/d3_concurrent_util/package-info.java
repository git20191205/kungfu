/**
 * D10 - 并发工具类
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、CountDownLatch（倒计时门闩）</h3>
 * <pre>
 * 场景：主线程等待 N 个子线程完成后再继续
 *
 * CountDownLatch latch = new CountDownLatch(3);
 *
 *   子线程1 ──→ countDown() ──┐
 *   子线程2 ──→ countDown() ──┼──→ state 减到 0 ──→ 主线程被唤醒
 *   子线程3 ──→ countDown() ──┘
 *                                  await() 阻塞等待
 *
 * 原理：基于 AQS 共享模式
 *   state = N（初始计数）
 *   countDown() → releaseShared() → state - 1
 *   await()     → acquireShared() → state == 0 时放行
 *
 * 注意：一次性的，计数到 0 后不可重置
 * </pre>
 *
 * <h3>二、CyclicBarrier（循环栅栏）</h3>
 * <pre>
 * 场景：N 个线程互相等待，全部到达栅栏后一起继续
 *
 * CyclicBarrier barrier = new CyclicBarrier(3, barrierAction);
 *
 *   线程1 ──→ await() ──┐
 *   线程2 ──→ await() ──┼──→ 全部到齐 ──→ 执行 barrierAction
 *   线程3 ──→ await() ──┘               ──→ 所有线程继续运行
 *
 * 原理：基于 ReentrantLock + Condition
 *   count 递减，最后一个到达的线程执行 barrierAction 并唤醒所有
 *
 * 特点：
 *   1. 可重用 —— reset() 后可进入下一轮
 *   2. 可指定 barrierAction —— 最后到达线程执行
 * </pre>
 *
 * <h3>三、Semaphore（信号量）</h3>
 * <pre>
 * 场景：控制同时访问资源的线程数（限流）
 *
 * Semaphore semaphore = new Semaphore(3);  // 允许3个线程同时访问
 *
 * ┌────────────────────────────────────────┐
 * │  permits = 3                           │
 * │                                        │
 * │  T1 acquire() → permits = 2  (进入)    │
 * │  T2 acquire() → permits = 1  (进入)    │
 * │  T3 acquire() → permits = 0  (进入)    │
 * │  T4 acquire() → 阻塞等待               │
 * │  T1 release() → permits = 1  (T4唤醒)  │
 * └────────────────────────────────────────┘
 *
 * 原理：基于 AQS 共享模式
 *   state = permits
 *   acquire() → state - 1（不足则阻塞）
 *   release() → state + 1（唤醒等待线程）
 * </pre>
 *
 * <h3>四、三者对比</h3>
 * <pre>
 * ┌─────────────────┬────────────────┬────────────────┬────────────────┐
 * │                 │ CountDownLatch │ CyclicBarrier  │ Semaphore      │
 * ├─────────────────┼────────────────┼────────────────┼────────────────┤
 * │ 等待方式         │ 一个等多个      │ 多个互相等      │ 控制并发数      │
 * │ 可否重用         │ 不可重用       │ 可重用(reset)  │ 可重用          │
 * │ 底层实现         │ AQS 共享模式   │ Lock+Condition │ AQS 共享模式    │
 * │ 计数方向         │ 递减到 0       │ 递减到 0       │ 动态增减        │
 * │ 典型场景         │ 主线程汇总     │ 多线程分阶段    │ 限流/资源池     │
 * └─────────────────┴────────────────┴────────────────┴────────────────┘
 * </pre>
 *
 * <h3>五、下一步</h3>
 * <p>线程池 —— ThreadPoolExecutor 核心参数与执行流程，掌握生产环境线程池配置。</p>
 *
 * @author kungfu
 * @since D10
 */
package com.kungfu.concurrent.d3_concurrent_util;
