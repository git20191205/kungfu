package com.kungfu.seckill.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kungfu.seckill.common.result.Result;
import com.kungfu.seckill.payment.entity.SeckillPayment;
import com.kungfu.seckill.payment.mapper.SeckillPaymentMapper;
import com.kungfu.seckill.payment.service.SeckillPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final SeckillPaymentMapper seckillPaymentMapper;
    private final SeckillPaymentService seckillPaymentService;

    @GetMapping("/{paymentNo}")
    public Result<SeckillPayment> getPayment(@PathVariable String paymentNo) {
        LambdaQueryWrapper<SeckillPayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillPayment::getPaymentNo, paymentNo);
        SeckillPayment payment = seckillPaymentMapper.selectOne(wrapper);
        if (payment == null) {
            return Result.fail("支付单不存在");
        }
        return Result.ok(payment);
    }

    @PostMapping("/callback/{paymentNo}")
    public Result<Void> manualCallback(@PathVariable String paymentNo,
                                       @RequestParam(defaultValue = "true") boolean success) {
        seckillPaymentService.handleCallback(paymentNo, success);
        return Result.ok();
    }
}
