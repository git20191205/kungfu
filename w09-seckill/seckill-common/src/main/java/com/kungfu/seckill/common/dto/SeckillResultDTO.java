package com.kungfu.seckill.common.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillResultDTO {
    private String orderNo;
    private String status;
    private String activityName;
    private BigDecimal seckillPrice;
}
