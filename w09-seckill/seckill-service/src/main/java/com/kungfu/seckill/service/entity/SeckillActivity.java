package com.kungfu.seckill.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_seckill_activity")
public class SeckillActivity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityName;
    private Long productId;
    private String productName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime createTime;
}
