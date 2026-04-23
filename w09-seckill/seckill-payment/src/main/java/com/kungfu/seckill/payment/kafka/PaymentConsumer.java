package com.kungfu.seckill.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.seckill.common.dto.PaymentMessage;
import com.kungfu.seckill.payment.service.SeckillPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class PaymentConsumer {

    @Resource
    private SeckillPaymentService seckillPaymentService;

    @Resource
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "seckill-payment", groupId = "seckill-payment-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            PaymentMessage msg = objectMapper.readValue(record.value(), PaymentMessage.class);
            log.info("收到支付请求, orderNo={}, amount={}", msg.getOrderNo(), msg.getAmount());
            seckillPaymentService.createPayment(msg);
        } catch (Exception e) {
            log.error("支付消息处理失败, value={}", record.value(), e);
        }
    }
}
