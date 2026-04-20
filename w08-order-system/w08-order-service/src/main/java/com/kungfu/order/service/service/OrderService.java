package com.kungfu.order.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kungfu.order.common.dto.OrderCreateDTO;
import com.kungfu.order.common.dto.PaymentCreateDTO;
import com.kungfu.order.common.dto.StockDeductDTO;
import com.kungfu.order.common.enums.OrderStatus;
import com.kungfu.order.common.result.Result;
import com.kungfu.order.service.entity.Order;
import com.kungfu.order.service.feign.PaymentFeignClient;
import com.kungfu.order.service.feign.StockFeignClient;
import com.kungfu.order.service.mapper.OrderMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final StockFeignClient stockFeignClient;
    private final PaymentFeignClient paymentFeignClient;

    /** 单价（简化，实际应查商品服务） */
    private static final BigDecimal UNIT_PRICE = new BigDecimal("99.00");

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO dto) {
        // 1. 生成订单号
        String orderNo = "ORD_" + System.currentTimeMillis();
        BigDecimal amount = UNIT_PRICE.multiply(BigDecimal.valueOf(dto.getQuantity()));

        // 2. 创建订单（PENDING）
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(dto.getUserId());
        order.setProductId(dto.getProductId());
        order.setQuantity(dto.getQuantity());
        order.setAmount(amount);
        order.setStatus(OrderStatus.PENDING.name());
        orderMapper.insert(order);
        log.info("订单创建成功: {}", orderNo);

        // 3. Feign 调用库存服务扣减库存
        StockDeductDTO stockDTO = new StockDeductDTO();
        stockDTO.setProductId(dto.getProductId());
        stockDTO.setQuantity(dto.getQuantity());
        stockDTO.setOrderNo(orderNo);
        Result<Boolean> stockResult = stockFeignClient.deductStock(stockDTO);
        if (!stockResult.isSuccess()) {
            throw new RuntimeException("库存扣减失败: " + stockResult.getMsg());
        }
        log.info("库存扣减成功: productId={}, quantity={}", dto.getProductId(), dto.getQuantity());

        // 4. Feign 调用支付服务创建支付单
        PaymentCreateDTO payDTO = new PaymentCreateDTO();
        payDTO.setOrderNo(orderNo);
        payDTO.setAmount(amount);
        Result<String> payResult = paymentFeignClient.createPayment(payDTO);
        if (!payResult.isSuccess()) {
            throw new RuntimeException("支付单创建失败: " + payResult.getMsg());
        }
        log.info("支付单创建成功: paymentNo={}", payResult.getData());

        return orderNo;
    }

    public Order getOrder(String orderNo) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    public void updateOrderStatus(String orderNo, OrderStatus status) {
        Order order = getOrder(orderNo);
        if (order != null) {
            order.setStatus(status.name());
            orderMapper.updateById(order);
            log.info("订单状态更新: {} → {}", orderNo, status);
        }
    }
}
