package com.kungfu.order.service.feign;

import com.kungfu.order.common.dto.PaymentCreateDTO;
import com.kungfu.order.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentFeignClient {

    @PostMapping("/api/payment/create")
    Result<String> createPayment(@RequestBody PaymentCreateDTO dto);
}
