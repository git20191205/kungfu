# Spring 专题 — 高频面试问答

> 基于 w03-spring 项目经验整理的 20 个高频面试题

## 一、IOC 容器（5 题）

### 1. 什么是 IOC？有什么好处？

**答**：
- **定义**：控制反转，对象创建和依赖注入由容器管理
- **好处**：
  - 解耦：对象不需要自己创建依赖
  - 易测试：可以注入 Mock 对象
  - 易维护：集中管理对象生命周期

### 2. IOC 容器的初始化过程？

**答**：
1. **加载配置**：读取 XML / 注解
2. **解析 BeanDefinition**：解析 Bean 定义
3. **注册 BeanDefinition**：注册到 BeanFactory
4. **实例化 Bean**：反射创建对象
5. **依赖注入**：注入属性
6. **初始化**：调用 init-method

### 3. Bean 的作用域有哪些？

**答**：
- **singleton**：单例（默认）
- **prototype**：每次创建新实例
- **request**：每个 HTTP 请求一个实例
- **session**：每个 Session 一个实例

### 4. Bean 的生命周期？

**答**：
1. 实例化：反射创建对象
2. 属性赋值：依赖注入
3. Aware 接口：BeanNameAware、ApplicationContextAware
4. BeanPostProcessor.postProcessBeforeInitialization()
5. InitializingBean.afterPropertiesSet() / init-method
6. BeanPostProcessor.postProcessAfterInitialization()
7. 使用
8. DisposableBean.destroy() / destroy-method

### 5. @Autowired vs @Resource 的区别？

**答**：
- **@Autowired**：Spring 注解，按类型注入，可配合 @Qualifier 按名称
- **@Resource**：JDK 注解，按名称注入，找不到再按类型

## 二、AOP 切面（5 题）

### 6. 什么是 AOP？有什么用？

**答**：
- **定义**：面向切面编程，横切关注点（日志、事务、权限）
- **用途**：
  - 日志记录
  - 事务管理
  - 权限校验
  - 性能监控

### 7. AOP 的实现原理？

**答**：
- **JDK 动态代理**：基于接口，Proxy.newProxyInstance()
- **CGLIB 代理**：基于继承，生成子类

### 8. AOP 的通知类型有哪些？

**答**：
- **@Before**：前置通知
- **@After**：后置通知
- **@AfterReturning**：返回通知
- **@AfterThrowing**：异常通知
- **@Around**：环绕通知（最强大）

### 9. 什么是切点表达式？

**答**：
```java
@Pointcut("execution(* com.example.service.*.*(..))")
// execution(返回值 包名.类名.方法名(参数))

// 示例
execution(* com.example.service.*.*(..))  // service 包下所有方法
execution(public * *(..))                 // 所有 public 方法
execution(* save*(..))                    // save 开头的方法
```

### 10. AOP 失效的场景？

**答**：
- **自调用**：同一个类内部方法调用，AOP 失效
- **非 public 方法**：CGLIB 无法代理
- **final 方法**：CGLIB 无法代理

## 三、事务管理（5 题）

### 11. Spring 事务的传播行为有哪些？

**答**：
- **REQUIRED**：有事务加入，无事务新建（默认）
- **REQUIRES_NEW**：总是新建事务
- **SUPPORTS**：有事务加入，无事务非事务执行
- **NOT_SUPPORTED**：非事务执行，挂起当前事务
- **MANDATORY**：必须有事务，否则抛异常
- **NEVER**：必须非事务，否则抛异常
- **NESTED**：嵌套事务

### 12. Spring 事务的隔离级别有哪些？

**答**：
- **DEFAULT**：使用数据库默认隔离级别
- **READ_UNCOMMITTED**：读未提交
- **READ_COMMITTED**：读已提交
- **REPEATABLE_READ**：可重复读
- **SERIALIZABLE**：串行化

### 13. @Transactional 失效的场景？

**答**：
- **非 public 方法**：AOP 无法代理
- **自调用**：同一个类内部方法调用
- **异常被捕获**：事务不会回滚
- **非 RuntimeException**：默认只回滚 RuntimeException

### 14. 如何手动控制事务？

**答**：
```java
@Autowired
private TransactionTemplate transactionTemplate;

public void doSomething() {
    transactionTemplate.execute(status -> {
        try {
            // 业务逻辑
            return result;
        } catch (Exception e) {
            status.setRollbackOnly();
            throw e;
        }
    });
}
```

### 15. 分布式事务如何处理？

**答**：
- **2PC**：两阶段提交，强一致性，性能差
- **TCC**：Try-Confirm-Cancel，最终一致性
- **Saga**：长事务，补偿机制
- **本地消息表**：最终一致性
- **MQ 事务消息**：RocketMQ / Kafka

## 四、Spring Boot（5 题）

### 16. Spring Boot 的自动配置原理？

**答**：
1. **@SpringBootApplication** = @Configuration + @EnableAutoConfiguration + @ComponentScan
2. **@EnableAutoConfiguration**：加载 META-INF/spring.factories
3. **@Conditional**：条件注解，满足条件才生效

### 17. Spring Boot 的启动流程？

**答**：
1. 创建 SpringApplication
2. 准备环境（Environment）
3. 创建 ApplicationContext
4. 刷新容器（refresh）
5. 调用 Runner（CommandLineRunner / ApplicationRunner）

### 18. 如何自定义 Starter？

**答**：
1. 创建 Maven 项目：xxx-spring-boot-starter
2. 创建自动配置类：XxxAutoConfiguration
3. 创建 META-INF/spring.factories
4. 配置条件注解：@ConditionalOnClass / @ConditionalOnProperty

### 19. Spring Boot 的配置优先级？

**答**（从高到低）：
1. 命令行参数
2. SPRING_APPLICATION_JSON
3. ServletConfig / ServletContext
4. JNDI
5. System.getProperties()
6. 操作系统环境变量
7. application-{profile}.properties
8. application.properties
9. @PropertySource
10. 默认属性

### 20. 秒杀系统的 Spring 优化经验？

**答**（基于 w09-seckill 项目）：
```java
// 1. 异步处理
@Async
@Transactional
public void createOrder(Long activityId, Long userId) {
    // 创建订单
}

// 2. 缓存
@Cacheable(value = "activity", key = "#id")
public Activity getById(Long id) {
    return activityMapper.selectById(id);
}

// 3. 事务优化
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void deductStock(Long activityId) {
    // 扣库存（独立事务）
}

// 4. 连接池配置
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000

// 效果：QPS 6711，P99 28ms
```

## 五、总结

Spring 面试核心考点：
1. **IOC 容器**：初始化过程、Bean 生命周期、作用域
2. **AOP 切面**：实现原理、通知类型、切点表达式
3. **事务管理**：传播行为、隔离级别、失效场景
4. **Spring Boot**：自动配置、启动流程、自定义 Starter

**面试技巧**：
- 结合项目经验（w09-seckill）
- 说出具体配置（连接池 50、异步处理）
- 展示优化思路（缓存、异步、事务优化）

**下一步**：D108 数据库专题面试问答
