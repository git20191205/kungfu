package com.kungfu.spring.d18_aop;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

import java.lang.reflect.Proxy;

/**
 * 【Demo】JDK 动态代理 vs CGLIB 代理
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>JDK 动态代理：基于接口，利用 java.lang.reflect.Proxy</li>
 *   <li>CGLIB 代理：基于子类继承，利用 ASM 字节码生成</li>
 *   <li>两种代理方式的适用场景和性能差异</li>
 *   <li>Spring 如何选择代理方式</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>面试必问：Spring AOP 用的是 JDK Proxy 还是 CGLIB？</li>
 *   <li>理解代理选择策略才能解释为什么 final 类不能被代理</li>
 *   <li>SpringBoot 2.0+ 默认使用 CGLIB（proxyTargetClass=true）</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D18 - Spring源码精髓
 */
public class ProxyPatternDemo {

    // ==================== 接口和实现（JDK 代理需要接口） ====================

    interface PayService {
        String pay(String orderId, double amount);
    }

    static class PayServiceImpl implements PayService {
        @Override
        public String pay(String orderId, double amount) {
            System.out.println("    [PayService] 执行支付: orderId=" + orderId + ", amount=" + amount);
            return "PAY-SUCCESS";
        }
    }

    // ==================== 无接口的类（CGLIB 代理） ====================

    static class NotificationService {
        public String send(String message) {
            System.out.println("    [NotificationService] 发送通知: " + message);
            return "SENT";
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   JDK 动态代理 vs CGLIB 代理 Demo");
        System.out.println("========================================\n");

        // 一、JDK 动态代理
        demonstrateJdkProxy();

        // 二、CGLIB 代理
        demonstrateCglibProxy();

        // 三、对比表
        showComparison();

        // 四、Spring 代理选择策略
        showSpringProxyStrategy();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. JDK Proxy: 基于接口，反射调用 InvocationHandler，Java 原生");
        System.out.println("  2. CGLIB: 基于子类继承，ASM 生成字节码，第三方库");
        System.out.println("  3. Spring 5 / SpringBoot 2.x+ 默认 proxyTargetClass=true（CGLIB）");
        System.out.println("  4. CGLIB 无法代理 final 类和 final 方法");
        System.out.println("  5. JDK Proxy 的代理对象实现了目标接口，可以强转为接口类型");
        System.out.println("  6. CGLIB 代理对象是目标类的子类，可以强转为目标类类型");
        System.out.println("  7. 性能: JDK 17+ 中 JDK Proxy 已经优化很好，差距不大");
        System.out.println();
    }

    // ==================== 一、JDK 动态代理 ====================

    private static void demonstrateJdkProxy() {
        System.out.println("=== 一、JDK 动态代理 ===\n");
        System.out.println("  条件：目标类必须实现接口");
        System.out.println("  原理：运行时生成 $Proxy0 类，实现目标接口，内部委托 InvocationHandler\n");

        PayServiceImpl target = new PayServiceImpl();

        PayService proxy = (PayService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class[]{PayService.class},
                (proxyObj, method, args) -> {
                    System.out.println("    [JDK Proxy] 前置: " + method.getName() + " 开始");
                    Object result = method.invoke(target, args);
                    System.out.println("    [JDK Proxy] 后置: " + method.getName() + " 结束");
                    return result;
                }
        );

        System.out.println("  调用代理方法:");
        String result = proxy.pay("ORD-001", 99.9);
        System.out.println("  返回: " + result);
        System.out.println();

        System.out.println("  代理对象信息:");
        System.out.println("    代理类名: " + proxy.getClass().getName());
        System.out.println("    是否为接口实例: " + (proxy instanceof PayService));
        System.out.println("    是否为实现类实例: " + (proxy instanceof PayServiceImpl));
        System.out.println();
    }

    // ==================== 二、CGLIB 代理 ====================

    private static void demonstrateCglibProxy() {
        System.out.println("=== 二、CGLIB 代理 ===\n");
        System.out.println("  条件：目标类不能是 final 类");
        System.out.println("  原理：运行时通过 ASM 生成目标类的子类，重写目标方法\n");

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(NotificationService.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
            System.out.println("    [CGLIB Proxy] 前置: " + method.getName() + " 开始");
            Object result = methodProxy.invokeSuper(obj, args);
            System.out.println("    [CGLIB Proxy] 后置: " + method.getName() + " 结束");
            return result;
        });

        NotificationService proxy = (NotificationService) enhancer.create();

        System.out.println("  调用代理方法:");
        String result = proxy.send("订单已发货");
        System.out.println("  返回: " + result);
        System.out.println();

        System.out.println("  代理对象信息:");
        System.out.println("    代理类名: " + proxy.getClass().getName());
        System.out.println("    是否为原始类实例: " + (proxy instanceof NotificationService));
        System.out.println("    父类: " + proxy.getClass().getSuperclass().getSimpleName());
        System.out.println();
    }

    // ==================== 三、对比 ====================

    private static void showComparison() {
        System.out.println("=== 三、JDK Proxy vs CGLIB 对比 ===\n");

        System.out.println("  ┌──────────────┬─────────────────────────┬───────────────────────────┐");
        System.out.println("  │ 对比项       │ JDK 动态代理            │ CGLIB 代理                │");
        System.out.println("  ├──────────────┼─────────────────────────┼───────────────────────────┤");
        System.out.println("  │ 实现方式     │ 基于接口（Proxy类）     │ 基于继承（ASM字节码）     │");
        System.out.println("  │ 要求         │ 目标类必须有接口        │ 目标类不能是 final         │");
        System.out.println("  │ 代理对象关系 │ 实现相同接口            │ 目标类的子类              │");
        System.out.println("  │ 拦截方式     │ InvocationHandler       │ MethodInterceptor         │");
        System.out.println("  │ 依赖         │ JDK 内置                │ 需要引入 cglib 库         │");
        System.out.println("  │ final 方法   │ 不影响（接口无 final）  │ final 方法无法代理        │");
        System.out.println("  │ 创建速度     │ 较快                    │ 较慢（字节码生成）        │");
        System.out.println("  │ 调用速度     │ 反射调用                │ FastClass 直接调用        │");
        System.out.println("  └──────────────┴─────────────────────────┴───────────────────────────┘");
        System.out.println();
    }

    // ==================== 四、Spring 代理选择策略 ====================

    private static void showSpringProxyStrategy() {
        System.out.println("=== 四、Spring 代理选择策略 ===\n");

        System.out.println("  Spring 选择代理方式的决策树：");
        System.out.println();
        System.out.println("    proxyTargetClass = true ?");
        System.out.println("    ├── 是 → 使用 CGLIB（SpringBoot 2.0+ 默认）");
        System.out.println("    └── 否");
        System.out.println("         ├── 目标类实现了接口？");
        System.out.println("         │   ├── 是 → 使用 JDK Proxy");
        System.out.println("         │   └── 否 → 使用 CGLIB");
        System.out.println("         └── 目标类是 final？");
        System.out.println("              └── 是 → 无法代理，抛出异常");
        System.out.println();

        System.out.println("  配置方式：");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  // 注解方式（默认 proxyTargetClass=false）");
        System.out.println("  @EnableAspectJAutoProxy(proxyTargetClass = true)");
        System.out.println();
        System.out.println("  // SpringBoot 配置（默认 true）");
        System.out.println("  spring.aop.proxy-target-class=true");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了两种代理方式后，下一步：
 * → 真实 Spring AOP 的 @Aspect 怎么用？（见 AopDemo.java）
 * → 事务的 AOP 实现原理是什么？（见 D19 TransactionDemo.java）
 */
