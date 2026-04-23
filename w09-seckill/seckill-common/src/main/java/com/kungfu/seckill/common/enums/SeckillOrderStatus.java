package com.kungfu.seckill.common.enums;

import lombok.Getter;

@Getter
public enum SeckillOrderStatus {
    PENDING("PENDING", "待支付"),
    PAID("PAID", "已支付"),
    CANCELLED("CANCELLED", "已取消"),
    TIMEOUT("TIMEOUT", "超时未支付");

    private final String code;
    private final String desc;

    SeckillOrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
