# Nacos 连接失败 - 快速排查指南

## 问题现象

Gateway 启动时报错：
```
com.alibaba.nacos.api.exception.NacosException: Client not connected, current status:STARTING
```

## 根本原因

**Nacos 服务未启动或未完全就绪**

## 解决步骤

### 1. 检查 Nacos 是否启动

```bash
# 检查端口
netstat -an | findstr "8848"
```

如果没有输出，说明 Nacos 未启动。

### 2. 启动 Nacos

```bash
cd D:\nacos\bin
startup.cmd -m standalone
```

### 3. 等待 Nacos 完全启动（重要！）

**必须等待 30-60 秒**，Nacos 需要时间初始化。

### 4. 验证 Nacos 就绪

#### 方法1: 浏览器访问
打开 http://localhost:8848/nacos

能看到登录页面 = Nacos 已就绪

#### 方法2: 健康检查接口
```bash
curl http://localhost:8848/nacos/v1/console/health/readiness
```

返回 `UP` 或 HTTP 200 = Nacos 已就绪

#### 方法3: 查看日志
```bash
# 查看 Nacos 启动日志
type D:\nacos\logs\start.out
```

看到 `Nacos started successfully` = 启动成功

### 5. 重新启动 Gateway

确认 Nacos 完全就绪后，再启动 Gateway：

```bash
cd D:\workspace\kungfu\w09-seckill
mvn spring-boot:run -pl seckill-gateway
```

## 启动顺序（重要）

```
1. MySQL     → 立即可用
2. Redis     → 立即可用
3. Kafka     → 立即可用
4. Nacos     → 启动后等待 30-60 秒 ⚠️
5. Gateway   → 等 Nacos 就绪后启动
6. Service   → 等 Nacos 就绪后启动
7. Order     → 等 Nacos 就绪后启动
8. Payment   → 等 Nacos 就绪后启动
```

## 验证 Nacos 连接成功

Gateway 启动后，查看日志应该看到：

```
INFO c.a.c.n.registry.NacosServiceRegistry : nacos registry, seckill-gateway 192.168.x.x:8090 register finished
```

或者在 Nacos 控制台查看：
1. 访问 http://localhost:8848/nacos
2. 登录（nacos/nacos）
3. 服务管理 → 服务列表
4. 应该看到 `seckill-gateway` 已注册

## 其他可能的问题

### 问题1: Nacos 端口被占用

```bash
# 检查 8848 端口
netstat -ano | findstr "8848"

# 如果被占用，找到进程 PID，然后结束进程
taskkill /F /PID <PID>
```

### 问题2: Nacos 配置错误

检查 `seckill-gateway/src/main/resources/application.yml`：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848  # 确保地址正确
```

### 问题3: 防火墙阻止

临时关闭防火墙测试：
```bash
# Windows 防火墙
netsh advfirewall set allprofiles state off
```

## 快速验证脚本

创建一个批处理文件 `check-nacos.bat`：

```batch
@echo off
echo 检查 Nacos 状态...
echo.

echo 1. 检查端口 8848
netstat -an | findstr "8848" | findstr "LISTENING"
if %errorlevel% neq 0 (
    echo [失败] Nacos 端口未监听
    exit /b 1
)
echo [成功] 端口 8848 正在监听
echo.

echo 2. 检查健康状态
curl -s http://localhost:8848/nacos/v1/console/health/readiness
echo.

echo 3. 检查控制台
curl -s -o nul -w "HTTP Status: %%{http_code}\n" http://localhost:8848/nacos
echo.

echo Nacos 检查完成！
```

运行：
```bash
check-nacos.bat
```

## 总结

**关键点：必须等待 Nacos 完全启动（30-60秒）后再启动微服务！**

不要看到 Nacos 窗口出现就立即启动微服务，要等待初始化完成。