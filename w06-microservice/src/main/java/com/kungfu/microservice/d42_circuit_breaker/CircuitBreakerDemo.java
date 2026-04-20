package com.kungfu.microservice.d42_circuit_breaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 【Demo】手写 Mini 熔断器 — 理解 Sentinel/Hystrix 原理
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>熔断器三态: CLOSED → OPEN → HALF_OPEN → CLOSED</li>
 *   <li>手写完整熔断器（失败计数 + 超时恢复 + 半开探测）</li>
 *   <li>模拟: 正常 → 故障累积 → 熔断 → 半开探测 → 恢复</li>
 *   <li>滑动窗口概念: 计数窗口 vs 时间窗口</li>
 *   <li>Sentinel vs Hystrix vs Resilience4j 对比</li>
 *   <li>Sentinel 规则: 流控 / 降级 / 系统保护</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * <p>熔断降级是微服务容错的核心，防止级联故障（雪崩效应）。</p>
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D42 - 熔断降级
 */
public class CircuitBreakerDemo {

    // =============================================================
    // 熔断器状态
    // =============================================================

    enum State {
        CLOSED,     // 关闭（正常放行，统计失败）
        OPEN,       // 打开（拒绝请求，返回降级）
        HALF_OPEN   // 半开（允许一个探测请求）
    }

    // =============================================================
    // Mini 熔断器
    // =============================================================

    static class MiniCircuitBreaker {
        private volatile State state = State.CLOSED;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);

        // 配置
        private final int failureThreshold;     // 失败阈值
        private final long timeoutMs;           // 熔断超时（毫秒）
        private final int halfOpenMaxProbes;    // 半开状态最大探测数
        private final String name;

        MiniCircuitBreaker(String name, int failureThreshold, long timeoutMs) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.timeoutMs = timeoutMs;
            this.halfOpenMaxProbes = 1;
        }

        /**
         * 执行受保护的调用
         */
        <T> T execute(Supplier<T> action, Supplier<T> fallback) {
            // 检查是否应该从 OPEN 转为 HALF_OPEN
            if (state == State.OPEN) {
                long elapsed = System.currentTimeMillis() - lastFailureTime.get();
                if (elapsed >= timeoutMs) {
                    state = State.HALF_OPEN;
                    successCount.set(0);
                    printState("超时到期，切换到 HALF_OPEN，允许探测");
                } else {
                    printState("OPEN 状态，直接降级 (剩余 "
                            + (timeoutMs - elapsed) + "ms)");
                    return fallback.get();
                }
            }

            // HALF_OPEN: 只允许有限探测
            if (state == State.HALF_OPEN) {
                try {
                    T result = action.get();
                    // 探测成功 → 恢复 CLOSED
                    state = State.CLOSED;
                    failureCount.set(0);
                    printState("探测成功！恢复 CLOSED");
                    return result;
                } catch (Exception e) {
                    // 探测失败 → 回到 OPEN
                    state = State.OPEN;
                    lastFailureTime.set(System.currentTimeMillis());
                    printState("探测失败！回到 OPEN");
                    return fallback.get();
                }
            }

            // CLOSED: 正常执行
            try {
                T result = action.get();
                // 成功则重置失败计数
                failureCount.set(0);
                return result;
            } catch (Exception e) {
                int failures = failureCount.incrementAndGet();
                lastFailureTime.set(System.currentTimeMillis());
                if (failures >= failureThreshold) {
                    state = State.OPEN;
                    printState("失败次数达到阈值 " + failureThreshold + "，切换到 OPEN！");
                } else {
                    printState("失败 " + failures + "/" + failureThreshold);
                }
                return fallback.get();
            }
        }

        private void printState(String msg) {
            String icon;
            switch (state) {
                case CLOSED:    icon = "✓"; break;
                case OPEN:      icon = "✗"; break;
                case HALF_OPEN: icon = "?"; break;
                default:        icon = " ";
            }
            System.out.printf("      [%s] [%s] %s — %s%n", name, icon, state, msg);
        }

        State getState() { return state; }
    }

    // =============================================================
    // 模拟的远程服务
    // =============================================================

    static class RemoteService {
        private volatile boolean healthy = true;

        void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        String call(String request) {
            if (!healthy) {
                throw new RuntimeException("Service Unavailable");
            }
            return "OK: " + request;
        }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  手写 Mini 熔断器 — 熔断降级原理");
        System.out.println("========================================\n");

        showPrinciple();
        demoCircuitBreaker();
        showSlidingWindow();
        showComparison();
        showSentinelRules();
        showKnowledgeLink();
    }

    private static void showPrinciple() {
        System.out.println("=== 一、熔断器原理 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    熔��器状态机                                │");
        System.out.println("  │                                                              │");
        System.out.println("  │     ┌──────────┐   失败达到阈值   ┌──────────┐              │");
        System.out.println("  │     │ CLOSED   │ ──────────────→ │  OPEN    │              │");
        System.out.println("  │     │ (正常)   │                  │ (熔断)   │              │");
        System.out.println("  │     └────┬─────┘ ←────────────── └────┬─────┘              │");
        System.out.println("  │          ↑       探测成功恢复          │                     │");
        System.out.println("  │          │                        超时到期                    │");
        System.out.println("  │          │       ┌──────────┐         │                     │");
        System.out.println("  │          └────── │HALF_OPEN │ ←───────┘                     │");
        System.out.println("  │     探测成功     │ (半开)   │ ──→ 探测失败回到 OPEN          │");
        System.out.println("  │                  └──────────┘                                │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  配置参数：");
        System.out.println("    ├─ failureThreshold = 5  (连续失败 5 次触发熔断)");
        System.out.println("    ├─ timeout = 5s          (熔断 5 秒后进入半开)");
        System.out.println("    └─ halfOpenProbes = 1    (半开状态允许 1 个探测请求)\n");
    }

    private static void demoCircuitBreaker() throws Exception {
        System.out.println("=== 二、模拟熔断全流程 ===\n");

        MiniCircuitBreaker breaker = new MiniCircuitBreaker("order-service", 5, 3000);
        RemoteService service = new RemoteService();

        // 阶段1: 正常请求 (CLOSED)
        System.out.println("  ── 阶段1: 正常请求 (CLOSED) ──");
        for (int i = 1; i <= 3; i++) {
            String result = breaker.execute(
                () -> service.call("req-" + System.currentTimeMillis()),
                () -> "FALLBACK: 降级响应"
            );
            System.out.println("    请求 #" + i + " → " + result);
        }
        System.out.println("    状态: " + breaker.getState() + "\n");

        // 阶段2: 服务开始故障
        System.out.println("  ── 阶段2: 服务故障，失败累积 ──");
        service.setHealthy(false);
        for (int i = 1; i <= 6; i++) {
            String result = breaker.execute(
                () -> service.call("req-fail"),
                () -> "FALLBACK: 降级响应"
            );
            System.out.println("    请求 #" + i + " → " + result);
        }
        System.out.println("    状态: " + breaker.getState() + "\n");

        // 阶段3: OPEN 状态，所有请求直接降级
        System.out.println("  ── 阶段3: OPEN 状态，请求直接降级 ──");
        for (int i = 1; i <= 3; i++) {
            String result = breaker.execute(
                () -> service.call("req-open"),
                () -> "FALLBACK: 降级响应"
            );
            System.out.println("    请求 #" + i + " → " + result);
        }
        System.out.println();

        // 阶段4: 等待超时，进入 HALF_OPEN
        System.out.println("  ── 阶段4: 等待 3 秒超时... ──");
        Thread.sleep(3200);

        // 服务恢复
        service.setHealthy(true);
        System.out.println("  ── 阶段5: 服务恢复，半开探测 ──");
        String result = breaker.execute(
            () -> service.call("probe-request"),
            () -> "FALLBACK: 降级响应"
        );
        System.out.println("    探测请求 → " + result);
        System.out.println("    状态: " + breaker.getState() + "\n");

        // 阶段6: 恢复正常
        System.out.println("  ── 阶段6: 恢复正常 (CLOSED) ──");
        for (int i = 1; i <= 3; i++) {
            result = breaker.execute(
                () -> service.call("req-recovered"),
                () -> "FALLBACK: 降级响应"
            );
            System.out.println("    请求 #" + i + " → " + result);
        }
        System.out.println("    状态: " + breaker.getState() + "\n");
    }

    private static void showSlidingWindow() {
        System.out.println("=== 三、滑动窗口 ===\n");

        System.out.println("  1. 计数滑动窗口（Sentinel 默认）");
        System.out.println("  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐");
        System.out.println("  │ ✓ │ ✓ │ ✗ │ ✓ │ ✗ │ ✗ │ ✓ │ ✗ │ ✗ │ ✗ │ ← 最近 10 次请求");
        System.out.println("  └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘");
        System.out.println("    失败率 = 6/10 = 60% > 阈值 50% → 触发熔断\n");

        System.out.println("  2. 时间滑动窗口（按时间段统计）");
        System.out.println("  ┌──────┬──────┬──────┬──────┬──────┐");
        System.out.println("  │ 0-1s │ 1-2s │ 2-3s │ 3-4s │ 4-5s │ ← 最近 5 秒");
        System.out.println("  │ 2/10 │ 1/8  │ 5/12 │ 8/10 │ 9/10 │");
        System.out.println("  └──────┴──────┴──────┴──────┴──────┘");
        System.out.println("    最近 5s 失败率 = 25/50 = 50% → 触发熔断\n");

        System.out.println("  ★ 区别：");
        System.out.println("    计数窗口: 统计最近 N 次请求的失败率");
        System.out.println("    时间窗口: 统计最近 N 秒内的失败率\n");
    }

    private static void showComparison() {
        System.out.println("=== 四、熔断框架对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │              │ Sentinel ★      │ Hystrix          │ Resilience4j     │");
        System.out.println("  ├──────────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 维护状态     │ ★ 活跃(阿里)    │ ✗ 停止维护      │ ✓ 活跃          │");
        System.out.println("  │ 隔离策略     │ 信号量           │ 线程池/信号量    │ 信号量           │");
        System.out.println("  │ 熔断策略     │ 慢调用/异常比例  │ 异常比例         │ 异常比例/慢调用  │");
        System.out.println("  │ 流控         │ ✓ QPS/线程数    │ ✗               │ ✓ 速率限制      │");
        System.out.println("  │ 控制台       │ ✓ Dashboard     │ ✓ Dashboard     │ ✗               │");
        System.out.println("  │ 动态规则     │ ✓ Nacos/Apollo  │ ✗               │ ✗               │");
        System.out.println("  │ 适用场景     │ Spring Cloud     │ 老项目           │ 轻量级           │");
        System.out.println("  └──────────────┴──────────────────┴──────────────────┴──────────────────┘\n");
    }

    private static void showSentinelRules() {
        System.out.println("=== 五、Sentinel 规则体系 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    Sentinel 规则                               │");
        System.out.println("  │                                                              │");
        System.out.println("  │  1. 流控规则 (FlowRule)                                      │");
        System.out.println("  │     ├─ QPS 模式: 每秒请求数超过阈值 → 拒绝/排队/预热         │");
        System.out.println("  │     └─ 线程数模式: 并发线程超过阈值 → 拒绝                    │");
        System.out.println("  │                                                              │");
        System.out.println("  │  2. 降级规则 (DegradeRule)                                   │");
        System.out.println("  │     ├─ 慢调用比例: RT > 阈值的比例超限 → 熔断                 │");
        System.out.println("  │     ├─ 异常比例: 异常请求比例超限 → 熔断                      │");
        System.out.println("  │     └─ 异常数: 异常请求数超限 → 熔断                          │");
        System.out.println("  │                                                              │");
        System.out.println("  │  3. 系统保护规则 (SystemRule)                                 │");
        System.out.println("  │     ├─ LOAD: 系统负载超限                                     │");
        System.out.println("  │     ├─ CPU: CPU 使用率超限                                    │");
        System.out.println("  │     └─ 总 QPS / 线程数 / RT                                  │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Sentinel 和 Hystrix 的区别？");
        System.out.println("    A: 1) Sentinel 支持流控+熔断+系统保护，Hystrix 只有熔断");
        System.out.println("       2) Sentinel 用信号量隔离（轻量），Hystrix 用线程池（重）");
        System.out.println("       3) Sentinel 有 Dashboard 实时监控 + 动态规则推送");
        System.out.println("       4) Hystrix 已停止维护，Sentinel 是阿里主推方案\n");
    }

    private static void showKnowledgeLink() {
        System.out.println("【知识串联】");
        System.out.println("  D41 网关限流 → D42 熔断降级 → D43 链路追踪");
        System.out.println("  熔断器模式防止级联故障（雪崩效应）");
        System.out.println("  令牌桶限流（D41 网关）+ 熔断降级 = 微服务容错双保险");
    }
}