# 秒杀系统运维手册

## 1. 系统架构

```
用户 → Gateway(8090) → seckill-service(8084) / seckill-order(8085) / seckill-payment(8086)
                            ↓                        ↓                       ↓
                     Redis / MySQL / Kafka / Nacos
                            ↓
                  Prometheus(9090) → Grafana(3000)
```

## 2. 快速启动

### 2.1 前置条件
- Docker 20.10+
- Docker Compose v2+
- JDK 8（用于本地构建）
- Maven 3.6+

### 2.2 构建与启动

```bash
# 1. 构建所有服务 JAR
mvn clean package -DskipTests

# 2. 一键启动全部容器
docker-compose up -d --build

# 3. 查看启动状态
docker-compose ps
```

### 2.3 启动顺序
docker-compose 已通过 depends_on + healthcheck 保证：
1. MySQL / Redis / Zookeeper 先启动
2. Kafka 等待 Zookeeper 就绪
3. Nacos 启动并通过健康检查
4. 业务服务等待 MySQL、Redis、Kafka、Nacos 全部就绪后启动
5. Prometheus / Grafana 最后启动

## 3. 服务端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| seckill-gateway | 8090 | API 网关 |
| seckill-service | 8084 | 秒杀核心服务 |
| seckill-order | 8085 | 订单服务 |
| seckill-payment | 8086 | 支付服务 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| Kafka | 9092 | 消息队列 |
| Nacos | 8848 | 注册/配置中心 |
| Prometheus | 9090 | 指标采集 |
| Grafana | 3000 | 监控面板 |

## 4. 健康检查

### 4.1 Actuator 端点
每个业务服务均暴露以下端点：

```bash
# 健康状态
curl http://localhost:8084/actuator/health

# Prometheus 指标
curl http://localhost:8084/actuator/prometheus

# 服务信息
curl http://localhost:8084/actuator/info
```

### 4.2 批量健康检查脚本

```bash
for port in 8084 8085 8086 8090; do
  echo "=== Port $port ==="
  curl -s http://localhost:$port/actuator/health | head -1
  echo ""
done
```

## 5. 监控告警

### 5.1 Prometheus
- 访问: http://localhost:9090
- 配置文件: `monitoring/prometheus.yml`
- 采集间隔: 15s

### 5.2 Grafana
- 访问: http://localhost:3000
- 默认账号: admin / admin123
- 导入 Dashboard: `monitoring/grafana-dashboard.json`

### 5.3 关键监控指标

| 指标 | PromQL | 告警阈值 |
|------|--------|----------|
| 请求 QPS | `rate(http_server_requests_seconds_count[1m])` | > 5000 |
| P99 延迟 | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` | > 2s |
| 错误率 | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | > 1% |
| JVM 堆内存 | `jvm_memory_used_bytes{area="heap"}` | > 80% |
| GC 暂停 | `rate(jvm_gc_pause_seconds_sum[5m])` | > 500ms |

## 6. 常用运维命令

```bash
# 查看所有容器状态
docker-compose ps

# 查看某服务日志
docker-compose logs -f seckill-service

# 重启单个服务
docker-compose restart seckill-service

# 停止所有服务
docker-compose down

# 停止并清除数据卷
docker-compose down -v

# 扩容服务实例（需去掉 container_name）
docker-compose up -d --scale seckill-service=3
```

## 7. 故障排查

### 7.1 服务无法启动
```bash
# 检查容器日志
docker-compose logs seckill-service

# 检查依赖服务是否就绪
docker-compose exec mysql mysqladmin ping -h localhost
docker-compose exec redis redis-cli ping
```

### 7.2 Nacos 注册失败
```bash
# 检查 Nacos 是否健康
curl http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10

# 检查网络连通性
docker-compose exec seckill-service ping nacos
```

### 7.3 Kafka 消息积压
```bash
# 查看消费者组 lag
docker-compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group <consumer-group>
```

## 8. 数据备份

```bash
# MySQL 备份
docker-compose exec mysql mysqldump -u root -p123456 kungfu_seckill > backup.sql

# Redis 备份（触发 RDB）
docker-compose exec redis redis-cli BGSAVE
```
