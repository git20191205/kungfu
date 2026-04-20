package com.kungfu.order.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kungfu.order.common.dto.PaymentCreateDTO;
import com.kungfu.order.common.dto.PaymentResultDTO;
import com.kungfu.order.payment.entity.Payment;
import com.kungfu.order.payment.kafka.PaymentKafkaProducer;
import com.kungfu.order.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentKafkaProducer kafkaProducer;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 创建支付单，模拟异步支付回调
     */
    public String createPayment(PaymentCreateDTO dto) {
        String paymentNo = "PAY_" + System.currentTimeMillis();

        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(dto.getOrderNo());
        payment.setAmount(dto.getAmount());
        payment.setStatus("PENDING");
        payment.setCreateTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        log.info("创建支付单: paymentNo={}, orderNo={}, amount={}", paymentNo, dto.getOrderNo(), dto.getAmount());

        // 模拟外部支付网关异步回调，2秒后自动回调成功
        scheduler.schedule(() -> {
            try {
                handleCallback(paymentNo, true);
            } catch (Exception e) {
                log.error("模拟支付回调异常: paymentNo={}", paymentNo, e);
            }
        }, 2, TimeUnit.SECONDS);

        return paymentNo;
    }

    /**
     * 处理支付回调：更新状态 + 发送Kafka消息
     */
    public void handleCallback(String paymentNo, boolean success) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo));

        if (payment == null) {
            log.warn("支付单不存在: paymentNo=", paymentNo);
            return;
        }

        payment.setStatus(success ? "PAID" : "FAILED");
        paymentMapper.updateById(payment);
        log.info("支付回调处理: paymentNo={}, status={}", paymentNo, payment.getStatus());

        PaymentResultDTO result = new PaymentResultDTO();
        result.setOrderNo(payment.getOrderNo());
        result.setPaymentNo(paymentNo);
        result.setSuccess(success);
        result.setMessage(success ? "支付成功" : "支付失败");

        kafkaProducer.sendPaymentResult(result);
    }
}
