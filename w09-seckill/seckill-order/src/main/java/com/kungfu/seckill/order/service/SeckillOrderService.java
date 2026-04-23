package com.kungfu.seckill.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.seckill.common.dto.PaymentMessage;
import com.kungfu.seckill.common.dto.SeckillOrderMessage;
import com.kungfu.seckill.order.entity.SeckillOrder;
import com.kungfu.seckill.order.mapper.SeckillOrderMapper;
import com.kungfu.seckill.order.mapper.SeckillStockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillOrderService {

    @Resource
    private SeckillOrderMapper seckillOrderMapper;

    @Resource
    private SeckillStockMapper seckillStockMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Transactional
    public void createOrder(SeckillOrderMessage msg) {
        // 1. 幂等检查：按 activity_id + user_id 查重
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getActivityId, msg.getActivityId())
               .eq(SeckillOrder::getUserId, msg.getUserId());
        SeckillOrder existing = seckillOrderMapper.selectOne(wrapper);
        if (existing != null) {
            log.warn("重复下单, activityId={}, userId={}, 已存在orderNo={}",
                    msg.getActivityId(), msg.getUserId(), existing.getOrderNo());
            return;
        }

        // 2. 构建订单实体
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(msg.getOrderNo());
        order.setActivityId(msg.getActivityId());
        order.setUserId(msg.getUserId());
        order.setProductId(msg.getProductId());
        order.setProductName(msg.getProductName());
        order.setSeckillPrice(msg.getSeckillPrice());
        order.setStatus("PENDING");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 3. 插入订单（唯一索引 uk_activity_user 兜底幂等）
        try {
            seckillOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("唯一索引拦截重复下单, activityId={}, userId={}", msg.getActivityId(), msg.getUserId());
            return;
        }

        // 4. MySQL 扣减库存
        int affected = seckillStockMapper.deductStock(msg.getActivityId());
        if (affected == 0) {
            log.error("MySQL库存不足, activityId={}", msg.getActivityId());
        }

        // 5. 缓存初始结果: PENDING
        String resultKey = "seckill:result:" + msg.getOrderNo();
        stringRedisTemplate.opsForValue().set(resultKey, "PENDING", 30, TimeUnit.MINUTES);

        // 6. 发送支付请求到 Kafka
        try {
            PaymentMessage paymentMsg = new PaymentMessage();
            paymentMsg.setOrderNo(msg.getOrderNo());
            paymentMsg.setAmount(msg.getSeckillPrice() != null ? msg.getSeckillPrice() : BigDecimal.ZERO);
            String json = objectMapper.writeValueAsString(paymentMsg);
            kafkaTemplate.send("seckill-payment", msg.getOrderNo(), json);
            log.info("支付请求已发送, orderNo={}", msg.getOrderNo());
        } catch (Exception e) {
            log.error("支付请求发送失败, orderNo={}", msg.getOrderNo(), e);
        }

        log.info("秒杀订单创建成功, orderNo={}, activityId={}, userId={}",
                msg.getOrderNo(), msg.getActivityId(), msg.getUserId());
    }
}
