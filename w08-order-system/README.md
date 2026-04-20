# W08: 分布式订单系统

> 阶段项目实战（D52-D60）— 整合 W01-W07 全部知识

## 架构图

```
                    ┌─────────────────────────────────────────────┐
                    │              API Gateway (8080)              │
                    │         路由 + 限流（Redis 令牌桶）          │
                    └────────────────────┬────────────────────────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              ↓                          ↓                          ↓
   ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
   │ Order Service    │     │ Stock Service    │     │ Payment Service  │
   │ (8081)           │────→│ (8082)           │     │ (8083)           │
   │                  │     │                  │     │                  │
   │ @GlobalTx        │────→│ Redis 缓存+锁   │     │ Kafka Producer   │
   │ Feign Client     │     │ Seata RM         │     │ 模拟支付回调     │
   │ Kafka Consumer   │     │                  │     │                  │
   └──────────────────┘     └──────────────────┘     └──────────────────┘
          │                                                    │
          │←──────────── Kafka (payment-result) ──────────────┘
          │
   ┌──────────────────────────────────────────────────────────────────┐
   │                    基础设施                                        │
   │  Nacos(8848) + Seata(8091) + MySQL(3306) + Redis(6379) + Kafka(9092) │
   └──────────────────────────────────────────────────────────────────┘
```

## 模块结构

| 模块 | 端口 | 职责 |
|------|------|------|
| w08-common | - | 公共 DTO、枚举、Result |
| w08-gateway | 8080 | API 网关（路由 + 限流） |
| w08-order-service | 8081 | 订单（Seata TM + Feign + Kafka Consumer） |
| w08-stock-service | 8082 | 库存（Redis 缓存 + 分布式锁 + Seata RM） |
| w08-payment-service | 8083 | 支付（Kafka Producer + 模拟回调） |

## 启动顺序

```bash
# 1. 中间件（确保已启动）
# MySQL(3306) + Redis(6379) + Kafka(9092) + Nacos(8848) + Seata(8091)

# 2. 初始化数据库（只需执行一次）
cd w08-order-system
mvn install -N -q && mvn install -pl w08-common -q && mvn compile -q
java -cp "w08-common/target/classes;w08-stock-service/target/classes;$(cat target/cp.txt)" \
  com.kungfu.order.common.DatabaseInitializer

# 3. 启动服务（每个开一个终端）
cd w08-stock-service   && mvn spring-boot:run
cd w08-payment-service && mvn spring-boot:run
cd w08-order-service   && mvn spring-boot:run
cd w08-gateway         && mvn spring-boot:run
```

## 测试

```bash
# 下单（通过 Gateway）
curl -X POST http://localhost:8080/api/order/create \
  -H "Content-Type: application/json" \
  -d '{"userId":1001,"productId":1,"quantity":2}'

# 查询订单
curl http://localhost:8080/api/order/{orderNo}

# 查询库存
curl http://localhost:8080/api/stock/1

# 直接调用（不经过 Gateway）
curl http://localhost:8081/api/order/create -X POST \
  -H "Content-Type: application/json" \
  -d '{"userId":1001,"productId":1,"quantity":2}'
```

## 核心流程

1. 用户调用 `POST /api/order/create`
2. OrderService 创建订单（status=PENDING）
3. OrderService 通过 Feign 调用 StockService 扣库存（Seata 分布式事务）
4. OrderService 通过 Feign 调用 PaymentService 创建支付单
5. PaymentService 模拟支付回调（2 秒后），发送 Kafka 消息
6. OrderService 消费 Kafka 消息，更新订单状态 → PAID

## 技术栈

- Spring Boot 2.7.18
- Spring Cloud 2021.0.8
- Spring Cloud Alibaba 2021.0.5.0
- Nacos 2.2.3（注册中心）
- Seata 1.7.1（分布式事务 AT 模式）
- Kafka（支付结果异步通知）
- Redis（库存缓存 + 分布式锁 + 网关限流）
- MySQL 8.0 + MyBatis-Plus 3.5.3
- OpenFeign（服务间调用）
- Spring Cloud Gateway（API 网关）
