package com.kungfu.seckill.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单服务 → 支付服务 的 Kafka 消息
 */
@Data
public class PaymentMessage {
    private String orderNo;
    private BigDecimal amount;
}
