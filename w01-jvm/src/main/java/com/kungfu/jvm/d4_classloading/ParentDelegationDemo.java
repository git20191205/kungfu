package com.kungfu.jvm.d4_classloading;

/**
 * 【Demo】双亲委派模型演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>三级类加载器层次：Bootstrap → Extension → Application</li>
 *   <li>双亲委派的工作流程和意义</li>
 *   <li>验证不同类由哪个加载器加载</li>
 *   <li>Class.forName() vs ClassLoader.loadClass() 的区别</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试必问："什么是双亲委派？为什么需要？"
 * - 理解后才能理解 Tomcat、SPI、热部署为什么要打破双亲委派
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法。可加 -verbose:class 观察类加载过程。
 *
 * @author kungfu
 * @since D4 - 类加载机制
 */
public class ParentDelegationDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  双亲委派模型演示");
        System.out.println("========================================\n");

        showClassLoaderHierarchy();
        testWhichLoaderLoaded();
        showDelegationProcess();
        testForNameVsLoadClass();
        showWhyParentDelegation();
        showSummary();
    }

    // =============================================================
    // 一、三级类加载器
    // =============================================================
    private static void showClassLoaderHierarchy() {
        System.out.println("=== 一、三级类加载器 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────┐");
        System.out.println("  │          Bootstrap ClassLoader                  │");
        System.out.println("  │          (启动类加载器 / 引导类加载器)             │");
        System.out.println("  │          C++ 实现，Java 中看到的是 null           │");
        System.out.println("  │          加载: rt.jar (java.lang.*, java.util.*) │");
        System.out.println("  │                JAVA_HOME/lib 下的核心库           │");
        System.out.println("  └──────────────────────┬──────────────────────────┘");
        System.out.println("                         │ 父加载器");
        System.out.println("  ┌──────────────────────▼──────────────────────────┐");
        System.out.println("  │          Extension ClassLoader                  │");
        System.out.println("  │          (扩展类加载器)                           │");
        System.out.println("  │          sun.misc.Launcher$ExtClassLoader        │");
        System.out.println("  │          加载: JAVA_HOME/lib/ext 下的扩展库       │");
        System.out.println("  └──────────────────────┬──────────────────────────┘");
        System.out.println("                         │ 父加载器");
        System.out.println("  ┌──────────────────────▼──────────────────────────┐");
        System.out.println("  │          Application ClassLoader                │");
        System.out.println("  │          (应用类加载器 / 系统类加载器)             │");
        System.out.println("  │          sun.misc.Launcher$AppClassLoader        │");
        System.out.println("  │          加载: classpath 下的所有类               │");
        System.out.println("  │          → 我们写的代码默认由它加载                │");
        System.out.println("  └──────────────────────┬──────────────────────────┘");
        System.out.println("                         │ 父加载器");
        System.out.println("  ┌──────────────────────▼──────────────────────────┐");
        System.out.println("  │          自定义 ClassLoader（可选）               │");
        System.out.println("  │          继承 java.lang.ClassLoader              │");
        System.out.println("  │          场景: Tomcat / OSGi / 热部署             │");
        System.out.println("  └─────────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、验证类加载器
    // =============================================================
    private static void testWhichLoaderLoaded() {
        System.out.println("=== 二、各种类由哪个加载器加载？===\n");

        // 1. Bootstrap 加载的类
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("  String.class.getClassLoader()    = " + stringLoader);
        System.out.println("  → null 表示 Bootstrap ClassLoader（C++ 实现，Java 中无法引用）");
        System.out.println();

        // 2. Extension 加载的类（JDK 8）
        // DNSNameService 在 ext 目录中
        try {
            Class<?> dnsClass = Class.forName("sun.net.spi.nameservice.dns.DNSNameService");
            System.out.println("  DNSNameService.getClassLoader()  = " + dnsClass.getClassLoader());
            System.out.println("  → ExtClassLoader 加载的扩展库类");
        } catch (ClassNotFoundException e) {
            System.out.println("  (DNS 扩展类未找到，不同 JDK 版本可能不包含)");
        }
        System.out.println();

        // 3. Application 加载的类
        ClassLoader selfLoader = ParentDelegationDemo.class.getClassLoader();
        System.out.println("  ParentDelegationDemo.getClassLoader() = " + selfLoader);
        System.out.println("  → AppClassLoader 加载我们自己写的类");
        System.out.println();

        // 4. 打印加载器链
        System.out.println("  加载器链（从子到父）:");
        ClassLoader loader = ParentDelegationDemo.class.getClassLoader();
        while (loader != null) {
            System.out.println("    " + loader);
            loader = loader.getParent();
        }
        System.out.println("    null (Bootstrap ClassLoader)");
        System.out.println();
    }

    // =============================================================
    // 三、双亲委派流程
    // =============================================================
    private static void showDelegationProcess() {
        System.out.println("=== 三、双亲委派工作流程 ===\n");

        System.out.println("  当 AppClassLoader 收到加载 \"com.kungfu.Foo\" 的请求：");
        System.out.println();
        System.out.println("  ① AppClassLoader: 我先不加载，问问我爸");
        System.out.println("     ↓ 委派给父加载器");
        System.out.println("  ② ExtClassLoader: 我也先不加载，问问我爸");
        System.out.println("     ↓ 委派给父加载器");
        System.out.println("  ③ Bootstrap: 我是顶层了，在 rt.jar 里找...");
        System.out.println("     → 没找到，返回给儿子");
        System.out.println("  ④ ExtClassLoader: 爸没找到，我在 ext 目录找...");
        System.out.println("     → 没找到，返回给儿子");
        System.out.println("  ⑤ AppClassLoader: 爸和爷爷都没找到，我自己在 classpath 找...");
        System.out.println("     → 找到了！加载 com.kungfu.Foo");
        System.out.println();

        System.out.println("  核心代码（ClassLoader.loadClass 简化版）:");
        System.out.println("  ┌────────────────────────────────────────────┐");
        System.out.println("  │ protected Class<?> loadClass(String name)  │");
        System.out.println("  │ {                                          │");
        System.out.println("  │   // 1. 先检查是否已经加载过                 │");
        System.out.println("  │   Class<?> c = findLoadedClass(name);      │");
        System.out.println("  │   if (c == null) {                         │");
        System.out.println("  │     // 2. 委派给父加载器                     │");
        System.out.println("  │     if (parent != null) {                  │");
        System.out.println("  │       c = parent.loadClass(name);          │");
        System.out.println("  │     } else {                               │");
        System.out.println("  │       c = findBootstrapClass(name);        │");
        System.out.println("  │     }                                      │");
        System.out.println("  │     // 3. 父加载器没找到，自己加载            │");
        System.out.println("  │     if (c == null) {                       │");
        System.out.println("  │       c = findClass(name); // 子类重写这个   │");
        System.out.println("  │     }                                      │");
        System.out.println("  │   }                                        │");
        System.out.println("  │   return c;                                │");
        System.out.println("  │ }                                          │");
        System.out.println("  └────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 四、Class.forName() vs ClassLoader.loadClass()
    // =============================================================
    static class ForNameDemo {
        static {
            System.out.println("    → ForNameDemo 的 static {} 被执行了！");
        }
    }

    private static void testForNameVsLoadClass() throws Exception {
        System.out.println("=== 四、Class.forName() vs ClassLoader.loadClass() ===\n");

        System.out.println("  1. Class.forName() → 加载 + 初始化（执行 static {}）");
        System.out.println("     调用 Class.forName(\"...ForNameDemo\"):");
        Class.forName("com.kungfu.jvm.d4_classloading.ParentDelegationDemo$ForNameDemo");
        System.out.println();

        System.out.println("  2. ClassLoader.loadClass() → 只加载，不初始化");
        System.out.println("     (ForNameDemo 已经初始化过了，这里用说明代替)");
        System.out.println("     ClassLoader.getSystemClassLoader().loadClass(\"Foo\")");
        System.out.println("     → 只执行加载阶段，static {} 不会执行");
        System.out.println();

        System.out.println("  ┌──────────────────────┬──────────────┬──────────────┐");
        System.out.println("  │                      │ Class.forName│ loadClass()  │");
        System.out.println("  ├──────────────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 加载类                │ ✓            │ ✓            │");
        System.out.println("  │ 执行 static {}       │ ✓ (默认)     │ ✗            │");
        System.out.println("  │ 典型使用场景          │ JDBC驱动注册  │ Spring懒加载  │");
        System.out.println("  └──────────────────────┴──────────────┴──────────────┘\n");

        System.out.println("  ★ JDBC 经典面试题:");
        System.out.println("    Class.forName(\"com.mysql.jdbc.Driver\") 为什么能注册驱动？");
        System.out.println("    → Driver 类的 static {} 中调用了 DriverManager.registerDriver()");
        System.out.println("    → Class.forName() 触发了初始化 → static {} 执行 → 驱动注册\n");
    }

    // =============================================================
    // 五、为什么需要双亲委派
    // =============================================================
    private static void showWhyParentDelegation() {
        System.out.println("=== 五、为什么需要双亲委派？===\n");

        System.out.println("  1. 安全性：防止核心类被篡改");
        System.out.println("     假设你写了一个 java.lang.String 类");
        System.out.println("     → 双亲委派: 请求先到 Bootstrap → 发现 rt.jar 里有 → 用核心库的");
        System.out.println("     → 你的「假 String」永远不会被加载");
        System.out.println("     → 防止恶意代码替换核心类（沙箱安全机制）");
        System.out.println();

        System.out.println("  2. 唯一性：保证核心类在 JVM 中只有一份");
        System.out.println("     → 两个不同加载器加载同一个类 → JVM 认为是不同的类！");
        System.out.println("     → instanceof 判断会失败");
        System.out.println("     → 双亲委派保证核心类由 Bootstrap 统一加载，全局唯一");
        System.out.println();

        System.out.println("  3. 避免重复加载：");
        System.out.println("     → 先找父加载器已加载的类 → 避免同一个类被加载多次\n");
    }

    // =============================================================
    // 六、总结
    // =============================================================
    private static void showSummary() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  Q: 什么是双亲委派？");
        System.out.println("  A: 收到类加载请求时，先委派父加载器处理");
        System.out.println("     父加载器找不到时，自己才尝试加载");
        System.out.println("     → 先父后己，逐层向上委派\n");

        System.out.println("  Q: 三级类加载器？");
        System.out.println("  A: Bootstrap(rt.jar) → Extension(ext) → Application(classpath)\n");

        System.out.println("  Q: 为什么需要双亲委派？");
        System.out.println("  A: 安全（防核心类被替换）+ 唯一性（同一个类只加载一次）\n");

        System.out.println("  Q: Class.forName 和 loadClass 的区别？");
        System.out.println("  A: forName 会初始化（执行static{}），loadClass 只加载不初始化");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 双亲委派 → 引出打破双亲委派的场景
 * → SPI（JDBC、JNDI）
 * → Tomcat（每个 Web 应用独立的 ClassLoader）
 * → 热部署（自定义 ClassLoader 实现类替换）
 *
 * 接下来看打破双亲委派 → BreakParentDelegationDemo.java
 */