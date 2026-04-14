/**
 * D16 - Bean 生命周期
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、Bean 生命周期 4 阶段</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                  Bean 完整生命周期                       │
 * │                                                         │
 * │  1. 实例化 ────────── 调用构造方法                      │
 * │       ↓                                                 │
 * │  2. 属性填充 ──────── @Autowired、@Value 注入           │
 * │       ↓               + Aware 接口回调                  │
 * │  3. 初始化 ──────── BPP前置 → @PostConstruct            │
 * │       ↓             → afterPropertiesSet → init-method  │
 * │       ↓             → BPP后置（AOP代理）                │
 * │  ★ Bean 就绪 ★                                         │
 * │       ↓                                                 │
 * │  4. 销毁 ──────── @PreDestroy → destroy()               │
 * │                    → destroy-method                     │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>二、Aware 接口族</h3>
 * <pre>
 * ┌──────────────────────────┬──────────────────────────────┐
 * │ Aware 接口               │ 注入内容                     │
 * ├──────────────────────────┼──────────────────────────────┤
 * │ BeanNameAware            │ Bean 在容器中的名称          │
 * │ BeanClassLoaderAware     │ 加载 Bean 的 ClassLoader     │
 * │ BeanFactoryAware         │ BeanFactory 引用             │
 * │ EnvironmentAware         │ Environment 环境变量         │
 * │ ApplicationContextAware  │ ApplicationContext 容器引用  │
 * │ ResourceLoaderAware      │ ResourceLoader 资源加载器    │
 * │ MessageSourceAware       │ MessageSource 国际化         │
 * │ ApplicationEventPublisherAware │ 事件发布器             │
 * └──────────────────────────┴──────────────────────────────┘
 *
 * 注意：BeanNameAware / BeanFactoryAware 在 invokeAwareMethods() 中回调
 *       其余 Aware 通过 ApplicationContextAwareProcessor（BPP）回调
 * </pre>
 *
 * <h3>三、BeanPostProcessor vs BeanFactoryPostProcessor</h3>
 * <pre>
 * BeanFactoryPostProcessor (BFPP):
 *   时机: Bean 实例化之前
 *   操作: BeanDefinition（元数据）
 *   用途: 修改属性、改变作用域、占位符替换
 *
 * BeanPostProcessor (BPP):
 *   时机: Bean 初始化前后
 *   操作: Bean 实例（对象）
 *   用途: AOP代理、@Autowired注入、@Async处理
 *
 * 重要内置 BPP:
 *   AutowiredAnnotationBeanPostProcessor    → @Autowired
 *   CommonAnnotationBeanPostProcessor       → @PostConstruct / @PreDestroy
 *   AnnotationAwareAspectJAutoProxyCreator  → AOP 代理
 * </pre>
 *
 * <h3>四、源码关键方法调用链</h3>
 * <pre>
 * AbstractBeanFactory.getBean()
 *   └── doGetBean()
 *         └── getSingleton()  ← 先查一级缓存
 *               └── createBean()
 *                     └── doCreateBean()
 *                           ├── createBeanInstance()      ← 实例化
 *                           ├── populateBean()            ← 属性填充
 *                           └── initializeBean()          ← 初始化
 *                                 ├── invokeAwareMethods()
 *                                 ├── applyBPPBeforeInit()
 *                                 ├── invokeInitMethods()
 *                                 └── applyBPPAfterInit() ← AOP代理
 * </pre>
 *
 * <h3>五、下一步</h3>
 * <p>循环依赖 —— 当两个 Bean 互相依赖时，Spring 如何通过三级缓存解决。</p>
 *
 * @author kungfu
 * @since D16
 */
package com.kungfu.spring.d16_bean_lifecycle;
