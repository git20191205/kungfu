package com.kungfu.seckill.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kungfu.seckill.common.dto.SeckillResultDTO;
import com.kungfu.seckill.common.result.Result;
import com.kungfu.seckill.order.entity.SeckillOrder;
import com.kungfu.seckill.order.mapper.SeckillOrderMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/seckill/result")
public class OrderQueryController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SeckillOrderMapper seckillOrderMapper;

    @GetMapping("/{orderNo}")
    public Result<SeckillResultDTO> queryResult(@PathVariable String orderNo) {
        // 1. 优先查 Redis 缓存
        String status = stringRedisTemplate.opsForValue().get("seckill:result:" + orderNo);
        if (status != null) {
            SeckillResultDTO dto = new SeckillResultDTO();
            dto.setOrderNo(orderNo);
            dto.setStatus(status);
            return Result.ok(dto);
        }

        // 2. 缓存未命中，查 MySQL
        LambdaQueryWrapper<SeckillOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillOrder::getOrderNo, orderNo);
        SeckillOrder order = seckillOrderMapper.selectOne(wrapper);

        if (order == null) {
            return Result.fail(404, "订单不存在");
        }

        SeckillResultDTO dto = new SeckillResultDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setStatus(order.getStatus());
        return Result.ok(dto);
    }
}
