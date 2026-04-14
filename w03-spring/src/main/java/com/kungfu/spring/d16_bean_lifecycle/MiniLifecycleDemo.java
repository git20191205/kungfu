package com.kungfu.spring.d16_bean_lifecycle;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 【Demo】手写模拟 Bean 生命周期 4 阶段
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>从零模拟 Bean 的完整生命周期：实例化 → 属性填充 → 初始化 → 销毁</li>
 *   <li>手写 BeanPostProcessor 的前置和后置回调</li>
 *   <li>展示 Aware 接口的注入时机</li>
 *   <li>理解 @PostConstruct / InitializingBean / init-method 的执行顺序</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>Bean 生命周期是 Spring 面试中最高频的考点之一</li>
 *   <li>AOP 代理、事务管理、注解处理都依赖 BeanPostProcessor</li>
 *   <li>理解生命周期才能正确使用扩展点，避免初始化顺序导致的 NPE</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，观察控制台的生命周期回调顺序
 *
 * @author kungfu
 * @since D16 - Spring源码精髓
 */
public class MiniLifecycleDemo {

    // ==================== 自定义注解 ====================

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniPostConstruct {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniPreDestroy {}

    // ==================== 生命周期接口 ====================

    interface MiniBeanNameAware {
        void setBeanName(String name);
    }

    interface MiniInitializingBean {
        void afterPropertiesSet();
    }

    interface MiniDisposableBean {
        void destroy();
    }

    interface MiniBeanPostProcessor {
        default Object postProcessBeforeInitialization(Object bean, String beanName) { return bean; }
        default Object postProcessAfterInitialization(Object bean, String beanName) { return bean; }
    }

    // ==================== 迷你容器 ====================

    static class MiniLifecycleContainer {

        private final List<MiniBeanPostProcessor> processors = new ArrayList<>();
        private final Map<String, Object> singletonMap = new LinkedHashMap<>();
        private final Map<String, Class<?>> beanClassMap = new LinkedHashMap<>();

        void addBeanPostProcessor(MiniBeanPostProcessor processor) {
            processors.add(processor);
        }

        void registerBean(String name, Class<?> clazz) {
            beanClassMap.put(name, clazz);
        }

        Object getBean(String name) {
            Object bean = singletonMap.get(name);
            if (bean == null) {
                bean = createBean(name, beanClassMap.get(name));
                singletonMap.put(name, bean);
            }
            return bean;
        }

        private Object createBean(String beanName, Class<?> clazz) {
            try {
                int step = 1;

                // ========== 阶段一：实例化 ==========
                System.out.println("  " + (step++) + ". [实例化] 调用构造方法 new " + clazz.getSimpleName() + "()");
                Object bean = clazz.getDeclaredConstructor().newInstance();

                // ========== 阶段二：属性填充 ==========
                System.out.println("  " + (step++) + ". [属性填充] 注入依赖属性（@Autowired 字段）");

                // Aware 回调（属性填充后、初始化前）
                if (bean instanceof MiniBeanNameAware) {
                    System.out.println("  " + (step++) + ". [Aware回调] setBeanName(\"" + beanName + "\")");
                    ((MiniBeanNameAware) bean).setBeanName(beanName);
                }

                // ========== 阶段三：初始化 ==========

                // BeanPostProcessor.postProcessBeforeInitialization
                for (MiniBeanPostProcessor processor : processors) {
                    System.out.println("  " + (step++) + ". [BPP前置] " + processor.getClass().getSimpleName()
                            + ".postProcessBeforeInitialization()");
                    bean = processor.postProcessBeforeInitialization(bean, beanName);
                }

                // @PostConstruct
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(MiniPostConstruct.class)) {
                        System.out.println("  " + (step++) + ". [@PostConstruct] " + method.getName() + "()");
                        method.invoke(bean);
                    }
                }

                // InitializingBean.afterPropertiesSet()
                if (bean instanceof MiniInitializingBean) {
                    System.out.println("  " + (step++) + ". [InitializingBean] afterPropertiesSet()");
                    ((MiniInitializingBean) bean).afterPropertiesSet();
                }

                // BeanPostProcessor.postProcessAfterInitialization
                for (MiniBeanPostProcessor processor : processors) {
                    System.out.println("  " + (step++) + ". [BPP后置] " + processor.getClass().getSimpleName()
                            + ".postProcessAfterInitialization()  ← AOP代理在此创建");
                    bean = processor.postProcessAfterInitialization(bean, beanName);
                }

                System.out.println("  " + step + ". [就绪] Bean \"" + beanName + "\" 创建完成，放入单例缓存");

                return bean;
            } catch (Exception e) {
                throw new RuntimeException("创建 Bean 失败: " + beanName, e);
            }
        }

        void destroyAll() {
            System.out.println("\n  --- 容器关闭，执行销毁回调 ---\n");
            for (Map.Entry<String, Object> entry : singletonMap.entrySet()) {
                String name = entry.getKey();
                Object bean = entry.getValue();

                // @PreDestroy
                for (Method method : bean.getClass().getDeclaredMethods()) {
                    if (method.isAnnotationPresent(MiniPreDestroy.class)) {
                        try {
                            System.out.println("  [@PreDestroy] " + name + "." + method.getName() + "()");
                            method.invoke(bean);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                // DisposableBean.destroy()
                if (bean instanceof MiniDisposableBean) {
                    System.out.println("  [DisposableBean] " + name + ".destroy()");
                    ((MiniDisposableBean) bean).destroy();
                }
            }
        }
    }

    // ==================== 示例 Bean ====================

    static class OrderService implements MiniBeanNameAware, MiniInitializingBean, MiniDisposableBean {

        private String beanName;
        private boolean ready = false;

        public OrderService() {
            System.out.println("    → OrderService 构造方法执行");
        }

        @Override
        public void setBeanName(String name) {
            this.beanName = name;
            System.out.println("    → 我知道自己的名字了: " + name);
        }

        @MiniPostConstruct
        public void postConstruct() {
            System.out.println("    → @PostConstruct: 执行自定义初始化逻辑");
        }

        @Override
        public void afterPropertiesSet() {
            this.ready = true;
            System.out.println("    → afterPropertiesSet: 属性设置完毕，标记就绪");
        }

        @MiniPreDestroy
        public void preDestroy() {
            System.out.println("    → @PreDestroy: 释放资源");
        }

        @Override
        public void destroy() {
            System.out.println("    → DisposableBean.destroy: 最终销毁");
        }

        public String getStatus() {
            return "OrderService[name=" + beanName + ", ready=" + ready + "]";
        }
    }

    // ==================== 自定义 BeanPostProcessor ====================

    static class LoggingBeanPostProcessor implements MiniBeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            System.out.println("    → 前置处理: 可以在此做校验、属性修改等");
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            System.out.println("    → 后置处理: 可以在此创建AOP代理、包装Bean等");
            return bean;
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   手写模拟 Bean 生命周期 Demo");
        System.out.println("========================================\n");

        // 一、Bean 生命周期总览
        showLifecycleOverview();

        // 二、手写模拟完整生命周期
        demonstrateMiniLifecycle();

        // 三、初始化回调执行顺序
        showCallbackOrder();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. Bean 生命周期 4 阶段：实例化 → 属性填充 → 初始化 → 销毁");
        System.out.println("  2. 初始化回调顺序：@PostConstruct → InitializingBean → init-method");
        System.out.println("  3. 销毁回调顺序：@PreDestroy → DisposableBean → destroy-method");
        System.out.println("  4. BPP 前置在 @PostConstruct 之前执行");
        System.out.println("  5. BPP 后置在所有初始化回调之后执行（AOP 代理在此创建）");
        System.out.println("  6. Aware 回调在属性填充之后、BPP 前置之前执行");
        System.out.println();
    }

    // ==================== 一、生命周期总览 ====================

    private static void showLifecycleOverview() {
        System.out.println("=== 一、Bean 生命周期 4 阶段总览 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    Bean 完整生命周期                              │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────┤");
        System.out.println("  │                                                                 │");
        System.out.println("  │  阶段一：实例化 (Instantiation)                                 │");
        System.out.println("  │    └── 调用构造方法创建 Bean 实例                               │");
        System.out.println("  │                         ↓                                       │");
        System.out.println("  │  阶段二：属性填充 (Population)                                  │");
        System.out.println("  │    ├── @Autowired / @Value 注入                                 │");
        System.out.println("  │    └── Aware 接口回调 (BeanNameAware, BeanFactoryAware...)      │");
        System.out.println("  │                         ↓                                       │");
        System.out.println("  │  阶段三：初始化 (Initialization)                                │");
        System.out.println("  │    ├── BeanPostProcessor.postProcessBeforeInitialization()      │");
        System.out.println("  │    ├── @PostConstruct                                           │");
        System.out.println("  │    ├── InitializingBean.afterPropertiesSet()                    │");
        System.out.println("  │    ├── 自定义 init-method                                       │");
        System.out.println("  │    └── BeanPostProcessor.postProcessAfterInitialization()       │");
        System.out.println("  │                         ↓                                       │");
        System.out.println("  │  ★ Bean 就绪，放入单例缓存 ★                                   │");
        System.out.println("  │                         ↓                                       │");
        System.out.println("  │  阶段四：销毁 (Destruction)                                     │");
        System.out.println("  │    ├── @PreDestroy                                              │");
        System.out.println("  │    ├── DisposableBean.destroy()                                 │");
        System.out.println("  │    └── 自定义 destroy-method                                    │");
        System.out.println("  │                                                                 │");
        System.out.println("  └─────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、手写模拟 ====================

    private static void demonstrateMiniLifecycle() {
        System.out.println("=== 二、手写模拟完整生命周期 ===\n");

        MiniLifecycleContainer container = new MiniLifecycleContainer();

        // 注册 BeanPostProcessor
        container.addBeanPostProcessor(new LoggingBeanPostProcessor());

        // 注册 Bean
        container.registerBean("orderService", OrderService.class);

        System.out.println("  --- 创建 Bean 过程 ---\n");

        // 获取 Bean（触发创建）
        OrderService service = (OrderService) container.getBean("orderService");

        System.out.println("\n  --- Bean 使用 ---\n");
        System.out.println("  " + service.getStatus());

        // 销毁
        container.destroyAll();
        System.out.println();
    }

    // ==================== 三、回调执行顺序 ====================

    private static void showCallbackOrder() {
        System.out.println("=== 三、初始化与销毁回调执行顺序 ===\n");

        System.out.println("  初始化回调顺序（从先到后）：");
        System.out.println("  ┌────┬────────────────────────────────────┬─────────────────────┐");
        System.out.println("  │ #  │ 回调方式                           │ 来源                │");
        System.out.println("  ├────┼────────────────────────────────────┼─────────────────────┤");
        System.out.println("  │ 1  │ BPP.postProcessBeforeInitialization│ BeanPostProcessor   │");
        System.out.println("  │ 2  │ @PostConstruct 标注的方法          │ JSR-250 注解        │");
        System.out.println("  │ 3  │ InitializingBean.afterPropertiesSet│ Spring 接口         │");
        System.out.println("  │ 4  │ 自定义 init-method                 │ XML/注解配置        │");
        System.out.println("  │ 5  │ BPP.postProcessAfterInitialization │ BeanPostProcessor   │");
        System.out.println("  └────┴────────────────────────────────────┴─────────────────────┘");
        System.out.println();

        System.out.println("  销毁回调顺序（从先到后）：");
        System.out.println("  ┌────┬────────────────────────────────────┬─────────────────────┐");
        System.out.println("  │ #  │ 回调方式                           │ 来源                │");
        System.out.println("  ├────┼────────────────────────────────────┼─────────────────────┤");
        System.out.println("  │ 1  │ @PreDestroy 标注的方法             │ JSR-250 注解        │");
        System.out.println("  │ 2  │ DisposableBean.destroy()           │ Spring 接口         │");
        System.out.println("  │ 3  │ 自定义 destroy-method              │ XML/注解配置        │");
        System.out.println("  └────┴────────────────────────────────────┴─────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 手写了生命周期模拟后，下一步：
 * → 真实 Spring 中 BeanPostProcessor 是怎样工作的？（见 BeanLifecycleDemo.java）
 * → 循环依赖场景下的生命周期是什么样的？（见 D17）
 */
