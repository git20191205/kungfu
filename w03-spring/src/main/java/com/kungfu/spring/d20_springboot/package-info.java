/**
 * D20 - SpringBoot 自动装配
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、@SpringBootApplication 分解</h3>
 * <pre>
 * @SpringBootApplication
 *   ├── @SpringBootConfiguration
 *   │     └── @Configuration
 *   ├── @EnableAutoConfiguration    ← 自动装配入口
 *   │     └── @Import(AutoConfigurationImportSelector.class)
 *   └── @ComponentScan              ← 包扫描
 * </pre>
 *
 * <h3>二、自动装配完整流程</h3>
 * <pre>
 * 1. SpringApplication.run()
 *    └── refreshContext()
 *        └── invokeBeanFactoryPostProcessors()
 *            └── ConfigurationClassPostProcessor  ← BFPP
 *                └── 解析 @Configuration 类
 *                    └── 处理 @Import 注解
 *
 * 2. AutoConfigurationImportSelector.selectImports()
 *    └── getAutoConfigurationEntry()
 *        ├── getCandidateConfigurations()
 *        │   └── SpringFactoriesLoader.loadFactoryNames()
 *        │       └── 读取 META-INF/spring.factories
 *        │           → key: EnableAutoConfiguration
 *        │           → values: 所有自动配置类全限定名
 *        ├── removeDuplicates()               ← 去重
 *        ├── getExclusions()                  ← 排除
 *        └── filter(configurations, filter)   ← @Conditional 过滤
 *            └── OnClassCondition
 *            └── OnBeanCondition
 *            └── OnPropertyCondition
 *
 * 3. 过滤后的 AutoConfiguration 类被注册为 BeanDefinition
 *    → 参与正常的 Bean 生命周期
 * </pre>
 *
 * <h3>三、@Conditional 注解族</h3>
 * <pre>
 * ┌─────────────────────────────┬────────────────────────────────┐
 * │ 注解                        │ 条件                           │
 * ├─────────────────────────────┼────────────────────────────────┤
 * │ @ConditionalOnClass         │ classpath 有指定类             │
 * │ @ConditionalOnMissingClass  │ classpath 无指定类             │
 * │ @ConditionalOnBean          │ 容器中有指定 Bean              │
 * │ @ConditionalOnMissingBean   │ 容器中无指定 Bean              │
 * │ @ConditionalOnProperty      │ 配置属性匹配                   │
 * │ @ConditionalOnResource      │ classpath 有指定资源           │
 * │ @ConditionalOnWebApplication│ 是 Web 应用                    │
 * │ @ConditionalOnExpression    │ SpEL 表达式为 true             │
 * └─────────────────────────────┴────────────────────────────────┘
 * </pre>
 *
 * <h3>四、自定义 Starter 三件套</h3>
 * <pre>
 * 1. xxx-spring-boot-starter（空壳 pom，聚合依赖）
 * 2. xxx-spring-boot-autoconfigure（自动配置逻辑）
 *    ├── XxxAutoConfiguration.java
 *    │   @Configuration
 *    │   @ConditionalOnClass(XxxService.class)
 *    │   @EnableConfigurationProperties(XxxProperties.class)
 *    │   public class XxxAutoConfiguration {
 *    │       @Bean
 *    │       @ConditionalOnMissingBean
 *    │       public XxxService xxxService(XxxProperties props) { }
 *    │   }
 *    ├── XxxProperties.java
 *    │   @ConfigurationProperties(prefix = "xxx")
 *    └── META-INF/spring.factories
 *        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
 *        com.xxx.XxxAutoConfiguration
 * 3. 使用方只需引入 starter 依赖
 * </pre>
 *
 * <h3>五、spring.factories → imports 演进</h3>
 * <pre>
 * Boot 2.x: META-INF/spring.factories
 *   → key-value 格式，多个值逗号分隔
 *   → 所有类型共用一个文件
 *
 * Boot 3.x: META-INF/spring/xxx.imports
 *   → 纯文本，每行一个类名
 *   → 按类型拆分为独立文件
 *   → 更清晰，解析更快
 * </pre>
 *
 * <h3>六、W03 完结总结</h3>
 * <pre>
 * D15 IoC 容器 → BeanDefinition + BeanFactory + refresh() 12步
 * D16 Bean 生命周期 → 4阶段 + BPP 扩展点
 * D17 循环依赖 → 三级缓存 + AOP 提前暴露
 * D18 AOP → JDK/CGLIB 代理 + 拦截器链
 * D19 事务 → @Transactional + TransactionInterceptor + 7种传播
 * D20 SpringBoot → 自动装配 + @Conditional 条件过滤
 *
 * 核心线索：
 *   IoC → Bean生命周期 → BPP → AOP代理 → 事务拦截 → 自动装配
 *   所有高级特性都建立在 IoC + BPP 扩展机制之上
 * </pre>
 *
 * @author kungfu
 * @since D20
 */
package com.kungfu.spring.d20_springboot;
