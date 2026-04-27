# w09-seckill 项目展示

> 高并发秒杀系统，QPS 6711, P99 28ms

## 项目简介

秒杀系统是一个高并发场景的典型应用，支持 10 万用户同时参与秒杀，通过四层过滤架构和三道防线防超卖，实现了 QPS 6711，P99 延迟 28ms，成功率 100%。

## 技术栈

- **后端框架**：Spring Boot 2.7.x + Spring Cloud 2021.x
- **数据库**：MySQL 8.0 + Redis 7.0
- **消息队列**：Kafka 3.3
- **微服务**：Nacos 2.2 + Sentinel 1.8 + Gateway 3.1
- **监控**：Prometheus + Grafana
- **容器化**：Docker + Docker Compose

## 架构设计

### 四层过滤架构

```
用户（10 万请求）
  ↓
CDN + 前端（过滤 50%）→ 5 万请求
  ↓
Gateway + Sentinel（限流 5000 QPS）→ 5000 请求
  ↓
Redis Lua（原子扣库存）→ 100 个请求
  ↓
Kafka + MySQL（异步创建订单）→ 100 个订单
```

### 三道防线防超卖

1. **Redis Lua 原子扣减**：保证库存扣减的原子性
2. **MySQL 乐观锁**：`UPDATE ... WHERE remain_stock > 0`
3. **唯一索引**：`UNIQUE KEY (activity_id, user_id)`

## 性能数据

### 压测结果

**测试工具**：wrk -t10 -c100 -d60s

**优化前**：
- QPS：3623
- P99 延迟：45ms
- 成功率：100%

**优化后**：
- QPS：6711（+85%）
- P99 延迟：28ms（-38%）
- 成功率：100%
- GC 停顿：< 50ms

### 优化历程

| 优化项 | QPS | 提升 | P99 延迟 |
|--------|-----|------|----------|
| 初始版本 | 3623 | - | 45ms |
| Redis Lua | 4200 | +16% | 38ms |
| Kafka 异步 | 5100 | +21% | 32ms |
| 布隆过滤器 | 5800 | +14% | 30ms |
| 连接池预热 | 6200 | +7% | 29ms |
| JVM 调优 | 6711 | +8% | 28ms |

## 核心功能

### 1. 秒杀下单

```java
@PostMapping("/seckill/{activityId}")
public Result<String> seckill(@PathVariable Long activityId, 
                               @RequestHeader("X-User-Id") Long userId) {
    // 1. 频率限制
    // 2. 布隆过滤器
    // 3. Redis Lua 原子扣库存
    // 4. 发送 Kafka 消息
    // 5. 返回成功
}
```

### 2. 异步创建订单

```java
@KafkaListener(topics = "seckill-order", concurrency = "10")
public void consume(SeckillMessage message) {
    // 1. MySQL 乐观锁扣库存
    // 2. 保存订单
    // 3. 返回结果
}
```

### 3. 订单查询

```java
@GetMapping("/orders")
public Result<List<Order>> list(@RequestParam Long userId) {
    // 1. 查询 Redis 缓存
    // 2. 缓存未命中，查询 MySQL
    // 3. 回写缓存
}
```

## 监控告警

### Prometheus 监控

- QPS：实时 QPS
- 延迟：P50、P95、P99
- 成功率：成功请求 / 总请求
- JVM：堆内存、GC 次数、GC 停顿

### Grafana 大盘

![Grafana Dashboard](docs/images/grafana-dashboard.png)

## 快速开始

### 1. 环境准备

```bash
# 安装 Docker 和 Docker Compose
docker --version
docker-compose --version
```

### 2. 启动中间件

```bash
# 启动 MySQL、Redis、Kafka、Nacos
docker-compose up -d
```

### 3. 启动服务

```bash
# 启动 Gateway
cd seckill-gateway && mvn spring-boot:run

# 启动 Seckill Service
cd seckill-service && mvn spring-boot:run

# 启动 Order Service
cd seckill-order && mvn spring-boot:run
```

### 4. 压测

```bash
# 压测秒杀接口
wrk -t10 -c100 -d60s http://localhost:8090/api/v1/seckill/1
```

## 文档

- [架构设计文档](docs/01_架构设计.md)
- [数据库设计文档](docs/02_数据库设计.md)
- [接口设计文档](docs/03_接口设计.md)
- [压测报告](docs/04_压测报告.md)
- [优化方案](docs/05_优化方案.md)

## 技术亮点

1. **四层过滤架构**：CDN → Gateway → Redis → Kafka
2. **三道防线防超卖**：Redis Lua + MySQL 乐观锁 + 唯一索引
3. **异步削峰**：Kafka 异步创建订单
4. **性能优化**：QPS 3623 → 6711（+85%）
5. **完整监控**：Prometheus + Grafana 实时监控

## 下一步优化

### 短期（1 周）
- 优化 SQL 查询
- 增加本地缓存
- 优化序列化

**预期**：QPS 6711 → 8000

### 中期（1 月）
- 服务分机部署（3 台 → 10 台）
- Redis Cluster 扩容
- 数据库读写分离

**预期**：QPS 8000 → 15000

### 长期（3 月）
- Kubernetes 集群部署
- 服务网格（Istio）
- 全链路压测

**预期**：QPS 15000 → 30000+

## 联系方式

- **作者**：张三
- **邮箱**：zhangsan@example.com
- **GitHub**：https://github.com/zhangsan/w09-seckill
- **博客**：https://blog.example.com

---

**项目地址**：https://github.com/zhangsan/w09-seckill  
**性能数据**：QPS 6711, P99 28ms  
**更新日期**：2026-04-27
