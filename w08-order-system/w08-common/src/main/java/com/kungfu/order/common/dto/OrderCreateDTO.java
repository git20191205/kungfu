package com.kungfu.order.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderCreateDTO {
    private Long userId;
    private Long productId;
    private Integer quantity;
}
