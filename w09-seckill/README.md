# 电商秒杀系统

> 高并发秒杀系统实战项目 - 支持 QPS 5000+，P99 延迟 < 200ms

## 项目简介

这是一个完整的电商秒杀系统，采用微服务架构，实现了高并发场景下的库存扣减、订单创建、支付回调等核心功能。

### 核心特性

- **高性能**: QPS 6711（单机），P99 延迟 28ms，超目标 34%
- **不超卖**: Redis Lua 原子扣减 + MySQL 乐观锁双重保障
- **高可用**: 微服务架构 + 熔断降级 + 限流保护
- **异步削峰**: Kafka 消息队列解耦，保护数据库
- **完整监控**: Prometheus + Grafana 实时监控告警

### 技术栈

| 类型 | 技术 |
|------|------|
| 框架 | Spring Boot 2.7.x + Spring Cloud 2021.x |
| 服务治理 | Nacos（注册中心 + 配置中心）|
| 网关 | Spring Cloud Gateway + Sentinel |
| 缓存 | Redis 7 + Lettuce |
| 消息队列 | Kafka 3.x |
| 数据库 | MySQL 8.0 + MyBatis |
| 监控 | Prometheus + Grafana + Micrometer |
| 容器化 | Docker + Docker Compose |

## 快速开始

### 前置条件

- Docker 20.10+
- Docker Compose v2+
- JDK 8
- Maven 3.6+

### 一键启动

```bash
# 1. 构建项目
mvn clean package -DskipTests

# 2. 启动所有服务（包括中间件 + 监控）
docker-compose up -d --build

# 3. 查看服务状态
docker-compose ps
```

### 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| API 网关 | http://localhost:8090 | 统一入口 |
| Nacos 控制台 | http://localhost:8848/nacos | 用户名/密码: nacos/nacos |
| Grafana 监控 | http://localhost:3000 | 用户名/密码: admin/admin123 |
| Prometheus | http://localhost:9090 | 指标查询 |

### 初始化数据

```bash
# 数据库会自动创建，初始数据已在 SQL 中配置
# 包含一场秒杀活动：iPhone 15，库存 100 件
```

## 核心功能

### 1. 秒杀下单

```bash
# 发起秒杀请求
curl -X POST http://localhost:8090/api/seckill/1 \
  -H "X-User-Id: 10001"

# 响应示例
{
  "code": 200,
  "msg": "下单成功，请等待支付",
  "data": {
    "orderNo": "SK_1776685328284"
  }
}
```

### 2. 查询秒杀结果

```bash
curl http://localhost:8090/api/seckill/result/SK_1776685328284

# 响应示例
{
  "code": 200,
  "data": {
    "orderNo": "SK_1776685328284",
    "status": "PENDING",
    "activityName": "iPhone 15 限时秒杀",
    "seckillPrice": 3999.00
  }
}
```

### 3. 查询活动详情

```bash
curl http://localhost:8090/api/seckill/activity/1
```

## 架构设计

### 整体架构

```
用户请求
   ↓
CDN + 前端（静态资源缓存）
   ↓
API Gateway（限流 + 鉴权）
   ↓
┌──────────────┬──────────────┬──────────────┐
│ Seckill      │ Order        │ Payment      │
│ Service      │ Service      │ Service      │
│              │              │              │
│ Redis Lua    │ Kafka 消费   │ 支付回调     │
│ 原子扣库存   │ 创建订单     │              │
└──────┬───────┴──────┬───────┴──────┬───────┘
       │              │              │
   ┌───▼───┐      ┌───▼───┐      ┌──▼──┐
   │ Redis │      │ MySQL │      │ MQ  │
   └───────┘      └───────┘      └─────┘
```

### 核心设计

#### 1. 四层过滤架构

| 层级 | 组件 | 职责 | 过滤比例 |
|------|------|------|---------|
| L1 | CDN + 前端 | 静态资源缓存 | 50% |
| L2 | Gateway + Sentinel | 限流 + 鉴权 | 80% |
| L3 | Redis Lua | 库存预扣 | 99% |
| L4 | Kafka + MySQL | 异步落单 | 最终 100 个订单 |

**10 万请求 → 2 万 → 100 个成功 → MySQL 落单**

#### 2. 防超卖三道防线

```
第1道: Redis Lua 原子扣减（99.9% 并发场景）
第2道: MySQL 乐观锁兜底（quantity > 0）
第3道: 唯一索引防重复（activity_id + user_id）
```

#### 3. 异步削峰

```
同步: 10万请求 → 全部打 MySQL → 挂了
异步: 10万请求 → Redis 过滤 → 100个进 Kafka → 慢慢消费
```

## 性能测试

### 最新压测结果（2026-04-27）

| 并发数 | QPS | P50 | P99 | 成功率 | 说明 |
|--------|-----|-----|-----|--------|------|
| 50 | 3876 | 9ms | 62ms | 100% | JIT 预热 |
| 100 | **6711** | 13ms | 28ms | 100% | **最优并发** ⭐ |
| 200 | 6579 | 25ms | 57ms | 100% | 轻微下降 |
| 500 | 5000 | 68ms | 147ms | 100% | 高并发稳定 |

### 与目标对比

| 指标 | 目标 | 实际（100并发） | 达标 |
|------|------|----------------|------|
| QPS | ≥ 5000 | 6711 | ✅ **134%** |
| P99 延迟 | < 200ms | 28ms | ✅ **优于目标 86%** |
| 库存准确率 | 100% | 100% | ✅ |

### 性能提升对比

相比首次压测，性能大幅提升：

| 并发 | 首次 QPS | 本次 QPS | 提升幅度 | 首次 P99 | 本次 P99 | 延迟改善 |
|------|---------|---------|---------|---------|---------|---------|
| 50 | 1192 | 3876 | **+225%** | 419ms | 62ms | **-85%** |
| 100 | 3623 | 6711 | **+85%** | 53ms | 28ms | **-47%** |
| 200 | 3311 | 6579 | **+99%** | 86ms | 57ms | **-34%** |
| 500 | 1565 | 5000 | **+219%** | 551ms | 147ms | **-73%** |

**性能提升原因**：
- JVM 预热充分（JIT 编译优化生效）
- 连接池复用（Redis/Kafka 连接已建立）
- 系统缓存命中（文件系统、网络缓冲区、CPU 缓存预热）
- GC 优化（内存分配模式稳定）

### 详细压测报告

查看完整压测数据和分析：
- [压测文档索引](docs/00_压测文档索引.md) - 所有压测文档导航
- [最新压测报告](docs/05_压测报告_20260427.md) - 详细测试数据
- [压测对比分析](docs/05_压测对比分析.md) - 性能提升分析
- [性能趋势分析](docs/05_性能趋势分析.md) - 历史趋势和预测
- [结果可视化](docs/05_压测结果可视化_20260427.md) - 图表展示

### 运行压测

```bash
# 方式1: Java 压测工具（阶梯加压）
mvn compile -pl seckill-common
java -cp seckill-common/target/classes \
  com.kungfu.seckill.common.benchmark.SeckillBenchmark

# 方式2: Shell 快速验证
bash seckill-common/src/main/java/com/kungfu/seckill/common/benchmark/benchmark.sh 100 500
```

## 监控告警

### Grafana Dashboard

导入 `monitoring/grafana-dashboard.json` 查看：

- 请求 QPS / 延迟分布
- JVM 堆内存 / GC 统计
- Redis / MySQL / Kafka 指标
- 错误率 / 成功率

### 关键指标

| 指标 | PromQL | 告警阈值 |
|------|--------|----------|
| 请求 QPS | `rate(http_server_requests_seconds_count[1m])` | > 5000 |
| P99 延迟 | `histogram_quantile(0.99, ...)` | > 2s |
| 错误率 | `rate(...{status=~"5.."}[5m])` | > 1% |
| JVM 堆内存 | `jvm_memory_used_bytes{area="heap"}` | > 80% |

## 项目结构

```
w09-seckill/
├── docs/                          # 文档
│   ├── 00_压测文档索引.md         # 压测文档导航
│   ├── 01_架构设计.md
│   ├── 02_接口文档.md
│   ├── 05_压测报告.md             # 首次压测
│   ├── 05_压测报告_20260427.md   # 最新压测
│   ├── 05_压测对比分析.md         # 性能对比
│   ├── 05_性能趋势分析.md         # 趋势预测
│   └── 05_压测结果可视化_20260427.md  # 图表展示
├── monitoring/                    # 监控配置
│   ├── prometheus.yml
│   ├── grafana-dashboard.json
│   └── OPERATIONS.md
├── seckill-common/                # 公共模块
├── seckill-gateway/               # API 网关（8090）
├── seckill-service/               # 秒杀服务（8084）
├── seckill-order/                 # 订单服务（8085）
├── seckill-payment/               # 支付服务（8086）
├── docker-compose.yml             # 容器编排
└── pom.xml
```

## 常用命令

```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f seckill-service

# 重启服务
docker-compose restart seckill-service

# 停止所有服务
docker-compose down

# 停止并清除数据
docker-compose down -v
```

## 技术亮点

1. **Redis Lua 原子操作**: 保证库存扣减的原子性，QPS 10万+
2. **Kafka 异步削峰**: 保护 MySQL，实现最终一致性
3. **布隆过滤器**: 快速判断用户是否已购买，减少 Redis 查询
4. **Sentinel 限流降级**: 保护系统不被打垮
5. **分布式链路追踪**: 完整的可观测性体系
6. **容器化部署**: 一键启动，开箱即用

## 学习路径

本项目是 [Java 架构师核心能力速成计划](../2.路线%20（4个月）.md) 第三个月（W9-W12）的实战项目：

- **W9**: 架构设计 + 环境搭建
- **W10**: 核心功能开发
- **W11**: 性能优化 + 压测
- **W12**: 容器化部署 + 监控体系

## 面试要点

### 如何保证不超卖？

三道防线：
1. Redis Lua 原子扣减（主要防线）
2. MySQL 乐观锁 `quantity > 0`（兜底）
3. 唯一索引 `(activity_id, user_id)`（防重复购买）

### 如何应对高并发？

四层过滤 + 异步削峰：
- Gateway 限流挡住 80%
- Redis 预扣挡住 99%
- Kafka 异步下单保护 MySQL
- 最终只有真正成功的请求到达数据库

### 性能瓶颈在哪？

- Redis Lua: 10万+ QPS，不是瓶颈
- Kafka: 5万+ QPS，不是瓶颈
- MySQL: 通过 Kafka 削峰，实际 < 1000 QPS
- 本地单机资源竞争是主要限制因素

## 许可证

MIT License

## 作者

lizhenqing - Java 架构师学习路线实战项目