package com.kungfu.order.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StockDeductDTO {
    private Long productId;
    private Integer quantity;
    private String orderNo;
}
