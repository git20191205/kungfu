package com.kungfu.seckill.common.enums;

import lombok.Getter;

@Getter
public enum ActivityStatus {
    PENDING("PENDING", "未开始"),
    ACTIVE("ACTIVE", "进行中"),
    ENDED("ENDED", "已结束");

    private final String code;
    private final String desc;

    ActivityStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
