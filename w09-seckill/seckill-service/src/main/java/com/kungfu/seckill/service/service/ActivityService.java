package com.kungfu.seckill.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kungfu.seckill.service.entity.SeckillActivity;
import com.kungfu.seckill.service.entity.SeckillStock;
import com.kungfu.seckill.service.mapper.SeckillActivityMapper;
import com.kungfu.seckill.service.mapper.SeckillStockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class ActivityService {

    @Resource
    private SeckillActivityMapper activityMapper;

    @Resource
    private SeckillStockMapper stockMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    public ActivityService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 获取活动信息，优先从 Redis 缓存读取，缓存未命中则查 DB 并回填
     */
    public SeckillActivity getActivity(Long activityId) {
        String cacheKey = "seckill:activity:" + activityId;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);

        if (json != null) {
            try {
                return objectMapper.readValue(json, SeckillActivity.class);
            } catch (JsonProcessingException e) {
                // 缓存数据异常，走 DB
            }
        }

        // 缓存未命中，查 DB
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity != null) {
            try {
                String value = objectMapper.writeValueAsString(activity);
                stringRedisTemplate.opsForValue().set(cacheKey, value, 30, TimeUnit.MINUTES);
            } catch (JsonProcessingException e) {
                // 序列化失败，不影响主流程
            }
        }
        return activity;
    }

    /**
     * 获取剩余库存，优先从 Redis 读取
     */
    public Integer getRemainStock(Long activityId) {
        String stockKey = "seckill:stock:" + activityId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);

        if (stockStr != null) {
            return Integer.parseInt(stockStr);
        }

        // Redis 中没有，查 DB
        SeckillStock stock = stockMapper.selectOne(
                new LambdaQueryWrapper<SeckillStock>()
                        .eq(SeckillStock::getActivityId, activityId)
        );
        return stock != null ? stock.getAvailableStock() : 0;
    }

    /**
     * 预热：将活动信息和库存加载到 Redis
     */
    public void warmup(Long activityId) {
        // 1. 加载活动信息到 Redis
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity != null) {
            try {
                String json = objectMapper.writeValueAsString(activity);
                stringRedisTemplate.opsForValue().set(
                        "seckill:activity:" + activityId, json, 30, TimeUnit.MINUTES);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("活动信息序列化失败", e);
            }
        }

        // 2. 加载库存到 Redis
        SeckillStock stock = stockMapper.selectOne(
                new LambdaQueryWrapper<SeckillStock>()
                        .eq(SeckillStock::getActivityId, activityId)
        );
        if (stock != null) {
            stringRedisTemplate.opsForValue().set(
                    "seckill:stock:" + activityId, String.valueOf(stock.getAvailableStock()));
        }
    }
}
