package com.kungfu.order.service.controller;

import com.kungfu.order.common.dto.OrderCreateDTO;
import com.kungfu.order.common.result.Result;
import com.kungfu.order.service.entity.Order;
import com.kungfu.order.service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<String> createOrder(@RequestBody OrderCreateDTO dto) {
        try {
            String orderNo = orderService.createOrder(dto);
            return Result.ok(orderNo);
        } catch (Exception e) {
            return Result.fail("下单失败: " + e.getMessage());
        }
    }

    @GetMapping("/{orderNo}")
    public Result<Order> getOrder(@PathVariable String orderNo) {
        Order order = orderService.getOrder(orderNo);
        if (order == null) {
            return Result.fail(404, "订单不存在");
        }
        return Result.ok(order);
    }
}
