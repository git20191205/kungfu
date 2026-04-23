package com.kungfu.seckill.order.kafka;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.seckill.common.dto.PaymentResultMessage;
import com.kungfu.seckill.order.entity.SeckillOrder;
import com.kungfu.seckill.order.mapper.SeckillOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PaymentResultConsumer {

    @Resource
    private SeckillOrderMapper seckillOrderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-result", groupId = "seckill-order-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            PaymentResultMessage msg = objectMapper.readValue(record.value(), PaymentResultMessage.class);
            String newStatus = msg.isSuccess() ? "PAID" : "CANCELLED";

            LambdaUpdateWrapper<SeckillOrder> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SeckillOrder::getOrderNo, msg.getOrderNo())
                   .set(SeckillOrder::getStatus, newStatus)
                   .set(SeckillOrder::getUpdateTime, LocalDateTime.now());
            seckillOrderMapper.update(null, wrapper);

            String resultKey = "seckill:result:" + msg.getOrderNo();
            stringRedisTemplate.opsForValue().set(resultKey, newStatus, 30, TimeUnit.MINUTES);

            log.info("支付结果处理完成, orderNo={}, paymentNo={}, status={}",
                    msg.getOrderNo(), msg.getPaymentNo(), newStatus);
        } catch (Exception e) {
            log.error("支付结果消息处理失败", e);
        }
    }
}
