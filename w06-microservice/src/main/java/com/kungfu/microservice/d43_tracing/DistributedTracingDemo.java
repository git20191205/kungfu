package com.kungfu.microservice.d43_tracing;

import java.util.*;

/**
 * 【Demo】手写 Mini 链路追踪 — 理解 SkyWalking/Zipkin 原理
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>核心概念: Trace / Span / TraceId / SpanId / ParentSpanId</li>
 *   <li>手写 Mini Tracing: UUID + Span 树</li>
 *   <li>模拟调用链: Gateway → OrderService → StockService → PaymentService</li>
 *   <li>TraceId 传播: HTTP Header (X-Trace-Id)</li>
 *   <li>Trace 数据格式（类 Zipkin/Jaeger）</li>
 *   <li>SkyWalking vs Zipkin vs Jaeger 对比</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * <p>分布式链路追踪是微服务可观测性的核心，排查跨服务问题的利器。</p>
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D43 - 链路追踪
 */
public class DistributedTracingDemo {

    // =============================================================
    // Span 数据结构
    // =============================================================

    static class Span {
        String traceId;
        String spanId;
        String parentSpanId;
        String serviceName;
        String operationName;
        long startTime;
        long endTime;
        String status;
        Map<String, String> tags = new LinkedHashMap<>();
        List<String> logs = new ArrayList<>();

        Span(String traceId, String spanId, String parentSpanId,
             String serviceName, String operationName) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.serviceName = serviceName;
            this.operationName = operationName;
            this.startTime = System.currentTimeMillis();
            this.status = "OK";
        }

        void finish() {
            this.endTime = System.currentTimeMillis();
        }

        long duration() {
            return endTime - startTime;
        }
    }

    // =============================================================
    // Trace 上下文（模拟 ThreadLocal 传播）
    // =============================================================

    static class TraceContext {
        private static final ThreadLocal<String> currentTraceId = new ThreadLocal<>();
        private static final ThreadLocal<String> currentSpanId = new ThreadLocal<>();

        static void set(String traceId, String spanId) {
            currentTraceId.set(traceId);
            currentSpanId.set(spanId);
        }

        static String getTraceId() { return currentTraceId.get(); }
        static String getSpanId() { return currentSpanId.get(); }

        static void clear() {
            currentTraceId.remove();
            currentSpanId.remove();
        }
    }

    // =============================================================
    // Mini Tracer
    // =============================================================

    static class MiniTracer {
        private final List<Span> spans = new ArrayList<>();
        private int spanCounter = 0;

        String generateTraceId() {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        String generateSpanId() {
            return String.format("span-%02d", ++spanCounter);
        }

        Span startSpan(String serviceName, String operationName) {
            String traceId = TraceContext.getTraceId();
            String parentSpanId = TraceContext.getSpanId();

            if (traceId == null) {
                // 入口 Span，生成新 TraceId
                traceId = generateTraceId();
            }

            String spanId = generateSpanId();
            Span span = new Span(traceId, spanId, parentSpanId, serviceName, operationName);
            spans.add(span);

            // 更新上下文
            TraceContext.set(traceId, spanId);
            return span;
        }

        void finishSpan(Span span) {
            span.finish();
            // 恢复父 Span 上下文
            TraceContext.set(span.traceId, span.parentSpanId);
        }

        List<Span> getSpans() { return spans; }
    }

    // =============================================================
    // 模拟微服务
    // =============================================================

    static class MiniService {
        private final String name;
        private final MiniTracer tracer;

        MiniService(String name, MiniTracer tracer) {
            this.name = name;
            this.tracer = tracer;
        }

        String handle(String operation, long simulatedLatencyMs, Runnable downstream) {
            Span span = tracer.startSpan(name, operation);
            span.tags.put("http.method", "GET");
            span.tags.put("http.url", "http://" + name + "/" + operation);

            // 模拟 HTTP Header 传播
            String traceId = TraceContext.getTraceId();
            String spanId = TraceContext.getSpanId();
            span.logs.add("Propagate headers: X-Trace-Id=" + traceId + ", X-Span-Id=" + spanId);

            try {
                // 模拟处理耗时
                Thread.sleep(simulatedLatencyMs);

                // 调用下游服务
                if (downstream != null) {
                    downstream.run();
                }

                span.status = "OK";
                span.tags.put("http.status_code", "200");
                return "OK";
            } catch (Exception e) {
                span.status = "ERROR";
                span.tags.put("http.status_code", "500");
                span.tags.put("error", e.getMessage());
                return "ERROR";
            } finally {
                tracer.finishSpan(span);
            }
        }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  手写 Mini 链路追踪 — Tracing 原理");
        System.out.println("========================================\n");

        showConcepts();
        demoTracing();
        showHeaderPropagation();
        showComparison();
        showSkyWalkingArchitecture();
        showKnowledgeLink();
    }

    private static void showConcepts() {
        System.out.println("=== 一、链路追踪核心概念 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    Trace / Span 关系                           │");
        System.out.println("  │                                                              │");
        System.out.println("  │  Trace (一次完整请求链路，共享同一个 TraceId)                  │");
        System.out.println("  │  │                                                           │");
        System.out.println("  │  ├── Span-A: Gateway (root span)                             │");
        System.out.println("  │  │   ├── Span-B: OrderService                                │");
        System.out.println("  │  │   │   ├── Span-C: StockService                            │");
        System.out.println("  │  │   │   └── Span-D: PaymentService                          │");
        System.out.println("  │  │   └── (return)                                            │");
        System.out.println("  │  └── (return)                                                │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  核心字段：");
        System.out.println("    ├─ TraceId:      全局唯一，贯穿整条链路 (如 UUID)");
        System.out.println("    ├─ SpanId:       当前操作的唯一标识");
        System.out.println("    ├─ ParentSpanId: 父操作的 SpanId（root span 为 null）");
        System.out.println("    ├─ ServiceName:  服务名称");
        System.out.println("    ├─ OperationName:操作名称");
        System.out.println("    ├─ StartTime:    开始时间");
        System.out.println("    ├─ Duration:     耗时");
        System.out.println("    └─ Tags/Logs:    附加信息\n");
    }

    private static void demoTracing() {
        System.out.println("=== 二、模拟分布式调用链路 ===\n");
        System.out.println("  调用链: Gateway → OrderService → StockService → PaymentService\n");

        MiniTracer tracer = new MiniTracer();
        TraceContext.clear();

        MiniService gateway = new MiniService("api-gateway", tracer);
        MiniService orderService = new MiniService("order-service", tracer);
        MiniService stockService = new MiniService("stock-service", tracer);
        MiniService paymentService = new MiniService("payment-service", tracer);

        // 模拟请求链路
        gateway.handle("/api/order/create", 5, () -> {
            orderService.handle("/orders/create", 10, () -> {
                stockService.handle("/stock/deduct", 15, null);
                paymentService.handle("/pay/process", 20, null);
            });
        });

        // 打印 Trace 树
        System.out.println("\n  ── Trace 树 ──\n");
        List<Span> spans = tracer.getSpans();
        String traceId = spans.get(0).traceId;
        System.out.println("  TraceId: " + traceId + "\n");

        // 时间线视图
        long baseTime = spans.get(0).startTime;
        System.out.println("  ┌─ 时间线 (ms) ──────────────────────────────────────────────┐");
        for (Span span : spans) {
            long offset = span.startTime - baseTime;
            long dur = span.duration();
            int indent = 0;
            if (span.parentSpanId == null) indent = 0;
            else if (span.parentSpanId.equals("span-01")) indent = 1;
            else indent = 2;

            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < offset / 2; i++) bar.append(" ");
            for (int i = 0; i < Math.max(1, dur / 2); i++) bar.append("█");

            String pad = indent == 0 ? "" : indent == 1 ? "  " : "    ";
            System.out.printf("  │ %s%-18s [%s] %3dms  %s%n",
                    pad, span.serviceName, span.spanId, dur, bar);
        }
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        // Zipkin 格式输出
        System.out.println("  ── Trace 数据（类 Zipkin JSON 格式）──\n");
        for (Span span : spans) {
            System.out.println("    {");
            System.out.println("      \"traceId\":    \"" + span.traceId + "\",");
            System.out.println("      \"spanId\":     \"" + span.spanId + "\",");
            System.out.println("      \"parentId\":   \"" + (span.parentSpanId != null ? span.parentSpanId : "") + "\",");
            System.out.println("      \"service\":    \"" + span.serviceName + "\",");
            System.out.println("      \"operation\":  \"" + span.operationName + "\",");
            System.out.println("      \"duration\":   " + span.duration() + "ms,");
            System.out.println("      \"status\":     \"" + span.status + "\",");
            System.out.println("      \"tags\":       " + span.tags);
            System.out.println("    }");
        }
        System.out.println();
    }

    private static void showHeaderPropagation() {
        System.out.println("=== 三、TraceId 传播机制 ===\n");

        System.out.println("  TraceId 通过 HTTP Header 在服务间传播：\n");

        System.out.println("  ┌──────────────┐  HTTP Request   ┌──────────────┐");
        System.out.println("  │   Gateway    │ ──────────────→ │ OrderService │");
        System.out.println("  │              │  Headers:        │              │");
        System.out.println("  │ 生成 TraceId │  X-Trace-Id: abc│ 读取 TraceId │");
        System.out.println("  │ 生成 SpanId  │  X-Span-Id: s01 │ 生成新 SpanId│");
        System.out.println("  └──────────────┘                 └──────┬───────┘");
        System.out.println("                                          │");
        System.out.println("                    HTTP Request          ▼");
        System.out.println("                   ┌──────────────────────────────┐");
        System.out.println("                   │  Headers:                    │");
        System.out.println("                   │  X-Trace-Id: abc (同一个)    │");
        System.out.println("                   │  X-Span-Id: s02 (新的)      │");
        System.out.println("                   │  X-Parent-Span-Id: s01      │");
        System.out.println("                   └──────────────┬───────────────┘");
        System.out.println("                                  ▼");
        System.out.println("                           ┌──────────────┐");
        System.out.println("                           │ StockService │");
        System.out.println("                           └──────────────┘\n");

        System.out.println("  传播方式：");
        System.out.println("    ├─ HTTP Header:  X-B3-TraceId / X-Trace-Id");
        System.out.println("    ├─ gRPC Metadata: 类似 HTTP Header");
        System.out.println("    ├─ MQ Message:   消息属性中携带 TraceId");
        System.out.println("    └─ ThreadLocal:  同进程内通过 ThreadLocal 传递\n");
    }

    private static void showComparison() {
        System.out.println("=== 四、链路追踪框架对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │              │ SkyWalking ★    │ Zipkin           │ Jaeger           │");
        System.out.println("  ├──────────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 探针方式     │ Java Agent(无侵入)│ SDK 埋点(侵入)  │ SDK 埋点(侵入)  │");
        System.out.println("  │ 性能影响     │ ★ 极小          │ 较小             │ 较小             │");
        System.out.println("  │ 存储         │ ES/H2/MySQL      │ ES/MySQL/Cassandra│ ES/Cassandra    │");
        System.out.println("  │ UI          │ ★ 功能丰富      │ 简洁             │ 功能丰富         │");
        System.out.println("  │ 告警         │ ✓ 内置          │ ✗               │ ✗               │");
        System.out.println("  │ 服务拓扑     │ ✓ 自动生成      │ ✓ 依赖图        │ ✓ 依赖图        │");
        System.out.println("  │ 语言支持     │ Java/.NET/Go/PHP │ 多语言           │ 多语言           │");
        System.out.println("  │ 社区         │ Apache 顶级项目  │ Twitter 开源     │ Uber 开源(CNCF) │");
        System.out.println("  └──────────────┴──────────────────┴──────────────────┴──────────────────┘\n");
    }

    private static void showSkyWalkingArchitecture() {
        System.out.println("=== 五、SkyWalking 架构 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    SkyWalking 架构                             │");
        System.out.println("  │                                                              │");
        System.out.println("  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │");
        System.out.println("  │  │ Service A   │  │ Service B   │  │ Service C   │         │");
        System.out.println("  │  │ + Agent     │  │ + Agent     │  │ + Agent     │         │");
        System.out.println("  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │");
        System.out.println("  │         │ gRPC           │ gRPC           │ gRPC            │");
        System.out.println("  │         └────────────────┼────────────────┘                 │");
        System.out.println("  │                          ▼                                   │");
        System.out.println("  │              ┌───────────────────────┐                       │");
        System.out.println("  │              │   OAP Server          │                       │");
        System.out.println("  │              │  (分析 + 聚合 + 告警) │                       │");
        System.out.println("  │              └───────────┬───────────┘                       │");
        System.out.println("  │                   ┌──────┴──────┐                            │");
        System.out.println("  │                   ▼             ▼                            │");
        System.out.println("  │           ┌────────────┐ ┌────────────┐                     │");
        System.out.println("  │           │ Storage    │ │ UI         │                     │");
        System.out.println("  │           │ (ES/MySQL) │ │ (Dashboard)│                     │");
        System.out.println("  │           └────────────┘ └────────────┘                     │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ SkyWalking 优势：");
        System.out.println("    1. Java Agent 方式，无代码侵入（-javaagent:skywalking-agent.jar）");
        System.out.println("    2. 自动采集: HTTP / Dubbo / MySQL / Redis / MQ 等");
        System.out.println("    3. 服务拓扑图自动生成");
        System.out.println("    4. 内置告警规则（响应时间、错误率等）\n");
    }

    private static void showKnowledgeLink() {
        System.out.println("【知识串联】");
        System.out.println("  D42 熔断降级 → D43 链路追踪 → 微服务可观测性三大支柱");
        System.out.println("  可观测性 = Tracing(链路追踪) + Metrics(指标监控) + Logging(日志)");
        System.out.println("  SkyWalking Agent 基于 Java Agent 技术（字节码增强，类似 D15 AOP）");
    }
}