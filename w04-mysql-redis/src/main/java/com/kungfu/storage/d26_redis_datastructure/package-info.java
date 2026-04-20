/**
 * D26 — Redis 5 种核心数据结构
 *
 * <h2>知识体系</h2>
 * <pre>
 *   Redis 数据结构
 *   ├── 1. String（字符串）
 *   │   ├── SET/GET         — 缓存对象（最常用）
 *   │   ├── SETNX           — 分布式锁基础
 *   │   ├── INCR/DECR       — 计数器（页面 PV、库存扣减）
 *   │   └── 底层: SDS（简单动态字符串，二进制安全）
 *   │
 *   ├── 2. List（列表）
 *   │   ├── LPUSH+RPOP      — 消息队列
 *   │   ├── LRANGE          — 最新 N 条（朋友圈、评论）
 *   │   └── 底层: quicklist（Redis 3.2+）
 *   │
 *   ├── 3. Hash（哈希）
 *   │   ├── HSET/HGETALL    — 对象存储（比 String+JSON 更省内存）
 *   │   └── 底层: listpack（小）/ hashtable（大）
 *   │
 *   ├── 4. Set（集合）
 *   │   ├── SADD/SISMEMBER  — 去重、标签
 *   │   ├── SINTER/SUNION   — 共同好友、共同兴趣
 *   │   ├── SRANDMEMBER     — 随机抽奖
 *   │   └── 底层: intset / hashtable
 *   │
 *   └── 5. ZSet（有序集合）
 *       ├── ZADD/ZREVRANGE  — 排行榜 TopN
 *       ├── ZRANGEBYSCORE   — 延迟队列（score=执行时间戳）
 *       └── 底层: listpack / skiplist + hashtable
 * </pre>
 *
 * <h2>面试高频考点</h2>
 * <pre>
 *   Q: Redis 有哪几种数据结构？各自场景？
 *   A: String=缓存/锁/计数；List=队列/最新N条；Hash=对象；
 *      Set=去重/交并集；ZSet=排行榜/延迟队列
 *
 *   Q: 为什么 ZSet 用 skiplist 而不是红黑树？
 *   A: 1) skiplist 范围查询更自然（层级链表直接顺序遍历）
 *      2) 实现更简单，易维护
 *      3) 内存占用可调（每层概率 p=1/4）
 *
 *   Q: 存一个用户对象，用 String+JSON 还是 Hash？
 *   A: 整体读写多 → String+JSON；只改部分字段 → Hash（避免反序列化）
 * </pre>
 *
 * @author kungfu
 */
package com.kungfu.storage.d26_redis_datastructure;

/*
 *  D26 一个 Demo：
 *
 *  RedisDataStructureDemo — 5 种数据结构完整演示（需要 Redis 服务）
 *  运行前：启动本地 Redis（默认 localhost:6379）
 *  然后运行 main，Demo 会自动使用 demo:d26: 前缀并清理所有 key
 */
