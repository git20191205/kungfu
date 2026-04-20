/**
 * D39 — 服务调用（Feign 模拟）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   服务调用
 *   └── FeignSimulationDemo
 *       ├── Feign 核心原理: 接口 + 注解 → 动态代理 → HTTP 调用
 *       ├── 手写 Mini Feign（JDK Proxy 实现）
 *       ├── 请求模板机制: path / method / params
 *       ├── Feign 拦截器: 添加认证头等
 *       ├── RestTemplate vs Feign vs WebClient 对比
 *       └── Feign + Ribbon 集成示意
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.microservice.d39_feign;
