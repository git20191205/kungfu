/**
 * D34 — 分布式锁进阶（Redisson 看门狗）
 *
 * <h2>知识体系</h2>
 * <pre>
 *   Redisson 分布式锁
 *   └── RedissonWatchDogDemo
 *       ├── Redisson 基本用法: RLock.lock() / unlock()
 *       ├── 看门狗机制: 后台线程每 lockWatchdogTimeout/3 续期
 *       ├── 可重入锁: Hash 结构记录重入次数
 *       ├── 公平锁: 按请求顺序获取
 *       └── RedLock: 多节点加锁（了解即可）
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.distributed.d34_redisson;
