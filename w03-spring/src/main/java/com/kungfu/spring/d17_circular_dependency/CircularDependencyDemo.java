package com.kungfu.spring.d17_circular_dependency;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 【Demo】真实 Spring 循环依赖场景
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>场景 1：字段注入 / Setter 注入 —— Spring 可以解决</li>
 *   <li>场景 2：构造器注入 —— Spring 无法解决，抛出异常</li>
 *   <li>场景 3：prototype 作用域 —— Spring 无法解决</li>
 *   <li>解决方案：@Lazy 注解延迟初始化</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>实际开发中循环依赖非常常见（服务互相调用）</li>
 *   <li>Spring 6+ 默认禁用循环依赖，需要熟练掌握 @Lazy 等解决方案</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D17 - Spring源码精髓
 */
public class CircularDependencyDemo {

    // ==================== 场景 1：字段注入循环依赖（可解决） ====================

    @Component
    static class FieldServiceA {
        @Autowired
        FieldServiceB fieldServiceB;
    }

    @Component
    static class FieldServiceB {
        @Autowired
        FieldServiceA fieldServiceA;
    }

    @Configuration
    @ComponentScan(basePackageClasses = CircularDependencyDemo.class,
                   useDefaultFilters = false,
                   includeFilters = @ComponentScan.Filter(
                           type = org.springframework.context.annotation.FilterType.REGEX,
                           pattern = ".*Field.*"))
    static class FieldInjectionConfig {}

    // ==================== 场景 2：构造器循环依赖（无法解决） ====================

    @Component
    static class ConstructorServiceA {
        private final ConstructorServiceB b;
        @Autowired
        public ConstructorServiceA(ConstructorServiceB b) {
            this.b = b;
        }
    }

    @Component
    static class ConstructorServiceB {
        private final ConstructorServiceA a;
        @Autowired
        public ConstructorServiceB(ConstructorServiceA a) {
            this.a = a;
        }
    }

    @Configuration
    @ComponentScan(basePackageClasses = CircularDependencyDemo.class,
                   useDefaultFilters = false,
                   includeFilters = @ComponentScan.Filter(
                           type = org.springframework.context.annotation.FilterType.REGEX,
                           pattern = ".*Constructor.*"))
    static class ConstructorInjectionConfig {}

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   真实 Spring 循环依赖 Demo");
        System.out.println("========================================\n");

        // 场景 1：字段注入
        scenario1_FieldInjection();

        // 场景 2：构造器注入
        scenario2_ConstructorInjection();

        // 场景对比
        showScenarioComparison();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. 能解决的场景：");
        System.out.println("     ✓ 字段注入（@Autowired 字段）");
        System.out.println("     ✓ Setter 注入");
        System.out.println("  2. 不能解决的场景：");
        System.out.println("     ✗ 构造器注入（实例化未完成，无法放入三级缓存）");
        System.out.println("     ✗ prototype 作用域（每次都创建新 Bean，不走缓存）");
        System.out.println("     ✗ @Async 标注的循环（代理对象不在三级缓存中，字段拿到原始对象）");
        System.out.println("  3. 解决方案：");
        System.out.println("     @Lazy —— 延迟初始化，生成代理对象，实际使用时才注入");
        System.out.println("     ObjectProvider / ObjectFactory —— 延迟获取");
        System.out.println("     重构：消除循环依赖（最佳实践）");
        System.out.println("  4. Spring 6 / SpringBoot 2.6+ 默认禁用循环依赖");
        System.out.println("     需要开启：spring.main.allow-circular-references=true");
        System.out.println();
    }

    // ==================== 场景 1 ====================

    private static void scenario1_FieldInjection() {
        System.out.println("=== 场景 1：字段注入循环依赖 ===\n");
        System.out.println("  FieldServiceA.fieldServiceB ↔ FieldServiceB.fieldServiceA");
        System.out.println("  使用 @Autowired 字段注入，Spring 可以通过三级缓存解决\n");

        try {
            AnnotationConfigApplicationContext ctx =
                    new AnnotationConfigApplicationContext(FieldInjectionConfig.class);
            FieldServiceA a = ctx.getBean(FieldServiceA.class);
            FieldServiceB b = ctx.getBean(FieldServiceB.class);

            System.out.println("  ✓ 容器启动成功！");
            System.out.println("    a.fieldServiceB == b ? " + (a.fieldServiceB == b));
            System.out.println("    b.fieldServiceA == a ? " + (b.fieldServiceA == a));
            ctx.close();
        } catch (Exception e) {
            System.out.println("  ✗ 失败: " + e.getMessage());
        }
        System.out.println();
    }

    // ==================== 场景 2 ====================

    private static void scenario2_ConstructorInjection() {
        System.out.println("=== 场景 2：构造器循环依赖 ===\n");
        System.out.println("  ConstructorServiceA(b) ↔ ConstructorServiceB(a)");
        System.out.println("  使用构造器注入，Spring 无法解决，抛出异常\n");

        try {
            AnnotationConfigApplicationContext ctx =
                    new AnnotationConfigApplicationContext(ConstructorInjectionConfig.class);
            ctx.getBean(ConstructorServiceA.class);
            System.out.println("  ✓ 容器启动成功（不应该到这里）");
            ctx.close();
        } catch (BeanCreationException e) {
            System.out.println("  ✗ 预期内抛出 BeanCurrentlyInCreationException:");
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            System.out.println("    根因: " + root.getClass().getSimpleName());
            System.out.println("    提示: " + (root.getMessage() != null
                    ? root.getMessage().split("\\n")[0] : "无"));
        } catch (Exception e) {
            System.out.println("  ✗ 抛出异常: " + e.getClass().getSimpleName());
            System.out.println("    原因: " + e.getMessage());
        }
        System.out.println();
    }

    // ==================== 场景对比 ====================

    private static void showScenarioComparison() {
        System.out.println("=== 循环依赖场景对比表 ===\n");

        System.out.println("  ┌────────────────────────┬──────────┬───────────────────────────────────────┐");
        System.out.println("  │ 场景                   │ 能否解决 │ 原因                                  │");
        System.out.println("  ├────────────────────────┼──────────┼───────────────────────────────────────┤");
        System.out.println("  │ 字段注入 (@Autowired)  │ ✓        │ 三级缓存生效                          │");
        System.out.println("  │ Setter 注入            │ ✓        │ 三级缓存生效                          │");
        System.out.println("  │ 构造器注入             │ ✗        │ 实例化未完成，无法放入三级缓存        │");
        System.out.println("  │ prototype 作用域       │ ✗        │ 不走单例缓存                          │");
        System.out.println("  │ 字段注入 + @Async      │ 部分 ✗   │ 代理对象不在三级缓存中                │");
        System.out.println("  │ 配合 @Lazy             │ ✓        │ 生成代理，延迟实际注入                │");
        System.out.println("  └────────────────────────┴──────────┴───────────────────────────────────────┘");
        System.out.println();

        System.out.println("  @Lazy 的工作原理：");
        System.out.println("  ─────────────────────────────────────────────────────────────────");
        System.out.println("  public class ServiceA {");
        System.out.println("      @Autowired");
        System.out.println("      @Lazy  // 生成 ServiceB 的代理对象，实际调用时才从容器获取");
        System.out.println("      private ServiceB serviceB;");
        System.out.println("  }");
        System.out.println("  ─────────────────────────────────────────────────────────────────");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了循环依赖后，下一步：
 * → AOP 代理是如何通过三级缓存提前暴露的？（见 D18）
 * → 为什么 Spring 推荐构造器注入？（虽然不能循环，但能强制暴露设计问题）
 */
