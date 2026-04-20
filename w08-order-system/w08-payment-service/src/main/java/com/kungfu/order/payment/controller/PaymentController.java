package com.kungfu.order.payment.controller;

import com.kungfu.order.common.dto.PaymentCreateDTO;
import com.kungfu.order.common.result.Result;
import com.kungfu.order.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public Result<String> create(@RequestBody PaymentCreateDTO dto) {
        log.info("收到创建支付请求: orderNo={}, amount={}", dto.getOrderNo(), dto.getAmount());
        String paymentNo = paymentService.createPayment(dto);
        return Result.ok(paymentNo);
    }

    @PostMapping("/callback/{paymentNo}")
    public Result<Void> callback(@PathVariable String paymentNo,
                                 @RequestParam(defaultValue = "true") boolean success) {
        log.info("手动触发支付回调: paymentNo={}, success={}", paymentNo, success);
        paymentService.handleCallback(paymentNo, success);
        return Result.ok();
    }
}
