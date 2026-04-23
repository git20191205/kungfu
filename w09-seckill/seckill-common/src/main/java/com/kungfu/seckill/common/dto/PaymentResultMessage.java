package com.kungfu.seckill.common.dto;

import lombok.Data;

/**
 * 支付服务 → 订单服务 的 Kafka 回调消息
 */
@Data
public class PaymentResultMessage {
    private String orderNo;
    private String paymentNo;
    private boolean success;
    private String message;
}
