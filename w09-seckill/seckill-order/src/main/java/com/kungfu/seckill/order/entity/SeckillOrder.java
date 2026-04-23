package com.kungfu.seckill.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_seckill_order")
public class SeckillOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long activityId;

    private Long userId;

    private Long productId;

    private String productName;

    private BigDecimal seckillPrice;

    private String status = "PENDING";

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
