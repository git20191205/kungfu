# W03: Spring源码精髓 (D15-D20)

## 学习目标

- IoC容器启动流程（refresh() 12步）
- Bean生命周期（4阶段 + 扩展点）
- 循环依赖（三级缓存解决原理）
- AOP实现原理（动态代理 + 拦截器链）
- 事务管理（@Transactional 源码 + 传播行为）
- SpringBoot自动装配（SPI + @Conditional）

## 模块结构

| Day | 主题 | 核心内容 |
|-----|------|---------|
| D15 | IoC容器启动 | 手写Mini-IoC + refresh()流程追踪 |
| D16 | Bean生命周期 | 手写4阶段模拟 + BeanPostProcessor |
| D17 | 循环依赖 | 手写三级缓存 + Spring真实场景 |
| D18 | AOP原理 | 手写动态代理链 + @Aspect实战 |
| D19 | 事务源码 | 手写事务管理器 + @Transactional + 传播行为 |
| D20 | SpringBoot | 手写自动装配 + spring.factories机制 |

## 技术栈

- Spring Framework 5.3.31
- Spring Boot 2.7.18
- AspectJ 1.9.7
- H2 Database（事务Demo）
- CGLIB（代理Demo）
