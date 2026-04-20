/**
 * D31 — CAP/BASE 理论
 *
 * <h2>知识体系</h2>
 * <pre>
 *   分布式理论
 *   └── CAPAndBASEDemo
 *       ├── CAP 定理: Consistency + Availability + Partition tolerance（三选二）
 *       │   ├── CP: 保一致性，牺牲可用性（ZooKeeper、Etcd、HBase）
 *       │   ├── AP: 保可用性，牺牲一致性（Eureka、Cassandra、DynamoDB）
 *       │   └── CA: 不存在（网络分区必然发生）
 *       ├── BASE 理论: Basically Available + Soft state + Eventually consistent
 *       │   └── 对 ACID 的妥协，适用于大规模分布式系统
 *       └── 一致性模型: 强一致 → 顺序一致 → 因果一致 → 最终一致
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.distributed.d31_cap;
