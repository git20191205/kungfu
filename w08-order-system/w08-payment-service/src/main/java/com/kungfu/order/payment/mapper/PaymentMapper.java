package com.kungfu.order.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kungfu.order.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
