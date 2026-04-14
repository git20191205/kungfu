package com.kungfu.spring.d18_aop;

import java.lang.reflect.*;
import java.util.*;

/**
 * 【Demo】手写动态代理 + 拦截器链
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>JDK 动态代理的实现原理</li>
 *   <li>手写 MethodInterceptor 拦截器链（模拟 AOP 的责任链模式）</li>
 *   <li>通知类型（Before / After / Around / AfterReturning / AfterThrowing）的执行顺序</li>
 *   <li>拦截器链的递归调用过程</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>AOP 是 Spring 的核心能力之一，理解代理和拦截器链是掌握 AOP 的关键</li>
 *   <li>Spring 事务 @Transactional、缓存 @Cacheable 等都基于 AOP 实现</li>
 *   <li>面试常问：AOP 的底层原理是什么？拦截器链如何执行？</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D18 - Spring源码精髓
 */
public class MiniAopDemo {

    // ==================== 业务接口 ====================

    interface OrderService {
        String createOrder(String productId) throws Exception;
    }

    static class OrderServiceImpl implements OrderService {
        @Override
        public String createOrder(String productId) throws Exception {
            System.out.println("      [业务逻辑] 创建订单: productId=" + productId);
            if ("error".equals(productId)) {
                throw new RuntimeException("创建订单失败！");
            }
            return "ORDER-" + System.currentTimeMillis();
        }
    }

    // ==================== 拦截器接口 ====================

    /** 方法拦截器（对应 Spring 的 MethodInterceptor） */
    interface MiniMethodInterceptor {
        Object invoke(MiniMethodInvocation invocation) throws Throwable;
    }

    /** 方法调用链（对应 Spring 的 ReflectiveMethodInvocation） */
    static class MiniMethodInvocation {
        private final Object target;
        private final Method method;
        private final Object[] args;
        private final List<MiniMethodInterceptor> interceptors;
        private int currentIndex = -1;

        MiniMethodInvocation(Object target, Method method, Object[] args,
                             List<MiniMethodInterceptor> interceptors) {
            this.target = target;
            this.method = method;
            this.args = args;
            this.interceptors = interceptors;
        }

        /** 递归调用下一个拦截器，所有拦截器都执行完后调用目标方法 */
        public Object proceed() throws Throwable {
            // 所有拦截器都执行完了，调用目标方法
            if (++currentIndex == interceptors.size()) {
                return method.invoke(target, args);
            }
            // 调用下一个拦截器
            return interceptors.get(currentIndex).invoke(this);
        }

        public Method getMethod() { return method; }
        public Object[] getArgs() { return args; }
    }

    // ==================== 5 种通知对应的拦截器 ====================

    /** @Before 前置通知 */
    static class BeforeInterceptor implements MiniMethodInterceptor {
        @Override
        public Object invoke(MiniMethodInvocation invocation) throws Throwable {
            System.out.println("    [Before] 方法执行前: " + invocation.getMethod().getName());
            return invocation.proceed();
        }
    }

    /** @After 后置通知（无论是否异常都执行） */
    static class AfterInterceptor implements MiniMethodInterceptor {
        @Override
        public Object invoke(MiniMethodInvocation invocation) throws Throwable {
            try {
                return invocation.proceed();
            } finally {
                System.out.println("    [After] 方法执行后（finally）: " + invocation.getMethod().getName());
            }
        }
    }

    /** @Around 环绕通知 */
    static class AroundInterceptor implements MiniMethodInterceptor {
        @Override
        public Object invoke(MiniMethodInvocation invocation) throws Throwable {
            System.out.println("    [Around-前] 环绕通知开始");
            long start = System.currentTimeMillis();
            Object result = invocation.proceed();
            long cost = System.currentTimeMillis() - start;
            System.out.println("    [Around-后] 环绕通知结束，耗时=" + cost + "ms");
            return result;
        }
    }

    /** @AfterReturning 返回通知 */
    static class AfterReturningInterceptor implements MiniMethodInterceptor {
        @Override
        public Object invoke(MiniMethodInvocation invocation) throws Throwable {
            Object result = invocation.proceed();
            System.out.println("    [AfterReturning] 方法正常返回: " + result);
            return result;
        }
    }

    /** @AfterThrowing 异常通知 */
    static class AfterThrowingInterceptor implements MiniMethodInterceptor {
        @Override
        public Object invoke(MiniMethodInvocation invocation) throws Throwable {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                System.out.println("    [AfterThrowing] 方法抛出异常: " + ex.getMessage());
                throw ex;
            }
        }
    }

    // ==================== 代理工厂 ====================

    @SuppressWarnings("unchecked")
    static <T> T createProxy(T target, Class<T> interfaceType,
                              List<MiniMethodInterceptor> interceptors) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class[]{interfaceType},
                (proxy, method, args) -> {
                    // 创建调用链
                    MiniMethodInvocation invocation =
                            new MiniMethodInvocation(target, method, args, interceptors);
                    return invocation.proceed();
                }
        );
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   手写动态代理 + 拦截器链 Demo");
        System.out.println("========================================\n");

        // 一、AOP 核心概念
        showCoreConcepts();

        // 二、拦截器链执行流程
        demonstrateInterceptorChain();

        // 三、异常场景
        demonstrateExceptionCase();

        // 四、通知执行顺序
        showAdviceOrder();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. AOP 底层基于动态代理：有接口用 JDK Proxy，无接口用 CGLIB");
        System.out.println("  2. Spring AOP 的拦截器链通过 ReflectiveMethodInvocation 递归执行");
        System.out.println("  3. 5 种通知类型执行顺序（同一切面内）：");
        System.out.println("     Around前 → Before → 目标方法 → AfterReturning → After → Around后");
        System.out.println("  4. AOP 代理在 BeanPostProcessor.postProcessAfterInitialization 中创建");
        System.out.println("  5. AnnotationAwareAspectJAutoProxyCreator 是负责创建 AOP 代理的 BPP");
        System.out.println("  6. 同一目标方法有多个切面时，按 @Order 排序");
        System.out.println();
    }

    // ==================== 一、核心概念 ====================

    private static void showCoreConcepts() {
        System.out.println("=== 一、AOP 核心概念 ===\n");

        System.out.println("  ┌────────────────┬──────────────────────────────────────────────┐");
        System.out.println("  │ 概念           │ 说明                                         │");
        System.out.println("  ├────────────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ Aspect（切面）  │ 横切关注点的模块化（如日志、事务、权限）     │");
        System.out.println("  │ JoinPoint      │ 程序执行的连接点（Spring 仅支持方法级别）    │");
        System.out.println("  │ Advice（通知） │ 在连接点执行的具体动作                       │");
        System.out.println("  │ Pointcut       │ 匹配连接点的表达式（决定对哪些方法增强）     │");
        System.out.println("  │ Weaving（织入）│ 将切面代码应用到目标对象的过程               │");
        System.out.println("  │ Target         │ 被代理的原始对象                             │");
        System.out.println("  │ Proxy          │ 织入切面后生成的代理对象                     │");
        System.out.println("  └────────────────┴──────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、拦截器链 ====================

    private static void demonstrateInterceptorChain() {
        System.out.println("=== 二、拦截器链执行流程（正常场景） ===\n");

        OrderServiceImpl target = new OrderServiceImpl();
        List<MiniMethodInterceptor> chain = Arrays.asList(
                new AroundInterceptor(),
                new BeforeInterceptor(),
                new AfterReturningInterceptor(),
                new AfterInterceptor()
        );

        OrderService proxy = createProxy(target, OrderService.class, chain);

        System.out.println("  调用代理对象的 createOrder(\"P001\"):\n");
        try {
            String result = proxy.createOrder("P001");
            System.out.println("\n  最终返回值: " + result);
        } catch (Exception e) {
            System.out.println("  异常: " + e.getMessage());
        }

        System.out.println();
        System.out.println("  拦截器链执行过程（递归模型）：");
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │ AroundInterceptor.invoke()                          │");
        System.out.println("  │   ├── [Around-前]                                   │");
        System.out.println("  │   ├── invocation.proceed()                          │");
        System.out.println("  │   │   ├── BeforeInterceptor.invoke()                │");
        System.out.println("  │   │   │   ├── [Before]                              │");
        System.out.println("  │   │   │   └── invocation.proceed()                  │");
        System.out.println("  │   │   │       ├── AfterReturningInterceptor.invoke()│");
        System.out.println("  │   │   │       │   ├── invocation.proceed()          │");
        System.out.println("  │   │   │       │   │   └── ...                       │");
        System.out.println("  │   │   │       │   │       └── target.createOrder()  │");
        System.out.println("  │   │   │       │   └── [AfterReturning]              │");
        System.out.println("  │   │   │       └── ...                               │");
        System.out.println("  │   └── [Around-后]                                   │");
        System.out.println("  └─────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 三、异常场景 ====================

    private static void demonstrateExceptionCase() {
        System.out.println("=== 三、拦截器链执行流程（异常场景） ===\n");

        OrderServiceImpl target = new OrderServiceImpl();
        List<MiniMethodInterceptor> chain = Arrays.asList(
                new AroundInterceptor(),
                new BeforeInterceptor(),
                new AfterThrowingInterceptor(),
                new AfterInterceptor()
        );

        OrderService proxy = createProxy(target, OrderService.class, chain);

        System.out.println("  调用代理对象的 createOrder(\"error\"):\n");
        try {
            proxy.createOrder("error");
        } catch (Exception e) {
            System.out.println("\n  最终抛出异常: " + e.getCause().getMessage());
        }
        System.out.println();
    }

    // ==================== 四、通知执行顺序 ====================

    private static void showAdviceOrder() {
        System.out.println("=== 四、Spring AOP 通知执行顺序 ===\n");

        System.out.println("  正常返回时：");
        System.out.println("  ┌────┬──────────────────────────┐");
        System.out.println("  │ #  │ 执行内容                 │");
        System.out.println("  ├────┼──────────────────────────┤");
        System.out.println("  │ 1  │ @Around（前半段）         │");
        System.out.println("  │ 2  │ @Before                  │");
        System.out.println("  │ 3  │ 目标方法执行             │");
        System.out.println("  │ 4  │ @AfterReturning          │");
        System.out.println("  │ 5  │ @After（finally）        │");
        System.out.println("  │ 6  │ @Around（后半段）         │");
        System.out.println("  └────┴──────────────────────────┘");
        System.out.println();

        System.out.println("  异常时：");
        System.out.println("  ┌────┬──────────────────────────┐");
        System.out.println("  │ #  │ 执行内容                 │");
        System.out.println("  ├────┼──────────────────────────┤");
        System.out.println("  │ 1  │ @Around（前半段）         │");
        System.out.println("  │ 2  │ @Before                  │");
        System.out.println("  │ 3  │ 目标方法抛出异常         │");
        System.out.println("  │ 4  │ @AfterThrowing           │");
        System.out.println("  │ 5  │ @After（finally）        │");
        System.out.println("  └────┴──────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 手写了拦截器链后，下一步：
 * → JDK Proxy 与 CGLIB 的区别？（见 ProxyPatternDemo.java）
 * → 真实 Spring AOP 如何使用？（见 AopDemo.java）
 */
