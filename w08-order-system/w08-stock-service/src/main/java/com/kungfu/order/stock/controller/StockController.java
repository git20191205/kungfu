package com.kungfu.order.stock.controller;

import com.kungfu.order.common.dto.StockDeductDTO;
import com.kungfu.order.common.result.Result;
import com.kungfu.order.stock.entity.Stock;
import com.kungfu.order.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/deduct")
    public Result<Boolean> deductStock(@RequestBody StockDeductDTO dto) {
        boolean success = stockService.deductStock(dto.getProductId(), dto.getQuantity(), dto.getOrderNo());
        if (success) {
            return Result.ok(true);
        }
        return Result.fail("Stock deduction failed: insufficient stock or lock conflict");
    }

    @GetMapping("/{productId}")
    public Result<Stock> getStock(@PathVariable Long productId) {
        Stock stock = stockService.getStock(productId);
        if (stock == null) {
            return Result.fail("Stock not found for productId: " + productId);
        }
        return Result.ok(stock);
    }
}
