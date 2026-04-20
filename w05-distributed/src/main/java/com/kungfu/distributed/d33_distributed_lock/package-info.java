/**
 * D33 — 分布式锁（Redis 实现）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   分布式锁
 *   └── RedisDistributedLockDemo
 *       ├── 为什么需要分布式锁: 多实例部署 → JVM 锁失效
 *       ├── Redis 锁核心: SET key value NX EX
 *       ├── 解锁安全: Lua 脚本保证原子性（判断+删除）
 *       ├── 问题1: 锁过期但业务未完成 → 看门狗续期
 *       ├── 问题2: 主从切换锁丢失 → RedLock 算法
 *       └── 实战: 模拟库存扣减的并发安全
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.distributed.d33_distributed_lock;
