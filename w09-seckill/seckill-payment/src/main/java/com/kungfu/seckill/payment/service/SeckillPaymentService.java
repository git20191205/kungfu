package com.kungfu.seckill.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kungfu.seckill.common.dto.PaymentMessage;
import com.kungfu.seckill.common.dto.PaymentResultMessage;
import com.kungfu.seckill.payment.entity.SeckillPayment;
import com.kungfu.seckill.payment.kafka.PaymentResultProducer;
import com.kungfu.seckill.payment.mapper.SeckillPaymentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillPaymentService {

    @Resource
    private SeckillPaymentMapper seckillPaymentMapper;

    @Resource
    private PaymentResultProducer paymentResultProducer;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Transactional
    public void createPayment(PaymentMessage msg) {
        // 1. 幂等检查：同一订单不重复创建支付单
        LambdaQueryWrapper<SeckillPayment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillPayment::getOrderNo, msg.getOrderNo());
        SeckillPayment existing = seckillPaymentMapper.selectOne(wrapper);
        if (existing != null) {
            log.warn("支付单已存在, orderNo={}, paymentNo={}", msg.getOrderNo(), existing.getPaymentNo());
            return;
        }

        // 2. 生成支付单
        String paymentNo = "PAY_" + System.currentTimeMillis() + "_" + msg.getOrderNo().hashCode();
        SeckillPayment payment = new SeckillPayment();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(msg.getOrderNo());
        payment.setAmount(msg.getAmount());
        payment.setStatus("PENDING");
        payment.setCreateTime(LocalDateTime.now());
        payment.setUpdateTime(LocalDateTime.now());
        seckillPaymentMapper.insert(payment);

        log.info("支付单创建成功, paymentNo={}, orderNo={}", paymentNo, msg.getOrderNo());

        // 3. 模拟支付：2秒后回调
        scheduler.schedule(() -> handleCallback(paymentNo, true), 2, TimeUnit.SECONDS);
    }

    public void handleCallback(String paymentNo, boolean success) {
        try {
            // 更新支付状态
            String newStatus = success ? "PAID" : "FAILED";
            LambdaUpdateWrapper<SeckillPayment> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SeckillPayment::getPaymentNo, paymentNo)
                   .set(SeckillPayment::getStatus, newStatus)
                   .set(SeckillPayment::getUpdateTime, LocalDateTime.now());
            seckillPaymentMapper.update(null, wrapper);

            // 查询支付单获取 orderNo
            LambdaQueryWrapper<SeckillPayment> query = new LambdaQueryWrapper<>();
            query.eq(SeckillPayment::getPaymentNo, paymentNo);
            SeckillPayment payment = seckillPaymentMapper.selectOne(query);
            if (payment == null) {
                log.error("支付单不存在, paymentNo={}", paymentNo);
                return;
            }

            // 发送支付结果到 Kafka
            PaymentResultMessage result = new PaymentResultMessage();
            result.setOrderNo(payment.getOrderNo());
            result.setPaymentNo(paymentNo);
            result.setSuccess(success);
            result.setMessage(success ? "支付成功" : "支付失败");
            paymentResultProducer.sendPaymentResult(result);

            log.info("支付回调处理完成, paymentNo={}, status={}", paymentNo, newStatus);
        } catch (Exception e) {
            log.error("支付回调处理异常, paymentNo={}", paymentNo, e);
        }
    }
}
