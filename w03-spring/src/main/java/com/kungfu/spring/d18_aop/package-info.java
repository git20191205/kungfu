/**
 * D18 - AOP 原理
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、AOP 核心概念</h3>
 * <pre>
 * ┌────────────────┬──────────────────────────────────────────┐
 * │ 概念           │ 说明                                     │
 * ├────────────────┼──────────────────────────────────────────┤
 * │ Aspect（切面） │ 横切关注点的模块化                       │
 * │ JoinPoint      │ 程序执行的连接点（方法调用）             │
 * │ Advice（通知） │ 在连接点执行的增强逻辑                   │
 * │ Pointcut       │ 匹配连接点的谓词/表达式                  │
 * │ Target         │ 被代理的原始对象                         │
 * │ Proxy          │ 织入切面后生成的代理对象                 │
 * │ Weaving        │ 将切面应用到目标对象的过程               │
 * └────────────────┴──────────────────────────────────────────┘
 * </pre>
 *
 * <h3>二、5 种通知类型</h3>
 * <pre>
 * ┌─────────────────┬─────────────────────────────────────────┐
 * │ 通知类型        │ 说明                                     │
 * ├─────────────────┼─────────────────────────────────────────┤
 * │ @Before         │ 方法执行前                               │
 * │ @After          │ 方法执行后（无论正常/异常，类似finally） │
 * │ @AfterReturning │ 方法正常返回后                           │
 * │ @AfterThrowing  │ 方法抛出异常后                           │
 * │ @Around         │ 包围目标方法，可控制是否执行             │
 * └─────────────────┴─────────────────────────────────────────┘
 *
 * Spring 5 执行顺序（同一切面内）：
 *   正常: Around前 → Before → 目标方法 → AfterReturning → After → Around后
 *   异常: Around前 → Before → 目标方法 → AfterThrowing  → After
 * </pre>
 *
 * <h3>三、JDK Proxy vs CGLIB</h3>
 * <pre>
 * ┌──────────────┬─────────────────────────┬──────────────────────┐
 * │ 对比项       │ JDK Proxy               │ CGLIB                │
 * ├──────────────┼─────────────────────────┼──────────────────────┤
 * │ 原理         │ 基于接口（Proxy + IH）  │ 基于继承（ASM字节码）│
 * │ 要求         │ 目标类必须有接口        │ 不能是 final 类      │
 * │ 代理对象     │ 接口实现类              │ 目标类的子类         │
 * │ SpringBoot   │ 需配置                  │ 默认（2.0+）         │
 * └──────────────┴─────────────────────────┴──────────────────────┘
 * </pre>
 *
 * <h3>四、AOP 代理创建流程</h3>
 * <pre>
 * 1. @EnableAspectJAutoProxy
 *    → 注册 AnnotationAwareAspectJAutoProxyCreator（BPP）
 *
 * 2. Bean 创建过程中：
 *    initializeBean() → applyBeanPostProcessorsAfterInitialization()
 *    → AnnotationAwareAspectJAutoProxyCreator.postProcessAfterInitialization()
 *
 * 3. 代理创建过程：
 *    wrapIfNecessary(bean, beanName, cacheKey)
 *    ├── getAdvicesAndAdvisorsForBean()     ← 查找匹配的增强器
 *    │   ├── findEligibleAdvisors()
 *    │   └── 按 @Order 排序
 *    └── createProxy()
 *        ├── proxyFactory.setTarget(bean)
 *        ├── proxyFactory.addAdvisors(...)
 *        └── proxyFactory.getProxy()
 *            ├── JdkDynamicAopProxy.getProxy()     ← 有接口
 *            └── CglibAopProxy.getProxy()           ← 无接口/proxyTargetClass
 *
 * 4. 拦截器链执行：
 *    ReflectiveMethodInvocation.proceed()
 *    → 递归调用 interceptorOrInterceptionAdvice.invoke(this)
 *    → 所有拦截器执行完后调用 target.method()
 * </pre>
 *
 * <h3>五、常见 AOP 失效场景</h3>
 * <pre>
 * 1. this 调用（自调用）—— 不走代理
 *    解决: 注入自身代理 / AopContext.currentProxy()
 *
 * 2. private / static / final 方法 —— 无法被代理
 *    解决: 改为 public 方法
 *
 * 3. Bean 未被 Spring 管理 —— 手动 new 的对象无代理
 *    解决: 使用 @Component / @Bean 注册
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>事务管理 —— 基于 AOP 的 @Transactional 实现原理。</p>
 *
 * @author kungfu
 * @since D18
 */
package com.kungfu.spring.d18_aop;
