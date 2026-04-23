package com.kungfu.seckill.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.seckill.common.dto.PaymentResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendPaymentResult(PaymentResultMessage result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            kafkaTemplate.send("payment-result", result.getOrderNo(), json);
            log.info("支付结果已发送, orderNo={}, success={}", result.getOrderNo(), result.isSuccess());
        } catch (Exception e) {
            log.error("支付结果发送失败, orderNo={}", result.getOrderNo(), e);
        }
    }
}
