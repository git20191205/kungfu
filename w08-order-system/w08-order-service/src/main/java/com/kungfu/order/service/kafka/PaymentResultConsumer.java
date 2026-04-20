package com.kungfu.order.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungfu.order.common.dto.PaymentResultDTO;
import com.kungfu.order.common.enums.OrderStatus;
import com.kungfu.order.service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "payment-result", groupId = "order-service-group")
    public void onPaymentResult(ConsumerRecord<String, String> record) {
        try {
            PaymentResultDTO result = objectMapper.readValue(record.value(), PaymentResultDTO.class);
            log.info("收到支付结果: orderNo={}, success={}", result.getOrderNo(), result.isSuccess());

            OrderStatus newStatus = result.isSuccess() ? OrderStatus.PAID : OrderStatus.FAILED;
            orderService.updateOrderStatus(result.getOrderNo(), newStatus);

        } catch (Exception e) {
            log.error("处理支付结果失败: {}", record.value(), e);
        }
    }
}
