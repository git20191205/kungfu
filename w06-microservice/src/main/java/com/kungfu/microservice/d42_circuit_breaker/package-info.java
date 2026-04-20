/**
 * D42 — 熔断降级
 *
 * <h2>知识体系</h2>
 * <pre>
 *   熔断降级
 *   └── CircuitBreakerDemo
 *       ├── 熔断器三态: CLOSED → OPEN → HALF_OPEN
 *       ├── 手写 Mini 熔断器（失败计数 + 超时恢复）
 *       ├── 模拟: 正常 → 故障累积 → 熔断 → 半开探测 → 恢复
 *       ├── 滑动窗口: 计数窗口 vs 时间窗口
 *       ├── Sentinel vs Hystrix vs Resilience4j 对比
 *       └── Sentinel 规则: 流控 / 降级 / 系统保护
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.microservice.d42_circuit_breaker;
