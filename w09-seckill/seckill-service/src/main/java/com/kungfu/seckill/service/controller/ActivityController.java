package com.kungfu.seckill.service.controller;

import com.kungfu.seckill.common.result.Result;
import com.kungfu.seckill.service.entity.SeckillActivity;
import com.kungfu.seckill.service.service.ActivityService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/seckill/activity")
public class ActivityController {

    @Resource
    private ActivityService activityService;

    @GetMapping("/{activityId}")
    public Result<Map<String, Object>> getActivity(@PathVariable Long activityId) {
        try {
            SeckillActivity activity = activityService.getActivity(activityId);
            if (activity == null) {
                return Result.fail("活动不存在");
            }
            Integer remainStock = activityService.getRemainStock(activityId);

            Map<String, Object> data = new HashMap<>();
            data.put("activityName", activity.getActivityName());
            data.put("productName", activity.getProductName());
            data.put("originalPrice", activity.getOriginalPrice());
            data.put("seckillPrice", activity.getSeckillPrice());
            data.put("remainStock", remainStock);
            data.put("startTime", activity.getStartTime());
            data.put("endTime", activity.getEndTime());
            data.put("status", activity.getStatus());

            return Result.ok(data);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/{activityId}/warmup")
    public Result<String> warmup(@PathVariable Long activityId) {
        try {
            activityService.warmup(activityId);
            return Result.ok("预热完成");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
