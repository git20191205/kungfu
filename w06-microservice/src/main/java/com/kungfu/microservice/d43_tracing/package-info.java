/**
 * D43 — 链路追踪
 *
 * <h2>知识体系</h2>
 * <pre>
 *   链路追踪
 *   └── DistributedTracingDemo
 *       ├── 核心概念: Trace / Span / TraceId / SpanId / ParentSpanId
 *       ├── 手写 Mini Tracing（UUID + Span 树）
 *       ├── 模拟调用链: Gateway → Order → Stock → Payment
 *       ├── TraceId 传播: HTTP Header (X-Trace-Id)
 *       ├── Trace 数据格式（类 Zipkin/Jaeger）
 *       ├── SkyWalking vs Zipkin vs Jaeger 对比
 *       └── SkyWalking 架构示意
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.microservice.d43_tracing;
