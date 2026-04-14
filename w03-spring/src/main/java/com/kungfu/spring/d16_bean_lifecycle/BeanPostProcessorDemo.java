package com.kungfu.spring.d16_bean_lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * 【Demo】BeanPostProcessor 与 BeanFactoryPostProcessor
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>BeanFactoryPostProcessor：在 Bean 实例化之前修改 BeanDefinition</li>
 *   <li>BeanPostProcessor：在 Bean 初始化前后拦截和增强</li>
 *   <li>通过 BPP 实现简单的接口代理（模拟 AOP 的工作原理）</li>
 *   <li>Spring 内置的重要 BPP 实现列表</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>BeanPostProcessor 是 Spring 最核心的扩展机制</li>
 *   <li>AOP、@Autowired、@Async、@Scheduled 等功能全部基于 BPP 实现</li>
 *   <li>面试中经常考察 BFPP 和 BPP 的区别以及执行时机</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D16 - Spring源码精髓
 */
public class BeanPostProcessorDemo {

    // ==================== 配置类 ====================

    @Configuration
    static class BPPConfig {

        @Bean
        public HelloService helloService() {
            return new HelloServiceImpl();
        }

        @Bean
        public MyBeanFactoryPostProcessor myBFPP() {
            return new MyBeanFactoryPostProcessor();
        }

        @Bean
        public LoggingBeanPostProcessor loggingBPP() {
            return new LoggingBeanPostProcessor();
        }

        @Bean
        public TimingBeanPostProcessor timingBPP() {
            return new TimingBeanPostProcessor();
        }
    }

    // ==================== 业务接口和实现 ====================

    interface HelloService {
        String sayHello(String name);
    }

    static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "!";
        }
    }

    // ==================== BeanFactoryPostProcessor ====================

    static class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            System.out.println("  [BeanFactoryPostProcessor] 在所有 Bean 实例化之前执行！");
            System.out.println("  [BeanFactoryPostProcessor] 可以修改 BeanDefinition");

            // 修改 BeanDefinition 示例
            BeanDefinition bd = beanFactory.getBeanDefinition("helloService");
            System.out.println("  [BeanFactoryPostProcessor] helloService 的作用域: "
                    + (bd.getScope().isEmpty() ? "singleton" : bd.getScope()));
            System.out.println("  [BeanFactoryPostProcessor] helloService 是否懒加载: " + bd.isLazyInit());
            System.out.println();
        }
    }

    // ==================== BeanPostProcessor: 日志记录 ====================

    static class LoggingBeanPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof HelloService) {
                System.out.println("  [LoggingBPP] Before: 即将初始化 " + beanName);
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof HelloService) {
                System.out.println("  [LoggingBPP] After: " + beanName + " 初始化完成");
            }
            return bean;
        }
    }

    // ==================== BeanPostProcessor: 耗时统计代理 ====================

    static class TimingBeanPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof HelloService) {
                System.out.println("  [TimingBPP] 为 " + beanName + " 创建耗时统计代理");

                // 使用 JDK 动态代理包装 Bean —— 这就是 AOP 的核心原理！
                return Proxy.newProxyInstance(
                        bean.getClass().getClassLoader(),
                        new Class[]{HelloService.class},
                        (proxy, method, args) -> {
                            long start = System.nanoTime();
                            Object result = method.invoke(bean, args);
                            long cost = System.nanoTime() - start;
                            System.out.println("    [TimingProxy] " + method.getName()
                                    + "() 耗时: " + cost + " ns");
                            return result;
                        }
                );
            }
            return bean;
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   BeanPostProcessor 扩展机制 Demo");
        System.out.println("========================================\n");

        // 一、BFPP vs BPP 对比
        showComparison();

        // 二、真实 Spring 验证
        System.out.println("=== 二、真实 Spring 容器验证 ===\n");
        demonstrateRealBPP();

        // 三、Spring 内置重要 BPP
        showBuiltInBPPs();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. BeanFactoryPostProcessor: 修改 BeanDefinition（实例化前）");
        System.out.println("  2. BeanPostProcessor: 增强 Bean 实例（初始化前后）");
        System.out.println("  3. BFPP 经典实现: PropertySourcesPlaceholderConfigurer（处理 ${...}）");
        System.out.println("  4. BPP 经典实现: AutowiredAnnotationBeanPostProcessor（处理 @Autowired）");
        System.out.println("  5. AOP 代理通过 AnnotationAwareAspectJAutoProxyCreator（BPP）在后置回调中创建");
        System.out.println("  6. BPP 的 postProcessAfterInitialization 返回代理对象时，容器缓存的是代理对象");
        System.out.println();
    }

    // ==================== 一、BFPP vs BPP ====================

    private static void showComparison() {
        System.out.println("=== 一、BeanFactoryPostProcessor vs BeanPostProcessor ===\n");

        System.out.println("  ┌───────────────┬─────────────────────────────┬─────────────────────────────────┐");
        System.out.println("  │ 对比项        │ BeanFactoryPostProcessor    │ BeanPostProcessor               │");
        System.out.println("  ├───────────────┼─────────────────────────────┼─────────────────────────────────┤");
        System.out.println("  │ 执行时机      │ Bean 实例化之前             │ Bean 初始化前后                 │");
        System.out.println("  │ 操作对象      │ BeanDefinition（元数据）    │ Bean 实例（对象）               │");
        System.out.println("  │ 方法数量      │ 1 个                        │ 2 个（before + after）          │");
        System.out.println("  │ 典型用途      │ 修改属性值、改变作用域      │ AOP代理、注入、校验             │");
        System.out.println("  │ 执行次数      │ 1 次（容器启动时）          │ 每个 Bean 初始化都会调用        │");
        System.out.println("  └───────────────┴─────────────────────────────┴─────────────────────────────────┘");
        System.out.println();

        System.out.println("  执行时序：");
        System.out.println("  BeanDefinition注册 → BFPP修改BD → Bean实例化 → 属性填充 → BPP前置 → 初始化 → BPP后置");
        System.out.println();
    }

    // ==================== 二、真实 Spring ====================

    private static void demonstrateRealBPP() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BPPConfig.class);

        System.out.println();
        System.out.println("  --- 调用被代理的 Bean ---\n");

        HelloService service = ctx.getBean(HelloService.class);
        System.out.println("  Bean 实际类型: " + service.getClass().getSimpleName());
        System.out.println("  是否为代理: " + Proxy.isProxyClass(service.getClass()));
        System.out.println("  调用结果: " + service.sayHello("Spring"));

        ctx.close();
        System.out.println();
    }

    // ==================== 三、Spring 内置 BPP ====================

    private static void showBuiltInBPPs() {
        System.out.println("=== 三、Spring 内置重要 BeanPostProcessor ===\n");

        System.out.println("  ┌────────────────────────────────────────────┬──────────────────────────────────┐");
        System.out.println("  │ BPP 实现类                                │ 功能                              │");
        System.out.println("  ├────────────────────────────────────────────┼──────────────────────────────────┤");
        System.out.println("  │ AutowiredAnnotationBeanPostProcessor      │ 处理 @Autowired / @Value          │");
        System.out.println("  │ CommonAnnotationBeanPostProcessor         │ 处理 @PostConstruct / @PreDestroy │");
        System.out.println("  │ AnnotationAwareAspectJAutoProxyCreator    │ 创建 AOP 代理对象                 │");
        System.out.println("  │ AsyncAnnotationBeanPostProcessor          │ 处理 @Async 异步方法              │");
        System.out.println("  │ ScheduledAnnotationBeanPostProcessor      │ 处理 @Scheduled 定时任务          │");
        System.out.println("  │ PersistenceAnnotationBeanPostProcessor    │ 处理 @PersistenceContext（JPA）   │");
        System.out.println("  └────────────────────────────────────────────┴──────────────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了 BPP 扩展机制后，下一步：
 * → 循环依赖场景下 BPP 会有什么问题？（见 D17）
 * → AOP 代理的创建过程是怎样的？（见 D18）
 */
