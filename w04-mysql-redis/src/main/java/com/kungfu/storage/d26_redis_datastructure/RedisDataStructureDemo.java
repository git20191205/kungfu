package com.kungfu.storage.d26_redis_datastructure;

import com.kungfu.storage.common.RedisUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.Tuple;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【Demo】Redis 5 种核心数据结构 — String / List / Hash / Set / ZSet
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>String: 缓存对象、SETNX 分布式锁、INCR 计数器</li>
 *   <li>List: LPUSH+RPOP 消息队列、LRANGE 取最新 N 条</li>
 *   <li>Hash: HSET/HGETALL 存储用户对象</li>
 *   <li>Set: SINTER 共同好友、SRANDMEMBER 随机抽奖</li>
 *   <li>ZSet: ZREVRANGE 排行榜、ZRANGEBYSCORE 延迟队列</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试："Redis 有几种数据结构？各自适合什么场景？"
 * 不熟悉数据结构就写不出高效的缓存代码，更无法设计排行榜、延迟队列等业务
 *
 * <h3>运行方式</h3>
 * <pre>
 * 1. 启动本地 Redis（默认 localhost:6379）
 * 2. 直接运行 main 方法
 * 3. 所有 key 使用 demo:d26: 前缀，Demo 结束自动清理
 * </pre>
 *
 * @author kungfu
 * @since D26 - Redis数据结构
 */
public class RedisDataStructureDemo {

    /** 统一 key 前缀，便于清理 */
    private static final String PREFIX = "demo:d26:";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Redis 5 种核心数据结构演示");
        System.out.println("========================================\n");

        // 连接检测
        if (!RedisUtil.testConnection()) {
            System.out.println("  ✗ Redis 连接失败，请先启动本地 Redis（localhost:6379）");
            return;
        }
        System.out.println("  ✓ Redis 连接成功\n");

        try (Jedis jedis = RedisUtil.getJedis()) {
            demoString(jedis);
            demoList(jedis);
            demoHash(jedis);
            demoSet(jedis);
            demoZSet(jedis);
            cleanup(jedis);
            showInterviewTips();
        } catch (Exception e) {
            System.out.println("  ✗ 执行失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            RedisUtil.shutdown();
        }
    }

    // =============================================================
    // 一、String — 缓存 / 分布式锁 / 计数器
    // =============================================================
    private static void demoString(Jedis jedis) {
        System.out.println("=== 一、String（字符串）===\n");

        // 1. 缓存对象（JSON）
        String userKey = PREFIX + "user:1001";
        String userJson = "{\"id\":1001,\"name\":\"Alice\",\"age\":28}";
        System.out.println("  ★ 场景1: 缓存用户对象");
        System.out.println("    命令: SET " + userKey + " " + userJson);
        jedis.set(userKey, userJson);
        System.out.println("    命令: GET " + userKey);
        System.out.println("    结果: " + jedis.get(userKey));
        System.out.println("    命令: EXPIRE " + userKey + " 3600（设置过期时间 1 小时）");
        jedis.expire(userKey, 3600);
        System.out.println("    TTL: " + jedis.ttl(userKey) + " 秒\n");

        // 2. SETNX 分布式锁
        String lockKey = PREFIX + "lock:order:2001";
        System.out.println("  ★ 场景2: 分布式锁（SETNX）");
        System.out.println("    命令: SETNX " + lockKey + " thread-A");
        long r1 = jedis.setnx(lockKey, "thread-A");
        System.out.println("    结果: " + r1 + "（1=加锁成功）");
        System.out.println("    命令: SETNX " + lockKey + " thread-B（模拟另一线程）");
        long r2 = jedis.setnx(lockKey, "thread-B");
        System.out.println("    结果: " + r2 + "（0=加锁失败，key 已存在）");
        System.out.println("    → 这就是分布式锁的基础：互斥性\n");

        // 3. INCR 计数器
        String counterKey = PREFIX + "counter:page_view";
        System.out.println("  ★ 场景3: 计数器（INCR 原子自增）");
        jedis.set(counterKey, "0");
        for (int i = 0; i < 5; i++) {
            long pv = jedis.incr(counterKey);
            System.out.println("    命令: INCR " + counterKey + " → " + pv);
        }
        System.out.println("    → 原子操作，高并发下不会漏计\n");

        System.out.println("  【面试速记】String 场景：");
        System.out.println("    1. 对象缓存（JSON 序列化）");
        System.out.println("    2. 分布式锁（SETNX/SET NX EX）");
        System.out.println("    3. 计数器（INCR/INCRBY，原子操作）");
        System.out.println("    4. 限流（固定窗口计数）\n");
    }

    // =============================================================
    // 二、List — 消息队列 / 最新列表
    // =============================================================
    private static void demoList(Jedis jedis) {
        System.out.println("=== 二、List（列表）===\n");

        String queueKey = PREFIX + "queue:message";

        // 1. LPUSH + RPOP = 简单消息队列（左进右出，FIFO）
        System.out.println("  ★ 场景1: 简单消息队列（LPUSH 生产 + RPOP 消费）");
        System.out.println("    命令: LPUSH " + queueKey + " msg1 msg2 msg3");
        jedis.lpush(queueKey, "msg1", "msg2", "msg3");
        System.out.println("    命令: LLEN " + queueKey + " → " + jedis.llen(queueKey));
        System.out.println("    消费顺序（RPOP）:");
        String m;
        while ((m = jedis.rpop(queueKey)) != null) {
            System.out.println("      RPOP → " + m);
        }
        System.out.println();

        // 2. LRANGE 取最新 N 条
        String feedKey = PREFIX + "feed:user:1001";
        System.out.println("  ★ 场景2: 最新 N 条动态（朋友圈/评论时间线）");
        jedis.lpush(feedKey, "动态1", "动态2", "动态3", "动态4", "动态5");
        System.out.println("    命令: LRANGE " + feedKey + " 0 2（取最新 3 条）");
        List<String> top3 = jedis.lrange(feedKey, 0, 2);
        System.out.println("    结果: " + top3);
        System.out.println("    命令: LTRIM " + feedKey + " 0 2（只保留最新 3 条，防无限膨胀）");
        jedis.ltrim(feedKey, 0, 2);
        System.out.println("    剩余长度: " + jedis.llen(feedKey) + "\n");

        System.out.println("  【面试速记】List 场景：");
        System.out.println("    1. 消息队列（LPUSH+RPOP，或 BRPOP 阻塞消费）");
        System.out.println("    2. 最新 N 条列表（LPUSH+LTRIM）");
        System.out.println("    3. 栈（LPUSH+LPOP）");
        System.out.println("    注意: 生产级消息队列建议用 Stream 或专业 MQ\n");
    }

    // =============================================================
    // 三、Hash — 对象存储
    // =============================================================
    private static void demoHash(Jedis jedis) {
        System.out.println("=== 三、Hash（哈希）===\n");

        String userKey = PREFIX + "user:hash:1001";

        System.out.println("  ★ 场景: 用户对象存储（字段级读写）");
        System.out.println("    命令: HSET " + userKey + " name Alice age 28 city Beijing");
        jedis.hset(userKey, "name", "Alice");
        jedis.hset(userKey, "age", "28");
        jedis.hset(userKey, "city", "Beijing");

        System.out.println("    命令: HGET " + userKey + " name → " + jedis.hget(userKey, "name"));
        System.out.println("    命令: HGETALL " + userKey);
        Map<String, String> all = jedis.hgetAll(userKey);
        System.out.println("    结果: " + all);

        System.out.println("    命令: HINCRBY " + userKey + " age 1（年龄+1）");
        jedis.hincrBy(userKey, "age", 1);
        System.out.println("    HGET age → " + jedis.hget(userKey, "age"));

        System.out.println("\n  ★ Hash vs String+JSON 对比：");
        System.out.println("  ┌──────────────────┬──────────────────────┬──────────────────────────┐");
        System.out.println("  │ 维度             │ String+JSON          │ Hash                     │");
        System.out.println("  ├──────────────────┼──────────────────────┼──────────────────────────┤");
        System.out.println("  │ 整体读写         │ ✓ 一次搞定           │ 需 HGETALL               │");
        System.out.println("  │ 改单个字段       │ ✗ 读全部+反序列+改+写 │ ✓ HSET 单字段直接改     │");
        System.out.println("  │ 内存占用         │ JSON 有冗余          │ ✓ listpack 压缩更省     │");
        System.out.println("  │ 原子操作         │ 字段级不原子         │ ✓ HINCRBY 原子          │");
        System.out.println("  └──────────────────┴──────────────────────┴──────────────────────────┘\n");

        System.out.println("  【面试速记】Hash 场景：");
        System.out.println("    1. 对象存储（字段多、频繁改单字段）");
        System.out.println("    2. 购物车（userId → {商品id: 数量}）");
        System.out.println("    3. 避免 String+JSON 的反序列化开销\n");
    }

    // =============================================================
    // 四、Set — 去重 / 共同好友 / 随机抽奖
    // =============================================================
    private static void demoSet(Jedis jedis) {
        System.out.println("=== 四、Set（集合）===\n");

        // 1. 共同好友（交集）
        String friendsA = PREFIX + "friends:alice";
        String friendsB = PREFIX + "friends:bob";
        System.out.println("  ★ 场景1: 共同好友（SINTER 交集）");
        jedis.sadd(friendsA, "Tom", "Jerry", "Mike", "Lucy");
        jedis.sadd(friendsB, "Tom", "Lucy", "Jack", "Rose");
        System.out.println("    SMEMBERS friends:alice = " + jedis.smembers(friendsA));
        System.out.println("    SMEMBERS friends:bob   = " + jedis.smembers(friendsB));
        System.out.println("    命令: SINTER friends:alice friends:bob");
        Set<String> common = jedis.sinter(friendsA, friendsB);
        System.out.println("    共同好友: " + common + "\n");

        // 2. 抽奖（随机取 N 个，不重复）
        String lotteryKey = PREFIX + "lottery:users";
        System.out.println("  ★ 场景2: 抽奖（SRANDMEMBER 随机不重复）");
        jedis.sadd(lotteryKey, "user1", "user2", "user3", "user4", "user5",
                "user6", "user7", "user8", "user9", "user10");
        System.out.println("    抽奖池 SCARD = " + jedis.scard(lotteryKey) + " 人");
        System.out.println("    命令: SRANDMEMBER " + lotteryKey + " 3（抽 3 个中奖者）");
        List<String> winners = jedis.srandmember(lotteryKey, 3);
        System.out.println("    中奖者: " + winners);
        System.out.println("    注: SRANDMEMBER 不移除元素；SPOP 抽完即删");
        System.out.println();

        // 3. 标签系统
        String tagKey = PREFIX + "tags:article:1";
        System.out.println("  ★ 场景3: 文章标签（天然去重）");
        jedis.sadd(tagKey, "Java", "Redis", "MySQL", "Java"); // 重复的 Java 会被去重
        System.out.println("    SADD 插入 4 个（含重复 Java），SCARD = " + jedis.scard(tagKey));
        System.out.println("    SMEMBERS: " + jedis.smembers(tagKey));
        System.out.println("    SISMEMBER Redis → " + jedis.sismember(tagKey, "Redis"));
        System.out.println();

        System.out.println("  【面试速记】Set 场景：");
        System.out.println("    1. 标签系统（天然去重）");
        System.out.println("    2. 共同好友/兴趣（SINTER 交集）");
        System.out.println("    3. 随机抽奖（SRANDMEMBER/SPOP）");
        System.out.println("    4. UV 统计（大数据量用 HyperLogLog 更省内存）\n");
    }

    // =============================================================
    // 五、ZSet — 排行榜 / 延迟队列
    // =============================================================
    private static void demoZSet(Jedis jedis) {
        System.out.println("=== 五、ZSet（有序集合）===\n");

        // 1. 排行榜
        String rankKey = PREFIX + "rank:score";
        System.out.println("  ★ 场景1: 游戏排行榜（score=分数）");
        jedis.zadd(rankKey, 1500, "player:Alice");
        jedis.zadd(rankKey, 3200, "player:Bob");
        jedis.zadd(rankKey, 2100, "player:Carol");
        jedis.zadd(rankKey, 4500, "player:David");
        jedis.zadd(rankKey, 1800, "player:Eve");

        System.out.println("    命令: ZREVRANGE " + rankKey + " 0 2 WITHSCORES（Top 3）");
        List<Tuple> top3 = jedis.zrevrangeWithScores(rankKey, 0, 2);
        int rank = 1;
        for (Tuple t : top3) {
            System.out.println("      第 " + rank++ + " 名: " + t.getElement() + " (score=" + (long) t.getScore() + ")");
        }
        System.out.println("    命令: ZREVRANK " + rankKey + " player:Carol → 查 Carol 的排名");
        System.out.println("    Carol 排名: 第 " + (jedis.zrevrank(rankKey, "player:Carol") + 1) + " 名");
        System.out.println("    命令: ZINCRBY " + rankKey + " 1000 player:Alice（Alice 加 1000 分）");
        jedis.zincrby(rankKey, 1000, "player:Alice");
        System.out.println("    Alice 新分数: " + jedis.zscore(rankKey, "player:Alice"));
        System.out.println();

        // 2. 延迟队列
        String delayKey = PREFIX + "delay:queue";
        System.out.println("  ★ 场景2: 延迟队列（score=执行时间戳）");
        long now = System.currentTimeMillis();
        jedis.zadd(delayKey, now + 1000, "task:sendEmail");   // 1 秒后执行
        jedis.zadd(delayKey, now + 2000, "task:pushNotify");  // 2 秒后执行
        jedis.zadd(delayKey, now + 500, "task:refreshCache"); // 0.5 秒后执行
        System.out.println("    添加 3 个延迟任务（时间戳作为 score）");

        System.out.println("    命令: ZRANGEBYSCORE " + delayKey + " 0 " + (now + 1000) + "（取 1 秒内到期的）");
        List<String> due = jedis.zrangeByScore(delayKey, 0, now + 1000);
        System.out.println("    到期任务: " + due);
        System.out.println("    → 生产用法: 后台线程定时 ZRANGEBYSCORE + ZREM 消费到期任务\n");

        System.out.println("  【面试速记】ZSet 场景：");
        System.out.println("    1. 排行榜 TopN（ZREVRANGE）");
        System.out.println("    2. 延迟队列（score=时间戳）");
        System.out.println("    3. 带权重去重（比 Set 多一个排序维度）");
        System.out.println("    底层: listpack（<128 元素）/ skiplist+hashtable\n");
    }

    // =============================================================
    // 清理
    // =============================================================
    private static void cleanup(Jedis jedis) {
        System.out.println("=== 清理 Demo 数据 ===\n");
        Set<String> keys = jedis.keys(PREFIX + "*");
        if (!keys.isEmpty()) {
            long deleted = jedis.del(keys.toArray(new String[0]));
            System.out.println("  ✓ 已删除 " + deleted + " 个 demo:d26: 开头的 key\n");
        } else {
            System.out.println("  无残留 key\n");
        }
    }

    // =============================================================
    // 面试速记
    // =============================================================
    private static void showInterviewTips() {
        System.out.println("========================================");
        System.out.println("   面试速记（D26）");
        System.out.println("========================================\n");

        System.out.println("  ┌────────┬──────────────────────┬──────────────────────────────┐");
        System.out.println("  │ 结构   │ 典型场景             │ 核心命令                      │");
        System.out.println("  ├────────┼──────────────────────┼──────────────────────────────┤");
        System.out.println("  │ String │ 缓存/锁/计数         │ SET/GET/SETNX/INCR           │");
        System.out.println("  │ List   │ 消息队列/最新N条     │ LPUSH/RPOP/LRANGE/LTRIM      │");
        System.out.println("  │ Hash   │ 对象存储             │ HSET/HGET/HGETALL/HINCRBY    │");
        System.out.println("  │ Set    │ 去重/交并集/抽奖     │ SADD/SINTER/SRANDMEMBER      │");
        System.out.println("  │ ZSet   │ 排行榜/延迟队列      │ ZADD/ZREVRANGE/ZRANGEBYSCORE │");
        System.out.println("  └────────┴──────────────────────┴──────────────────────────────┘\n");

        System.out.println("  一句话秒答：");
        System.out.println("    String=万金油，List=顺序列表，Hash=对象，Set=集合运算，ZSet=排序");
        System.out.println("    相关结构：Bitmap（签到）、HyperLogLog（UV）、GEO（地理位置）、Stream（消息流）\n");

        System.out.println("  选型决策：");
        System.out.println("    需要排序？       → ZSet");
        System.out.println("    需要交并差？     → Set");
        System.out.println("    按字段存对象？   → Hash");
        System.out.println("    需要顺序/队列？  → List");
        System.out.println("    其他？           → String\n");
    }
}

/*
 * 【知识串联】
 * D26 全部知识点：
 *   1. RedisDataStructureDemo — 5 种核心数据结构 + 业务场景（本类）
 *
 * W04 完整路线：
 *   D22 → MySQL 索引（B+Tree、回表、覆盖索引）
 *   D23 → EXPLAIN 执行计划
 *   D24 → 事务隔离（MVCC）
 *   D25 → 锁机制（行锁、间隙锁）
 *   D26 → Redis 数据结构（本课）
 *   D27 → Redis 持久化（RDB vs AOF）
 *   D28 → 缓存问题（穿透/击穿/雪崩 + 一致性）
 *   D29 → 综合实战（缓存架构设计）
 */
