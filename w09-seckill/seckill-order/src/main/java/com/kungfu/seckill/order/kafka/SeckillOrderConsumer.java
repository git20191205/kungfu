package com.kungfu.seckill.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.seckill.common.dto.SeckillOrderMessage;
import com.kungfu.seckill.order.service.SeckillOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillOrderConsumer {

    @Resource
    private SeckillOrderService seckillOrderService;

    @Resource
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "seckill-order", groupId = "seckill-order-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            log.info("收到秒杀订单消息, key={}, partition={}, offset={}",
                    record.key(), record.partition(), record.offset());

            SeckillOrderMessage msg = objectMapper.readValue(record.value(), SeckillOrderMessage.class);
            seckillOrderService.createOrder(msg);

            log.info("秒杀订单消息处理成功, orderNo={}", msg.getOrderNo());
        } catch (Exception e) {
            log.error("秒杀订单消息处理失败, value={}", record.value(), e);
            // 不抛出异常，避免无限重试
        }
    }
}
