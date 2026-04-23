package com.kungfu.seckill.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillStockMapper {

    @Update("UPDATE t_seckill_stock SET available_stock = available_stock - 1 WHERE activity_id = #{activityId} AND available_stock > 0")
    int deductStock(@Param("activityId") Long activityId);
}
