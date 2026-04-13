package com.kungfu.concurrent.d4_thread_pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】手写简化版线程池
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 通过手写一个最小化的线程池，演示 ThreadPoolExecutor 的核心原理：
 * <ol>
 *   <li>Worker 线程不断从阻塞队列中取任务并执行</li>
 *   <li>execute() 的三步判断逻辑：核心线程 → 队列 → 拒绝</li>
 *   <li>shutdown() 通过中断通知所有 Worker 退出</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 线程池是并发编程中最核心的基础设施，面试高频考点。
 * 理解手写线程池的原理，才能真正掌握 ThreadPoolExecutor 的参数含义和运行机制。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，观察线程池的创建、任务提交、执行和关闭过程。
 *
 * @author kungfu
 * @since D11 - 并发编程
 */
public class MiniThreadPool {

    /** 核心线程数 */
    private final int coreSize;

    /** 任务队列（有界阻塞队列） */
    private final BlockingQueue<Runnable> taskQueue;

    /** 工作线程列表 */
    private final List<Worker> workers;

    /** 线程池是否已关闭 */
    private volatile boolean isShutdown;

    // ========================================
    // 内部类：Worker 工作线程
    // ========================================

    /**
     * Worker 是线程池中真正干活的线程。
     * 它不断从 taskQueue 中取任务并执行，直到线程池关闭且队列为空。
     */
    private class Worker extends Thread {

        Worker(String name) {
            super(name);
        }

        @Override
        public void run() {
            System.out.println("    [" + getName() + "] 启动，开始从队列取任务...");
            while (true) {
                try {
                    // 如果已经 shutdown 且队列为空，退出循环
                    if (isShutdown && taskQueue.isEmpty()) {
                        break;
                    }
                    // 带超时地从队列取任务，避免永远阻塞
                    Runnable task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (InterruptedException e) {
                    // 收到中断信号，检查是否应该退出
                    if (isShutdown) {
                        break;
                    }
                }
            }
            System.out.println("    [" + getName() + "] 退出");
        }
    }

    // ========================================
    // 构造方法
    // ========================================

    /**
     * 创建一个简化版线程池
     *
     * @param coreSize      核心线程数
     * @param queueCapacity 任务队列容量
     */
    public MiniThreadPool(int coreSize, int queueCapacity) {
        this.coreSize = coreSize;
        this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.workers = new ArrayList<>();
        this.isShutdown = false;
    }

    // ========================================
    // execute()：提交任务的核心逻辑
    // ========================================

    /**
     * 提交一个任务到线程池执行。
     * <p>
     * 判断逻辑（模拟 ThreadPoolExecutor）：
     * <ol>
     *   <li>如果当前 Worker 数 < coreSize → 创建新 Worker 线程</li>
     *   <li>否则尝试放入队列</li>
     *   <li>如果队列满了 → 拒绝（抛出 RejectedExecutionException）</li>
     * </ol>
     *
     * @param task 要执行的任务
     * @throws RejectedExecutionException 如果线程池已关闭或队列已满
     */
    public synchronized void execute(Runnable task) {
        if (isShutdown) {
            throw new RejectedExecutionException("线程池已关闭，拒绝接收新任务");
        }

        // Step 1: 如果工作线程数 < 核心线程数，创建新的 Worker
        if (workers.size() < coreSize) {
            Worker worker = new Worker("Worker-" + workers.size());
            workers.add(worker);
            worker.start();
            // 新 Worker 启动后，把任务放入队列让它取
            taskQueue.offer(task);
            return;
        }

        // Step 2: 尝试放入队列
        boolean offered = taskQueue.offer(task);
        if (offered) {
            return;
        }

        // Step 3: 队列满了，拒绝任务
        throw new RejectedExecutionException(
                "任务队列已满（容量=" + taskQueue.size() + "），拒绝任务！");
    }

    // ========================================
    // shutdown()：关闭线程池
    // ========================================

    /**
     * 关闭线程池：设置标志位并中断所有 Worker。
     * 已在队列中的任务仍会执行完毕。
     */
    public void shutdown() {
        isShutdown = true;
        for (Worker worker : workers) {
            worker.interrupt();
        }
    }

    // ========================================
    // awaitTermination()：等待所有 Worker 退出
    // ========================================

    /**
     * 等待所有 Worker 线程退出（最长等待 5 秒）。
     */
    public void awaitTermination() {
        for (Worker worker : workers) {
            try {
                worker.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========================================
    // main()：测试入口
    // ========================================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  【Demo】手写简化版线程池");
        System.out.println("========================================");
        System.out.println();

        // --------------------------------------------------
        // Step 1：创建线程池
        // --------------------------------------------------
        System.out.println(">>> Step 1：创建线程池（coreSize=2, queueCapacity=5）");
        System.out.println("    类似于 new ThreadPoolExecutor(2, 2, 0, SECONDS, new ArrayBlockingQueue<>(5))");
        System.out.println();

        MiniThreadPool pool = new MiniThreadPool(2, 5);

        // --------------------------------------------------
        // Step 2：提交任务
        // --------------------------------------------------
        System.out.println(">>> Step 2：提交 8 个任务");
        System.out.println("    前 2 个任务 → 创建核心线程执行");
        System.out.println("    第 3~7 个任务 → 放入队列等待");
        System.out.println("    第 8 个任务 → 队列满，被拒绝！");
        System.out.println();

        int submitted = 0;
        for (int i = 1; i <= 8; i++) {
            final int taskNo = i;
            try {
                pool.execute(() -> {
                    System.out.println("    [" + Thread.currentThread().getName()
                            + "] 正在执行任务 #" + taskNo);
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("    [" + Thread.currentThread().getName()
                            + "] 完成任务 #" + taskNo);
                });
                submitted++;
                System.out.println("    任务 #" + taskNo + " 提交成功");
            } catch (RejectedExecutionException e) {
                System.out.println("    任务 #" + taskNo + " 被拒绝: " + e.getMessage());
            }
        }
        System.out.println();

        // --------------------------------------------------
        // Step 3：等待任务执行
        // --------------------------------------------------
        System.out.println(">>> Step 3：等待任务执行...");
        System.out.println();
        Thread.sleep(2500);

        // --------------------------------------------------
        // Step 4：关闭线程池
        // --------------------------------------------------
        System.out.println();
        System.out.println(">>> Step 4：关闭线程池");
        System.out.println("    调用 shutdown() → 设置 isShutdown=true，中断所有 Worker");
        pool.shutdown();

        System.out.println("    调用 awaitTermination() → 等待所有 Worker 退出");
        pool.awaitTermination();
        System.out.println();

        // --------------------------------------------------
        // 测试结果
        // --------------------------------------------------
        System.out.println("========================================");
        System.out.println("  测试结果");
        System.out.println("========================================");
        System.out.println("    成功提交任务数: " + submitted);
        System.out.println("    核心线程数:     " + pool.coreSize);
        System.out.println("    队列容量:       5");
        System.out.println("    最大可接收任务: coreSize + queueCapacity = 2 + 5 = 7");
        System.out.println("    第 8 个任务被拒绝 → 符合预期！");
        System.out.println();
        System.out.println("    execute() 的三步判断逻辑：");
        System.out.println("    ┌─ workers < coreSize? ──→ 创建新 Worker 线程");
        System.out.println("    ├─ 队列未满?            ──→ 放入队列等待");
        System.out.println("    └─ 队列已满?            ──→ 拒绝（RejectedExecutionException）");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 手写线程池帮助理解 ThreadPoolExecutor 的核心原理：
 * → Worker 线程不断从队列取任务执行
 * → execute() 的判断逻辑：核心线程 → 队列 → 拒绝
 * → shutdown() 通过中断通知 Worker 退出
 */
