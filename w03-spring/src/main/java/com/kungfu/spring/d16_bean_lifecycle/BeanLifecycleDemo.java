package com.kungfu.spring.d16_bean_lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 【Demo】真实 Spring Bean 生命周期全流程
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>使用真实 Spring 容器观察 Bean 生命周期的完整回调顺序</li>
 *   <li>Aware 接口（BeanNameAware、BeanFactoryAware、ApplicationContextAware）的注入时机</li>
 *   <li>BeanPostProcessor 的前置和后置回调</li>
 *   <li>@PostConstruct / InitializingBean / @Bean(initMethod) 的执行顺序</li>
 *   <li>@PreDestroy / DisposableBean / @Bean(destroyMethod) 的执行顺序</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>验证手写模拟（MiniLifecycleDemo）的正确性</li>
 *   <li>真实 Spring 环境下的回调顺序是面试标准答案</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，观察控制台输出的回调顺序
 *
 * @author kungfu
 * @since D16 - Spring源码精髓
 */
public class BeanLifecycleDemo {

    // ==================== 配置类 ====================

    @Configuration
    static class LifecycleConfig {

        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        public LifecycleBean lifecycleBean() {
            return new LifecycleBean();
        }

        @Bean
        public LifecycleObserverBPP lifecycleObserverBPP() {
            return new LifecycleObserverBPP();
        }
    }

    // ==================== 全回调 Bean ====================

    static class LifecycleBean implements
            BeanNameAware, BeanFactoryAware, ApplicationContextAware,
            InitializingBean, DisposableBean {

        private int step = 1;

        public LifecycleBean() {
            log("构造方法 new LifecycleBean()");
        }

        // ---------- Aware 回调 ----------

        @Override
        public void setBeanName(String name) {
            log("BeanNameAware.setBeanName(\"" + name + "\")");
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            log("BeanFactoryAware.setBeanFactory()");
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            log("ApplicationContextAware.setApplicationContext()");
        }

        // ---------- 初始化回调 ----------

        @PostConstruct
        public void postConstruct() {
            log("@PostConstruct");
        }

        @Override
        public void afterPropertiesSet() {
            log("InitializingBean.afterPropertiesSet()");
        }

        public void customInit() {
            log("自定义 init-method: customInit()");
        }

        // ---------- 销毁回调 ----------

        @PreDestroy
        public void preDestroy() {
            log("@PreDestroy");
        }

        @Override
        public void destroy() {
            log("DisposableBean.destroy()");
        }

        public void customDestroy() {
            log("自定义 destroy-method: customDestroy()");
        }

        // ---------- 业务方法 ----------

        public String doWork() {
            return "LifecycleBean is working!";
        }

        private void log(String msg) {
            System.out.printf("  %2d. %s%n", step++, msg);
        }
    }

    // ==================== BeanPostProcessor ====================

    static class LifecycleObserverBPP implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof LifecycleBean) {
                LifecycleBean lb = (LifecycleBean) bean;
                lb.step = lb.step; // 保持步骤连续
                System.out.printf("  %2d. BeanPostProcessor.postProcessBeforeInitialization(\"%s\")%n",
                        lb.step++, beanName);
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof LifecycleBean) {
                LifecycleBean lb = (LifecycleBean) bean;
                System.out.printf("  %2d. BeanPostProcessor.postProcessAfterInitialization(\"%s\")  ← AOP代理在此生成%n",
                        lb.step++, beanName);
            }
            return bean;
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   真实 Spring Bean 生命周期 Demo");
        System.out.println("========================================\n");

        System.out.println("=== Bean 生命周期回调顺序（真实 Spring 验证） ===\n");

        System.out.println("  --- 容器启动 ---\n");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(LifecycleConfig.class);

        System.out.println();
        System.out.println("  --- Bean 使用 ---\n");
        LifecycleBean bean = ctx.getBean(LifecycleBean.class);
        System.out.println("  调用结果: " + bean.doWork());

        System.out.println();
        System.out.println("  --- 容器关闭 ---\n");
        ctx.close();

        System.out.println();
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 总结：完整回调顺序                                           │");
        System.out.println("  │                                                              │");
        System.out.println("  │ 构造方法 → BeanNameAware → BeanFactoryAware                 │");
        System.out.println("  │ → ApplicationContextAware → BPP前置                         │");
        System.out.println("  │ → @PostConstruct → InitializingBean → init-method           │");
        System.out.println("  │ → BPP后置(AOP代理) → Bean就绪                               │");
        System.out.println("  │                                                              │");
        System.out.println("  │ @PreDestroy → DisposableBean → destroy-method               │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. Aware 接口回调在 BPP 前置之前执行");
        System.out.println("  2. @PostConstruct 实际上是通过 CommonAnnotationBeanPostProcessor 实现的");
        System.out.println("  3. 初始化三连：@PostConstruct → afterPropertiesSet() → init-method");
        System.out.println("  4. 销毁三连：@PreDestroy → destroy() → destroy-method");
        System.out.println("  5. AOP 代理对象在 BPP 后置回调中创建（AnnotationAwareAspectJAutoProxyCreator）");
        System.out.println("  6. prototype 作用域的 Bean 不会执行销毁回调（容器不管理其生命周期）");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了 Bean 生命周期后，下一步：
 * → BeanPostProcessor 有哪些重要实现？（见 BeanPostProcessorDemo.java）
 * → 循环依赖时的生命周期是怎样的？（见 D17）
 */
