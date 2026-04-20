package com.kungfu.order.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.order.common.dto.PaymentResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendPaymentResult(PaymentResultDTO dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            kafkaTemplate.send("payment-result", dto.getOrderNo(), json);
            log.info("发送支付结果消息: topic=payment-result, key={}, value={}", dto.getOrderNo(), json);
        } catch (Exception e) {
            log.error("发送支付结果消息失败: {}", e.getMessage(), e);
        }
    }
}
