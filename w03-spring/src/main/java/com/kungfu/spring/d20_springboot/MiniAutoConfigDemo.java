package com.kungfu.spring.d20_springboot;

import java.io.*;
import java.lang.annotation.*;
import java.net.URL;
import java.util.*;

/**
 * 【Demo】手写迷你自动装配机制
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>手写模拟 SPI 加载机制（类似 spring.factories）</li>
 *   <li>手写 @Conditional 条件装配逻辑</li>
 *   <li>展示自动装配的核心流程：扫描 → 过滤 → 注册</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>SpringBoot 的「约定大于配置」核心就是自动装配</li>
 *   <li>面试必问：SpringBoot 自动装配的原理是什么？</li>
 *   <li>理解自动装配才能自定义 Starter、排查自动配置冲突</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D20 - Spring源码精髓
 */
public class MiniAutoConfigDemo {

    // ==================== 自定义注解 ====================

    /** 模拟 @ConditionalOnClass */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniConditionalOnClass {
        String value();  // 全限定类名
    }

    /** 模拟 @ConditionalOnProperty */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniConditionalOnProperty {
        String name();
        String havingValue() default "";
    }

    /** 模拟 @Configuration */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniAutoConfiguration {}

    // ==================== 模拟的自动配置类 ====================

    @MiniAutoConfiguration
    @MiniConditionalOnClass("com.zaxxer.hikari.HikariDataSource")
    static class DataSourceAutoConfig {
        public String describe() {
            return "DataSourceAutoConfig: 配置 HikariCP 数据源";
        }
    }

    @MiniAutoConfiguration
    @MiniConditionalOnClass("org.springframework.data.redis.core.RedisTemplate")
    static class RedisAutoConfig {
        public String describe() {
            return "RedisAutoConfig: 配置 RedisTemplate";
        }
    }

    @MiniAutoConfiguration
    @MiniConditionalOnClass("com.kungfu.spring.d20_springboot.MiniAutoConfigDemo") // 这个类一定存在
    static class AlwaysMatchConfig {
        public String describe() {
            return "AlwaysMatchConfig: 这个自动配置一定生效";
        }
    }

    @MiniAutoConfiguration
    @MiniConditionalOnProperty(name = "mini.feature.enabled", havingValue = "true")
    static class PropertyBasedConfig {
        public String describe() {
            return "PropertyBasedConfig: 基于配置属性开关";
        }
    }

    // ==================== 迷你自动装配引擎 ====================

    static class MiniAutoConfigEngine {

        private final Properties properties;

        MiniAutoConfigEngine(Properties properties) {
            this.properties = properties;
        }

        /**
         * 模拟 SpringBoot 的自动装配流程
         * 对应 AutoConfigurationImportSelector.getAutoConfigurationEntry()
         */
        List<Object> loadAutoConfigurations() {
            System.out.println("  --- Step 1: 扫描自动配置类（模拟 spring.factories 加载） ---\n");

            // 模拟从 spring.factories 读取的自动配置类
            List<Class<?>> candidates = Arrays.asList(
                    DataSourceAutoConfig.class,
                    RedisAutoConfig.class,
                    AlwaysMatchConfig.class,
                    PropertyBasedConfig.class
            );

            System.out.println("  发现 " + candidates.size() + " 个候选自动配置类：");
            for (Class<?> c : candidates) {
                System.out.println("    - " + c.getSimpleName());
            }
            System.out.println();

            // Step 2: 条件过滤
            System.out.println("  --- Step 2: 条件过滤（@Conditional 判断） ---\n");
            List<Object> matched = new ArrayList<>();

            for (Class<?> configClass : candidates) {
                boolean pass = evaluateConditions(configClass);
                String status = pass ? "✓ 生效" : "✗ 跳过";
                System.out.println("    " + status + " " + configClass.getSimpleName());

                if (pass) {
                    try {
                        matched.add(configClass.getDeclaredConstructor().newInstance());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            System.out.println();
            System.out.println("  --- Step 3: 注册生效的自动配置 ---\n");
            System.out.println("  最终生效 " + matched.size() + " 个自动配置类");
            return matched;
        }

        /**
         * 评估条件注解 —— 对应 ConditionEvaluator
         */
        private boolean evaluateConditions(Class<?> configClass) {
            // 检查 @MiniConditionalOnClass
            MiniConditionalOnClass onClass = configClass.getAnnotation(MiniConditionalOnClass.class);
            if (onClass != null) {
                try {
                    Class.forName(onClass.value());
                    System.out.println("      @ConditionalOnClass(\"" + onClass.value() + "\") → 类存在 ✓");
                } catch (ClassNotFoundException e) {
                    System.out.println("      @ConditionalOnClass(\"" + onClass.value() + "\") → 类不存在 ✗");
                    return false;
                }
            }

            // 检查 @MiniConditionalOnProperty
            MiniConditionalOnProperty onProp = configClass.getAnnotation(MiniConditionalOnProperty.class);
            if (onProp != null) {
                String actual = properties.getProperty(onProp.name(), "");
                boolean match = onProp.havingValue().isEmpty() || onProp.havingValue().equals(actual);
                System.out.println("      @ConditionalOnProperty(\"" + onProp.name()
                        + "\"=\"" + actual + "\", expected=\"" + onProp.havingValue() + "\") → "
                        + (match ? "匹配 ✓" : "不匹配 ✗"));
                return match;
            }

            return true;
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   手写迷你自动装配 Demo");
        System.out.println("========================================\n");

        // 一、自动装配原理
        showAutoConfigPrinciple();

        // 二、模拟自动装配流程
        demonstrateMiniAutoConfig();

        // 三、对比 Spring 真实实现
        showSpringMapping();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan");
        System.out.println("  2. @EnableAutoConfiguration → @Import(AutoConfigurationImportSelector)");
        System.out.println("  3. AutoConfigurationImportSelector 读取 META-INF/spring.factories (Boot 2.x)");
        System.out.println("     或 META-INF/spring/org.springframework...AutoConfiguration.imports (Boot 3.x)");
        System.out.println("  4. 加载所有候选 AutoConfiguration，然后按 @Conditional 条件过滤");
        System.out.println("  5. 条件注解族：@ConditionalOnClass / OnBean / OnProperty / OnMissingBean ...");
        System.out.println("  6. 用户定义的 @Bean 优先于自动配置（@ConditionalOnMissingBean 保证不覆盖）");
        System.out.println();
    }

    // ==================== 一、原理 ====================

    private static void showAutoConfigPrinciple() {
        System.out.println("=== 一、SpringBoot 自动装配原理 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────────────┐");
        System.out.println("  │ @SpringBootApplication                                              │");
        System.out.println("  │   ├── @SpringBootConfiguration                                     │");
        System.out.println("  │   │     └── @Configuration                                         │");
        System.out.println("  │   ├── @EnableAutoConfiguration   ← 自动装配的入口                  │");
        System.out.println("  │   │     └── @Import(AutoConfigurationImportSelector.class)          │");
        System.out.println("  │   │           └── selectImports() → 读取 spring.factories          │");
        System.out.println("  │   │               └── getAutoConfigurationEntry()                   │");
        System.out.println("  │   │                     ├── 加载候选自动配置类                      │");
        System.out.println("  │   │                     ├── 去重                                    │");
        System.out.println("  │   │                     ├── 排除（exclude）                         │");
        System.out.println("  │   │                     └── @Conditional 条件过滤                   │");
        System.out.println("  │   └── @ComponentScan                                               │");
        System.out.println("  └─────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、模拟 ====================

    private static void demonstrateMiniAutoConfig() {
        System.out.println("=== 二、模拟自动装配流程 ===\n");

        // 模拟应用配置
        Properties props = new Properties();
        props.setProperty("mini.feature.enabled", "false");  // 关闭特性开关

        System.out.println("  模拟配置: mini.feature.enabled=false\n");

        MiniAutoConfigEngine engine = new MiniAutoConfigEngine(props);
        List<Object> configs = engine.loadAutoConfigurations();

        System.out.println();
        for (Object config : configs) {
            try {
                String desc = (String) config.getClass().getMethod("describe").invoke(config);
                System.out.println("    → " + desc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    // ==================== 三、映射 ====================

    private static void showSpringMapping() {
        System.out.println("=== 三、Mini 与 SpringBoot 真实实现映射 ===\n");

        System.out.println("  ┌────────────────────────────┬──────────────────────────────────────────┐");
        System.out.println("  │ Mini 实现                  │ SpringBoot 真实实现                       │");
        System.out.println("  ├────────────────────────────┼──────────────────────────────────────────┤");
        System.out.println("  │ @MiniAutoConfiguration     │ @AutoConfiguration / @Configuration      │");
        System.out.println("  │ @MiniConditionalOnClass    │ @ConditionalOnClass                      │");
        System.out.println("  │ @MiniConditionalOnProperty │ @ConditionalOnProperty                   │");
        System.out.println("  │ candidates 列表            │ META-INF/spring.factories                │");
        System.out.println("  │ evaluateConditions()       │ ConditionEvaluator / OnClassCondition    │");
        System.out.println("  │ loadAutoConfigurations()   │ AutoConfigurationImportSelector          │");
        System.out.println("  │                            │ .getAutoConfigurationEntry()             │");
        System.out.println("  └────────────────────────────┴──────────────────────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 手写了自动装配后，下一步：
 * → 真实 SpringBoot 自动配置是怎么工作的？（见 AutoConfigurationDemo.java）
 * → @Conditional 系列注解详解（见 ConditionalDemo.java）
 */
