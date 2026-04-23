package com.kungfu.seckill.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_seckill_stock")
public class SeckillStock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockStock;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
