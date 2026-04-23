package com.kungfu.seckill.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.kungfu.seckill.order.mapper")
public class SeckillOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillOrderApplication.class, args);
    }
}
