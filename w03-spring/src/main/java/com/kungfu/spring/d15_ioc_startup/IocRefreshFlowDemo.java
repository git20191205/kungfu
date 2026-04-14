package com.kungfu.spring.d15_ioc_startup;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 【Demo】Spring IoC 容器 refresh() 启动流程
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>使用真实 AnnotationConfigApplicationContext 启动 Spring 容器</li>
 *   <li>追踪 refresh() 方法的 12 个核心步骤</li>
 *   <li>通过 BeanFactoryPostProcessor 观察容器启动过程</li>
 *   <li>展示 BeanFactory 与 ApplicationContext 的区别</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>refresh() 是 Spring 容器最核心的方法，一切从这里开始</li>
 *   <li>面试常问：请描述 Spring 容器的启动流程</li>
 *   <li>理解 refresh() 是读懂 Bean 生命周期、AOP、事务等高级特性的前提</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，观察控制台输出
 *
 * @author kungfu
 * @since D15 - Spring源码精髓
 */
public class IocRefreshFlowDemo {

    // ==================== 配置类 ====================

    @Configuration
    @ComponentScan("com.kungfu.spring.d15_ioc_startup")
    static class AppConfig {

        @Bean
        public String greeting() {
            System.out.println("    [AppConfig] @Bean greeting() 被调用 —— 注册 Bean 定义");
            return "Hello from Spring IoC!";
        }
    }

    // ==================== 示例 Bean ====================

    @Component("d15DemoService")
    static class DemoService {

        @PostConstruct
        public void init() {
            System.out.println("    [DemoService] @PostConstruct 初始化回调");
        }

        public String sayHello() {
            return "DemoService is working!";
        }
    }

    // ==================== BeanFactoryPostProcessor ====================

    @Component("d15ObserverProcessor")
    static class ObserverBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            int count = beanFactory.getBeanDefinitionCount();
            String[] names = beanFactory.getBeanDefinitionNames();
            System.out.println("    [BeanFactoryPostProcessor] 容器中共有 " + count + " 个 BeanDefinition");
            System.out.println("    [BeanFactoryPostProcessor] 自定义 Bean 列表：");
            for (String name : names) {
                BeanDefinition bd = beanFactory.getBeanDefinition(name);
                if (bd.getBeanClassName() != null && bd.getBeanClassName().contains("kungfu")) {
                    System.out.println("      - " + name + " -> " + bd.getBeanClassName());
                }
            }
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Spring IoC 容器 refresh() 流程 Demo");
        System.out.println("========================================\n");

        // 一、refresh() 12 步流程
        showRefreshSteps();

        // 二、真实启动 Spring 容器
        demonstrateRealSpring();

        // 三、BeanFactory vs ApplicationContext
        showBeanFactoryVsAppContext();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. refresh() 是 Spring 容器启动的核心方法，被 AbstractApplicationContext 定义");
        System.out.println("  2. 12 步流程按顺序：准备 → 获取BF → 定制BF → 后置处理 → 调用BFPP →");
        System.out.println("     注册BPP → 初始化消息源 → 初始化事件广播 → 刷新子容器 → 注册监听器 →");
        System.out.println("     实例化单例 → 完成刷新");
        System.out.println("  3. BeanFactoryPostProcessor 在 Bean 实例化之前执行，可以修改 BeanDefinition");
        System.out.println("  4. BeanPostProcessor 在 Bean 实例化之后执行，可以包装 Bean（AOP 的入口）");
        System.out.println("  5. ApplicationContext = BeanFactory + 事件机制 + 国际化 + AOP + 资源加载");
        System.out.println();
    }

    // ==================== 一、refresh() 12 步 ====================

    private static void showRefreshSteps() {
        System.out.println("=== 一、AbstractApplicationContext.refresh() 12 步 ===\n");

        System.out.println("  ┌─────┬──────────────────────────────────────┬──────────────────────────────────┐");
        System.out.println("  │ 步骤│ 方法名                                │ 作用                             │");
        System.out.println("  ├─────┼──────────────────────────────────────┼──────────────────────────────────┤");
        System.out.println("  │  1  │ prepareRefresh()                     │ 准备工作：设置启动时间、激活标志 │");
        System.out.println("  │  2  │ obtainFreshBeanFactory()             │ 获取/创建 BeanFactory            │");
        System.out.println("  │  3  │ prepareBeanFactory(bf)               │ 设置类加载器、SpEL、Aware接口等  │");
        System.out.println("  │  4  │ postProcessBeanFactory(bf)           │ 子类定制 BeanFactory（模板方法） │");
        System.out.println("  │  5  │ invokeBeanFactoryPostProcessors(bf)  │ ★ 调用 BeanFactoryPostProcessor │");
        System.out.println("  │  6  │ registerBeanPostProcessors(bf)       │ 注册 BeanPostProcessor           │");
        System.out.println("  │  7  │ initMessageSource()                  │ 初始化国际化消息源               │");
        System.out.println("  │  8  │ initApplicationEventMulticaster()    │ 初始化事件广播器                 │");
        System.out.println("  │  9  │ onRefresh()                          │ 子类扩展（如 SpringBoot 启动Web）│");
        System.out.println("  │ 10  │ registerListeners()                  │ 注册事件监听器                   │");
        System.out.println("  │ 11  │ finishBeanFactoryInitialization(bf)  │ ★ 实例化所有非懒加载的单例 Bean │");
        System.out.println("  │ 12  │ finishRefresh()                      │ 发布 ContextRefreshedEvent       │");
        System.out.println("  └─────┴──────────────────────────────────────┴──────────────────────────────────┘");
        System.out.println();
        System.out.println("  核心步骤标记 ★：");
        System.out.println("    第5步：BeanFactoryPostProcessor 可以修改 BeanDefinition（Spring Boot 自动装配的入口）");
        System.out.println("    第11步：所有单例 Bean 在这里被创建（Bean 生命周期在此触发）");
        System.out.println();
    }

    // ==================== 二、真实 Spring 容器 ====================

    private static void demonstrateRealSpring() {
        System.out.println("=== 二、真实 Spring 容器启动 ===\n");
        System.out.println("  使用 AnnotationConfigApplicationContext 启动...\n");

        // 启动容器
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);

        System.out.println();
        System.out.println("  --- 容器启动完成，开始使用 Bean ---\n");

        // 获取 Bean
        DemoService service = ctx.getBean("d15DemoService", DemoService.class);
        System.out.println("  DemoService.sayHello() = " + service.sayHello());

        String greeting = ctx.getBean("greeting", String.class);
        System.out.println("  greeting Bean = " + greeting);

        // 查看 BeanFactory 内部信息
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ctx.getBeanFactory();
        System.out.println();
        System.out.println("  BeanFactory 类型: " + beanFactory.getClass().getSimpleName());
        System.out.println("  BeanDefinition 总数: " + beanFactory.getBeanDefinitionCount());
        System.out.println("  单例 Bean 总数: " + beanFactory.getSingletonCount());

        ctx.close();
        System.out.println("\n  容器已关闭\n");
    }

    // ==================== 三、BeanFactory vs ApplicationContext ====================

    private static void showBeanFactoryVsAppContext() {
        System.out.println("=== 三、BeanFactory vs ApplicationContext ===\n");

        System.out.println("  ┌───────────────────┬───────────────────────────┬──────────────────────────────┐");
        System.out.println("  │ 特性              │ BeanFactory               │ ApplicationContext           │");
        System.out.println("  ├───────────────────┼───────────────────────────┼──────────────────────────────┤");
        System.out.println("  │ Bean 实例化时机   │ 延迟加载（getBean时创建） │ 预实例化（启动时创建单例）   │");
        System.out.println("  │ AOP 支持          │ 手动配置                  │ 自动支持                     │");
        System.out.println("  │ 事件机制          │ 不支持                    │ 支持（ApplicationEvent）     │");
        System.out.println("  │ 国际化            │ 不支持                    │ 支持（MessageSource）        │");
        System.out.println("  │ 资源加载          │ 不支持                    │ 支持（ResourceLoader）       │");
        System.out.println("  │ 环境抽象          │ 不支持                    │ 支持（Environment）          │");
        System.out.println("  └───────────────────┴───────────────────────────┴──────────────────────────────┘");
        System.out.println();
        System.out.println("  继承关系：");
        System.out.println("  BeanFactory");
        System.out.println("    ├── HierarchicalBeanFactory");
        System.out.println("    ├── ListableBeanFactory");
        System.out.println("    └── AutowireCapableBeanFactory");
        System.out.println("          └── DefaultListableBeanFactory（最终实现类）");
        System.out.println();
        System.out.println("  ApplicationContext extends BeanFactory");
        System.out.println("    ├── AnnotationConfigApplicationContext（注解驱动）");
        System.out.println("    ├── ClassPathXmlApplicationContext（XML驱动）");
        System.out.println("    └── GenericWebApplicationContext（Web环境）");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了 refresh() 启动流程后，下一步：
 * → Bean 在容器中经历了怎样的生命周期？（见 D16 BeanLifecycleDemo.java）
 * → 循环依赖是如何通过三级缓存解决的？（见 D17 CircularDependencyDemo.java）
 */
