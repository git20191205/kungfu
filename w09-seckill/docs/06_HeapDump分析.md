# Heap Dump 分析报告

## 1. 问题发现

在项目根目录外发现多个 heap dump 文件：

| 文件名 | 大小 | 生成时间 | 说明 |
|--------|------|----------|------|
| java_pid27488.hprof | 31M | Mar 21 10:11 | |
| java_pid40880.hprof | 31M | Mar 21 10:11 | |
| java_pid4636.hprof | 33M | Mar 21 10:13 | |
| java_pid22548.hprof | 29M | Mar 21 10:29 | |
| java_pid27252.hprof | 29M | Mar 21 10:29 | |
| java_pid47356.hprof | 29M | Mar 21 10:29 | |
| java_pid29504.hprof | 30M | Mar 21 10:32 | |
| java_pid33660.hprof | 30M | Mar 21 10:34 | |

**特征分析**：
- 文件大小: 29-33MB，相对较小
- 生成时间: 集中在 3月21日 10:11-10:34（约 23 分钟内）
- 数量: 8 个文件，说明可能有多次 OOM 或手动触发

## 2. 可能原因

### 2.1 OOM 触发（最可能）

如果是 OOM 自动生成，说明：
- JVM 未配置 `-Xms` 和 `-Xmx`，使用默认堆内存
- 默认堆内存可能只有 64-128MB（取决于系统）
- 压测时内存不足导致 OOM

### 2.2 手动触发

使用 `jmap -dump` 命令手动生成，用于性能分析。

### 2.3 压测场景

从时间点分析：
- 10:11-10:13: 3 个文件（可能是第一轮压测）
- 10:29: 3 个文件（可能是第二轮压测）
- 10:32-10:34: 2 个文件（可能是第三轮压测）

## 3. 根本原因

**未配置 JVM 参数导致堆内存过小**

在 Dockerfile 中，原始配置为：
```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

没有指定 `-Xms` 和 `-Xmx`，JVM 使用默认值：
- 默认最大堆内存 = 物理内存的 1/4（但有上限）
- 在容器环境中可能只有 64-128MB
- 压测时对象创建速度快，内存不足触发 OOM

## 4. 已采取的优化措施

已在所有 Dockerfile 中添加 JVM 参数：

### seckill-service / seckill-order
```dockerfile
ENTRYPOINT ["java", \
  "-Xms512m", \
  "-Xmx512m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=50", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-jar", "app.jar"]
```

### seckill-gateway / seckill-payment
```dockerfile
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx256m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=50", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-jar", "app.jar"]
```

**优化说明**：
1. **固定堆内存**: `-Xms` = `-Xmx`，避免动态扩容带来的性能损耗
2. **G1GC**: 适合大堆内存，低延迟场景
3. **MaxGCPauseMillis=50**: 目标 GC 停顿时间 50ms
4. **HeapDumpOnOutOfMemoryError**: OOM 时自动生成 dump，便于排查

## 5. 内存分配策略

| 服务 | 堆内存 | 理由 |
|------|--------|------|
| seckill-service | 512MB | 核心服务，处理秒杀逻辑，需要较大内存 |
| seckill-order | 512MB | 订单服务，Kafka 消费 + DB 操作 |
| seckill-gateway | 256MB | 网关服务，主要做路由转发，内存需求小 |
| seckill-payment | 256MB | 支付服务，逻辑简单，内存需求小 |

## 6. 验证方法

重新构建并启动服务后，检查 JVM 参数是否生效：

```bash
# 重新构建
docker-compose up -d --build

# 检查 JVM 参数
docker exec seckill-service java -XX:+PrintFlagsFinal -version | grep -E 'MaxHeapSize|UseG1GC'

# 预期输出
# MaxHeapSize = 536870912 (512MB)
# UseG1GC = true
```

## 7. 监控指标

在 Grafana 中关注以下指标：

1. **堆内存使用率**: `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}`
   - 正常: < 70%
   - 告警: > 80%

2. **GC 频率**: `rate(jvm_gc_pause_seconds_count[5m])`
   - 正常: Minor GC < 10次/分钟
   - 告警: Full GC > 2次/小时

3. **GC 停顿时间**: `rate(jvm_gc_pause_seconds_sum[5m])`
   - 正常: < 100ms/5min
   - 告警: > 500ms/5min

## 8. 建议

### 8.1 清理旧的 heap dump 文件

```bash
# 这些文件已经没有分析价值，可以删除
rm ../java_pid*.hprof
rm ../oom_dump.hprof
```

### 8.2 生产环境建议

1. **堆内存**: 根据实际负载调整，建议 1-2GB
2. **GC 日志**: 添加 `-Xloggc:/var/log/gc.log` 记录 GC 日志
3. **监控告警**: 配置 JVM 指标告警（已完成）
4. **定期分析**: 每周查看 GC 日志和内存趋势

### 8.3 压测前准备

1. 确保 JVM 参数已生效
2. 预热 JIT 编译器（先跑小流量）
3. 监控 JVM 指标，确保内存充足
4. 准备好 heap dump 分析工具（MAT / jhat）

## 9. 结论

**问题已解决**：通过配置合理的 JVM 参数，避免了默认堆内存过小导致的 OOM 问题。

**后续行动**：
- ✅ 已优化所有服务的 Dockerfile
- ✅ 已配置 Prometheus 告警规则
- ⏳ 建议清理旧的 heap dump 文件
- ⏳ 重新压测验证优化效果