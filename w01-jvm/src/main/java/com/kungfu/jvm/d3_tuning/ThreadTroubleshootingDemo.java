package com.kungfu.jvm.d3_tuning;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 【Demo】线程问题排查实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>场景1 — CPU 飙高：死循环线程（用 jstack 定位具体代码行）</li>
 *   <li>场景2 — 死锁：两个线程交叉持锁（用 jstack 检测）</li>
 *   <li>场景3 — 线程池打满：大量线程 WAITING（用 jstack 看线程状态分布）</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试必问：
 * - "线上 CPU 飙到 100% 怎么排查？"
 * - "怎么排查死锁？"
 * - "线程池任务堆积怎么排查？"
 *
 * <h3>运行方式</h3>
 * 每次只取消注释一个场景，运行后在另一个终端用 jstack 排查：
 * <pre>
 * 1. jps -l                    → 找到 PID
 * 2. jstack &lt;PID&gt;              → 打印所有线程堆栈
 * 3. jstack -l &lt;PID&gt;           → 包含锁信息（死锁检测）
 *
 * CPU 飙高排查（Linux）：
 * 1. top                       → 找到 CPU 高的 Java 进程 PID
 * 2. top -Hp &lt;PID&gt;             → 找到 CPU 高的线程 TID
 * 3. printf '%x\n' &lt;TID&gt;       → TID 转 16 进制
 * 4. jstack &lt;PID&gt; | grep &lt;hex_TID&gt; -A 30  → 看这个线程在干什么
 *
 * Windows 下用 Process Explorer 或 jvisualvm 替代 top -Hp
 * </pre>
 *
 * @author kungfu
 * @since D3 - JVM调优实战
 */
public class ThreadTroubleshootingDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  线程问题排查实战");
        System.out.println("========================================\n");

        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        System.out.println("  当前进程 PID: " + pid);
        System.out.println("  请在另一个终端执行: jstack " + pid + "\n");

        // ★ 每次只取消注释一个来运行 ★
//        cpuSpike();
//         deadlock();
         threadPoolFull();

        System.out.println("========================================");
        showTroubleshootingGuide();
    }

    // =============================================================
    // 场景一：CPU 飙高 — 死循环
    // =============================================================
    /**
     * 模拟：某个业务线程陷入死循环，导致 CPU 飙高
     * 线上常见原因：
     * - 正则表达式回溯（ReDoS）
     * - HashMap 多线程并发导致链表成环（JDK 7）
     * - 业务逻辑 bug 导致 while 条件永远为 true
     * - 自旋锁 CAS 长时间不成功
     */
    private static void cpuSpike() throws InterruptedException {
        System.out.println("--- 场景一：CPU 飙高（死循环）---\n");

        System.out.println("  启动一个死循环线程，模拟 CPU 100%");
        System.out.println("  排查步骤：");
        System.out.println("  1. top → 找到 CPU 高的 Java 进程 PID");
        System.out.println("  2. top -Hp <PID> → 找到 CPU 高的线程 TID");
        System.out.println("  3. printf '%x' <TID> → 转 16 进制 (如 15521 → 0x3ca1)");
        System.out.println("  4. jstack <PID> | grep 0x3ca1 -A 30");
        System.out.println("  5. → 看到线程堆栈，定位到具体代码行！\n");

        // 启动死循环线程
        Thread cpuHog = new Thread(() -> {
            System.out.println("  [CPU-HOG] 线程开始死循环...");
            long counter = 0;
            // 模拟死循环（实际是长循环，30 秒后退出）
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 30000) {
                counter++; // 纯计算，CPU 飙满
                // 就是这一行导致 CPU 100%！
                // jstack 会定位到这个方法的这个位置
            }
            System.out.println("  [CPU-HOG] 循环结束，共执行 " + counter + " 次");
        }, "cpu-hog-thread");
        cpuHog.setDaemon(true);
        cpuHog.start();

        // 同时启动一个正常线程做对比
        Thread normal = new Thread(() -> {
            try {
                for (int i = 0; i < 30; i++) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "normal-worker-thread");
        normal.setDaemon(true);
        normal.start();

        System.out.println("  等待 30 秒（期间用 jstack 观察）...");
        System.out.println("  jstack 输出中找 \"cpu-hog-thread\" → 状态是 RUNNABLE");
        System.out.println("  jstack 输出中找 \"normal-worker-thread\" → 状态是 TIMED_WAITING\n");

        // 用 MXBean 检测线程状态
        Thread.sleep(2000); // 等线程启动
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);
        System.out.println("  当前线程状态：");
        for (ThreadInfo t : threads) {
            String name = t.getThreadName();
            if (name.equals("cpu-hog-thread") || name.equals("normal-worker-thread")) {
                System.out.println("    [" + name + "] → " + t.getThreadState());
            }
        }
        System.out.println("  → CPU 高的线程一定是 RUNNABLE 状态\n");

        cpuHog.join(30000);

        System.out.println("  ★ jstack 示例输出（关键部分）：");
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │ \"cpu-hog-thread\" #12 daemon prio=5 os_prio=0           │");
        System.out.println("  │    java.lang.Thread.State: RUNNABLE     ← CPU 高的线程  │");
        System.out.println("  │    at ...ThreadTroubleshootingDemo.lambda$cpuSpike$0    │");
        System.out.println("  │       (ThreadTroubleshootingDemo.java:XX)  ← 定位到代码行│");
        System.out.println("  └─────────────────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 场景二：死锁 — 交叉持锁
    // =============================================================
    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    /**
     * 模拟：两个线程交叉持锁 → 互相等待 → 死锁
     * 线上常见原因：
     * - 多个 synchronized 嵌套，锁顺序不一致
     * - 数据库事务交叉更新不同行
     * - 分布式锁获取顺序不一致
     */
    private static void deadlock() throws InterruptedException {
        System.out.println("--- 场景二：死锁 ---\n");

        System.out.println("  线程1: 先锁A → 再锁B");
        System.out.println("  线程2: 先锁B → 再锁A");
        System.out.println("  → 互相等待，形成死锁！\n");

        CountDownLatch latch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("  [Thread-1] 持有 LOCK_A，等待获取 LOCK_B...");
                latch.countDown();
                try { Thread.sleep(100); } catch (InterruptedException e) { }
                synchronized (LOCK_B) {
                    System.out.println("  [Thread-1] 获得 LOCK_B（不会执行到这里）");
                }
            }
        }, "deadlock-thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                System.out.println("  [Thread-2] 持有 LOCK_B，等待获取 LOCK_A...");
                latch.countDown();
                try { Thread.sleep(100); } catch (InterruptedException e) { }
                synchronized (LOCK_A) {
                    System.out.println("  [Thread-2] 获得 LOCK_A（不会执行到这里）");
                }
            }
        }, "deadlock-thread-2");

        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        latch.await(); // 等两个线程都锁住了
        Thread.sleep(500); // 确保死锁形成

        // 用 MXBean 检测死锁
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedIds = threadBean.findDeadlockedThreads();

        if (deadlockedIds != null) {
            System.out.println("\n  ★ MXBean 检测到死锁！涉及 " + deadlockedIds.length + " 个线程：");
            ThreadInfo[] deadlockedThreads = threadBean.getThreadInfo(deadlockedIds, true, true);
            for (ThreadInfo info : deadlockedThreads) {
                System.out.println("    线程: " + info.getThreadName());
                System.out.println("    状态: " + info.getThreadState());
                System.out.println("    等待锁: " + info.getLockName());
                System.out.println("    锁持有者: " + info.getLockOwnerName());
                System.out.println();
            }
        }

        System.out.println("  排查步骤：");
        System.out.println("  1. jstack <PID>  或  jstack -l <PID>");
        System.out.println("  2. 日志末尾会自动输出 \"Found one Java-level deadlock\"");
        System.out.println("  3. 显示两个线程各持有什么锁，等待什么锁\n");

        System.out.println("  ★ jstack 死锁输出示例：");
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ Found one Java-level deadlock:                      │");
        System.out.println("  │ =============================                      │");
        System.out.println("  │ \"deadlock-thread-1\":                               │");
        System.out.println("  │   waiting to lock monitor 0x... (LOCK_B)           │");
        System.out.println("  │   which is held by \"deadlock-thread-2\"             │");
        System.out.println("  │                                                     │");
        System.out.println("  │ \"deadlock-thread-2\":                               │");
        System.out.println("  │   waiting to lock monitor 0x... (LOCK_A)           │");
        System.out.println("  │   which is held by \"deadlock-thread-1\"             │");
        System.out.println("  └─────────────────────────────────────────────────────┘\n");

        System.out.println("  修复方案：");
        System.out.println("  1. 统一锁顺序: 所有线程按相同顺序获取锁（先A后B）");
        System.out.println("  2. 用 tryLock() + 超时: 获取不到锁就放弃，避免无限等待");
        System.out.println("  3. 减少锁粒度: 缩小 synchronized 的范围");
        System.out.println("  4. 用 java.util.concurrent 的高级并发工具替代 synchronized\n");

        // 等一会让用户有时间 jstack
        System.out.println("  等待 20 秒（期间用 jstack 观察死锁信息）...\n");
        Thread.sleep(20000);
    }

    // =============================================================
    // 场景三：线程池打满 — 大量线程 WAITING
    // =============================================================
    /**
     * 模拟：线程池所有线程被慢任务占满，后续任务全部阻塞
     * 线上常见原因：
     * - 下游服务超时（HTTP 调用 60s 超时）
     * - 数据库慢查询占满连接池
     * - 锁竞争导致线程排队
     */
    private static void threadPoolFull() throws InterruptedException {
        System.out.println("--- 场景三：线程池打满 ---\n");

        System.out.println("  模拟：线程池只有 3 个线程，但任务都很慢（模拟下游超时）");
        System.out.println("  队列容量 5，后续任务全部阻塞等待\n");

        // 线程池: 3 个核心线程, 3 个最大线程, 队列容量 5
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                3, 3, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(5),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("biz-worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 调用者线程执行
        );

        System.out.println("  线程池配置: core=3, max=3, queue=5");
        System.out.println("  提交 8 个慢任务（每个耗时 15 秒模拟下游超时）\n");

        for (int i = 0; i < 8; i++) {
            final int taskId = i + 1;
            executor.submit(() -> {
                System.out.println("  [Task-" + taskId + "] 开始执行（模拟调用下游服务）...");
                try {
                    Thread.sleep(15000); // 模拟下游超时 15s
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("  [Task-" + taskId + "] 执行完成");
            });
        }

        Thread.sleep(1000); // 等任务开始执行

        System.out.println("\n  线程池状态：");
        System.out.println("    活跃线程: " + executor.getActiveCount());
        System.out.println("    队列等待: " + executor.getQueue().size());
        System.out.println("    已完成:   " + executor.getCompletedTaskCount());
        System.out.println();

        // 打印线程状态
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);
        int waitingCount = 0;
        int runnableCount = 0;
        System.out.println("  业务线程状态：");
        for (ThreadInfo t : threads) {
            if (t.getThreadName().startsWith("biz-worker")) {
                System.out.println("    [" + t.getThreadName() + "] → " + t.getThreadState());
                if (t.getThreadState() == Thread.State.TIMED_WAITING) waitingCount++;
                if (t.getThreadState() == Thread.State.RUNNABLE) runnableCount++;
            }
        }
        System.out.println("    TIMED_WAITING: " + waitingCount + " 个（在 sleep 中，模拟下游超时）");
        System.out.println();

        System.out.println("  排查步骤：");
        System.out.println("  1. jstack <PID> → 看到大量线程处于 WAITING / TIMED_WAITING");
        System.out.println("  2. 堆栈显示卡在哪里（如 SocketInputStream.read → 下游超时）");
        System.out.println("  3. 线程名中通常能看出是哪个线程池\n");

        System.out.println("  ★ jstack 中线程状态含义：");
        System.out.println("  ┌──────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 状态              │ 含义                                  │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ RUNNABLE         │ 正在运行或等待 CPU 时间片               │");
        System.out.println("  │ BLOCKED          │ 等待获取 synchronized 锁               │");
        System.out.println("  │ WAITING          │ 无限等待（wait/join/park 无超时）        │");
        System.out.println("  │ TIMED_WAITING    │ 有超时的等待（sleep/wait+timeout/park）  │");
        System.out.println("  └──────────────────┴──────────────────────────────────────┘\n");

        System.out.println("  修复方案：");
        System.out.println("  1. 设置合理的超时: HTTP 调用 connectTimeout=3s, readTimeout=5s");
        System.out.println("  2. 加熔断器: Sentinel / Resilience4j，下游异常时快速失败");
        System.out.println("  3. 线程池隔离: 不同下游服务用不同线程池，互不影响");
        System.out.println("  4. 监控线程池指标: 活跃线程数、队列大小、拒绝次数\n");

        System.out.println("  等待 15 秒（期间用 jstack 观察线程状态）...\n");
        Thread.sleep(15000);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // =============================================================
    // 排查指南
    // =============================================================
    private static void showTroubleshootingGuide() {
        System.out.println("  线上排查速查表");
        System.out.println("========================================\n");

        System.out.println("  ★ CPU 飙高排查（面试必背）：");
        System.out.println("  ┌───┬──────────────────────────────────────────┐");
        System.out.println("  │ # │ 操作                                      │");
        System.out.println("  ├───┼──────────────────────────────────────────┤");
        System.out.println("  │ 1 │ top → 找 CPU 高的 Java 进程 PID           │");
        System.out.println("  │ 2 │ top -Hp <PID> → 找 CPU 高的线程 TID       │");
        System.out.println("  │ 3 │ printf '%x' <TID> → TID 转 16 进制        │");
        System.out.println("  │ 4 │ jstack <PID> | grep <hex> -A 30          │");
        System.out.println("  │ 5 │ → 看堆栈定位到具体代码行                    │");
        System.out.println("  └───┴──────────────────────────────────────────┘\n");

        System.out.println("  ★ 死锁排查：");
        System.out.println("  ┌───┬──────────────────────────────────────────┐");
        System.out.println("  │ # │ 操作                                      │");
        System.out.println("  ├───┼──────────────────────────────────────────┤");
        System.out.println("  │ 1 │ jstack -l <PID>                           │");
        System.out.println("  │ 2 │ 搜索 \"Found one Java-level deadlock\"      │");
        System.out.println("  │ 3 │ 看哪两个线程互相持锁等待                    │");
        System.out.println("  │ 4 │ 统一锁顺序 / 用 tryLock + 超时             │");
        System.out.println("  └───┴──────────────────────────────────────────┘\n");

        System.out.println("  ★ 线程池打满排查：");
        System.out.println("  ┌───┬──────────────────────────────────────────┐");
        System.out.println("  │ # │ 操作                                      │");
        System.out.println("  ├───┼──────────────────────────────────────────┤");
        System.out.println("  │ 1 │ jstack <PID> → 看线程状态分布              │");
        System.out.println("  │ 2 │ 大量 WAITING/TIMED_WAITING → 被阻塞了     │");
        System.out.println("  │ 3 │ 堆栈中找卡在哪里（通常是 I/O 调用）         │");
        System.out.println("  │ 4 │ 加超时 / 加熔断 / 线程池隔离               │");
        System.out.println("  └───┴──────────────────────────────────────────┘\n");

        System.out.println("  ★ 线程 dump 分析技巧：");
        System.out.println("    1. 连续 dump 3 次（间隔 5 秒）: jstack <PID> > dump1.txt");
        System.out.println("    2. 对比 3 次 dump → 如果同一个线程一直在同一个位置 → 就是问题点");
        System.out.println("    3. 统计各状态线程数: grep 'java.lang.Thread.State' dump.txt | sort | uniq -c");
        System.out.println("    4. 在线分析工具: https://fastthread.io");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * D3 全部知识点：
 *   1. JvmMonitorCommandDemo       — 监控命令实战
 *   2. GCLogAnalysisDemo           — GC 日志分析
 *   3. JvmParameterTuningDemo      — JVM 参数调优
 *   4. OOMTroubleshootingDemo      — OOM 案例排查
 *   5. ThreadTroubleshootingDemo   — 线程问题排查（本类）
 *
 * 线程排查 → 引出并发编程知识：
 * → synchronized 锁机制 → W02 并发编程
 * → 线程池原理与参数 → W02 并发编程
 */