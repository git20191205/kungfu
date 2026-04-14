package com.kungfu.spring.d20_springboot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.List;

/**
 * 【Demo】真实 SpringBoot 自动装配机制
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>SpringFactoriesLoader 如何加载 spring.factories</li>
 *   <li>@ConditionalOnClass / @ConditionalOnMissingBean / @ConditionalOnProperty 的真实行为</li>
 *   <li>自定义自动配置类的写法</li>
 *   <li>自动装配与用户配置的优先级关系</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>验证手写模拟（MiniAutoConfigDemo）的正确性</li>
 *   <li>自定义 Spring Boot Starter 的必备知识</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D20 - Spring源码精髓
 */
public class AutoConfigurationDemo {

    // ==================== 自定义组件和接口 ====================

    interface GreetingService {
        String greet(String name);
    }

    /** 默认的 GreetingService（自动配置提供的默认实现） */
    static class DefaultGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name + "! (from default auto-config)";
        }
    }

    /** 用户自定义的 GreetingService */
    static class CustomGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hi, " + name + "! (from user-defined bean)";
        }
    }

    // ==================== 自动配置类 ====================

    @Configuration
    static class GreetingAutoConfiguration {

        /** @ConditionalOnMissingBean: 只在用户没有定义 GreetingService 时才生效 */
        @Bean
        @ConditionalOnMissingBean(GreetingService.class)
        public GreetingService greetingService() {
            System.out.println("  [AutoConfig] 用户未定义 GreetingService，使用默认实现");
            return new DefaultGreetingService();
        }
    }

    // ==================== 用户配置（注释掉可以看到自动配置生效） ====================

    @Configuration
    static class UserConfig {
        // 如果用户定义了 GreetingService，自动配置的 @ConditionalOnMissingBean 不生效
        // 取消注释下面的 @Bean 可以看到效果
        // @Bean
        // public GreetingService greetingService() {
        //     System.out.println("  [UserConfig] 用户自定义 GreetingService");
        //     return new CustomGreetingService();
        // }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   真实 SpringBoot 自动装配 Demo");
        System.out.println("========================================\n");

        // 一、SpringFactoriesLoader
        demonstrateSpringFactories();

        // 二、@Conditional 条件装配
        demonstrateConditionalConfig();

        // 三、自定义 Starter 模板
        showCustomStarterTemplate();

        // 四、spring.factories vs imports 文件
        showFactoriesEvolution();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. 自动装配入口：@EnableAutoConfiguration → AutoConfigurationImportSelector");
        System.out.println("  2. Boot 2.x: META-INF/spring.factories");
        System.out.println("     Boot 3.x: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        System.out.println("  3. @ConditionalOnMissingBean 保证用户自定义 Bean 优先于自动配置");
        System.out.println("  4. 自动配置类执行顺序可通过 @AutoConfigureOrder / @AutoConfigureBefore/After 控制");
        System.out.println("  5. 自定义 Starter 三件套：xxx-spring-boot-starter + xxx-autoconfigure + spring.factories");
        System.out.println("  6. 启动时查看生效的自动配置：--debug 或 Conditions Evaluation Report");
        System.out.println();
    }

    // ==================== 一、SpringFactoriesLoader ====================

    private static void demonstrateSpringFactories() {
        System.out.println("=== 一、SpringFactoriesLoader 加载机制 ===\n");

        System.out.println("  SpringFactoriesLoader.loadFactoryNames() 加载 spring.factories 内容：\n");

        try {
            // 加载 EnableAutoConfiguration 对应的自动配置类列表
            List<String> autoConfigs = SpringFactoriesLoader.loadFactoryNames(
                    org.springframework.boot.autoconfigure.EnableAutoConfiguration.class,
                    AutoConfigurationDemo.class.getClassLoader()
            );

            System.out.println("  共加载 " + autoConfigs.size() + " 个自动配置类");
            System.out.println("  前 10 个示例：");
            for (int i = 0; i < Math.min(10, autoConfigs.size()); i++) {
                String name = autoConfigs.get(i);
                // 取短类名
                String shortName = name.substring(name.lastIndexOf('.') + 1);
                System.out.println("    " + (i + 1) + ". " + shortName);
            }
            if (autoConfigs.size() > 10) {
                System.out.println("    ... 还有 " + (autoConfigs.size() - 10) + " 个");
            }
        } catch (Exception e) {
            System.out.println("  (无法加载 EnableAutoConfiguration 配置)");
        }
        System.out.println();
    }

    // ==================== 二、条件装配 ====================

    private static void demonstrateConditionalConfig() {
        System.out.println("=== 二、@ConditionalOnMissingBean 实战 ===\n");

        System.out.println("  场景：GreetingAutoConfiguration 用 @ConditionalOnMissingBean 提供默认 Bean");
        System.out.println("        用户可以自定义 GreetingService 覆盖默认实现\n");

        // 启动容器（仅加载自动配置，不加用户配置）
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                GreetingAutoConfiguration.class, UserConfig.class);

        GreetingService service = ctx.getBean(GreetingService.class);
        System.out.println("  Bean 类型: " + service.getClass().getSimpleName());
        System.out.println("  调用结果: " + service.greet("SpringBoot"));
        System.out.println();

        ctx.close();
    }

    // ==================== 三、自定义 Starter 模板 ====================

    private static void showCustomStarterTemplate() {
        System.out.println("=== 三、自定义 Spring Boot Starter 模板 ===\n");

        System.out.println("  项目结构：");
        System.out.println("  ┌── xxx-spring-boot-starter/        ← 空壳 Starter（只有 pom 依赖）");
        System.out.println("  │    └── pom.xml");
        System.out.println("  │         └── dependency: xxx-spring-boot-autoconfigure");
        System.out.println("  │");
        System.out.println("  └── xxx-spring-boot-autoconfigure/  ← 自动配置逻辑");
        System.out.println("       ├── src/main/java/");
        System.out.println("       │    ├── XxxAutoConfiguration.java");
        System.out.println("       │    └── XxxProperties.java");
        System.out.println("       └── src/main/resources/");
        System.out.println("            └── META-INF/spring.factories");
        System.out.println();

        System.out.println("  XxxAutoConfiguration.java 模板：");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  @Configuration");
        System.out.println("  @ConditionalOnClass(XxxService.class)");
        System.out.println("  @EnableConfigurationProperties(XxxProperties.class)");
        System.out.println("  public class XxxAutoConfiguration {");
        System.out.println();
        System.out.println("      @Bean");
        System.out.println("      @ConditionalOnMissingBean");
        System.out.println("      public XxxService xxxService(XxxProperties props) {");
        System.out.println("          return new XxxService(props.getEndpoint());");
        System.out.println("      }");
        System.out.println("  }");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();

        System.out.println("  spring.factories 内容：");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\");
        System.out.println("  com.xxx.autoconfigure.XxxAutoConfiguration");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();
    }

    // ==================== 四、spring.factories 演进 ====================

    private static void showFactoriesEvolution() {
        System.out.println("=== 四、spring.factories 到 imports 文件的演进 ===\n");

        System.out.println("  ┌──────────────┬──────────────────────────────────────────────────────────┐");
        System.out.println("  │ 版本         │ 自动配置文件位置                                         │");
        System.out.println("  ├──────────────┼──────────────────────────────────────────────────────────┤");
        System.out.println("  │ Boot 2.x     │ META-INF/spring.factories                               │");
        System.out.println("  │              │ key = EnableAutoConfiguration                            │");
        System.out.println("  ├──────────────┼──────────────────────────────────────────────────────────┤");
        System.out.println("  │ Boot 2.7     │ 两种方式并存（过渡版本）                                 │");
        System.out.println("  ├──────────────┼──────────────────────────────────────────────────────────┤");
        System.out.println("  │ Boot 3.x     │ META-INF/spring/                                        │");
        System.out.println("  │              │   org.springframework.boot.autoconfigure.                │");
        System.out.println("  │              │   AutoConfiguration.imports                              │");
        System.out.println("  │              │ （纯文本，每行一个配置类全限定名）                       │");
        System.out.println("  └──────────────┴──────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  Boot 3.x .imports 文件示例：");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println("  com.xxx.autoconfigure.XxxAutoConfiguration");
        System.out.println("  com.yyy.autoconfigure.YyyAutoConfiguration");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了自动装配后，下一步：
 * → @Conditional 系列注解详解（见 ConditionalDemo.java）
 * → 下一周（W04）进入 MySQL + Redis 实战
 */
