package com.kungfu.order.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentCreateDTO {
    private String orderNo;
    private BigDecimal amount;
}
