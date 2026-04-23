package com.kungfu.seckill.common.benchmark;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀系统压测工具 — 阶梯加压 + QPS/P99 统计
 *
 * 使用方式:
 *   java -cp seckill-common/target/classes com.kungfu.seckill.common.benchmark.SeckillBenchmark
 *
 * 前置条件: 4 个微服务已启动，活动已预热
 */
public class SeckillBenchmark {

    private static final String GATEWAY = "http://localhost:8090";
    private static final long ACTIVITY_ID = 1;

    // ==================== main ====================

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  秒杀系统压测 — SeckillBenchmark");
        System.out.println("========================================");
        System.out.println();

        // 1. 预热
        System.out.println("=== 预热活动 ===");
        httpPost(GATEWAY + "/api/seckill/activity/" + ACTIVITY_ID + "/warmup", null);
        System.out.println("  预热完成");
        System.out.println();

        // 2. 查询初始库存
        String activityJson = httpGet(GATEWAY + "/api/seckill/activity/" + ACTIVITY_ID);
        System.out.println("  初始库存: " + extractField(activityJson, "remainStock"));
        System.out.println();

        // 3. 阶梯加压
        int[] concurrencyLevels = {50, 100, 200, 500};
        int requestsPerLevel = 1000;

        System.out.println("=== 阶梯加压测试 ===");
        System.out.println();
        System.out.println("  ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐");
        System.out.println("  │ 并发数   │ 总请求   │ 成功     │ 失败     │ QPS      │ P50(ms)  │ P99(ms)  │");
        System.out.println("  ├──────────┼──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤");

        int userIdBase = 100000;
        for (int concurrency : concurrencyLevels) {
            // 每轮重新预热库存
            httpPost(GATEWAY + "/api/seckill/activity/" + ACTIVITY_ID + "/warmup", null);
            Thread.sleep(500);

            BenchmarkResult result = runBenchmark(concurrency, requestsPerLevel, userIdBase);
            userIdBase += requestsPerLevel + 1000;

            System.out.printf("  │ %-8d │ %-8d │ %-8d │ %-8d │ %-8.0f │ %-8d │ %-8d │%n",
                    concurrency, requestsPerLevel, result.success, result.fail,
                    result.qps, result.p50, result.p99);
        }

        System.out.println("  └──────────┴──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘");
        System.out.println();

        // 4. 最终库存
        activityJson = httpGet(GATEWAY + "/api/seckill/activity/" + ACTIVITY_ID);
        System.out.println("=== 最终库存 ===");
        System.out.println("  remainStock: " + extractField(activityJson, "remainStock"));
        System.out.println();
        System.out.println("=== 压测完成 ===");
    }

    // ==================== 核心压测方法 ====================

    static BenchmarkResult runBenchmark(int concurrency, int totalRequests, int userIdBase) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        long[] latencies = new long[totalRequests];

        long startTime = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            final long userId = userIdBase + i;
            pool.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    String url = GATEWAY + "/api/seckill/" + ACTIVITY_ID;
                    int code = doSeckillRequest(url, userId);
                    if (code == 200) {
                        success.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latencies[idx] = (System.nanoTime() - t0) / 1_000_000; // ms
                    latch.countDown();
                }
            });
        }

        latch.await();
        long totalTime = (System.nanoTime() - startTime) / 1_000_000;
        pool.shutdown();

        Arrays.sort(latencies);
        double qps = totalRequests * 1000.0 / totalTime;
        long p50 = latencies[(int) (totalRequests * 0.50)];
        long p99 = latencies[(int) (totalRequests * 0.99)];

        return new BenchmarkResult(success.get(), fail.get(), qps, p50, p99, totalTime);
    }

    // ==================== HTTP 工具方法 ====================

    static int doSeckillRequest(String urlStr, long userId) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-User-Id", String.valueOf(userId));
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int code = conn.getResponseCode();
        // 读取响应体（避免连接泄漏）
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
            while (br.readLine() != null) { /* drain */ }
        }
        conn.disconnect();
        // 解析业务 code
        return code == 200 ? 200 : 500;
    }

    static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();
        return sb.toString();
    }

    static String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (body != null) {
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes());
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();
        return sb.toString();
    }

    static String extractField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return "N/A";
        int start = idx + key.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        return json.substring(start, end).trim();
    }

    // ==================== 结果封装 ====================

    static class BenchmarkResult {
        int success, fail;
        double qps;
        long p50, p99, totalMs;

        BenchmarkResult(int success, int fail, double qps, long p50, long p99, long totalMs) {
            this.success = success;
            this.fail = fail;
            this.qps = qps;
            this.p50 = p50;
            this.p99 = p99;
            this.totalMs = totalMs;
        }
    }
}
