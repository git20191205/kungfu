package com.kungfu.concurrent.d8_blockingqueue;

import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】阻塞队列 — 生产者消费者的基石
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>BlockingQueue 四组 API 的行为差异</li>
 *   <li>ArrayBlockingQueue 与 LinkedBlockingQueue 对比</li>
 *   <li>SynchronousQueue 直接交付模式</li>
 *   <li>DelayQueue 延时任务调度</li>
 *   <li>阻塞队列与线程池的关系</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * 阻塞队列是线程池的核心组件，理解不同队列的特性直接影响线程池的行为，
 * 也是手写生产者-消费者模型的基础。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可
 *
 * @author kungfu
 * @since D16 - 并发编程
 */
public class BlockingQueueDemo {

    // ========== 一、BlockingQueue 接口方法对比 ==========

    /**
     * 一、BlockingQueue 接口方法对比
     * 演示四组 API 的行为差异：抛异常、返回特殊值、阻塞、超时
     */
    private static void showAPIComparison() {
        System.out.println("=== 一、BlockingQueue 接口方法对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("  │   方式       │   插入       │   移除       │   检查       │");
        System.out.println("  ├──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 抛异常       │ add(e)       │ remove()     │ element()    │");
        System.out.println("  ├──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 返回特殊值   │ offer(e)     │ poll()       │ peek()       │");
        System.out.println("  ├──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 阻塞         │ put(e)       │ take()       │ —            │");
        System.out.println("  ├──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 超时         │ offer(e,t,u) │ poll(t,u)    │ —            │");
        System.out.println("  └──────────────┴──────────────┴──────────────┴──────────────┘");
        System.out.println();

        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        // --- 第一组：抛异常 ---
        System.out.println("  【第一组：抛异常 — add / remove / element】");
        queue.add("A");
        queue.add("B");
        System.out.println("    add(\"A\"), add(\"B\") 成功，队列已满");
        try {
            queue.add("C");
        } catch (IllegalStateException e) {
            System.out.println("    add(\"C\") → 抛出 IllegalStateException: " + e.getMessage());
        }
        System.out.println("    element() → " + queue.element() + " (查看队头，不移除)");
        System.out.println("    remove()  → " + queue.remove());
        System.out.println("    remove()  → " + queue.remove());
        try {
            queue.remove();
        } catch (NoSuchElementException e) {
            System.out.println("    remove()  → 抛出 NoSuchElementException (队列为空)");
        }
        System.out.println();

        // --- 第二组：返回特殊值 ---
        System.out.println("  【第二组：返回特殊值 — offer / poll / peek】");
        System.out.println("    offer(\"X\") → " + queue.offer("X"));
        System.out.println("    offer(\"Y\") → " + queue.offer("Y"));
        System.out.println("    offer(\"Z\") → " + queue.offer("Z") + " (队列满，返回 false)");
        System.out.println("    peek()     → " + queue.peek() + " (查看队头，不移除)");
        System.out.println("    poll()     → " + queue.poll());
        System.out.println("    poll()     → " + queue.poll());
        System.out.println("    poll()     → " + queue.poll() + " (队列空，返回 null)");
        System.out.println();

        // --- 第三组：阻塞 ---
        System.out.println("  【第三组：阻塞 — put / take】");
        System.out.println("    put() 在队列满时阻塞，take() 在队列空时阻塞");
        System.out.println("    (生产者-消费者模型的核心方法，后续 SynchronousQueue 中详细演示)");
        System.out.println();

        // --- 第四组：超时 ---
        System.out.println("  【第四组：超时 — offer(e,t,u) / poll(t,u)】");
        try {
            boolean result = queue.offer("TIMEOUT", 500, TimeUnit.MILLISECONDS);
            System.out.println("    offer(\"TIMEOUT\", 500ms) → " + result + " (队列未满，立即成功)");
            result = queue.offer("TIMEOUT2", 500, TimeUnit.MILLISECONDS);
            System.out.println("    offer(\"TIMEOUT2\", 500ms) → " + result + " (队列未满，立即成功)");
            result = queue.offer("TIMEOUT3", 500, TimeUnit.MILLISECONDS);
            System.out.println("    offer(\"TIMEOUT3\", 500ms) → " + result + " (队列满，等待 500ms 后返回 false)");
            queue.clear();
            String val = queue.poll(500, TimeUnit.MILLISECONDS);
            System.out.println("    poll(500ms) → " + val + " (队列空，等待 500ms 后返回 null)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println();
    }

    // ========== 二、ArrayBlockingQueue vs LinkedBlockingQueue ==========

    /**
     * 二、ArrayBlockingQueue vs LinkedBlockingQueue
     * 对比两种最常用的 BlockingQueue 实现的特性与吞吐量
     */
    private static void compareArrayVsLinked() throws InterruptedException {
        System.out.println("=== 二、ArrayBlockingQueue vs LinkedBlockingQueue ===\n");

        System.out.println("  特性对比：");
        System.out.println("  ┌──────────────────┬─────────────────────────┬─────────────────────────────┐");
        System.out.println("  │     特性         │  ArrayBlockingQueue     │  LinkedBlockingQueue        │");
        System.out.println("  ├──────────────────┼─────────────────────────┼─────────────────────────────┤");
        System.out.println("  │ 底层结构         │ 数组                    │ 链表                        │");
        System.out.println("  ├──────────────────┼─────────────────────────┼─────────────────────────────┤");
        System.out.println("  │ 是否有界         │ 必须指定容量（有界）     │ 可选（默认 Integer.MAX_VALUE）│");
        System.out.println("  ├──────────────────┼─────────────────────────┼─────────────────────────────┤");
        System.out.println("  │ 锁的数量         │ 一把锁（ReentrantLock） │ 两把锁（putLock + takeLock） │");
        System.out.println("  ├──────────────────┼─────────────────────────┼─────────────────────────────┤");
        System.out.println("  │ 吞吐量           │ 较低（put/take 互斥）   │ 较高（put/take 可并行）      │");
        System.out.println("  ├──────────────────┼─────────────────────────┼─────────────────────────────┤");
        System.out.println("  │ 内存             │ 预分配数组，无 GC 压力  │ 动态分配节点，有 GC 压力     │");
        System.out.println("  └──────────────────┴─────────────────────────┴─────────────────────────────┘");
        System.out.println();

        int capacity = 1024;
        int totalItems = 100_000;

        // ArrayBlockingQueue 吞吐量测试
        long arrayTime = measureThroughput(new ArrayBlockingQueue<>(capacity), totalItems);
        // LinkedBlockingQueue 吞吐量测试
        long linkedTime = measureThroughput(new LinkedBlockingQueue<>(capacity), totalItems);

        System.out.println("  吞吐量测试（" + totalItems + " 个元素，容量 " + capacity + "）：");
        System.out.println("    ArrayBlockingQueue  耗时: " + arrayTime + " ms");
        System.out.println("    LinkedBlockingQueue 耗时: " + linkedTime + " ms");
        System.out.println();
        System.out.println("  说明：LinkedBlockingQueue 使用两把锁（putLock + takeLock），");
        System.out.println("  生产者和消费者可以并行操作，通常吞吐量更高。");
        System.out.println();
    }

    /**
     * 测量队列的生产者-消费者吞吐量
     */
    private static long measureThroughput(BlockingQueue<Integer> queue, int totalItems) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        long start = System.currentTimeMillis();

        // 生产者
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < totalItems; i++) {
                    queue.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }, "Producer");

        // 消费者
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < totalItems; i++) {
                    queue.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }, "Consumer");

        producer.start();
        consumer.start();
        latch.await();

        return System.currentTimeMillis() - start;
    }

    // ========== 三、SynchronousQueue — 直接交付 ==========

    /**
     * 三、SynchronousQueue — 直接交付
     * 容量为 0，put 必须等待 take，实现线程间直接传递
     */
    private static void demonstrateSynchronousQueue() throws InterruptedException {
        System.out.println("=== 三、SynchronousQueue — 直接交付 ===\n");

        System.out.println("  SynchronousQueue 特点：");
        System.out.println("    - 容量为 0，不存储任何元素");
        System.out.println("    - put() 阻塞直到另一个线程调用 take()");
        System.out.println("    - 实现生产者与消费者之间的直接交付（hand-off）");
        System.out.println("    - Executors.newCachedThreadPool() 使用它");
        System.out.println();

        SynchronousQueue<String> queue = new SynchronousQueue<>();

        // 生产者线程
        Thread producer = new Thread(() -> {
            try {
                String[] items = {"任务A", "任务B", "任务C"};
                for (String item : items) {
                    System.out.println("    [生产者] 准备交付: " + item + " (将阻塞直到消费者取走)");
                    queue.put(item);
                    System.out.println("    [生产者] 已交付: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SQ-Producer");

        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    Thread.sleep(300); // 模拟消费者处理延迟
                    String item = queue.take();
                    System.out.println("    [消费者] 收到: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SQ-Consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println();
        System.out.println("  可以观察到：生产者 put 后必须等消费者 take 才能继续，实现了直接交付。");
        System.out.println();
    }

    // ========== 四、DelayQueue — 延时任务 ==========

    /**
     * 延时任务元素，实现 Delayed 接口
     */
    static class DelayedTask implements Delayed {
        private final String name;
        private final long executeTime; // 绝对执行时间（毫秒）

        DelayedTask(String name, long delayMs) {
            this.name = name;
            this.executeTime = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = executeTime - System.currentTimeMillis();
            return unit.convert(remaining, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            long diff = this.getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
            return Long.compare(diff, 0);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 四、DelayQueue — 延时任务
     * 按延时时间排序，只有到期的元素才能被取出
     */
    private static void demonstrateDelayQueue() throws InterruptedException {
        System.out.println("=== 四、DelayQueue — 延时任务 ===\n");

        System.out.println("  DelayQueue 特点：");
        System.out.println("    - 元素必须实现 Delayed 接口（getDelay + compareTo）");
        System.out.println("    - take() 只能取出延时到期的元素");
        System.out.println("    - 内部按到期时间排序（PriorityQueue）");
        System.out.println("    - 应用场景：定时任务、缓存过期、订单超时");
        System.out.println();

        DelayQueue<DelayedTask> delayQueue = new DelayQueue<>();

        // 放入 3 个不同延时的任务（注意：放入顺序与取出顺序不同）
        long startTime = System.currentTimeMillis();
        delayQueue.put(new DelayedTask("任务C (延时1500ms)", 1500));
        delayQueue.put(new DelayedTask("任务A (延时500ms)", 500));
        delayQueue.put(new DelayedTask("任务B (延时1000ms)", 1000));
        System.out.println("    放入 3 个任务：延时 500ms、1000ms、1500ms");
        System.out.println("    取出顺序按延时到期时间排列：\n");

        for (int i = 0; i < 3; i++) {
            DelayedTask task = delayQueue.take();
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("    取出: " + task + " (已过 " + elapsed + "ms)");
        }

        System.out.println();
    }

    // ========== 五、BlockingQueue 与线程池的关系 ==========

    /**
     * 五、BlockingQueue 与线程池的关系
     * 不同的队列类型决定了线程池的行为模式
     */
    private static void showThreadPoolRelation() {
        System.out.println("=== 五、BlockingQueue 与线程池的关系 ===\n");

        System.out.println("  ┌──────────────────────┬──────────────────────┬──────────────────────────────┐");
        System.out.println("  │   队列类型           │   对应线程池          │   行为特点                   │");
        System.out.println("  ├──────────────────────┼──────────────────────┼──────────────────────────────┤");
        System.out.println("  │ LinkedBlockingQueue  │ FixedThreadPool      │ 无界队列，不会创建非核心线程  │");
        System.out.println("  ├──────────────────────┼──────────────────────┼──────────────────────────────┤");
        System.out.println("  │ SynchronousQueue     │ CachedThreadPool     │ 直接交付，总是创建新线程      │");
        System.out.println("  ├──────────────────────┼──────────────────────┼──────────────────────────────┤");
        System.out.println("  │ ArrayBlockingQueue   │ 自定义线程池          │ 有界队列，可触发拒绝策略      │");
        System.out.println("  ├──────────────────────┼──────────────────────┼──────────────────────────────┤");
        System.out.println("  │ DelayedWorkQueue     │ ScheduledThreadPool  │ 按延时排序，定时执行          │");
        System.out.println("  └──────────────────────┴──────────────────────┴──────────────────────────────┘");
        System.out.println();

        System.out.println("  关键理解：");
        System.out.println("    1. 无界队列（LinkedBlockingQueue）→ maximumPoolSize 形同虚设");
        System.out.println("       任务永远能入队，永远不会触发创建非核心线程，也不会触发拒绝策略");
        System.out.println("       风险：任务堆积可能导致 OOM");
        System.out.println();
        System.out.println("    2. 直接交付（SynchronousQueue）→ 每个任务都交给线程处理");
        System.out.println("       没有空闲线程就创建新线程（直到 maximumPoolSize）");
        System.out.println("       适合大量短生命周期的异步任务");
        System.out.println();
        System.out.println("    3. 有界队列（ArrayBlockingQueue）→ 最可控的方式");
        System.out.println("       核心线程满 → 入队 → 队列满 → 创建非核心线程 → 都满 → 拒绝策略");
        System.out.println("       阿里巴巴规范推荐使用 ThreadPoolExecutor + 有界队列");
        System.out.println();
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  阻塞队列 — 生产者消费者的基石");
        System.out.println("========================================\n");

        showAPIComparison();
        compareArrayVsLinked();
        demonstrateSynchronousQueue();
        demonstrateDelayQueue();
        showThreadPoolRelation();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. BlockingQueue 四组 API：抛异常/返回值/阻塞/超时");
        System.out.println("  2. ArrayBlockingQueue 有界 + 一把锁; LinkedBlockingQueue 可选有界 + 两把锁");
        System.out.println("  3. SynchronousQueue 容量为 0，Executors.newCachedThreadPool 使用它");
        System.out.println("  4. DelayQueue 按延时时间排序，用于定时任务");
        System.out.println("  5. 线程池的行为很大程度取决于使用的队列类型");
        System.out.println();
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * BlockingQueue → 线程池工作队列 → 生产者消费者模式
 * → 阻塞队列是线程池 ThreadPoolExecutor 的核心组件
 * → 不同的队列选择直接决定了线程池的任务调度行为
 * → 掌握队列特性是正确配置线程池的前提
 * → 下一步学习 CompletableFuture，了解异步编程的更高级抽象
 */
