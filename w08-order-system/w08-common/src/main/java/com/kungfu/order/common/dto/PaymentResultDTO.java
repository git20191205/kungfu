package com.kungfu.order.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** Kafka 支付结果消息体 */
@Data
public class PaymentResultDTO {
    private String orderNo;
    private String paymentNo;
    private boolean success;
    private String message;
}
