package com.kungfu.microservice.d41_gateway;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 【Demo】手写 Mini 网关 — 理解 Spring Cloud Gateway 原理
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>网关架构: Client → Gateway → Services</li>
 *   <li>路由匹配: 路径路由 + 谓词</li>
 *   <li>过滤器链: Pre-Filter → Route → Post-Filter</li>
 *   <li>令牌桶限流算法</li>
 *   <li>Gateway vs Zuul vs Nginx 对比</li>
 *   <li>生产配置模板</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * <p>网关是微服务的统一入口，承担路由、鉴权、限流、日志等横切关注点。</p>
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D41 - 服务网关
 */
public class GatewaySimulationDemo {

    // =============================================================
    // 请求 / 响应模型
    // =============================================================

    static class GatewayRequest {
        String method;
        String path;
        Map<String, String> headers;
        Map<String, String> queryParams;
        String clientIp;

        GatewayRequest(String method, String path, String clientIp) {
            this.method = method;
            this.path = path;
            this.clientIp = clientIp;
            this.headers = new LinkedHashMap<>();
            this.queryParams = new LinkedHashMap<>();
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static class GatewayResponse {
        int status;
        String body;
        Map<String, String> headers = new LinkedHashMap<>();

        GatewayResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    // =============================================================
    // 过滤器接口
    // =============================================================

    interface GatewayFilter {
        String name();
        /** 返回 null 表示继续，返回 GatewayResponse 表示短路 */
        GatewayResponse preFilter(GatewayRequest request);
        void postFilter(GatewayRequest request, GatewayResponse response);
    }

    // =============================================================
    // 路由定义
    // =============================================================

    static class Route {
        String id;
        String targetUri;
        Predicate<GatewayRequest> predicate;
        String predicateDesc;

        Route(String id, String targetUri, Predicate<GatewayRequest> predicate, String desc) {
            this.id = id;
            this.targetUri = targetUri;
            this.predicate = predicate;
            this.predicateDesc = desc;
        }
    }

    // =============================================================
    // 令牌桶限流器
    // =============================================================

    static class TokenBucketRateLimiter {
        private final int maxTokens;
        private final int refillRate; // tokens per second
        private double tokens;
        private long lastRefillTime;

        TokenBucketRateLimiter(int maxTokens, int refillRate) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.tokens = maxTokens;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double elapsed = (now - lastRefillTime) / 1000.0;
            tokens = Math.min(maxTokens, tokens + elapsed * refillRate);
            lastRefillTime = now;
        }

        double currentTokens() {
            refill();
            return tokens;
        }
    }

    // =============================================================
    // 具体过滤器实现
    // =============================================================

    /** 认证过滤器 */
    static class AuthFilter implements GatewayFilter {
        private final Set<String> whitelist = new HashSet<>(Arrays.asList(
            "/api/auth/login", "/api/auth/register", "/health"
        ));

        @Override
        public String name() { return "AuthFilter"; }

        @Override
        public GatewayResponse preFilter(GatewayRequest request) {
            // 白名单放行
            for (String path : whitelist) {
                if (request.path.startsWith(path)) {
                    System.out.println("      [Auth] " + request.path + " → 白名单放行 ✓");
                    return null;
                }
            }
            // 检查 Token
            String token = request.headers.get("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                System.out.println("      [Auth] " + request.path + " → 未携带Token，拒绝 ✗");
                return new GatewayResponse(401, "{\"error\":\"Unauthorized\"}");
            }
            System.out.println("      [Auth] " + request.path + " → Token验证通过 ✓");
            return null;
        }

        @Override
        public void postFilter(GatewayRequest request, GatewayResponse response) {}
    }

    /** 限流过滤器 */
    static class RateLimitFilter implements GatewayFilter {
        private final TokenBucketRateLimiter limiter;

        RateLimitFilter(int maxTokens, int refillRate) {
            this.limiter = new TokenBucketRateLimiter(maxTokens, refillRate);
        }

        @Override
        public String name() { return "RateLimitFilter"; }

        @Override
        public GatewayResponse preFilter(GatewayRequest request) {
            if (!limiter.tryAcquire()) {
                System.out.println("      [RateLimit] 令牌不足，限流 ✗ (剩余: "
                        + String.format("%.1f", limiter.currentTokens()) + ")");
                return new GatewayResponse(429, "{\"error\":\"Too Many Requests\"}");
            }
            System.out.println("      [RateLimit] 获取令牌成功 ✓ (剩余: "
                    + String.format("%.1f", limiter.currentTokens()) + ")");
            return null;
        }

        @Override
        public void postFilter(GatewayRequest request, GatewayResponse response) {}
    }

    /** 日志过滤器 */
    static class LogFilter implements GatewayFilter {
        private final AtomicLong requestId = new AtomicLong(0);

        @Override
        public String name() { return "LogFilter"; }

        @Override
        public GatewayResponse preFilter(GatewayRequest request) {
            long id = requestId.incrementAndGet();
            request.headers.put("X-Request-Id", String.valueOf(id));
            System.out.println("      [Log-Pre] #" + id + " " + request.method + " " + request.path
                    + " from " + request.clientIp);
            return null;
        }

        @Override
        public void postFilter(GatewayRequest request, GatewayResponse response) {
            response.headers.put("X-Response-Time", System.currentTimeMillis() + "ms");
            System.out.println("      [Log-Post] #" + request.headers.get("X-Request-Id")
                    + " → " + response.status);
        }
    }

    // =============================================================
    // Mini Gateway
    // =============================================================

    static class MiniGateway {
        private final List<Route> routes = new ArrayList<>();
        private final List<GatewayFilter> filters = new ArrayList<>();

        void addRoute(Route route) { routes.add(route); }
        void addFilter(GatewayFilter filter) { filters.add(filter); }

        GatewayResponse handle(GatewayRequest request) {
            System.out.println("    ┌─ Gateway 处理请求 ─────────────────────────────");
            System.out.println("    │ 请求: " + request);

            // 1. Pre-Filters
            System.out.println("    │ [Pre-Filters]");
            for (GatewayFilter filter : filters) {
                GatewayResponse shortCircuit = filter.preFilter(request);
                if (shortCircuit != null) {
                    System.out.println("    │ → 过滤器短路，直接返回 " + shortCircuit.status);
                    System.out.println("    └──────────────────────────────────────────────");
                    return shortCircuit;
                }
            }

            // 2. Route matching
            Route matched = null;
            for (Route route : routes) {
                if (route.predicate.test(request)) {
                    matched = route;
                    break;
                }
            }

            GatewayResponse response;
            if (matched == null) {
                System.out.println("    │ [Route] 无匹配路由 → 404");
                response = new GatewayResponse(404, "{\"error\":\"Not Found\"}");
            } else {
                System.out.println("    │ [Route] 匹配: " + matched.id
                        + " → 转发到 " + matched.targetUri);
                // 模拟转发
                response = new GatewayResponse(200,
                        "{\"from\":\"" + matched.targetUri + "\",\"result\":\"ok\"}");
            }

            // 3. Post-Filters (reverse order)
            System.out.println("    │ [Post-Filters]");
            for (int i = filters.size() - 1; i >= 0; i--) {
                filters.get(i).postFilter(request, response);
            }

            System.out.println("    │ → 最终响应: " + response.status + " " + response.body);
            System.out.println("    └──────────────────────────────────────────────");
            return response;
        }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  手写 Mini 网关 — Gateway 原理");
        System.out.println("========================================\n");

        showArchitecture();
        demoGateway();
        showPredicateTypes();
        showComparison();
        showProductionConfig();
        showKnowledgeLink();
    }

    private static void showArchitecture() {
        System.out.println("=== 一、网关架构 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    API Gateway 架构                            │");
        System.out.println("  │                                                              │");
        System.out.println("  │  Client ──→ ┌──────────────────────────────┐                 │");
        System.out.println("  │             │         API Gateway          │                 │");
        System.out.println("  │             │  ┌────────────────────────┐  │                 │");
        System.out.println("  │             │  │ Pre-Filters            │  │                 │");
        System.out.println("  │             │  │  ├─ AuthFilter         │  │                 │");
        System.out.println("  │             │  │  ├─ RateLimitFilter    │  │                 │");
        System.out.println("  │             │  │  └─ LogFilter          │  │                 │");
        System.out.println("  │             │  ├────────────────────────┤  │                 │");
        System.out.println("  │             │  │ Route Matching         │  │                 │");
        System.out.println("  │             │  │  /api/order/** → :8081 │  │                 │");
        System.out.println("  │             │  │  /api/stock/** → :8082 │  │                 │");
        System.out.println("  │             │  ├────────────────────────┤  │                 │");
        System.out.println("  │             │  │ Post-Filters           │  │                 │");
        System.out.println("  │             │  │  └─ LogFilter          │  │                 │");
        System.out.println("  │             │  └────────────────────────┘  │                 │");
        System.out.println("  │             └──────────┬───────────────────┘                 │");
        System.out.println("  │           ┌────────────┼────────────┐                        │");
        System.out.println("  │           ▼            ▼            ▼                        │");
        System.out.println("  │    order-service  stock-service  pay-service                 │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");
    }

    private static void demoGateway() {
        System.out.println("=== 二、模拟网关请求处理 ===\n");

        MiniGateway gateway = new MiniGateway();

        // 配置路由
        gateway.addRoute(new Route("order-route", "http://order-service:8081",
                req -> req.path.startsWith("/api/order"), "Path=/api/order/**"));
        gateway.addRoute(new Route("stock-route", "http://stock-service:8082",
                req -> req.path.startsWith("/api/stock"), "Path=/api/stock/**"));
        gateway.addRoute(new Route("auth-route", "http://auth-service:8083",
                req -> req.path.startsWith("/api/auth"), "Path=/api/auth/**"));

        // 配置过滤器链
        gateway.addFilter(new LogFilter());
        gateway.addFilter(new RateLimitFilter(3, 1)); // 桶容量3，每秒补1个
        gateway.addFilter(new AuthFilter());

        // 场景1: 正常请求（带Token）
        System.out.println("  场景1: 正常请求（带Token）");
        GatewayRequest req1 = new GatewayRequest("GET", "/api/order/1001", "10.0.0.1");
        req1.headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9...");
        gateway.handle(req1);
        System.out.println();

        // 场景2: 未认证请求
        System.out.println("  场景2: 未认证请求（无Token）");
        GatewayRequest req2 = new GatewayRequest("GET", "/api/stock/SKU-001", "10.0.0.2");
        gateway.handle(req2);
        System.out.println();

        // 场景3: 白名单路径
        System.out.println("  场景3: 白名单路径（登录接口）");
        GatewayRequest req3 = new GatewayRequest("POST", "/api/auth/login", "10.0.0.3");
        gateway.handle(req3);
        System.out.println();

        // 场景4: 限流
        System.out.println("  场景4: 连续请求触发限流");
        for (int i = 0; i < 3; i++) {
            GatewayRequest req = new GatewayRequest("GET", "/api/auth/register", "10.0.0.4");
            GatewayResponse resp = gateway.handle(req);
            if (resp.status == 429) {
                System.out.println("    → 第 " + (i + 1) + " 次请求被限流！");
            }
        }
        System.out.println();

        // 场景5: 无匹配路由
        System.out.println("  场景5: 无匹配路由");
        GatewayRequest req5 = new GatewayRequest("GET", "/api/unknown/test", "10.0.0.5");
        req5.headers.put("Authorization", "Bearer token...");
        gateway.handle(req5);
        System.out.println();
    }

    private static void showPredicateTypes() {
        System.out.println("=== 三、Gateway 谓词类型 ===\n");

        System.out.println("  ┌──────────────────┬──────────────────────────────────────────┐");
        System.out.println("  │ 谓词类型         │ 示例                                     │");
        System.out.println("  ├──────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ Path             │ Path=/api/order/**                       │");
        System.out.println("  │ Method           │ Method=GET,POST                          │");
        System.out.println("  │ Header           │ Header=X-Request-Id, \\d+                │");
        System.out.println("  │ Query            │ Query=name, zhangsan                     │");
        System.out.println("  │ Host             │ Host=**.example.com                      │");
        System.out.println("  │ RemoteAddr       │ RemoteAddr=192.168.1.0/24                │");
        System.out.println("  │ Between          │ Between=时间1, 时间2                     │");
        System.out.println("  │ Weight           │ Weight=group1, 8 (灰度发布)              │");
        System.out.println("  └──────────────────┴──────────────────────────────────────────┘\n");
    }

    private static void showComparison() {
        System.out.println("=== 四、网关对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │              │ Gateway ★       │ Zuul 1.x         │ Nginx            │");
        System.out.println("  ├──────────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 异步模型     │ Netty + WebFlux  │ Servlet 阻塞     │ 事件驱动         │");
        System.out.println("  │ 性能         │ ★ 高            │ 一般             │ ★★ 极高         │");
        System.out.println("  │ 编程语言     │ Java             │ Java             │ C + Lua          │");
        System.out.println("  │ 动态路由     │ ✓               │ ✓               │ 需 reload        │");
        System.out.println("  │ 限流熔断     │ 集成 Sentinel    │ 集成 Hystrix     │ 需 Lua 扩展      │");
        System.out.println("  │ 服务发现     │ ✓ Nacos/Eureka  │ ✓ Eureka        │ ✗ 需配置         │");
        System.out.println("  │ 适用场景     │ Spring Cloud     │ 老项目           │ 静态资源/反代    │");
        System.out.println("  └──────────────┴──────────────────┴──────────────────┴──────────────────┘\n");
    }

    private static void showProductionConfig() {
        System.out.println("=== 五、生产配置模板 ===\n");

        System.out.println("  # application.yml — Spring Cloud Gateway 配置");
        System.out.println("  spring:");
        System.out.println("    cloud:");
        System.out.println("      gateway:");
        System.out.println("        routes:");
        System.out.println("          - id: order-service");
        System.out.println("            uri: lb://order-service");
        System.out.println("            predicates:");
        System.out.println("              - Path=/api/order/**");
        System.out.println("            filters:");
        System.out.println("              - StripPrefix=1");
        System.out.println("              - name: RequestRateLimiter");
        System.out.println("                args:");
        System.out.println("                  redis-rate-limiter.replenishRate: 10");
        System.out.println("                  redis-rate-limiter.burstCapacity: 20");
        System.out.println("          - id: stock-service");
        System.out.println("            uri: lb://stock-service");
        System.out.println("            predicates:");
        System.out.println("              - Path=/api/stock/**");
        System.out.println("            filters:");
        System.out.println("              - StripPrefix=1");
        System.out.println("        default-filters:");
        System.out.println("          - AddResponseHeader=X-Gateway, KungFu-Gateway");
        System.out.println();
    }

    private static void showKnowledgeLink() {
        System.out.println("【知识串联】");
        System.out.println("  D40 负载均衡 → D41 网关路由 → D42 熔断降级");
        System.out.println("  网关 = 路由 + 过滤器链（类似 Servlet Filter，D16 Spring MVC）");
        System.out.println("  令牌桶限流在 Sentinel 中也有应用（D42）");
    }
}