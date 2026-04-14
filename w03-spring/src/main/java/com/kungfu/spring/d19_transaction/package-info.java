/**
 * D19 - 事务源码
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、@Transactional 核心组件</h3>
 * <pre>
 * ┌──────────────────────────────────────┬────────────────────────────────┐
 * │ 组件                                 │ 作用                           │
 * ├──────────────────────────────────────┼────────────────────────────────┤
 * │ @Transactional                       │ 声明式事务注解                 │
 * │ TransactionInterceptor               │ 事务拦截器（AOP Advice）       │
 * │ BeanFactoryTransactionAttributeSource│ 解析 @Transactional 元数据     │
 * │ TransactionAttribute                 │ 事务属性（隔离级别、传播等）   │
 * │ PlatformTransactionManager           │ 事务管理器（顶级接口）         │
 * │ → DataSourceTransactionManager       │ JDBC 事务管理器                │
 * │ → JpaTransactionManager              │ JPA 事务管理器                 │
 * │ TransactionSynchronizationManager    │ 事务同步管理（ThreadLocal）    │
 * │ TransactionStatus                    │ 当前事务状态                   │
 * │ TransactionDefinition                │ 事务定义（属性）               │
 * └──────────────────────────────────────┴────────────────────────────────┘
 * </pre>
 *
 * <h3>二、事务执行流程</h3>
 * <pre>
 * 调用 @Transactional 方法：
 *
 *   AOP 代理 → TransactionInterceptor.invoke()
 *     ├── getTransactionAttribute()        ← 获取事务属性
 *     ├── determineTransactionManager()    ← 确定事务管理器
 *     ├── createTransactionIfNecessary()   ← 开启事务（或加入）
 *     │     ├── txManager.getTransaction(txDef)
 *     │     ├── DataSourceUtils.getConnection()
 *     │     └── TransactionSynchronizationManager
 *     │           .bindResource(dataSource, connHolder)   ← 绑定到 ThreadLocal
 *     │
 *     ├── 执行业务方法
 *     │
 *     ├── 正常：commitTransactionAfterReturning()
 *     │     └── txManager.commit(status)
 *     │
 *     └── 异常：completeTransactionAfterThrowing()
 *           ├── 是否需要回滚？
 *           ├── 是 → txManager.rollback(status)
 *           └── 否 → txManager.commit(status)
 * </pre>
 *
 * <h3>三、7 种传播行为</h3>
 * <pre>
 * ┌──────────────────┬──────────────┬──────────────────────┐
 * │ 传播行为         │ 有事务        │ 无事务               │
 * ├──────────────────┼──────────────┼──────────────────────┤
 * │ REQUIRED（默认） │ 加入          │ 新建                 │
 * │ REQUIRES_NEW     │ 挂起，新建    │ 新建                 │
 * │ NESTED           │ 嵌套保存点    │ 新建                 │
 * │ SUPPORTS         │ 加入          │ 非事务执行           │
 * │ NOT_SUPPORTED    │ 挂起          │ 非事务执行           │
 * │ MANDATORY        │ 加入          │ 抛异常               │
 * │ NEVER            │ 抛异常        │ 非事务执行           │
 * └──────────────────┴──────────────┴──────────────────────┘
 * </pre>
 *
 * <h3>四、事务失效场景</h3>
 * <pre>
 * 1. this 自调用      → 不走代理
 *    解决: 注入自身代理 / AopContext.currentProxy()
 *
 * 2. 非 public 方法   → 默认只代理 public
 *    解决: 改为 public
 *
 * 3. final / static   → 无法被代理
 *
 * 4. checked 异常     → 默认不回滚
 *    解决: rollbackFor = Exception.class
 *
 * 5. try-catch 吞异常 → 事务不感知
 *    解决: TransactionAspectSupport.currentTransactionStatus()
 *            .setRollbackOnly()
 *
 * 6. 非 Spring Bean   → 手动 new 无代理
 *    解决: 改用 @Component
 *
 * 7. 多线程           → 子线程脱离事务（ThreadLocal 不共享）
 *    解决: 拆分事务边界
 * </pre>
 *
 * <h3>五、隔离级别</h3>
 * <pre>
 * ┌──────────────────┬──────┬──────┬──────┬──────┐
 * │ 隔离级别         │ 脏读 │不可重复读│ 幻读 │ 备注 │
 * ├──────────────────┼──────┼──────┼──────┼──────┤
 * │ READ_UNCOMMITTED │  √   │  √   │  √   │      │
 * │ READ_COMMITTED   │  ✗   │  √   │  √   │Oracle默认│
 * │ REPEATABLE_READ  │  ✗   │  ✗   │  √   │MySQL默认 │
 * │ SERIALIZABLE     │  ✗   │  ✗   │  ✗   │ 串行  │
 * └──────────────────┴──────┴──────┴──────┴──────┘
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>SpringBoot 自动装配 —— 事务管理器、数据源等如何被自动配置。</p>
 *
 * @author kungfu
 * @since D19
 */
package com.kungfu.spring.d19_transaction;
