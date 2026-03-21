package com.kungfu.jvm.d4_classloading;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Driver;
import java.util.ServiceLoader;

/**
 * 【Demo】打破双亲委派模型
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>三种打破双亲委派的经典场景（SPI、Tomcat、热部署）</li>
 *   <li>自定义 ClassLoader 实现类隔离</li>
 *   <li>线程上下文类加载器（Thread Context ClassLoader）</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * 面试高频追问："什么情况下需要打破双亲委派？怎么打破？"
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法。
 *
 * @author kungfu
 * @since D4 - 类加载机制
 */
public class BreakParentDelegationDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  打破双亲委派模型");
        System.out.println("========================================\n");

        showBreakScenarios();
        testSPI();
        testCustomClassLoader();
        showTomcatClassLoader();
        showSummary();
    }

    // =============================================================
    // 一、三种打破双亲委派的场景
    // =============================================================
    private static void showBreakScenarios() {
        System.out.println("=== 一、为什么要打破双亲委派？===\n");

        System.out.println("  双亲委派的局限：父加载器无法访问子加载器加载的类");
        System.out.println("  → 但有时候核心类需要调用用户代码（如 JDBC、JNDI）");
        System.out.println();

        System.out.println("  三大经典场景:");
        System.out.println("  ┌───┬────────────┬────────────────────────────────────────┐");
        System.out.println("  │ # │ 场景        │ 原因                                    │");
        System.out.println("  ├───┼────────────┼────────────────────────────────────────┤");
        System.out.println("  │ 1 │ SPI 机制    │ Bootstrap 加载的接口需要调用厂商实现       │");
        System.out.println("  │   │ (JDBC等)    │ → 用线程上下文类加载器(TCCL)向下委派       │");
        System.out.println("  ├───┼────────────┼────────────────────────────────────────┤");
        System.out.println("  │ 2 │ Tomcat      │ 不同 Web 应用需要加载不同版本的同名类      │");
        System.out.println("  │   │             │ → 每个 WebApp 有独立的 ClassLoader         │");
        System.out.println("  │   │             │ → 优先加载自己 WEB-INF/lib，再委派父加载器 │");
        System.out.println("  ├───┼────────────┼────────────────────────────────────────┤");
        System.out.println("  │ 3 │ 热部署      │ 需要在不重启 JVM 的情况下替换类            │");
        System.out.println("  │   │ (OSGi等)    │ → 丢弃旧的 ClassLoader，创建新的加载新类   │");
        System.out.println("  └───┴────────────┴────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、SPI 机制（线程上下文类加载器）
    // =============================================================
    private static void testSPI() {
        System.out.println("=== 二、SPI 机制 — 线程上下文类加载器 ===\n");

        System.out.println("  问题：JDBC 接口 java.sql.Driver 在 rt.jar 中（Bootstrap 加载）");
        System.out.println("        MySQL 实现 com.mysql.jdbc.Driver 在 classpath 中（App 加载）");
        System.out.println("        Bootstrap 加载器看不到 classpath → 无法加载 MySQL 驱动！");
        System.out.println();

        System.out.println("  解决：线程上下文类加载器（Thread Context ClassLoader / TCCL）");
        System.out.println("  ┌────────────────────────────────────────────────────────┐");
        System.out.println("  │ Bootstrap 加载 DriverManager                           │");
        System.out.println("  │   ↓                                                    │");
        System.out.println("  │ DriverManager.getConnection() 内部:                    │");
        System.out.println("  │   ClassLoader cl = Thread.currentThread()              │");
        System.out.println("  │                         .getContextClassLoader();       │");
        System.out.println("  │   → TCCL 默认是 AppClassLoader                        │");
        System.out.println("  │   → 用 TCCL 加载 MySQL 驱动（向下委派！反双亲委派！）    │");
        System.out.println("  │   ServiceLoader.load(Driver.class, cl)                 │");
        System.out.println("  └────────────────────────────────────────────────────────┘\n");

        // 实际演示 TCCL
        System.out.println("  当前线程上下文类加载器:");
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        System.out.println("    " + tccl);
        System.out.println("  → 默认就是 AppClassLoader");
        System.out.println();

        // 演示 ServiceLoader（SPI 的标准实现）
        System.out.println("  ServiceLoader 加载 JDBC 驱动:");
        System.out.println("    ServiceLoader.load(Driver.class) 会读取:");
        System.out.println("    META-INF/services/java.sql.Driver 文件");
        System.out.println("    → 里面写着实现类的全限定名（如 com.mysql.cj.jdbc.Driver）");
        System.out.println("    → ServiceLoader 用 TCCL 去加载这些实现类");
        System.out.println();

        // 列出当前可用的 JDBC 驱动
        ServiceLoader<Driver> drivers = ServiceLoader.load(Driver.class);
        System.out.println("  当前 JVM 中通过 SPI 发现的 JDBC 驱动:");
        boolean found = false;
        for (Driver driver : drivers) {
            System.out.println("    " + driver.getClass().getName()
                    + " (由 " + driver.getClass().getClassLoader() + " 加载)");
            found = true;
        }
        if (!found) {
            System.out.println("    (未发现任何 JDBC 驱动，classpath 中没有数据库驱动 jar)");
        }
        System.out.println();

        System.out.println("  ★ SPI 打破双亲委派的本质:");
        System.out.println("    正常: 子→父→父→... （向上委派）");
        System.out.println("    SPI:  Bootstrap 用 TCCL（AppClassLoader）加载（向下委派）\n");
    }

    // =============================================================
    // 三、自定义 ClassLoader（模拟类隔离）
    // =============================================================

    /**
     * 自定义类加载器 — 重写 findClass()（推荐方式）
     * 只重写 findClass 不破坏双亲委派（父加载器找不到才调 findClass）
     */
    static class CustomClassLoader extends ClassLoader {
        private String classPath;

        public CustomClassLoader(String classPath) {
            this.classPath = classPath;
        }

        public CustomClassLoader(String classPath, ClassLoader parent) {
            super(parent);
            this.classPath = classPath;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                String fileName = classPath + File.separator
                        + name.replace('.', File.separatorChar) + ".class";
                InputStream is = new FileInputStream(fileName);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                is.close();
                byte[] classData = baos.toByteArray();
                return defineClass(name, classData, 0, classData.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    private static void testCustomClassLoader() {
        System.out.println("=== 三、自定义 ClassLoader ===\n");

        System.out.println("  两种自定义方式:");
        System.out.println("  ┌─────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 方式             │ 说明                                  │");
        System.out.println("  ├─────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 重写 findClass() │ 推荐！保持双亲委派                     │");
        System.out.println("  │                 │ 父加载器找不到才调用你的 findClass       │");
        System.out.println("  ├─────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ 重写 loadClass() │ 打破双亲委派！                         │");
        System.out.println("  │                 │ 可以改变委派逻辑（先自己加载再委派父类） │");
        System.out.println("  └─────────────────┴──────────────────────────────────────┘\n");

        // 验证：同一个类由不同 ClassLoader 加载 → JVM 认为是不同类
        System.out.println("  ★ 关键概念：类的唯一性 = 类全名 + ClassLoader");
        System.out.println("    同一个 .class 文件，被两个不同的 ClassLoader 加载");
        System.out.println("    → JVM 认为是两个不同的类！");
        System.out.println("    → instanceof 判断为 false");
        System.out.println("    → 两个类的 static 变量互不影响");
        System.out.println();

        // 演示验证
        ClassLoader loader1 = ParentDelegationDemo.class.getClassLoader();
        ClassLoader loader2 = ParentDelegationDemo.class.getClassLoader();
        System.out.println("  验证: 同一个 ClassLoader 加载同一个类 → 同一个 Class 对象");
        Class<?> class1 = ParentDelegationDemo.class;
        Class<?> class2 = ParentDelegationDemo.class;
        System.out.println("    class1 == class2 : " + (class1 == class2) + " (同一个 ClassLoader)\n");
    }

    // =============================================================
    // 四、Tomcat 的类加载器
    // =============================================================
    private static void showTomcatClassLoader() {
        System.out.println("=== 四、Tomcat 类加载器（面试高频）===\n");

        System.out.println("  Tomcat 为什么要打破双亲委派？");
        System.out.println("  → 一个 Tomcat 部署多个 Web 应用");
        System.out.println("  → App1 用 Spring 4，App2 用 Spring 5");
        System.out.println("  → 同名类 org.springframework.xxx 必须互相隔离！\n");

        System.out.println("  Tomcat 类加载器层次:");
        System.out.println("  ┌─────────────────────────────────────────────┐");
        System.out.println("  │            Bootstrap ClassLoader            │");
        System.out.println("  │            ↓                                │");
        System.out.println("  │         Extension ClassLoader               │");
        System.out.println("  │            ↓                                │");
        System.out.println("  │        Application ClassLoader              │");
        System.out.println("  │            ↓                                │");
        System.out.println("  │ ┌──────────────────────────────┐            │");
        System.out.println("  │ │     Common ClassLoader       │ Tomcat公共库│");
        System.out.println("  │ │     (catalina.properties)    │            │");
        System.out.println("  │ └──────────┬───────────────────┘            │");
        System.out.println("  │     ┌──────┴──────┐                        │");
        System.out.println("  │     ↓             ↓                        │");
        System.out.println("  │ ┌─────────┐  ┌─────────┐                   │");
        System.out.println("  │ │ WebApp1 │  │ WebApp2 │  各自独立!         │");
        System.out.println("  │ │Loader   │  │Loader   │  互不影响!         │");
        System.out.println("  │ └─────────┘  └─────────┘                   │");
        System.out.println("  └─────────────────────────────────────────────┘\n");

        System.out.println("  WebAppClassLoader 的加载顺序（打破双亲委派）:");
        System.out.println("  1. 先从自己的 WEB-INF/classes 找");
        System.out.println("  2. 再从自己的 WEB-INF/lib/*.jar 找");
        System.out.println("  3. 最后才委派给父加载器");
        System.out.println("  → 和标准双亲委派相反！先自己找，找不到才向上委派\n");

        System.out.println("  但 Tomcat 仍然保护核心类:");
        System.out.println("  → java.* 和 javax.* 开头的类强制走双亲委派");
        System.out.println("  → 防止 Web 应用替换 JDK 核心类\n");
    }

    // =============================================================
    // 五、总结
    // =============================================================
    private static void showSummary() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  Q: 怎么打破双亲委派？");
        System.out.println("  A: 重写 loadClass() 方法，改变委派逻辑\n");

        System.out.println("  Q: SPI 怎么打破的？");
        System.out.println("  A: Bootstrap 加载的 DriverManager 用线程上下文类加载器(TCCL)");
        System.out.println("     加载 classpath 中的驱动实现 → 向下委派\n");

        System.out.println("  Q: Tomcat 怎么打破的？");
        System.out.println("  A: WebAppClassLoader 优先加载 WEB-INF 下的类");
        System.out.println("     每个 Web 应用独立的 ClassLoader 实现类隔离\n");

        System.out.println("  Q: 热部署怎么实现？");
        System.out.println("  A: 丢弃旧 ClassLoader（让它被 GC），创建新的 ClassLoader");
        System.out.println("     重新加载修改后的类 → 新旧类由不同 ClassLoader 加载 → 互不影响\n");

        System.out.println("  Q: 类的唯一性由什么决定？");
        System.out.println("  A: 类全限定名 + 加载它的 ClassLoader");
        System.out.println("     同一个 .class 被不同 ClassLoader 加载 → 不是同一个类");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * D4 全部知识点：
 *   1. ClassLoadingProcessDemo     — 类加载五阶段 + clinit 顺序
 *   2. ParentDelegationDemo        — 双亲委派模型
 *   3. BreakParentDelegationDemo   — 打破双亲委派（SPI/Tomcat/热部署）
 *
 * W01-JVM 完整路线：
 *   D1 → 内存模型
 *   D2 → GC
 *   D3 → 调优
 *   D4 → 类加载（本模块）
 */