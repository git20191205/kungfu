package com.kungfu.seckill.service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.seckill.common.dto.SeckillOrderMessage;
import com.kungfu.seckill.service.entity.SeckillActivity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.BitSet;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private ActivityService activityService;

    @Resource
    private ObjectMapper objectMapper;

    private BitSet bloom = new BitSet(1 << 20);

    private RedisScript<Long> seckillScript;

    @PostConstruct
    public void init() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/seckill.lua")));
        script.setResultType(Long.class);
        this.seckillScript = script;
    }

    /**
     * 执行秒杀
     */
    public String doSeckill(Long activityId, Long userId) throws Exception {
        // 1. 检查活动状态
        SeckillActivity activity = activityService.getActivity(activityId);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }
        if (!"ACTIVE".equals(activity.getStatus())) {
            throw new RuntimeException("活动未开始或已结束");
        }

        // 2. 频率限制：同一用户 5 秒内只能请求一次
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent("seckill:limit:" + userId, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new RuntimeException("请求过于频繁");
        }

        // 3. 布隆过滤器：检查是否已购买
        if (mightContain(activityId, userId)) {
            throw new RuntimeException("您已购买过");
        }

        // 4. Redis Lua 原子扣库存
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.singletonList("seckill:stock:" + activityId),
                "1"
        );
        if (result == null || result == -1) {
            throw new RuntimeException("活动不存在");
        }
        if (result == 0) {
            throw new RuntimeException("已售罄");
        }

        // 5. 生成订单号
        String orderNo = "SK_" + System.currentTimeMillis() + "_" + userId;

        // 6. 构建消息并序列化
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderNo(orderNo);
        message.setActivityId(activityId);
        message.setUserId(userId);
        message.setProductId(activity.getProductId());
        message.setProductName(activity.getProductName());
        message.setSeckillPrice(activity.getSeckillPrice());
        message.setQuantity(1);

        String json = objectMapper.writeValueAsString(message);

        // 7. 发送 Kafka 消息
        kafkaTemplate.send("seckill-order", String.valueOf(activityId), json);

        // 8. 标记布隆过滤器
        markPurchased(activityId, userId);

        // 9. 返回订单号
        return orderNo;
    }

    // ==================== 布隆过滤器 ====================

    private int hash1(long activityId, long userId) {
        return Math.abs((int) ((activityId * 31 + userId) % (1 << 20)));
    }

    private int hash2(long activityId, long userId) {
        return Math.abs((int) ((activityId * 131 + userId * 7) % (1 << 20)));
    }

    private int hash3(long activityId, long userId) {
        return Math.abs((int) ((activityId * 1049 + userId * 13) % (1 << 20)));
    }

    private boolean mightContain(long activityId, long userId) {
        return bloom.get(hash1(activityId, userId))
                && bloom.get(hash2(activityId, userId))
                && bloom.get(hash3(activityId, userId));
    }

    private void markPurchased(long activityId, long userId) {
        bloom.set(hash1(activityId, userId));
        bloom.set(hash2(activityId, userId));
        bloom.set(hash3(activityId, userId));
    }
}
