package com.kungfu.storage.common;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis 连接工具类
 *
 * <p>统一管理 Redis 连接配置。修改下方常量即可切换到不同的 Redis 实例。</p>
 *
 * @author kungfu
 * @since W04 - MySQL+Redis实战
 */
public class RedisUtil {

    /** Redis 地址 */
    private static final String HOST = "localhost";

    /** Redis 端口 */
    private static final int PORT = 6379;

    /** Redis 密码（无密码设为 null） */
    private static final String PASSWORD = null;

    /** Redis 数据库索引（默认 0） */
    private static final int DATABASE = 0;

    /** 连接池 */
    private static volatile JedisPool pool;

    /** 获取连接池（懒加载 + 双重检查锁） */
    private static JedisPool getPool() {
        if (pool == null) {
            synchronized (RedisUtil.class) {
                if (pool == null) {
                    JedisPoolConfig config = new JedisPoolConfig();
                    config.setMaxTotal(10);
                    config.setMaxIdle(5);
                    config.setMinIdle(1);
                    config.setTestOnBorrow(true);
                    pool = new JedisPool(config, HOST, PORT, 2000, PASSWORD, DATABASE);
                }
            }
        }
        return pool;
    }

    /** 获取 Jedis 实例（用完必须 close 或用 try-with-resources） */
    public static Jedis getJedis() {
        return getPool().getResource();
    }

    /** 关闭连接池（程序退出时调用） */
    public static void shutdown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    /** 快速测试连接 */
    public static boolean testConnection() {
        try (Jedis jedis = getJedis()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
