package com.kungfu.spring.d19_transaction;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 【Demo】手写事务管理器（模拟 @Transactional 的 AOP 实现）
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>自定义 @MiniTransactional 注解</li>
 *   <li>用 ThreadLocal 模拟连接绑定（对应 TransactionSynchronizationManager）</li>
 *   <li>用动态代理 + 拦截器实现事务切面</li>
 *   <li>演示 begin / commit / rollback 的完整流程</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>@Transactional 是 Spring AOP 的经典应用，理解它的实现原理是综合考察</li>
 *   <li>手写可以帮助理解事务传播行为、连接复用、异常回滚等核心概念</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D19 - Spring源码精髓
 */
public class MiniTransactionDemo {

    // ==================== 自定义事务注解 ====================

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniTransactional {
        Class<? extends Throwable>[] rollbackFor() default {RuntimeException.class};
    }

    // ==================== 模拟连接 ====================

    /** 模拟数据库连接 —— 对应 java.sql.Connection */
    static class MockConnection {
        private final int id;
        private boolean inTransaction = false;

        MockConnection(int id) { this.id = id; }

        public void beginTransaction() {
            this.inTransaction = true;
            System.out.println("    [Connection-" + id + "] BEGIN TRANSACTION");
        }

        public void commit() {
            this.inTransaction = false;
            System.out.println("    [Connection-" + id + "] COMMIT");
        }

        public void rollback() {
            this.inTransaction = false;
            System.out.println("    [Connection-" + id + "] ROLLBACK");
        }

        public void executeSql(String sql) {
            System.out.println("    [Connection-" + id + "] 执行 SQL: " + sql
                    + " (inTx=" + inTransaction + ")");
        }

        public int getId() { return id; }
    }

    // ==================== 事务管理器 ====================

    /**
     * 迷你事务管理器 —— 对应 DataSourceTransactionManager
     * 核心思想：用 ThreadLocal 保存当前线程的连接，保证方法调用期间使用同一连接
     */
    static class MiniTransactionManager {

        /** 模拟 TransactionSynchronizationManager 的 resources 字段 */
        private static final ThreadLocal<MockConnection> CURRENT_CONNECTION = new ThreadLocal<>();

        /** 模拟事务嵌套计数（用于传播行为） */
        private static final ThreadLocal<Integer> TX_DEPTH = ThreadLocal.withInitial(() -> 0);

        private static int connectionIdCounter = 0;

        /** 获取当前线程绑定的连接，如果没有则创建新的 */
        public static MockConnection getConnection() {
            MockConnection conn = CURRENT_CONNECTION.get();
            if (conn == null) {
                conn = new MockConnection(++connectionIdCounter);
                CURRENT_CONNECTION.set(conn);
                System.out.println("    [TxManager] 为当前线程创建新连接 Connection-" + conn.getId());
            } else {
                System.out.println("    [TxManager] 复用当前线程的连接 Connection-" + conn.getId());
            }
            return conn;
        }

        /** 开启事务 */
        public static MockConnection begin() {
            int depth = TX_DEPTH.get();
            MockConnection conn = getConnection();
            if (depth == 0) {
                conn.beginTransaction();
                System.out.println("    [TxManager] 开启新事务（外层）");
            } else {
                System.out.println("    [TxManager] 加入已有事务（嵌套depth=" + depth + "）");
            }
            TX_DEPTH.set(depth + 1);
            return conn;
        }

        /** 提交事务 */
        public static void commit() {
            int depth = TX_DEPTH.get() - 1;
            TX_DEPTH.set(depth);
            MockConnection conn = CURRENT_CONNECTION.get();
            if (depth == 0 && conn != null) {
                conn.commit();
                CURRENT_CONNECTION.remove();
                TX_DEPTH.remove();
                System.out.println("    [TxManager] 提交事务（外层），清理连接");
            } else {
                System.out.println("    [TxManager] 内层事务方法返回，等待外层提交");
            }
        }

        /** 回滚事务 */
        public static void rollback() {
            MockConnection conn = CURRENT_CONNECTION.get();
            if (conn != null) {
                conn.rollback();
                CURRENT_CONNECTION.remove();
                TX_DEPTH.remove();
                System.out.println("    [TxManager] 回滚事务");
            }
        }
    }

    // ==================== 事务代理工厂 ====================

    @SuppressWarnings("unchecked")
    static <T> T createTxProxy(T target, Class<T> interfaceType) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class[]{interfaceType},
                (proxy, method, args) -> {
                    // 获取目标方法上的 @MiniTransactional 注解
                    Method targetMethod = target.getClass()
                            .getMethod(method.getName(), method.getParameterTypes());
                    MiniTransactional tx = targetMethod.getAnnotation(MiniTransactional.class);

                    if (tx == null) {
                        // 无事务注解，直接调用
                        return method.invoke(target, args);
                    }

                    // ★ 事务拦截逻辑（对应 TransactionInterceptor.invoke()）
                    System.out.println("  → 进入事务切面: " + method.getName() + "()");
                    MiniTransactionManager.begin();

                    try {
                        Object result = method.invoke(target, args);
                        MiniTransactionManager.commit();
                        System.out.println("  ← 离开事务切面: " + method.getName() + "() [正常]");
                        return result;
                    } catch (Throwable ex) {
                        Throwable cause = (ex instanceof java.lang.reflect.InvocationTargetException)
                                ? ex.getCause() : ex;
                        // 判断是否需要回滚
                        if (shouldRollback(cause, tx.rollbackFor())) {
                            MiniTransactionManager.rollback();
                            System.out.println("  ← 离开事务切面: " + method.getName()
                                    + "() [回滚: " + cause.getClass().getSimpleName() + "]");
                        } else {
                            MiniTransactionManager.commit();
                            System.out.println("  ← 离开事务切面: " + method.getName()
                                    + "() [非回滚异常，已提交]");
                        }
                        throw cause;
                    }
                }
        );
    }

    private static boolean shouldRollback(Throwable ex, Class<? extends Throwable>[] rollbackFor) {
        for (Class<? extends Throwable> clazz : rollbackFor) {
            if (clazz.isAssignableFrom(ex.getClass())) return true;
        }
        return false;
    }

    // ==================== 业务接口和实现 ====================

    interface AccountService {
        void transfer(String from, String to, double amount);
        void badTransfer(String from, String to);
    }

    static class AccountServiceImpl implements AccountService {

        @MiniTransactional
        @Override
        public void transfer(String from, String to, double amount) {
            MockConnection conn = MiniTransactionManager.getConnection();
            conn.executeSql("UPDATE account SET balance=balance-" + amount + " WHERE name='" + from + "'");
            conn.executeSql("UPDATE account SET balance=balance+" + amount + " WHERE name='" + to + "'");
        }

        @MiniTransactional
        @Override
        public void badTransfer(String from, String to) {
            MockConnection conn = MiniTransactionManager.getConnection();
            conn.executeSql("UPDATE account SET balance=balance-100 WHERE name='" + from + "'");
            // 模拟异常，应该回滚
            throw new RuntimeException("网络异常！");
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   手写事务管理器 Demo");
        System.out.println("========================================\n");

        // 一、事务管理核心原理
        showPrinciple();

        // 二、正常转账（事务提交）
        AccountService service = createTxProxy(new AccountServiceImpl(), AccountService.class);

        System.out.println("=== 二、正常转账（事务应提交） ===\n");
        service.transfer("Alice", "Bob", 100.0);
        System.out.println();

        // 三、异常转账（事务回滚）
        System.out.println("=== 三、异常转账（事务应回滚） ===\n");
        try {
            service.badTransfer("Alice", "Bob");
        } catch (Exception e) {
            System.out.println("  [业务层] 捕获异常: " + e.getMessage());
        }
        System.out.println();

        // 四、Spring 源码映射
        showSpringMapping();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. @Transactional 基于 AOP 实现，本质是方法拦截");
        System.out.println("  2. 核心组件:");
        System.out.println("     TransactionInterceptor      → 事务拦截器（对应拦截器链中的一环）");
        System.out.println("     PlatformTransactionManager  → 事务管理器接口");
        System.out.println("     TransactionSynchronizationManager → ThreadLocal 连接绑定");
        System.out.println("  3. 连接通过 ThreadLocal 绑定到当前线程，保证方法内使用同一连接");
        System.out.println("  4. 默认只有 RuntimeException 和 Error 才回滚，checked Exception 不回滚");
        System.out.println("  5. 需要 checked 异常回滚要配置 rollbackFor = Exception.class");
        System.out.println();
    }

    // ==================== 一、核心原理 ====================

    private static void showPrinciple() {
        System.out.println("=== 一、事务管理核心原理 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │  业务调用流程                                            │");
        System.out.println("  │                                                         │");
        System.out.println("  │  业务方法 → AOP 代理 → 事务拦截器                       │");
        System.out.println("  │                        ├── 1. begin (获取连接/开启事务)│");
        System.out.println("  │                        ├── 2. 执行目标方法              │");
        System.out.println("  │                        ├── 3a. 正常 → commit           │");
        System.out.println("  │                        └── 3b. 异常 → rollback         │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  关键机制：ThreadLocal 保存连接");
        System.out.println("    → 当前线程的多次 DAO 调用共享同一连接");
        System.out.println("    → 保证多个 SQL 在同一事务中");
        System.out.println();
    }

    // ==================== 四、Spring 源码映射 ====================

    private static void showSpringMapping() {
        System.out.println("=== 四、Mini 实现与 Spring 源码映射 ===\n");

        System.out.println("  ┌──────────────────────────────┬────────────────────────────────────────────┐");
        System.out.println("  │ Mini 实现                    │ Spring 真实实现                            │");
        System.out.println("  ├──────────────────────────────┼────────────────────────────────────────────┤");
        System.out.println("  │ @MiniTransactional           │ @Transactional                             │");
        System.out.println("  │ MiniTransactionManager       │ PlatformTransactionManager                 │");
        System.out.println("  │                              │ → DataSourceTransactionManager（JDBC）     │");
        System.out.println("  │                              │ → JpaTransactionManager                    │");
        System.out.println("  │ CURRENT_CONNECTION           │ TransactionSynchronizationManager.resources│");
        System.out.println("  │ (ThreadLocal)                │ (ThreadLocal&lt;Map&lt;Object, Object&gt;&gt;)         │");
        System.out.println("  │ 事务代理 InvocationHandler   │ TransactionInterceptor                     │");
        System.out.println("  │ begin/commit/rollback        │ TransactionStatus / TransactionDefinition  │");
        System.out.println("  └──────────────────────────────┴────────────────────────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 手写了事务管理器后，下一步：
 * → 真实 Spring @Transactional 怎么用？（见 TransactionDemo.java）
 * → 事务的 7 种传播行为？（见 TransactionPropagationDemo.java）
 */
