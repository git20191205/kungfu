/**
 * D40 — 负载均衡
 *
 * <h2>知识体系</h2>
 * <pre>
 *   负载均衡
 *   └── LoadBalanceDemo
 *       ├── Round Robin: 简单轮询
 *       ├── Weighted Round Robin: 平滑加权轮询（Nginx 算法）
 *       ├── Random: 随机选择
 *       ├── Weighted Random: 加权随机
 *       ├── Consistent Hash: 一致性哈希（虚拟节点）
 *       ├── 10000 次请求分布统计
 *       └── 客户端 LB(Ribbon) vs 服务端 LB(Nginx) 对比
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.microservice.d40_loadbalance;
