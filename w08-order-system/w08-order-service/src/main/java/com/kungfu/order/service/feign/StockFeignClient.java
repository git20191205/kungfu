package com.kungfu.order.service.feign;

import com.kungfu.order.common.dto.StockDeductDTO;
import com.kungfu.order.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "stock-service")
public interface StockFeignClient {

    @PostMapping("/api/stock/deduct")
    Result<Boolean> deductStock(@RequestBody StockDeductDTO dto);
}
