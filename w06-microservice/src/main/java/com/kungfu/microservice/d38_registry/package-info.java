/**
 * D38 — 服务注册与发现
 *
 * <h2>知识体系</h2>
 * <pre>
 *   服务注册发现
 *   └── ServiceRegistryDemo
 *       ├── 手写 Mini 注册中心（内存 Map 实现）
 *       ├── 服务注册: 实例启动 → 注册到注册中心
 *       ├── 服务发现: 消费者从注册中心获取实例列表
 *       ├── 心跳检测: 定时续约，超时剔除
 *       ├── Nacos vs Eureka vs ZooKeeper 对比
 *       └── AP(Eureka) vs CP(ZooKeeper) vs AP+CP(Nacos)
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.microservice.d38_registry;
