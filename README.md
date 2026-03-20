# Kungfu — Java 架构师修炼工程

从 JVM 底层到分布式架构，六个月系统化学习 Java 架构师核心技能。

## 学习路线概览

| 周次 | 模块 | 主题 | 关键内容 |
|------|------|------|----------|
| W01 | `w01-jvm` | JVM 实战调优 | 内存模型、GC 算法、调优实战、OOM 排查 |
| W02 | `w02-concurrent` | 并发编程核心 | 线程池、锁机制、AQS、并发容器 |
| W03 | `w03-spring` | Spring 源码精髓 | IoC、AOP、事务、SpringBoot 自动装配 |
| W04 | `w04-mysql-redis` | MySQL + Redis | 索引优化、事务与锁、缓存策略 |
| W05 | `w05-distributed` | 分布式基础 | CAP、分布式锁/ID/事务、一致性算法 |
| W06 | `w06-microservice` | 微服务架构 | 注册发现、熔断限流、网关、配置中心 |
| W07 | `w07-mq` | 消息队列 | RabbitMQ/Kafka、可靠投递、顺序消息 |
| W08-10 | `project-order` | 分布式订单系统 | 综合实战项目 |
| W11-16 | `project-seckill` | 电商秒杀系统 | 高并发、多级缓存、全链路压测 |
| W17-19 | `w17-system-design` | 系统设计 | 架构方法论、高可用、可扩展设计 |

## 环境要求

- JDK 8+
- Maven 3.6+
- Docker & Docker Compose（中间件环境）
- IDE：IntelliJ IDEA（推荐）

## 快速开始

```bash
# 1. 编译整个工程
mvn clean compile

# 2. 启动中间件（MySQL + Redis）
docker-compose up -d

# 3. 运行第一个 Demo
cd w01-jvm
mvn exec:java -Dexec.mainClass="com.kungfu.jvm.d1_memory_model.MemoryAreaDemo"
```

## 项目结构

```
kungfu/
├── pom.xml                 ← 父 POM（依赖版本统一管理）
├── w01-jvm/                ← W01：JVM 实战调优
├── w02-concurrent/         ← W02：并发编程核心
├── w03-spring/             ← W03：Spring 源码精髓
├── w04-mysql-redis/        ← W04：MySQL + Redis 实战
├── w05-distributed/        ← W05：分布式基础
├── w06-microservice/       ← W06：微服务架构
├── w07-mq/                 ← W07：消息队列
├── project-order/          ← W08-10：分布式订单系统
├── project-seckill/        ← W11-16：电商秒杀系统
├── w17-system-design/      ← W17-19：系统设计
├── docs/                   ← 学习文档
├── docker-compose.yml      ← 中间件环境
└── README.md               ← 本文件
```

## 学习原则

- **知其然知其所以然**：每个知识点都要理解底层原理
- **代码驱动**：每个概念都有可运行的 Demo
- **精而简**：Demo 精炼、通俗易懂、能举一反三
- **遵循规范**：代码遵循阿里巴巴 Java 开发手册
