package com.kungfu.spring.d18_aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 【Demo】真实 Spring AOP 实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>@Aspect + @EnableAspectJAutoProxy 完整用法</li>
 *   <li>5 种通知类型的真实执行顺序</li>
 *   <li>Pointcut 表达式的常见写法</li>
 *   <li>代理对象和原始对象的区别</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>验证手写拦截器链（MiniAopDemo）与真实 Spring AOP 的行为是否一致</li>
 *   <li>AOP 是日志、监控、事务、权限等横切关注点的标准实现方式</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D18 - Spring源码精髓
 */
public class AopDemo {

    // ==================== 配置类 ====================

    @Configuration
    @EnableAspectJAutoProxy  // 开启 AOP 自动代理
    @ComponentScan(basePackageClasses = AopDemo.class)
    static class AopConfig {
    }

    // ==================== 业务类 ====================

    @Service("d18UserService")
    static class UserService {

        public String findUser(String userId) {
            System.out.println("    [UserService] 查询用户: " + userId);
            return "User-" + userId;
        }

        public void deleteUser(String userId) {
            System.out.println("    [UserService] 删除用户: " + userId);
            throw new RuntimeException("删除失败: 用户 " + userId + " 不存在");
        }
    }

    // ==================== 切面 ====================

    @Aspect
    @Component
    static class LoggingAspect {

        /** Pointcut: 匹配 UserService 的所有方法 */
        @Pointcut("execution(* com.kungfu.spring.d18_aop.AopDemo.UserService.*(..))")
        public void userServiceMethods() {}

        @Before("userServiceMethods()")
        public void before(JoinPoint joinPoint) {
            System.out.println("    [@Before] " + joinPoint.getSignature().getName() + " 即将执行");
        }

        @After("userServiceMethods()")
        public void after(JoinPoint joinPoint) {
            System.out.println("    [@After] " + joinPoint.getSignature().getName() + " 已执行（finally）");
        }

        @AfterReturning(pointcut = "userServiceMethods()", returning = "result")
        public void afterReturning(JoinPoint joinPoint, Object result) {
            System.out.println("    [@AfterReturning] " + joinPoint.getSignature().getName()
                    + " 正常返回: " + result);
        }

        @AfterThrowing(pointcut = "userServiceMethods()", throwing = "ex")
        public void afterThrowing(JoinPoint joinPoint, Exception ex) {
            System.out.println("    [@AfterThrowing] " + joinPoint.getSignature().getName()
                    + " 抛出异常: " + ex.getMessage());
        }

        @Around("userServiceMethods()")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("    [@Around-前] " + pjp.getSignature().getName() + " 环绕开始");
            long start = System.currentTimeMillis();
            try {
                Object result = pjp.proceed();
                return result;
            } finally {
                long cost = System.currentTimeMillis() - start;
                System.out.println("    [@Around-后] " + pjp.getSignature().getName()
                        + " 环绕结束，耗时=" + cost + "ms");
            }
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   真实 Spring AOP 实战 Demo");
        System.out.println("========================================\n");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AopConfig.class);
        UserService userService = ctx.getBean("d18UserService", UserService.class);

        // 一、正常调用
        System.out.println("=== 一、正常调用（观察通知执行顺序） ===\n");
        String result = userService.findUser("1001");
        System.out.println("\n  最终结果: " + result);

        // 二、异常调用
        System.out.println("\n=== 二、异常调用（观察 AfterThrowing） ===\n");
        try {
            userService.deleteUser("9999");
        } catch (Exception e) {
            System.out.println("\n  捕获异常: " + e.getMessage());
        }

        // 三、代理信息
        System.out.println("\n=== 三、代理对象信息 ===\n");
        System.out.println("  Bean 类名: " + userService.getClass().getName());
        System.out.println("  是否为 CGLIB 代理: " + userService.getClass().getName().contains("$$"));
        System.out.println("  父类: " + userService.getClass().getSuperclass().getSimpleName());

        // 四、Pointcut 表达式
        showPointcutExpressions();

        ctx.close();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. @EnableAspectJAutoProxy 注册 AnnotationAwareAspectJAutoProxyCreator");
        System.out.println("  2. 该 BPP 在 postProcessAfterInitialization 中为匹配的 Bean 创建代理");
        System.out.println("  3. 同一切面内 Spring 5 的执行顺序：");
        System.out.println("     Around前 → Before → 目标方法 → AfterReturning/AfterThrowing → After → Around后");
        System.out.println("  4. this 调用（同类方法互调）不走代理，AOP 不生效");
        System.out.println("     解决：注入自身代理、AopContext.currentProxy()、拆分类");
        System.out.println();
    }

    // ==================== Pointcut 表达式 ====================

    private static void showPointcutExpressions() {
        System.out.println("\n=== 四、常用 Pointcut 表达式 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────┬──────────────────────────┐");
        System.out.println("  │ 表达式                                                   │ 说明                     │");
        System.out.println("  ├──────────────────────────────────────────────────────────┼──────────────────────────┤");
        System.out.println("  │ execution(* com.xxx.service.*.*(..))                     │ service 包所有方法       │");
        System.out.println("  │ execution(public * com.xxx..*.*(..))                     │ com.xxx 下所有 public    │");
        System.out.println("  │ @annotation(com.xxx.Log)                                │ 标注 @Log 的方法         │");
        System.out.println("  │ @within(org.springframework.stereotype.Service)          │ @Service 类的所有方法    │");
        System.out.println("  │ bean(userService)                                       │ 名为 userService 的 Bean │");
        System.out.println("  └──────────────────────────────────────────────────────────┴──────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 掌握了 AOP 原理后，下一步：
 * → 事务管理如何基于 AOP 实现？（见 D19 TransactionDemo.java）
 * → 事务的 7 种传播行为是什么？（见 D19 TransactionPropagationDemo.java）
 */
