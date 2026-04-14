/**
 * D17 - 循环依赖与三级缓存
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、三级缓存结构</h3>
 * <pre>
 * DefaultSingletonBeanRegistry 核心字段：
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ Map&lt;String, Object&gt; singletonObjects      ← 一级：成品 Bean          │
 * │ Map&lt;String, Object&gt; earlySingletonObjects ← 二级：半成品 Bean        │
 * │ Map&lt;String, ObjectFactory&gt; singletonFactories ← 三级：ObjectFactory │
 * │ Set&lt;String&gt; singletonsInCreation          ← 正在创建的 Bean 名集合   │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * getSingleton(beanName, allowEarlyReference) 查找流程：
 *   一级命中 → 返回成品
 *   一级未命中 且 正在创建中
 *     → 二级命中 → 返回半成品
 *     → 三级命中 → 执行 ObjectFactory.getObject()
 *                → 结果放入二级缓存
 *                → 清除三级缓存条目
 * </pre>
 *
 * <h3>二、循环依赖解决过程（A 依赖 B，B 依赖 A）</h3>
 * <pre>
 * 1. getBean("A")
 *    ├── createBean("A")
 *    │   ├── 实例化 A（半成品）
 *    │   ├── addSingletonFactory("A", () -> A)  ← 放入三级缓存
 *    │   └── populate(A)
 *    │       └── getBean("B")
 *    │           ├── createBean("B")
 *    │           │   ├── 实例化 B（半成品）
 *    │           │   ├── addSingletonFactory("B", () -> B)
 *    │           │   └── populate(B)
 *    │           │       └── getBean("A")
 *    │           │           ├── 一级缓存没有
 *    │           │           ├── "A" 正在创建中
 *    │           │           ├── 二级缓存没有
 *    │           │           └── 三级缓存命中! 执行 ObjectFactory
 *    │           │               → 升级到二级缓存
 *    │           │               → 返回 A 的早期引用
 *    │           ├── B 初始化完成
 *    │           └── B 放入一级缓存
 *    │       └── A.b = B
 *    ├── A 初始化完成
 *    └── A 放入一级缓存
 * </pre>
 *
 * <h3>三、为什么需要三级缓存？</h3>
 * <pre>
 * 如果只有一级缓存：无法处理循环依赖
 *
 * 如果只有两级缓存（一级 + 二级）：
 *   没有 AOP 时够用，但有 AOP 时不够
 *   因为 AOP 代理在 BPP 后置回调中生成，属性注入时还不是代理对象
 *
 * 三级缓存的 ObjectFactory：
 *   → getEarlyBeanReference(beanName, beanDef, bean)
 *   → 如果 Bean 需要被代理（匹配 AOP 切面），提前生成代理
 *   → 保证循环依赖方拿到的是代理对象，而非原始对象
 * </pre>
 *
 * <h3>四、无法解决的场景</h3>
 * <pre>
 * ┌────────────────────────┬─────────────────────────────────────────┐
 * │ 场景                   │ 原因                                    │
 * ├────────────────────────┼─────────────────────────────────────────┤
 * │ 构造器循环依赖         │ 实例化未完成 → 无法放入三级缓存         │
 * │ prototype 循环依赖     │ 不走单例缓存                            │
 * │ @Async 循环依赖        │ @Async 代理在初始化后生成，三级缓存拿到 │
 * │                        │ 的是原始对象                            │
 * │ 跨 @Scope 循环         │ 不同作用域之间无统一缓存                │
 * └────────────────────────┴─────────────────────────────────────────┘
 *
 * 解决方案：
 *   1. @Lazy —— 生成代理，延迟实际注入
 *   2. 重构代码 —— 消除循环依赖（推荐）
 *   3. ObjectProvider&lt;T&gt; —— 方法级延迟获取
 *   4. ApplicationContextAware —— 手动从容器获取（反模式）
 * </pre>
 *
 * <h3>五、Spring 6 / Boot 2.6+ 的变化</h3>
 * <pre>
 * 默认禁用循环依赖：
 *   spring.main.allow-circular-references=false
 *
 * 原因：
 *   - 循环依赖是设计缺陷的信号
 *   - Spring 官方认为应该通过重构消除
 *   - 三级缓存实现复杂，性能有损
 *
 * 开启方式（不推荐）：
 *   spring.main.allow-circular-references=true
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>AOP 原理 —— 理解动态代理与拦截器链，看三级缓存如何配合 AOP 工作。</p>
 *
 * @author kungfu
 * @since D17
 */
package com.kungfu.spring.d17_circular_dependency;
