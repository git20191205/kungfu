/**
 * D41 — 服务网关
 *
 * <h2>知识体系</h2>
 * <pre>
 *   服务网关
 *   └── GatewaySimulationDemo
 *       ├── 网关架构: Client → Gateway → Services
 *       ├── 路由匹配: 路径路由 + 谓词（Path/Header/Query/Method）
 *       ├── 过滤器链: Pre-Filter → Route → Post-Filter
 *       ├── 限流: 令牌桶算法实现
 *       ├── Gateway vs Zuul vs Nginx 对比
 *       └── 生产配置模板（YAML 格式）
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.microservice.d41_gateway;
