/**
 * D28 — 缓存问题（穿透/击穿/雪崩 + 一致性）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   缓存问题
 *   ├── 1. CacheProblemDemo — 穿透/击穿/雪崩
 *   │   ├── 穿透: 查不存在的数据 → 布隆过滤器 + 空值缓存
 *   │   ├── 击穿: 热点 key 过期 → 互斥锁（SETNX）重建
 *   │   └── 雪崩: 大批 key 同时过期 → 随机 TTL + 多级缓存
 *   │
 *   └── 2. CacheConsistencyDemo — 缓存一致性
 *       ├── Cache-Aside: 先更新 DB，再删缓存
 *       ├── 延迟双删: 删缓存 → 更新 DB → sleep → 再删缓存
 *       └── 为什么「先删缓存再更新 DB」有问题
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.storage.d28_cache;
