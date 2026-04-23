package com.kungfu.seckill.service.controller;

import com.kungfu.seckill.common.result.Result;
import com.kungfu.seckill.service.service.SeckillService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Resource
    private SeckillService seckillService;

    @PostMapping("/{activityId}")
    public Result<String> doSeckill(@PathVariable Long activityId,
                                    @RequestHeader("X-User-Id") Long userId) {
        try {
            String orderNo = seckillService.doSeckill(activityId, userId);
            return Result.ok(orderNo);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
