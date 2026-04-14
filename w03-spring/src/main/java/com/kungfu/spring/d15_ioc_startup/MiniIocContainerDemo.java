package com.kungfu.spring.d15_ioc_startup;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【Demo】手写迷你 IoC 容器
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>从零实现一个最小 IoC 容器（BeanDefinition + BeanFactory + DI）</li>
 *   <li>理解 Spring IoC 的核心思想：控制反转 + 依赖注入</li>
 *   <li>展示 Bean 注册 → 实例化 → 属性注入 → 获取 的完整流程</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>Spring 的 IoC 容器是整个框架的基石，理解它的工作原理是掌握 Spring 源码的第一步</li>
 *   <li>面试高频考点：IoC vs DI、BeanDefinition 的作用、BeanFactory vs ApplicationContext</li>
 *   <li>手写实现可以帮助理解 Spring 源码中 DefaultListableBeanFactory 的核心逻辑</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法，观察控制台输出
 *
 * @author kungfu
 * @since D15 - Spring源码精髓
 */
public class MiniIocContainerDemo {

    // ==================== 自定义注解 ====================

    /** 模拟 @Component */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniComponent {
        String value() default "";
    }

    /** 模拟 @Autowired */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MiniAutowired {
    }

    // ==================== BeanDefinition ====================

    /**
     * Bean 定义信息 —— 对应 Spring 的 BeanDefinition。
     * 保存 Bean 的元数据（类型、名称、是否单例等），容器根据它来创建 Bean。
     */
    static class MiniBeanDefinition {
        private String beanName;
        private Class<?> beanClass;
        private boolean singleton = true;

        public MiniBeanDefinition(String beanName, Class<?> beanClass) {
            this.beanName = beanName;
            this.beanClass = beanClass;
        }

        public String getBeanName() { return beanName; }
        public Class<?> getBeanClass() { return beanClass; }
        public boolean isSingleton() { return singleton; }
    }

    // ==================== BeanFactory ====================

    /**
     * 迷你 BeanFactory —— 对应 Spring 的 DefaultListableBeanFactory。
     * 职责：注册 BeanDefinition → 实例化 Bean → 依赖注入 → 缓存单例。
     */
    static class MiniBeanFactory {

        /** BeanDefinition 注册表（对应 beanDefinitionMap） */
        private final Map<String, MiniBeanDefinition> beanDefinitionMap = new LinkedHashMap<>();

        /** 单例缓存（对应 singletonObjects，即一级缓存） */
        private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();

        // ---------- 1. 注册 BeanDefinition ----------

        public void registerBeanDefinition(String beanName, MiniBeanDefinition bd) {
            beanDefinitionMap.put(beanName, bd);
            System.out.println("  [注册] BeanDefinition: " + beanName + " -> " + bd.getBeanClass().getSimpleName());
        }

        // ---------- 2. 获取 Bean ----------

        public Object getBean(String beanName) {
            // 先从单例缓存取
            Object bean = singletonObjects.get(beanName);
            if (bean != null) {
                System.out.println("  [缓存] 从 singletonObjects 获取: " + beanName);
                return bean;
            }

            // 缓存没有，则创建
            MiniBeanDefinition bd = beanDefinitionMap.get(beanName);
            if (bd == null) {
                throw new RuntimeException("No bean named '" + beanName + "' is defined");
            }

            return createBean(beanName, bd);
        }

        @SuppressWarnings("unchecked")
        public <T> T getBean(String beanName, Class<T> requiredType) {
            return (T) getBean(beanName);
        }

        // ---------- 3. 创建 Bean ----------

        private Object createBean(String beanName, MiniBeanDefinition bd) {
            try {
                // 3.1 实例化（对应 createBeanInstance）
                Object bean = bd.getBeanClass().getDeclaredConstructor().newInstance();
                System.out.println("  [实例化] " + beanName + " = new " + bd.getBeanClass().getSimpleName() + "()");

                // 3.2 属性注入（对应 populateBean）
                populateBean(beanName, bean);

                // 3.3 初始化（对应 initializeBean）
                initializeBean(beanName, bean);

                // 3.4 放入单例缓存
                if (bd.isSingleton()) {
                    singletonObjects.put(beanName, bean);
                    System.out.println("  [缓存] 放入 singletonObjects: " + beanName);
                }

                return bean;
            } catch (Exception e) {
                throw new RuntimeException("Error creating bean '" + beanName + "'", e);
            }
        }

        // ---------- 4. 属性注入 ----------

        private void populateBean(String beanName, Object bean) throws Exception {
            Field[] fields = bean.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(MiniAutowired.class)) {
                    // 按类型查找依赖的 Bean
                    String depBeanName = findBeanNameByType(field.getType());
                    if (depBeanName != null) {
                        Object depBean = getBean(depBeanName);
                        field.setAccessible(true);
                        field.set(bean, depBean);
                        System.out.println("  [注入] " + beanName + "." + field.getName()
                                + " <- " + depBeanName);
                    }
                }
            }
        }

        // ---------- 5. 初始化 ----------

        private void initializeBean(String beanName, Object bean) {
            // 模拟 InitializingBean.afterPropertiesSet()
            if (bean instanceof MiniInitializingBean) {
                ((MiniInitializingBean) bean).afterPropertiesSet();
                System.out.println("  [初始化] " + beanName + ".afterPropertiesSet()");
            }
        }

        // ---------- 辅助方法 ----------

        private String findBeanNameByType(Class<?> type) {
            for (Map.Entry<String, MiniBeanDefinition> entry : beanDefinitionMap.entrySet()) {
                if (type.isAssignableFrom(entry.getValue().getBeanClass())) {
                    return entry.getKey();
                }
            }
            return null;
        }

        /** 预实例化所有单例（对应 refresh() 的 finishBeanFactoryInitialization） */
        public void preInstantiateSingletons() {
            System.out.println("\n  [预实例化] 开始预实例化所有单例 Bean...\n");
            for (String beanName : new ArrayList<>(beanDefinitionMap.keySet())) {
                if (beanDefinitionMap.get(beanName).isSingleton()) {
                    getBean(beanName);
                    System.out.println();
                }
            }
        }
    }

    /** 模拟 InitializingBean */
    interface MiniInitializingBean {
        void afterPropertiesSet();
    }

    // ==================== 示例 Bean ====================

    @MiniComponent("userDao")
    static class UserDao {
        public String findUser(String id) {
            return "User-" + id;
        }
    }

    @MiniComponent("userService")
    static class UserService implements MiniInitializingBean {

        @MiniAutowired
        private UserDao userDao;

        private boolean initialized = false;

        @Override
        public void afterPropertiesSet() {
            initialized = true;
        }

        public String getUser(String id) {
            return "[Service] " + userDao.findUser(id) + " (initialized=" + initialized + ")";
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   手写迷你 IoC 容器 Demo");
        System.out.println("========================================\n");

        // 一、IoC vs DI 核心概念
        showCoreConcepts();

        // 二、手写 IoC 容器实战
        demonstrateMiniIoc();

        // 三、对比 Spring 真实结构
        showSpringMapping();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. IoC = 控制反转：对象的创建和管理权从程序员转移到容器");
        System.out.println("  2. DI = 依赖注入：容器负责将依赖对象注入到目标对象中");
        System.out.println("  3. BeanDefinition 是 Bean 的「设计图纸」，BeanFactory 是「工厂」");
        System.out.println("  4. Spring 容器启动流程：扫描 → 注册BD → 实例化 → 注入 → 初始化");
        System.out.println("  5. BeanFactory 是 IoC 容器的根接口，ApplicationContext 是它的高级封装");
        System.out.println("  6. 单例 Bean 默认在容器启动时预实例化（懒加载需 @Lazy）");
        System.out.println();
    }

    // ==================== 一、核心概念 ====================

    private static void showCoreConcepts() {
        System.out.println("=== 一、IoC 与 DI 核心概念 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │ 传统方式（程序员控制）              IoC方式（容器控制）  │");
        System.out.println("  │                                                         │");
        System.out.println("  │ UserService service = new UserService();                │");
        System.out.println("  │ service.setUserDao(new UserDao());  ← 程序员手动创建    │");
        System.out.println("  │                                                         │");
        System.out.println("  │ vs.                                                     │");
        System.out.println("  │                                                         │");
        System.out.println("  │ @Autowired                                              │");
        System.out.println("  │ private UserDao userDao;            ← 容器自动注入      │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  IoC 容器的核心组件：");
        System.out.println("  ┌─────────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 组件                │ 职责                                  │");
        System.out.println("  ├─────────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ BeanDefinition      │ Bean 的元数据（类型、作用域、依赖等） │");
        System.out.println("  │ BeanDefinitionReader│ 从配置源读取并注册 BeanDefinition     │");
        System.out.println("  │ BeanFactory         │ Bean 的创建工厂（核心容器）           │");
        System.out.println("  │ ApplicationContext  │ BeanFactory 的增强版（含事件、AOP等） │");
        System.out.println("  │ BeanPostProcessor   │ Bean 创建过程中的扩展点               │");
        System.out.println("  └─────────────────────┴──────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、手写 IoC 实战 ====================

    private static void demonstrateMiniIoc() {
        System.out.println("=== 二、手写 IoC 容器实战 ===\n");

        // 1. 创建 BeanFactory
        MiniBeanFactory factory = new MiniBeanFactory();
        System.out.println("  --- Step 1: 注册 BeanDefinition ---\n");

        // 2. 注册 BeanDefinition（模拟包扫描）
        factory.registerBeanDefinition("userDao",
                new MiniBeanDefinition("userDao", UserDao.class));
        factory.registerBeanDefinition("userService",
                new MiniBeanDefinition("userService", UserService.class));

        // 3. 预实例化所有单例
        System.out.println("\n  --- Step 2: 预实例化单例 Bean ---");
        factory.preInstantiateSingletons();

        // 4. 使用 Bean
        System.out.println("  --- Step 3: 使用 Bean ---\n");
        UserService service = factory.getBean("userService", UserService.class);
        String result = service.getUser("1001");
        System.out.println("  调用结果: " + result);
        System.out.println();
    }

    // ==================== 三、对比 Spring 真实结构 ====================

    private static void showSpringMapping() {
        System.out.println("=== 三、Mini-IoC 与 Spring 真实结构对比 ===\n");

        System.out.println("  ┌──────────────────────┬────────────────────────────────────────┐");
        System.out.println("  │ Mini-IoC             │ Spring 真实实现                        │");
        System.out.println("  ├──────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ MiniBeanDefinition   │ BeanDefinition (接口)                  │");
        System.out.println("  │                      │ → GenericBeanDefinition                │");
        System.out.println("  │                      │ → RootBeanDefinition                   │");
        System.out.println("  ├──────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ MiniBeanFactory      │ DefaultListableBeanFactory             │");
        System.out.println("  │ .beanDefinitionMap   │ .beanDefinitionMap (ConcurrentHashMap) │");
        System.out.println("  │ .singletonObjects    │ .singletonObjects (一级缓存)           │");
        System.out.println("  ├──────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ @MiniComponent       │ @Component / @Service / @Repository    │");
        System.out.println("  │ @MiniAutowired       │ @Autowired / @Resource / @Inject       │");
        System.out.println("  ├──────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ MiniInitializingBean │ InitializingBean                       │");
        System.out.println("  │ .afterPropertiesSet  │ .afterPropertiesSet()                  │");
        System.out.println("  ├──────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ createBean()         │ AbstractAutowireCapableBeanFactory     │");
        System.out.println("  │                      │ .createBean() → doCreateBean()         │");
        System.out.println("  ├──────────────────────┼────────────────────────────────────────┤");
        System.out.println("  │ populateBean()       │ .populateBean()                        │");
        System.out.println("  │ initializeBean()     │ .initializeBean()                      │");
        System.out.println("  └──────────────────────┴────────────────────────────────────────┘");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 手写了迷你 IoC 容器后，下一步：
 * → 真实 Spring 的 refresh() 流程是怎样的？（见 IocRefreshFlowDemo.java）
 * → Bean 的完整生命周期有哪些扩展点？（见 D16 BeanLifecycleDemo.java）
 */
