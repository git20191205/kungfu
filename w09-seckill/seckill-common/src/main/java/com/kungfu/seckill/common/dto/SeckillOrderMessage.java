package com.kungfu.seckill.common.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillOrderMessage {
    private String orderNo;
    private Long activityId;
    private Long userId;
    private Long productId;
    private String productName;
    private BigDecimal seckillPrice;
    private Integer quantity;
}
