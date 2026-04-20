package com.kungfu.storage.d27_redis_persistence;

import com.kungfu.storage.common.RedisUtil;
import redis.clients.jedis.Jedis;

/**
 * 【Demo】Redis 持久化 — RDB vs AOF 对比
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>RDB 与 AOF 原理对比</li>
 *   <li>查看当前 Redis 持久化配置（INFO persistence）</li>
 *   <li>触发 BGSAVE 并观察</li>
 *   <li>生产环境配置推荐</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 需要 Redis 运行中，直接运行 main
 *
 * @author kungfu
 * @since D27 - Redis持久化
 */
public class RedisPersistenceDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Redis 持久化 — RDB vs AOF");
        System.out.println("========================================\n");

        // 一、RDB vs AOF 对比
        showComparison();

        // 二、查看当前配置
        showCurrentConfig();

        // 三、触发 BGSAVE
        triggerBgsave();

        // 四、生产配置推荐
        showProductionConfig();
    }

    private static void showComparison() {
        System.out.println("=== 一、RDB vs AOF vs 混合持久化 ===\n");

        System.out.println("  ┌──────────────┬──────────────────────────┬──────────────────────────┬──────────────────────┐");
        System.out.println("  │              │ RDB (快照)               │ AOF (日志)               │ 混合 (4.0+)          │");
        System.out.println("  ├──────────────┼──────────────────────────┼──────────────────────────┼──────────────────────┤");
        System.out.println("  │ 原理         │ fork 子进程写全量快照    │ 追加每条写命令到日志     │ RDB 头 + AOF 尾      │");
        System.out.println("  │ 触发方式     │ BGSAVE / save 配置       │ 实时追加                 │ BGREWRITEAOF 时生成  │");
        System.out.println("  │ 数据安全     │ 可能丢最后快照后的数据   │ 最多丢 1 秒（everysec） │ 兼顾两者             │");
        System.out.println("  │ 恢复速度     │ ★ 快（二进制加载）      │ 慢（重放命令）           │ 快                   │");
        System.out.println("  │ 文件大小     │ ★ 小（压缩二进制）      │ 大（文本命令）           │ 中等                 │");
        System.out.println("  │ 适用场景     │ 备份、灾备               │ 数据安全要求高           │ ★ 生产推荐          │");
        System.out.println("  └──────────────┴──────────────────────────┴──────────────────────────┴──────────────────────┘\n");

        System.out.println("  AOF 的 3 种 fsync 策略：");
        System.out.println("    always    → 每条命令 fsync → 最安全，性能最差");
        System.out.println("    everysec  → 每秒 fsync → ★ 推荐，最多丢 1 秒");
        System.out.println("    no        → 由 OS 决定 → 性能最好，可能丢几十秒\n");
    }

    private static void showCurrentConfig() {
        System.out.println("=== 二、查看当前 Redis 持久化配置 ===\n");

        if (!RedisUtil.testConnection()) {
            System.out.println("  ✗ Redis 连接失败，跳过此步骤\n");
            return;
        }

        try (Jedis jedis = RedisUtil.getJedis()) {
            String info = jedis.info("persistence");
            System.out.println("  INFO persistence 关键字段：\n");

            String[] lines = info.split("\r?\n");
            String[] interestingKeys = {
                "rdb_last_save_time", "rdb_last_bgsave_status", "rdb_last_bgsave_time_sec",
                "aof_enabled", "aof_last_rewrite_time_sec", "aof_last_bgrewrite_status",
                "aof_current_size", "aof_base_size"
            };
            for (String line : lines) {
                for (String key : interestingKeys) {
                    if (line.startsWith(key + ":")) {
                        System.out.println("    " + line.trim());
                    }
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("  ✗ 查询失败: " + e.getMessage() + "\n");
        }
    }

    private static void triggerBgsave() {
        System.out.println("=== 三、触发 BGSAVE ===\n");

        if (!RedisUtil.testConnection()) {
            System.out.println("  ✗ Redis 连接失败，跳过此步骤\n");
            return;
        }

        try (Jedis jedis = RedisUtil.getJedis()) {
            String result = jedis.bgsave();
            System.out.println("  BGSAVE 响应: " + result);
            System.out.println("  → Redis 启动后台子进程生成 RDB 快照");
            System.out.println("  → 子进程使用 COW (Copy-On-Write) 技术，不阻塞主线程\n");
        } catch (Exception e) {
            System.out.println("  ✗ BGSAVE 失败: " + e.getMessage() + "\n");
        }

        RedisUtil.shutdown();
    }

    private static void showProductionConfig() {
        System.out.println("=== 四、生产配置推荐 ===\n");

        System.out.println("  ★ 推荐：混合持久化（Redis 4.0+）\n");
        System.out.println("  ┌────────────────────────────────────────────┐");
        System.out.println("  │ # redis.conf                              │");
        System.out.println("  │                                            │");
        System.out.println("  │ # 开启 AOF                                │");
        System.out.println("  │ appendonly yes                             │");
        System.out.println("  │ appendfsync everysec                      │");
        System.out.println("  │                                            │");
        System.out.println("  │ # 开启混合持久化                           │");
        System.out.println("  │ aof-use-rdb-preamble yes                  │");
        System.out.println("  │                                            │");
        System.out.println("  │ # RDB 自动快照（备份用）                   │");
        System.out.println("  │ save 900 1                                │");
        System.out.println("  │ save 300 10                               │");
        System.out.println("  │ save 60 10000                             │");
        System.out.println("  └────────────────────────────────────────────┘\n");

        System.out.println("  面试速记：");
        System.out.println("  1. RDB 适合备份和灾备，AOF 适合数据安全");
        System.out.println("  2. 生产推荐混合持久化 = RDB快照 + AOF增量");
        System.out.println("  3. AOF 用 everysec 策略，最多丢 1 秒数据");
        System.out.println("  4. BGSAVE 用 fork+COW，不阻塞主线程");
        System.out.println();
    }
}
