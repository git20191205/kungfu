/**
 * D15 - IoC 容器启动流程
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、IoC 与 DI</h3>
 * <pre>
 * IoC（Inversion of Control）控制反转：
 *   传统方式 —— 程序员 new 对象，手动组装依赖
 *   IoC 方式 —— 容器创建对象，自动注入依赖
 *
 * DI（Dependency Injection）依赖注入：
 *   ├── 构造器注入（推荐，不可变）
 *   ├── Setter 注入
 *   └── 字段注入（@Autowired，最方便但不推荐用于核心 Bean）
 * </pre>
 *
 * <h3>二、Spring 容器核心组件</h3>
 * <pre>
 * ┌───────────────────────────────────────────────────────┐
 * │               ApplicationContext                       │
 * │  ┌─────────────────────────────────────────────────┐  │
 * │  │           DefaultListableBeanFactory             │  │
 * │  │  ┌─────────────────────────────────────────┐    │  │
 * │  │  │ beanDefinitionMap (BeanDefinition 注册表)│    │  │
 * │  │  ├─────────────────────────────────────────┤    │  │
 * │  │  │ singletonObjects  (一级缓存: 成品 Bean) │    │  │
 * │  │  │ earlySingletonObjects (二级缓存: 半成品) │    │  │
 * │  │  │ singletonFactories (三级缓存: ObjectFactory)│ │  │
 * │  │  └─────────────────────────────────────────┘    │  │
 * │  └─────────────────────────────────────────────────┘  │
 * │  + MessageSource (国际化)                              │
 * │  + ApplicationEventMulticaster (事件广播)              │
 * │  + ResourceLoader (资源加载)                           │
 * │  + Environment (环境变量/配置)                         │
 * └───────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>三、refresh() 12 步流程</h3>
 * <pre>
 * AbstractApplicationContext.refresh() 方法执行流程：
 *
 *  1. prepareRefresh()                 → 设置启动时间、激活标志
 *  2. obtainFreshBeanFactory()         → 获取/刷新 BeanFactory
 *  3. prepareBeanFactory(bf)           → 配置类加载器、SpEL、Aware
 *  4. postProcessBeanFactory(bf)       → 子类定制（模板方法）
 *  5. invokeBeanFactoryPostProcessors  → ★ 调用 BFPP（修改BD）
 *  6. registerBeanPostProcessors       → 注册 BPP（Bean增强）
 *  7. initMessageSource()              → 国际化
 *  8. initApplicationEventMulticaster  → 事件广播器
 *  9. onRefresh()                      → 子类扩展
 * 10. registerListeners()              → 注册监听器
 * 11. finishBeanFactoryInitialization  → ★ 预实例化所有单例
 * 12. finishRefresh()                  → 发布 ContextRefreshedEvent
 *
 * 关键步骤：
 *   第5步 —— ConfigurationClassPostProcessor 在此处理 @Configuration、@Bean、
 *            @ComponentScan、@Import 等注解
 *   第11步 —— getBean() → doGetBean() → createBean() → doCreateBean()
 *            触发完整的 Bean 生命周期
 * </pre>
 *
 * <h3>四、BeanDefinition 核心属性</h3>
 * <pre>
 * ┌──────────────────────┬──────────────────────────────────┐
 * │ 属性                 │ 含义                             │
 * ├──────────────────────┼──────────────────────────────────┤
 * │ beanClassName        │ Bean 的全限定类名                │
 * │ scope                │ 作用域（singleton/prototype）    │
 * │ lazyInit             │ 是否延迟加载                     │
 * │ dependsOn            │ 依赖的其他 Bean                  │
 * │ autowireCandidate    │ 是否作为自动装配的候选           │
 * │ primary              │ 是否为首选 Bean                  │
 * │ initMethodName       │ 自定义初始化方法名               │
 * │ destroyMethodName    │ 自定义销毁方法名                 │
 * │ constructorArgValues │ 构造器参数值                     │
 * │ propertyValues       │ 属性值                           │
 * └──────────────────────┴──────────────────────────────────┘
 * </pre>
 *
 * <h3>五、下一步</h3>
 * <p>Bean 生命周期 —— 从 BeanDefinition 到可用 Bean，经历了哪些阶段和扩展点。</p>
 *
 * @author kungfu
 * @since D15
 */
package com.kungfu.spring.d15_ioc_startup;
