package com.kungfu.spring.d20_springboot;

import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

/**
 * 【Demo】@Conditional 系列条件注解
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>@ConditionalOnClass / @ConditionalOnMissingClass：类存在/不存在时生效</li>
 *   <li>@ConditionalOnBean / @ConditionalOnMissingBean：Bean 存在/不存在时生效</li>
 *   <li>@ConditionalOnProperty：配置属性匹配时生效</li>
 *   <li>自定义 @Conditional 的实现方式</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>@Conditional 是 SpringBoot 自动装配的过滤核心</li>
 *   <li>理解条件注解才能排查「为什么某个 Bean 没有自动注册」</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D20 - Spring源码精髓
 */
public class ConditionalDemo {

    // ==================== 配置类 ====================

    @Configuration
    static class ConditionalConfig {

        /** 只在 H2 驱动存在时生效 */
        @Bean("h2Service")
        @ConditionalOnClass(name = "org.h2.Driver")
        public String h2Service() {
            return "H2 Database Service (H2 驱动存在)";
        }

        /** 只在 Oracle 驱动不存在时生效 */
        @Bean("noOracleService")
        @ConditionalOnMissingClass("oracle.jdbc.OracleDriver")
        public String noOracleService() {
            return "Non-Oracle Service (Oracle 驱动不存在)";
        }

        /** 只在名为 h2Service 的 Bean 存在时生效 */
        @Bean("dependentService")
        @ConditionalOnBean(name = "h2Service")
        public String dependentService() {
            return "Dependent Service (依赖 h2Service)";
        }

        /** 只在 missingBean 不存在时生效 */
        @Bean("fallbackService")
        @ConditionalOnMissingBean(name = "missingBean")
        public String fallbackService() {
            return "Fallback Service (missingBean 不存在)";
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   @Conditional 条件注解 Demo");
        System.out.println("========================================\n");

        // 一、条件注解全家族
        showConditionalFamily();

        // 二、真实验证
        demonstrateConditional();

        // 三、自定义 Condition
        showCustomCondition();

        // 四、排查自动配置的方法
        showDebugTips();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. @ConditionalOnClass: classpath 有指定类时生效（最常用）");
        System.out.println("  2. @ConditionalOnMissingBean: 容器中没有指定 Bean 时生效（默认值兜底）");
        System.out.println("  3. @ConditionalOnProperty: 配置属性匹配时生效（功能开关）");
        System.out.println("  4. 条件注解在 ConfigurationClassParser 阶段评估");
        System.out.println("  5. 排查自动配置：--debug / ConditionEvaluationReport / Actuator /conditions");
        System.out.println();
    }

    // ==================== 一、全家族 ====================

    private static void showConditionalFamily() {
        System.out.println("=== 一、@Conditional 条件注解全家族 ===\n");

        System.out.println("  ┌─────────────────────────────┬──────────────────────────────────────────┐");
        System.out.println("  │ 条件注解                    │ 说明                                      │");
        System.out.println("  ├─────────────────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ @ConditionalOnClass         │ classpath 中存在指定类                    │");
        System.out.println("  │ @ConditionalOnMissingClass  │ classpath 中不存在指定类                  │");
        System.out.println("  │ @ConditionalOnBean          │ 容器中存在指定 Bean                       │");
        System.out.println("  │ @ConditionalOnMissingBean   │ 容器中不存在指定 Bean（兜底默认值）       │");
        System.out.println("  │ @ConditionalOnProperty      │ 配置属性匹配指定值                        │");
        System.out.println("  │ @ConditionalOnResource      │ classpath 中存在指定资源文件              │");
        System.out.println("  │ @ConditionalOnWebApplication│ 当前是 Web 应用                           │");
        System.out.println("  │ @ConditionalOnNotWebApp     │ 当前不是 Web 应用                         │");
        System.out.println("  │ @ConditionalOnExpression     │ SpEL 表达式为 true                        │");
        System.out.println("  │ @ConditionalOnJava          │ JDK 版本满足条件                          │");
        System.out.println("  └─────────────────────────────┴──────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、真实验证 ====================

    private static void demonstrateConditional() {
        System.out.println("=== 二、@Conditional 真实验证 ===\n");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ConditionalConfig.class);

        String[] beanNames = ctx.getBeanDefinitionNames();
        System.out.println("  容器中注册的自定义 Bean：\n");
        for (String name : beanNames) {
            if (name.endsWith("Service")) {
                Object bean = ctx.getBean(name);
                System.out.println("  ✓ " + name + " = " + bean);
            }
        }

        // 检查条件不满足的 Bean
        System.out.println();
        System.out.println("  条件不满足而未注册的 Bean：");
        if (!ctx.containsBean("h2Service") && !ctx.containsBean("noOracleService")) {
            System.out.println("  (所有条件都满足了)");
        }

        ctx.close();
        System.out.println();
    }

    // ==================== 三、自定义 Condition ====================

    private static void showCustomCondition() {
        System.out.println("=== 三、自定义 Condition 写法 ===\n");

        System.out.println("  Step 1: 实现 Condition 接口");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  public class OnLinuxCondition implements Condition {");
        System.out.println("      @Override");
        System.out.println("      public boolean matches(ConditionContext ctx,");
        System.out.println("                             AnnotatedTypeMetadata metadata) {");
        System.out.println("          String os = ctx.getEnvironment()");
        System.out.println("                          .getProperty(\"os.name\", \"\");");
        System.out.println("          return os.toLowerCase().contains(\"linux\");");
        System.out.println("      }");
        System.out.println("  }");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();

        System.out.println("  Step 2: 使用 @Conditional(OnLinuxCondition.class)");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  @Bean");
        System.out.println("  @Conditional(OnLinuxCondition.class)");
        System.out.println("  public LinuxService linuxService() { ... }");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();
    }

    // ==================== 四、排查技巧 ====================

    private static void showDebugTips() {
        System.out.println("=== 四、排查自动配置的方法 ===\n");

        System.out.println("  ┌────┬──────────────────────────────────────────────────────────┐");
        System.out.println("  │ #  │ 排查方式                                                 │");
        System.out.println("  ├────┼──────────────────────────────────────────────────────────┤");
        System.out.println("  │ 1  │ 启动参数 --debug（打印 ConditionEvaluationReport）       │");
        System.out.println("  │ 2  │ Actuator /conditions 端点（Web 环境）                    │");
        System.out.println("  │ 3  │ 日志级别 logging.level.org.springframework.boot.auto=DEBUG│");
        System.out.println("  │ 4  │ 在 IDEA 中使用 Spring Boot Dashboard 查看                │");
        System.out.println("  │ 5  │ @ConditionalOnMissingBean 是否被其他 Bean 提前注册覆盖   │");
        System.out.println("  └────┴──────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  常见排查场景：");
        System.out.println("    Q: 为什么 DataSource 没有自动配置？");
        System.out.println("    A: 检查 classpath 是否有 JDBC 驱动 (@ConditionalOnClass)");
        System.out.println();
        System.out.println("    Q: 为什么我的 Bean 被覆盖了？");
        System.out.println("    A: 检查自动配置类是否没有 @ConditionalOnMissingBean");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * W03 Spring 源码精髓到此完成。回顾：
 *   D15: IoC 容器启动（refresh 12步）
 *   D16: Bean 生命周期（4阶段 + BPP）
 *   D17: 循环依赖（三级缓存）
 *   D18: AOP（动态代理 + 拦截器链）
 *   D19: 事务（@Transactional + 传播行为）
 *   D20: SpringBoot（自动装配 + @Conditional）
 *
 * 下一步进入 W04: MySQL + Redis 实战
 */
