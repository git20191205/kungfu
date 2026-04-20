# W06: 微服务架构 (D38-D43)

> 对应学习路线 D38-D43（D44 为休息日）

## 学习目标

- 服务注册发现 — 手写 Mini 注册中心（注册/发现/心跳/剔除）
- 服务调用 — Feign 动态代理原理模拟
- 负载均衡 — 5 种算法实现（轮询/加权/随机/一致性哈希）
- 服务网关 — 路由匹配 + 过滤器链 + 令牌桶限流
- 熔断降级 — 状态机（CLOSED/OPEN/HALF_OPEN）+ 滑动窗口
- 链路追踪 — Trace/Span 模型 + TraceId 传播

## 模块结构

| Day | 目录 | 主题 | Demo | 需要外部服务 |
|-----|------|------|------|-------------|
| D38 | d38_registry | 服务注册发现 | ServiceRegistryDemo | 无 |
| D39 | d39_feign | 服务调用 | FeignSimulationDemo | 无 |
| D40 | d40_loadbalance | 负载均衡 | LoadBalanceDemo | 无 |
| D41 | d41_gateway | 服务网关 | GatewaySimulationDemo | 无 |
| D42 | d42_circuit_breaker | 熔断降级 | CircuitBreakerDemo | 无 |
| D43 | d43_tracing | 链路追踪 | DistributedTracingDemo | 无 |

## 运行前提

全部纯 Java，直接运行 main 方法，不需要任何外部服务。

## 设计理念

W06 采用「手写模拟」方式而非真实 Spring Cloud 组件：
- 理解原理比会配置更重要
- 面试问的是「Ribbon 怎么做负载均衡」而不是「怎么配置 Ribbon」
- 每个 Demo 都是对应组件的核心算法实现

## 面试核心问题

- Q: Nacos 和 Eureka 的区别？
- Q: Feign 底层原理？（JDK 动态代理 + HTTP 客户端）
- Q: 负载均衡有哪些算法？各自适用场景？
- Q: 网关的作用？过滤器链怎么工作？
- Q: 熔断器三种状态？什么时候触发熔断？
- Q: 分布式链路追踪原理？TraceId 怎么传播？
- Q: 一致性哈希解决什么问题？虚拟节点的作用？

## 技术栈

- 纯 Java（JDK 1.8+）
- JDK 动态代理（Feign 模拟）
- ConcurrentHashMap + ScheduledExecutor（注册中心）
- 无外部依赖
