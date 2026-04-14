package com.kungfu.spring.d17_circular_dependency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 【Demo】手写三级缓存解决循环依赖
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>用最简化的代码还原 Spring 三级缓存机制</li>
 *   <li>模拟 A→B→A 的循环依赖场景</li>
 *   <li>对比二级缓存和三级缓存的区别（AOP 代理场景）</li>
 *   <li>验证为什么构造器循环依赖无法解决</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>循环依赖是 Spring 面试的"送命题"，答对能区分是否真正理解 IoC 源码</li>
 *   <li>理解三级缓存才能明白为什么 @Autowired 字段注入可以循环，而构造器注入不行</li>
 *   <li>Spring 6 / SpringBoot 2.6+ 默认禁用循环依赖，需要理解原理后才能正确处理</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D17 - Spring源码精髓
 */
public class MiniCircularDepDemo {

    // ==================== 示例 Bean（字段注入循环依赖） ====================

    static class ServiceA {
        ServiceB serviceB;  // 依赖 B

        @Override
        public String toString() {
            return "ServiceA@" + Integer.toHexString(hashCode())
                    + "[serviceB=" + (serviceB == null ? "null" : "ServiceB@" + Integer.toHexString(serviceB.hashCode())) + "]";
        }
    }

    static class ServiceB {
        ServiceA serviceA;  // 依赖 A

        @Override
        public String toString() {
            return "ServiceB@" + Integer.toHexString(hashCode())
                    + "[serviceA=" + (serviceA == null ? "null" : "ServiceA@" + Integer.toHexString(serviceA.hashCode())) + "]";
        }
    }

    // ==================== 迷你 IoC 容器（含三级缓存） ====================

    static class MiniIocWithCache {

        /** 一级缓存：完全初始化好的 Bean */
        private final Map<String, Object> singletonObjects = new HashMap<>();

        /** 二级缓存：早期的 Bean（未完成属性注入） */
        private final Map<String, Object> earlySingletonObjects = new HashMap<>();

        /** 三级缓存：ObjectFactory（用于生成早期 Bean 或其代理） */
        private final Map<String, Supplier<Object>> singletonFactories = new HashMap<>();

        /** 正在创建中的 Bean 名称集合（防止重复创建） */
        private final Set<String> singletonsInCreation = new HashSet<>();

        /** Bean 注册表：beanName -> Class */
        private final Map<String, Class<?>> beanClassMap = new HashMap<>();

        void registerBean(String name, Class<?> clazz) {
            beanClassMap.put(name, clazz);
        }

        Object getBean(String beanName) {
            // ★ 核心：查找三级缓存
            Object bean = getSingleton(beanName);
            if (bean != null) {
                System.out.println("  [" + beanName + "] 从缓存获取: " + bean);
                return bean;
            }
            return createBean(beanName);
        }

        /**
         * 获取单例 Bean（查三级缓存）—— 对应 Spring 的 DefaultSingletonBeanRegistry.getSingleton()
         */
        private Object getSingleton(String beanName) {
            // 1. 先查一级缓存
            Object singletonObject = singletonObjects.get(beanName);
            if (singletonObject != null) {
                return singletonObject;
            }

            // 2. 查二级缓存（且 Bean 正在创建中）
            if (singletonsInCreation.contains(beanName)) {
                singletonObject = earlySingletonObjects.get(beanName);
                if (singletonObject != null) {
                    System.out.println("      ↳ 二级缓存命中: " + beanName);
                    return singletonObject;
                }

                // 3. 查三级缓存（执行 ObjectFactory）
                Supplier<Object> factory = singletonFactories.get(beanName);
                if (factory != null) {
                    System.out.println("      ↳ 三级缓存命中: 执行 ObjectFactory 生成 " + beanName);
                    singletonObject = factory.get();
                    // 升级到二级缓存
                    earlySingletonObjects.put(beanName, singletonObject);
                    singletonFactories.remove(beanName);
                    System.out.println("      ↳ 已从三级缓存移动到二级缓存");
                    return singletonObject;
                }
            }

            return null;
        }

        /**
         * 创建 Bean —— 对应 Spring 的 createBean() → doCreateBean()
         */
        @SuppressWarnings("unchecked")
        private Object createBean(String beanName) {
            try {
                System.out.println("\n  [创建开始] " + beanName);
                singletonsInCreation.add(beanName);

                // 1. 实例化（半成品）
                Class<?> clazz = beanClassMap.get(beanName);
                Object earlyBean = clazz.getDeclaredConstructor().newInstance();
                System.out.println("    [1.实例化] new " + clazz.getSimpleName() + "() 完成，但字段还没注入");

                // ★ 2. 加入三级缓存（关键！）
                Object finalEarlyBean = earlyBean;
                singletonFactories.put(beanName, () -> {
                    System.out.println("        [ObjectFactory] 生成早期引用（可能被包装为AOP代理）");
                    return finalEarlyBean;  // 真实 Spring 中这里会调用 getEarlyBeanReference（可能返回AOP代理）
                });
                System.out.println("    [2.放入三级缓存] singletonFactories.put(\"" + beanName + "\", ObjectFactory)");

                // 3. 属性填充（可能触发循环依赖）
                System.out.println("    [3.属性填充] 开始注入 " + beanName + " 的依赖字段");
                populate(beanName, earlyBean);

                // 4. 初始化（此处省略）

                // ★ 5. 放入一级缓存，清除二、三级缓存
                singletonObjects.put(beanName, earlyBean);
                earlySingletonObjects.remove(beanName);
                singletonFactories.remove(beanName);
                singletonsInCreation.remove(beanName);
                System.out.println("    [5.放入一级缓存] singletonObjects.put(\"" + beanName + "\", bean)");
                System.out.println("  [创建完成] " + beanName + " = " + earlyBean);

                return earlyBean;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create bean " + beanName, e);
            }
        }

        /**
         * 属性填充（简化：根据字段类型反射查找依赖 Bean）
         */
        private void populate(String beanName, Object bean) throws Exception {
            if (bean instanceof ServiceA) {
                System.out.println("      → 需要注入 serviceB，递归 getBean(\"serviceB\")");
                Object dep = getBean("serviceB");
                ((ServiceA) bean).serviceB = (ServiceB) dep;
                System.out.println("      → serviceA.serviceB 注入完成");
            } else if (bean instanceof ServiceB) {
                System.out.println("      → 需要注入 serviceA，递归 getBean(\"serviceA\")");
                Object dep = getBean("serviceA");
                ((ServiceB) bean).serviceA = (ServiceA) dep;
                System.out.println("      → serviceB.serviceA 注入完成");
            }
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   手写三级缓存解决循环依赖 Demo");
        System.out.println("========================================\n");

        // 一、三级缓存原理
        showThreeLevelCache();

        // 二、实战模拟
        demonstrateCircularDep();

        // 三、为什么构造器注入无法解决
        showConstructorCaseExplanation();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. 三级缓存：");
        System.out.println("     singletonObjects      → 成品 Bean（已完成所有初始化）");
        System.out.println("     earlySingletonObjects → 半成品 Bean（已实例化，未填充属性）");
        System.out.println("     singletonFactories    → ObjectFactory（产生早期引用，可能是AOP代理）");
        System.out.println("  2. 查找顺序：一级 → 二级 → 三级（一、二级缓存相同则直接返回）");
        System.out.println("  3. 三级缓存存在的意义：为 AOP 代理对象提供生成时机");
        System.out.println("     无AOP场景下，二级缓存就够了；有AOP时需要三级缓存保证提前生成代理");
        System.out.println("  4. 构造器循环依赖无法解决：实例化未完成，无法放入三级缓存");
        System.out.println("  5. prototype 循环依赖无法解决：prototype 不走缓存");
        System.out.println("  6. Spring 6 / SpringBoot 2.6+ 默认禁用循环依赖（spring.main.allow-circular-references=false）");
        System.out.println();
    }

    // ==================== 一、三级缓存原理 ====================

    private static void showThreeLevelCache() {
        System.out.println("=== 一、三级缓存结构 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("  │                  DefaultSingletonBeanRegistry                    │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────┤");
        System.out.println("  │  一级缓存：singletonObjects      <beanName, 完整的Bean>         │");
        System.out.println("  │            ↑  放入 populate() 和 initializeBean() 完成后         │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────┤");
        System.out.println("  │  二级缓存：earlySingletonObjects <beanName, 半成品Bean>         │");
        System.out.println("  │            ↑  三级缓存的 ObjectFactory 调用后放入               │");
        System.out.println("  ├─────────────────────────────────────────────────────────────────┤");
        System.out.println("  │  三级缓存：singletonFactories    <beanName, ObjectFactory>      │");
        System.out.println("  │            ↑  Bean 实例化完成后立即放入                         │");
        System.out.println("  └─────────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  查找流程：");
        System.out.println("    getSingleton(beanName)");
        System.out.println("      ├── 查一级缓存 → 有则返回");
        System.out.println("      ├── 查二级缓存 → 有则返回");
        System.out.println("      └── 查三级缓存 → 执行 ObjectFactory.getObject() → 升级到二级缓存");
        System.out.println();
    }

    // ==================== 二、实战 ====================

    private static void demonstrateCircularDep() {
        System.out.println("=== 二、循环依赖解决过程模拟 ===\n");

        MiniIocWithCache container = new MiniIocWithCache();
        container.registerBean("serviceA", ServiceA.class);
        container.registerBean("serviceB", ServiceB.class);

        System.out.println("  场景：ServiceA 依赖 ServiceB，ServiceB 依赖 ServiceA（循环）");
        System.out.println("  模拟 getBean(\"serviceA\") 的完整过程...\n");

        ServiceA a = (ServiceA) container.getBean("serviceA");

        System.out.println("\n  最终结果：");
        System.out.println("    a         = " + a);
        System.out.println("    a.serviceB = " + a.serviceB);
        System.out.println("    a.serviceB.serviceA == a ? " + (a.serviceB.serviceA == a));
        System.out.println();
    }

    // ==================== 三、构造器注入为什么不行 ====================

    private static void showConstructorCaseExplanation() {
        System.out.println("=== 三、为什么构造器循环依赖无法解决？ ===\n");

        System.out.println("  字段注入 / Setter 注入：");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. new ServiceA()                 ← 实例化成功               │");
        System.out.println("  │ 2. 放入三级缓存                    ← 此时可被循环依赖方获取  │");
        System.out.println("  │ 3. 注入字段 serviceB              ← 触发循环，从缓存拿到A    │");
        System.out.println("  │ 4. 完成属性填充和初始化                                       │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  构造器注入：");
        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 1. new ServiceA(serviceB)         ← 需要 B 才能实例化         │");
        System.out.println("  │   ├── getBean(\"serviceB\")                                    │");
        System.out.println("  │   └── new ServiceB(serviceA)      ← 需要 A 才能实例化         │");
        System.out.println("  │       └── A 还没实例化完成，无法放入三级缓存                 │");
        System.out.println("  │                                                              │");
        System.out.println("  │ → 抛出 BeanCurrentlyInCreationException ✗                    │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  结论：循环依赖的前提是「至少一方的实例化可以独立于另一方」");
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了三级缓存后，下一步：
 * → 真实 Spring 中循环依赖的处理（见 CircularDependencyDemo.java）
 * → AOP 代理如何通过三级缓存注入？（见 D18 AOP）
 */
