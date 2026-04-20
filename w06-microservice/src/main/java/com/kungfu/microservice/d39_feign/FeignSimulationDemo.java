package com.kungfu.microservice.d39_feign;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * 【Demo】手写 Mini Feign — 理解声明式服务调用原理
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Feign 核心原理: 接口 + 注解 → JDK 动态代理 → HTTP 调用</li>
 *   <li>手写 Mini Feign: 定义接口，用 Proxy 生成实现</li>
 *   <li>请求模板机制: path / method / params</li>
 *   <li>Feign 拦截器: 添加认证头</li>
 *   <li>RestTemplate vs Feign vs WebClient 对比</li>
 *   <li>Feign + Ribbon 集成示意</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * <p>Feign 让微服务间调用像本地方法一样简单，理解其动态代理原理是面试高频考点。</p>
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D39 - 服务调用
 */
public class FeignSimulationDemo {

    // =============================================================
    // 自定义注解（模拟 Feign 注解）
    // =============================================================

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface FeignClient {
        String name();
        String url() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface GetMapping {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PostMapping {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface PathVariable {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface RequestParam {
        String value();
    }

    // =============================================================
    // Feign 拦截器接口
    // =============================================================

    interface RequestInterceptor {
        void apply(RequestTemplate template);
    }

    // =============================================================
    // 请求模板
    // =============================================================

    static class RequestTemplate {
        String method;
        String url;
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> queryParams = new LinkedHashMap<>();
        String body;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(method).append(" ").append(url);
            if (!queryParams.isEmpty()) {
                sb.append("?");
                List<String> params = new ArrayList<>();
                for (Map.Entry<String, String> e : queryParams.entrySet()) {
                    params.add(e.getKey() + "=" + e.getValue());
                }
                sb.append(String.join("&", params));
            }
            sb.append(" HTTP/1.1\n");
            for (Map.Entry<String, String> h : headers.entrySet()) {
                sb.append("      ").append(h.getKey()).append(": ").append(h.getValue()).append("\n");
            }
            if (body != null) {
                sb.append("      Body: ").append(body).append("\n");
            }
            return sb.toString();
        }
    }

    // =============================================================
    // 模拟响应
    // =============================================================

    static class HttpResponse {
        int status;
        String body;

        HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    // =============================================================
    // Mini Feign 核心：动态代理工厂
    // =============================================================

    static class MiniFeignBuilder {
        private String baseUrl;
        private final List<RequestInterceptor> interceptors = new ArrayList<>();

        MiniFeignBuilder url(String url) {
            this.baseUrl = url;
            return this;
        }

        MiniFeignBuilder addInterceptor(RequestInterceptor interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        @SuppressWarnings("unchecked")
        <T> T target(Class<T> apiType) {
            // 读取 @FeignClient 注解
            FeignClient fc = apiType.getAnnotation(FeignClient.class);
            String targetUrl = (baseUrl != null) ? baseUrl :
                    (fc != null && !fc.url().isEmpty()) ? fc.url() : "http://localhost:8080";
            String serviceName = (fc != null) ? fc.name() : apiType.getSimpleName();

            return (T) Proxy.newProxyInstance(
                apiType.getClassLoader(),
                new Class[]{apiType},
                (proxy, method, args) -> {
                    // 构建请求模板
                    RequestTemplate template = buildTemplate(method, args, targetUrl);

                    // 应用拦截器
                    for (RequestInterceptor interceptor : interceptors) {
                        interceptor.apply(template);
                    }

                    // 模拟发送 HTTP 请求（打印而非真正发送）
                    System.out.println("    ┌─ Feign 代理拦截 ─────────────────────────────────");
                    System.out.println("    │ 服务: " + serviceName);
                    System.out.println("    │ 方法: " + method.getName() + "()");
                    System.out.println("    │ 请求: " + template);
                    System.out.println("    │ → 模拟 HTTP 调用...");

                    // 模拟响应
                    HttpResponse resp = simulateHttp(template);
                    System.out.println("    │ ← 响应: " + resp.status + " " + resp.body);
                    System.out.println("    └──────────────────────────────────────────────────");

                    // 返回模拟结果
                    if (method.getReturnType() == String.class) {
                        return resp.body;
                    }
                    return null;
                }
            );
        }

        private RequestTemplate buildTemplate(Method method, Object[] args, String baseUrl) {
            RequestTemplate template = new RequestTemplate();

            // 解析方法注解
            GetMapping get = method.getAnnotation(GetMapping.class);
            PostMapping post = method.getAnnotation(PostMapping.class);

            if (get != null) {
                template.method = "GET";
                template.url = baseUrl + get.value();
            } else if (post != null) {
                template.method = "POST";
                template.url = baseUrl + post.value();
            }

            // 解析参数注解
            Parameter[] params = method.getParameters();
            if (args != null) {
                for (int i = 0; i < params.length; i++) {
                    PathVariable pv = params[i].getAnnotation(PathVariable.class);
                    RequestParam rp = params[i].getAnnotation(RequestParam.class);
                    if (pv != null) {
                        template.url = template.url.replace("{" + pv.value() + "}", String.valueOf(args[i]));
                    } else if (rp != null) {
                        template.queryParams.put(rp.value(), String.valueOf(args[i]));
                    }
                }
            }

            template.headers.put("Content-Type", "application/json");
            return template;
        }

        private HttpResponse simulateHttp(RequestTemplate template) {
            // 模拟不同接口的响应
            if (template.url.contains("/orders/")) {
                return new HttpResponse(200, "{\"orderId\":\"ORD-1001\",\"status\":\"PAID\"}");
            } else if (template.url.contains("/orders")) {
                return new HttpResponse(201, "{\"orderId\":\"ORD-1002\",\"status\":\"CREATED\"}");
            } else if (template.url.contains("/stock/")) {
                return new HttpResponse(200, "{\"sku\":\"SKU-001\",\"quantity\":100}");
            }
            return new HttpResponse(200, "{\"result\":\"ok\"}");
        }
    }

    // =============================================================
    // 模拟的 Feign 客户端接口
    // =============================================================

    @FeignClient(name = "order-service", url = "http://order-service:8080")
    interface OrderServiceClient {
        @GetMapping("/api/orders/{orderId}")
        String getOrder(@PathVariable("orderId") String orderId);

        @PostMapping("/api/orders")
        String createOrder(@RequestParam("userId") String userId, @RequestParam("amount") double amount);
    }

    @FeignClient(name = "stock-service", url = "http://stock-service:8081")
    interface StockServiceClient {
        @GetMapping("/api/stock/{sku}")
        String getStock(@PathVariable("sku") String sku);
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  手写 Mini Feign — 声明式服务调用");
        System.out.println("========================================\n");

        showPrinciple();
        demoMiniFeignProxy();
        demoInterceptor();
        showComparison();
        showRibbonIntegration();
        showKnowledgeLink();
    }

    private static void showPrinciple() {
        System.out.println("=== 一、Feign 核心原理 ===\n");

        System.out.println("  Feign 的本质: 接口 + 注解 → 动态代理 → HTTP 调用\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    Feign 工作流程                              │");
        System.out.println("  │                                                              │");
        System.out.println("  │  @FeignClient(\"order-service\")                               │");
        System.out.println("  │  interface OrderClient {                                     │");
        System.out.println("  │      @GetMapping(\"/orders/{id}\")                             │");
        System.out.println("  │      Order getOrder(@PathVariable Long id);                  │");
        System.out.println("  │  }                                                           │");
        System.out.println("  │         │                                                    │");
        System.out.println("  │         ▼                                                    │");
        System.out.println("  │  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐    │");
        System.out.println("  │  │ JDK Proxy   │ →  │ RequestTemplate│ →  │ HTTP Client  │    │");
        System.out.println("  │  │ (动态代理)   │    │ (请求模板)    │    │ (发送请求)   │    │");
        System.out.println("  │  └─────────────┘    └──────────────┘    └──────────────┘    │");
        System.out.println("  │         ↑                   ↑                   ↑           │");
        System.out.println("  │    解析接口+注解       填充参数+URL        拦截器增强         │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  核心步骤：");
        System.out.println("    1. @EnableFeignClients 扫描所有 @FeignClient 接口");
        System.out.println("    2. 为每个接口创建 JDK 动态代理");
        System.out.println("    3. 调用方法时 → 解析注解 → 构建 RequestTemplate");
        System.out.println("    4. 经过拦截器链 → 发送 HTTP 请求 → 解码响应\n");
    }

    private static void demoMiniFeignProxy() {
        System.out.println("=== 二、手写 Mini Feign 动态代理 ===\n");

        // 创建 Mini Feign 客户端
        MiniFeignBuilder builder = new MiniFeignBuilder();

        System.out.println("  ★ 创建 OrderServiceClient 代理...\n");
        OrderServiceClient orderClient = builder
                .url("http://order-service:8080")
                .target(OrderServiceClient.class);

        // 调用方法 → 代理拦截 → 构建 HTTP 请求
        System.out.println("  调用 orderClient.getOrder(\"ORD-1001\"):\n");
        String result1 = orderClient.getOrder("ORD-1001");
        System.out.println("    返回值: " + result1 + "\n");

        System.out.println("  调用 orderClient.createOrder(\"U001\", 99.9):\n");
        String result2 = orderClient.createOrder("U001", 99.9);
        System.out.println("    返回值: " + result2 + "\n");

        System.out.println("  ★ 创建 StockServiceClient 代理...\n");
        StockServiceClient stockClient = builder
                .url("http://stock-service:8081")
                .target(StockServiceClient.class);

        System.out.println("  调用 stockClient.getStock(\"SKU-001\"):\n");
        String result3 = stockClient.getStock("SKU-001");
        System.out.println("    返回值: " + result3 + "\n");

        System.out.println("  ✓ 关键点: 我们只定义了接口，没有写任何实现类！");
        System.out.println("    Feign 通过 JDK Proxy 自动生成了实现。\n");
    }

    private static void demoInterceptor() {
        System.out.println("=== 三、Feign 拦截器 ===\n");

        System.out.println("  拦截器可以在请求发送前修改请求（添加 Header、签名等）\n");

        // 认证拦截器
        RequestInterceptor authInterceptor = template -> {
            template.headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9...");
            template.headers.put("X-Request-Id", UUID.randomUUID().toString().substring(0, 8));
        };

        // 日志拦截器
        RequestInterceptor logInterceptor = template ->
            template.headers.put("X-Log-Timestamp", String.valueOf(System.currentTimeMillis()));

        OrderServiceClient client = new MiniFeignBuilder()
                .url("http://order-service:8080")
                .addInterceptor(authInterceptor)
                .addInterceptor(logInterceptor)
                .target(OrderServiceClient.class);

        System.out.println("  带拦截器调用 getOrder(\"ORD-2001\"):\n");
        client.getOrder("ORD-2001");
        System.out.println();
        System.out.println("  ✓ 注意 Header 中自动添加了 Authorization 和 X-Request-Id\n");
    }

    private static void showComparison() {
        System.out.println("=== 四、服务调用方式对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────────┬──────────────────┬──────────────────┐");
        System.out.println("  │              │ RestTemplate     │ Feign ★         │ WebClient        │");
        System.out.println("  ├──────────────┼──────────────────┼──────────────────┼──────────────────┤");
        System.out.println("  │ 编程风格     │ 命令式           │ 声明式           │ 响应式           │");
        System.out.println("  │ 使用方式     │ 手动拼 URL       │ 接口 + 注解      │ 链式 API         │");
        System.out.println("  │ 负载均衡     │ @LoadBalanced    │ 内置 Ribbon      │ 需手动集成       │");
        System.out.println("  │ 熔断集成     │ 需手动           │ fallback 属性    │ 需手动           │");
        System.out.println("  │ 代码量       │ 多               │ 少 ★            │ 中               │");
        System.out.println("  │ 可读性       │ 一般             │ 好 ★            │ 一般             │");
        System.out.println("  │ 异步支持     │ ✗               │ ✗               │ ✓ 原生           │");
        System.out.println("  │ Spring 版本  │ 5.x 已废弃      │ 推荐             │ WebFlux 推荐     │");
        System.out.println("  └──────────────┴──────────────────┴──────────────────┴──────────────────┘\n");

        System.out.println("  代码对比：");
        System.out.println("    // RestTemplate（命令式）");
        System.out.println("    Order order = restTemplate.getForObject(");
        System.out.println("        \"http://order-service/api/orders/\" + orderId, Order.class);\n");
        System.out.println("    // Feign（声明式）★ 推荐");
        System.out.println("    Order order = orderClient.getOrder(orderId);  // 像调本地方法\n");
        System.out.println("    // WebClient（响应式）");
        System.out.println("    Mono<Order> order = webClient.get()");
        System.out.println("        .uri(\"/api/orders/{id}\", orderId).retrieve().bodyToMono(Order.class);\n");
    }

    private static void showRibbonIntegration() {
        System.out.println("=== 五、Feign + Ribbon 集成 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │              Feign + Ribbon + Nacos 调用链路                   │");
        System.out.println("  │                                                              │");
        System.out.println("  │  orderClient.getOrder(id)                                    │");
        System.out.println("  │         │                                                    │");
        System.out.println("  │         ▼                                                    │");
        System.out.println("  │  ┌─────────────┐                                            │");
        System.out.println("  │  │ Feign Proxy  │  解析 @FeignClient(\"order-service\")        │");
        System.out.println("  │  └──────┬──────┘                                            │");
        System.out.println("  │         ▼                                                    │");
        System.out.println("  │  ┌─────────────┐  从 Nacos 获取 order-service 实例列表       │");
        System.out.println("  │  │   Ribbon     │  [192.168.1.1:8080, 192.168.1.2:8080]      │");
        System.out.println("  │  │  负载均衡    │  选择一个实例（轮询/随机/权重）              │");
        System.out.println("  │  └──────┬──────┘                                            │");
        System.out.println("  │         ▼                                                    │");
        System.out.println("  │  ┌─────────────┐                                            │");
        System.out.println("  │  │ HTTP Client  │  GET http://192.168.1.1:8080/orders/1001   │");
        System.out.println("  │  └──────┬──────┘                                            │");
        System.out.println("  │         ▼                                                    │");
        System.out.println("  │  ┌─────────────┐                                            │");
        System.out.println("  │  │ order-service│  处理请求，返回结果                         │");
        System.out.println("  │  └─────────────┘                                            │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Feign 的工作原理？");
        System.out.println("    A: 1) @EnableFeignClients 扫描 @FeignClient 接口");
        System.out.println("       2) 为每个接口创建 JDK 动态代理（InvocationHandler）");
        System.out.println("       3) 调用方法时解析注解，构建 RequestTemplate");
        System.out.println("       4) 经过拦截器链（添加 Header、签名等）");
        System.out.println("       5) 通过 Ribbon 负载均衡选择实例");
        System.out.println("       6) 用 HTTP Client 发送请求，解码响应\n");
    }

    private static void showKnowledgeLink() {
        System.out.println("【知识串联】");
        System.out.println("  D38 注册发现 → D39 Feign 服务调用 → D40 负载均衡");
        System.out.println("  Feign 底层依赖 Ribbon 做负载均衡，依赖 Nacos 做服务发现");
        System.out.println("  Feign 的核心是 JDK 动态代理（D15 Spring AOP 同源技术）");
    }
}