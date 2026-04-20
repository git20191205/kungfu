package com.kungfu.distributed.d32_distributed_id;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【Demo】雪花算法（Snowflake）— 分布式 ID 生成
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>手写完整的 Snowflake 算法</li>
 *   <li>ID 结构解析：符号位 + 时间戳 + 机器ID + 序列号</li>
 *   <li>并发安全性验证：多线程生成 100 万 ID 无重复</li>
 *   <li>对比其他分布式 ID 方案</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："分布式 ID 怎么生成？雪花算法原理？时钟回拨怎么处理？"
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D32 - 分布式ID
 */
public class SnowflakeDemo {

    // =============================================================
    // 手写 Snowflake 算法
    // =============================================================

    /**
     * Snowflake ID 结构（64 位）：
     * 0 | 0000...0000 (41位时间戳) | 00000 (5位数据中心) | 00000 (5位机器) | 000000000000 (12位序列号)
     */
    static class Snowflake {
        // 起始时间戳（2024-01-01 00:00:00）
        private static final long EPOCH = 1704067200000L;

        // 各部分位数
        private static final long DATACENTER_BITS = 5L;
        private static final long WORKER_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;

        // 最大值
        private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS); // 31
        private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);         // 31
        private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);         // 4095

        // 位移量
        private static final long WORKER_SHIFT = SEQUENCE_BITS;                   // 12
        private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS; // 17
        private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS; // 22

        private final long datacenterId;
        private final long workerId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        Snowflake(long datacenterId, long workerId) {
            if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
                throw new IllegalArgumentException("datacenterId 超出范围 [0, " + MAX_DATACENTER_ID + "]");
            }
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException("workerId 超出范围 [0, " + MAX_WORKER_ID + "]");
            }
            this.datacenterId = datacenterId;
            this.workerId = workerId;
        }

        synchronized long nextId() {
            long timestamp = System.currentTimeMillis();

            // 时钟回拨检测
            if (timestamp < lastTimestamp) {
                long offset = lastTimestamp - timestamp;
                if (offset <= 5) {
                    // 小幅回拨：等待追上
                    try { Thread.sleep(offset << 1); } catch (InterruptedException ignored) {}
                    timestamp = System.currentTimeMillis();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException("时钟回拨 " + (lastTimestamp - timestamp) + "ms，拒绝生成 ID");
                    }
                } else {
                    throw new RuntimeException("时钟回拨 " + offset + "ms，拒绝生成 ID");
                }
            }

            if (timestamp == lastTimestamp) {
                // 同一毫秒内，序列号递增
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    // 序列号溢出，等待下一毫秒
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                // 新的毫秒，序列号归零
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_SHIFT)
                    | (workerId << WORKER_SHIFT)
                    | sequence;
        }

        private long waitNextMillis(long last) {
            long ts = System.currentTimeMillis();
            while (ts <= last) {
                ts = System.currentTimeMillis();
            }
            return ts;
        }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  雪花算法（Snowflake）— 分布式 ID");
        System.out.println("========================================\n");

        showIdStructure();
        demoGenerate();
        demoConcurrency();
        demoParseId();
        showAlternatives();
    }

    private static void showIdStructure() {
        System.out.println("=== 一、Snowflake ID 结构（64 位）===\n");
        System.out.println("  ┌───┬─────────────────────────────────────────┬───────────┬───────────┬──────────────────┐");
        System.out.println("  │ 0 │ 41 位时间戳（毫秒级，可用 69 年）      │ 5位数据中心│ 5位机器ID │ 12位序列号(4096) │");
        System.out.println("  └───┴─────────────────────────────────────────┴───────────┴───────────┴──────────────────┘");
        System.out.println("  1位   41位                                      5位          5位         12位\n");

        System.out.println("  容量计算：");
        System.out.println("    时间戳 41 位: 2^41 ms ≈ 69.7 年");
        System.out.println("    数据中心 5 位: 最多 32 个数据中心");
        System.out.println("    机器 5 位: 每个数据中心最多 32 台机器");
        System.out.println("    序列号 12 位: 每毫秒每台机器最多 4096 个 ID");
        System.out.println("    总 QPS: 32 × 32 × 4096 × 1000 = 41.9 亿/秒\n");
    }

    private static void demoGenerate() {
        System.out.println("=== 二、生成 ID ===\n");

        Snowflake sf = new Snowflake(1, 1);
        System.out.println("  Snowflake(datacenterId=1, workerId=1)\n");

        for (int i = 0; i < 10; i++) {
            long id = sf.nextId();
            System.out.println("    ID[" + i + "]: " + id);
        }
        System.out.println();
        System.out.println("  特点：ID 趋势递增（同一毫秒内严格递增）\n");
    }

    private static void demoConcurrency() throws Exception {
        System.out.println("=== 三、并发安全性验证 ===\n");

        Snowflake sf = new Snowflake(1, 1);
        int threadCount = 10;
        int idsPerThread = 100000;
        int totalIds = threadCount * idsPerThread;

        ConcurrentHashMap<Long, Boolean> idSet = new ConcurrentHashMap<>();
        AtomicLong duplicateCount = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long start = System.currentTimeMillis();
        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                for (int i = 0; i < idsPerThread; i++) {
                    long id = sf.nextId();
                    if (idSet.putIfAbsent(id, Boolean.TRUE) != null) {
                        duplicateCount.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("  " + threadCount + " 线程 × " + idsPerThread + " ID = " + totalIds + " 个 ID");
        System.out.println("  耗时: " + elapsed + " ms");
        System.out.println("  QPS: " + String.format("%,.0f", totalIds * 1000.0 / elapsed) + " ID/s");
        System.out.println("  重复数: " + duplicateCount.get() + (duplicateCount.get() == 0 ? " ✓ 无重复" : " ✗ 有重复！"));
        System.out.println();
    }

    private static void demoParseId() {
        System.out.println("=== 四、解析 ID ===\n");

        Snowflake sf = new Snowflake(3, 7);
        long id = sf.nextId();

        long timestamp = (id >> 22) + 1704067200000L;
        long datacenterId = (id >> 17) & 0x1F;
        long workerId = (id >> 12) & 0x1F;
        long sequence = id & 0xFFF;

        System.out.println("  ID: " + id);
        System.out.println("  二进制: " + Long.toBinaryString(id));
        System.out.println("  解析结果：");
        System.out.println("    时间戳:    " + timestamp + " (" + new java.util.Date(timestamp) + ")");
        System.out.println("    数据中心:  " + datacenterId);
        System.out.println("    机器ID:    " + workerId);
        System.out.println("    序列号:    " + sequence);
        System.out.println();
    }

    private static void showAlternatives() {
        System.out.println("=== 五、分布式 ID 方案对比 ===\n");

        System.out.println("  ┌──────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("  │ 方案             │ 有序性       │ 性能         │ 依赖         │ 适用场景     │");
        System.out.println("  ├──────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ UUID             │ 无序         │ ★★★       │ 无           │ 非DB主键     │");
        System.out.println("  │ DB 自增          │ 严格递增     │ ★           │ 数据库       │ 单库小规模   │");
        System.out.println("  │ Redis INCR       │ 严格递增     │ ★★         │ Redis        │ 中等规模     │");
        System.out.println("  │ Snowflake ★     │ 趋势递增     │ ★★★       │ 无           │ 大规模分布式 │");
        System.out.println("  │ Leaf (美团)      │ 趋势递增     │ ★★★       │ DB/ZK        │ 企业级       │");
        System.out.println("  └──────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: 雪花算法时钟回拨怎么处理？");
        System.out.println("    A: 1) 小幅回拨（<5ms）→ 等待追上");
        System.out.println("       2) 大幅回拨 → 拒绝生成，抛异常");
        System.out.println("       3) 美团 Leaf 方案：用 ZK 记录上次时间戳，启动时校验");
        System.out.println();
    }
}
