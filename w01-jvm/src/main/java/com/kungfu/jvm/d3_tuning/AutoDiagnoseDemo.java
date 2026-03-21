package com.kungfu.jvm.d3_tuning;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【Demo】自动化 JVM 诊断
 *
 * <h3>这个 Demo 演示什么？</h3>
 * 将 jps/jstack 等诊断命令自动化执行，不需要手动在另一个终端操作。
 * 程序会自动制造问题场景 → 自动执行诊断命令 → 自动保存结果 → 自动分析结论。
 *
 * <h3>三个诊断场景</h3>
 * <ol>
 *   <li>CPU 飙高诊断 — 自动执行 jstack，找出 RUNNABLE 死循环线程</li>
 *   <li>死锁诊断 — 自动执行 jstack，检测 "Found one Java-level deadlock"</li>
 *   <li>线程池满诊断 — 自动执行 jstack，统计各线程状态分布</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，无需特殊 VM 参数。
 * 诊断结果保存到 docs 目录下。
 *
 * @author kungfu
 * @since D3 - JVM调优实战
 * @see ThreadTroubleshootingDemo 手动排查版（需要在另一个终端执行命令）
 */
public class AutoDiagnoseDemo {

    /** 诊断结果保存目录（与其他 Demo 的日志文件放在一起） */
    private static final String DOCS_DIR = "w01-jvm/src/main/java/com/kungfu/jvm/d3_tuning/docs";

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  自动化 JVM 诊断");
        System.out.println("========================================\n");

        String pid = getPid();
        System.out.println("  当前进程 PID: " + pid);

        // 确保 docs 目录存在
        Files.createDirectories(Paths.get(DOCS_DIR));

        System.out.println("  诊断结果将保存到 " + DOCS_DIR + " 目录\n");

        // 依次运行 3 个诊断场景
        diagnoseCpuSpike(pid);
        diagnoseDeadlock(pid);
        diagnoseThreadPoolFull(pid);

        System.out.println("========================================");
        System.out.println("  所有诊断完成！");
        System.out.println("  请查看 " + DOCS_DIR + " 目录下的诊断文件");
        System.out.println("========================================");
    }

    // =============================================================
    // 场景一：CPU 飙高诊断
    // =============================================================
    private static void diagnoseCpuSpike(String pid) throws Exception {
        System.out.println("--- 场景一：CPU 飙高诊断 ---\n");

        // 1. 启动死循环线程（AtomicBoolean 保证可见性，否则 JIT 可能缓存值导致线程停不下来）
        AtomicBoolean running = new AtomicBoolean(true);
        Thread cpuHog = new Thread(() -> {
            while (running.get()) {
                // 死循环，CPU 飙满
            }
        }, "cpu-hog-thread");
        cpuHog.setDaemon(true);
        cpuHog.start();
        System.out.println("  [启动] 死循环线程 cpu-hog-thread");

        // 2. 等待线程跑起来
        Thread.sleep(2000);

        // 3. 自动执行 jps
        System.out.println("  [执行] jps -l");
        String jpsOutput = runCommand("jps", "-l");
        saveToFile("05.AutoDiagnoseDemo_cpu_jps.txt", jpsOutput);
        System.out.println("  [保存] → 05.AutoDiagnoseDemo_cpu_jps.txt");

        // 4. 自动执行 jstack
        System.out.println("  [执行] jstack " + pid);
        String jstackOutput = runCommand("jstack", pid);
        saveToFile("05.AutoDiagnoseDemo_cpu_jstack.txt", jstackOutput);
        System.out.println("  [保存] → 05.AutoDiagnoseDemo_cpu_jstack.txt");

        // 5. 解析 jstack 输出，找出 RUNNABLE 线程
        System.out.println("\n  [分析] RUNNABLE 线程：");
        String[] lines = jstackOutput.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("java.lang.Thread.State: RUNNABLE")) {
                // 往上找线程名
                for (int j = i - 1; j >= 0; j--) {
                    if (lines[j].startsWith("\"")) {
                        System.out.println("    → " + lines[j].trim());
                        break;
                    }
                }
            }
        }

        // 6. 停止死循环线程
        running.set(false);
        cpuHog.join(3000);
        if (cpuHog.isAlive()) {
            cpuHog.stop(); // 兜底强制终止
        }
        System.out.println("\n  [清理] 死循环线程已停止");

        // 7. 输出诊断结论
        System.out.println("\n  ★ 诊断结论：");
        System.out.println("    如果 cpu-hog-thread 处于 RUNNABLE 状态，说明它在持续消耗 CPU");
        System.out.println("    jstack 堆栈可定位到具体死循环代码行\n");
    }

    // =============================================================
    // 场景二：死锁诊断
    // =============================================================
    // 用 ReentrantLock 替代 synchronized，这样可以用 interrupt() 干净终止死锁线程
    // jstack 同样能检测到 ReentrantLock 的死锁
    private static final ReentrantLock LOCK_A = new ReentrantLock();
    private static final ReentrantLock LOCK_B = new ReentrantLock();

    private static void diagnoseDeadlock(String pid) throws Exception {
        System.out.println("--- 场景二：死锁诊断 ---\n");

        // 1. 启动两个交叉锁线程
        CountDownLatch latch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try {
                LOCK_A.lockInterruptibly();
                try {
                    latch.countDown();
                    Thread.sleep(100);
                    LOCK_B.lockInterruptibly();
                    try {
                        // 不会执行到这里
                    } finally { LOCK_B.unlock(); }
                } finally { LOCK_A.unlock(); }
            } catch (InterruptedException e) {
                // 被 interrupt 终止，正常退出
            }
        }, "deadlock-thread-1");

        Thread t2 = new Thread(() -> {
            try {
                LOCK_B.lockInterruptibly();
                try {
                    latch.countDown();
                    Thread.sleep(100);
                    LOCK_A.lockInterruptibly();
                    try {
                        // 不会执行到这里
                    } finally { LOCK_A.unlock(); }
                } finally { LOCK_B.unlock(); }
            } catch (InterruptedException e) {
                // 被 interrupt 终止，正常退出
            }
        }, "deadlock-thread-2");

        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        System.out.println("  [启动] 两个交叉锁线程");

        // 2. 等待死锁形成
        latch.await();
        Thread.sleep(2000);

        // 3. 自动执行 jstack
        System.out.println("  [执行] jstack " + pid);
        String jstackOutput = runCommand("jstack", pid);
        saveToFile("05.AutoDiagnoseDemo_deadlock_jstack.txt", jstackOutput);
        System.out.println("  [保存] → 05.AutoDiagnoseDemo_deadlock_jstack.txt");

        // 4. 解析 jstack 输出，找死锁信息
        boolean deadlockFound = jstackOutput.contains("Found one Java-level deadlock")
                || jstackOutput.contains("Found a total of 1 deadlock");

        // 5. 输出诊断结论
        System.out.println("\n  ★ 诊断结论：");
        if (deadlockFound) {
            System.out.println("    检测到死锁！jstack 输出中包含死锁信息");
            System.out.println("    修复方案：统一锁顺序 / 用 tryLock + 超时");
        } else {
            System.out.println("    jstack 未检测到死锁标记");
            System.out.println("    注意：ReentrantLock 死锁需要 jstack -l 才能检测到");
        }

        // 6. 清理：lockInterruptibly 的死锁可以用 interrupt() 干净终止
        t1.interrupt();
        t2.interrupt();
        t1.join(2000);
        t2.join(2000);
        System.out.println("  [清理] 死锁线程已终止\n");
    }

    // =============================================================
    // 场景三：线程池满诊断
    // =============================================================
    private static void diagnoseThreadPoolFull(String pid) throws Exception {
        System.out.println("--- 场景三：线程池满诊断 ---\n");

        // 1. 启动线程池 + 提交慢任务
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                5, 5, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("biz-pool-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );

        // 提交慢任务填满线程池
        for (int i = 0; i < 5; i++) {
            final int taskId = i + 1;
            executor.submit(() -> {
                try {
                    Thread.sleep(30000); // 模拟慢任务
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        System.out.println("  [启动] 线程池(5线程) + 5个慢任务");

        // 2. 等待线程池填满
        Thread.sleep(2000);

        // 3. 自动执行 jstack
        System.out.println("  [执行] jstack " + pid);
        String jstackOutput = runCommand("jstack", pid);
        saveToFile("05.AutoDiagnoseDemo_threadpool_jstack.txt", jstackOutput);
        System.out.println("  [保存] → 05.AutoDiagnoseDemo_threadpool_jstack.txt");

        // 4. 统计各线程状态数量
        Map<String, Integer> stateCount = parseThreadStates(jstackOutput);

        // 5. 输出诊断结论
        System.out.println("\n  ★ 线程状态分布：");
        for (Map.Entry<String, Integer> entry : stateCount.entrySet()) {
            System.out.println("    " + entry.getKey() + ": " + entry.getValue() + " 个");
        }

        System.out.println("\n  ★ 诊断结论：");
        int waiting = stateCount.getOrDefault("WAITING", 0);
        int timedWaiting = stateCount.getOrDefault("TIMED_WAITING", 0);
        if (waiting + timedWaiting > 5) {
            System.out.println("    大量线程处于 WAITING/TIMED_WAITING 状态");
            System.out.println("    可能是线程池被慢任务占满，或下游服务超时");
            System.out.println("    修复方案：加超时 / 加熔断 / 线程池隔离");
        } else {
            System.out.println("    线程状态分布正常");
        }
        System.out.println();

        // 清理
        executor.shutdownNow();
        executor.awaitTermination(3, TimeUnit.SECONDS);
    }

    // =============================================================
    // 工具方法
    // =============================================================

    /**
     * 获取当前进程 PID
     */
    private static String getPid() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    /**
     * 执行系统命令，返回输出内容
     */
    private static String runCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(30, TimeUnit.SECONDS);
            return output.toString();
        } catch (Exception e) {
            return "命令执行失败: " + e.getMessage();
        }
    }

    /**
     * 保存内容到 docs 目录
     */
    private static void saveToFile(String filename, String content) throws Exception {
        Path path = Paths.get(DOCS_DIR, filename);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析 jstack 输出，统计线程状态分布
     */
    private static Map<String, Integer> parseThreadStates(String jstackOutput) {
        Map<String, Integer> stateCount = new HashMap<>();
        Pattern pattern = Pattern.compile("java\\.lang\\.Thread\\.State:\\s+(\\S+)");
        Matcher matcher = pattern.matcher(jstackOutput);
        while (matcher.find()) {
            String state = matcher.group(1);
            stateCount.merge(state, 1, Integer::sum);
        }
        return stateCount;
    }
}

/*
 * 【知识串联】
 * 本类是 ThreadTroubleshootingDemo 的自动化版本：
 *   - ThreadTroubleshootingDemo — 需要手动在另一个终端执行 jstack
 *   - AutoDiagnoseDemo         — 自动执行命令 + 保存结果 + 分析结论（本类）
 *
 * D3 全部知识点：
 *   1. JvmMonitorCommandDemo       — 监控命令实战
 *   2. GCLogAnalysisDemo           — GC 日志分析
 *   3. JvmParameterTuningDemo      — JVM 参数调优
 *   4. OOMTroubleshootingDemo      — OOM 案例排查
 *   5. ThreadTroubleshootingDemo   — 线程问题排查（手动版）
 *   6. AutoDiagnoseDemo            — 自动化诊断（本类）
 */
