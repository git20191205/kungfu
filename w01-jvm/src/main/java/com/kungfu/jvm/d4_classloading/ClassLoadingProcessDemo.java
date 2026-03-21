package com.kungfu.jvm.d4_classloading;

/**
 * 【Demo】类加载过程演示
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>类加载的五个阶段：加载 → 验证 → 准备 → 解析 → 初始化</li>
 *   <li>准备阶段 vs 初始化阶段对 static 变量的影响</li>
 *   <li>类初始化的触发时机（6 种主动引用 vs 被动引用）</li>
 *   <li>经典笔试题：静态变量和静态代码块的执行顺序</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * - 面试必问："类加载的过程？"、"static 变量什么时候赋值？"
 * - 理解 clinit 才能理解单例模式中的线程安全问题
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法。可加 -verbose:class 观察类加载顺序。
 *
 * @author kungfu
 * @since D4 - 类加载机制
 */
public class ClassLoadingProcessDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  类加载过程演示");
        System.out.println("========================================\n");

        showLoadingProcess();
        testPrepareVsInit();
        testActiveReference();
        testPassiveReference();
        testClinitOrder();
        showSummary();
    }

    // =============================================================
    // 一、类加载的五个阶段
    // =============================================================
    private static void showLoadingProcess() {
        System.out.println("=== 一、类加载的五个阶段 ===\n");

        System.out.println("  加载 → 验证 → 准备 → 解析 → 初始化");
        System.out.println("  ─────────────────────────────────────");
        System.out.println("  │      连接（Linking）阶段     │");
        System.out.println();

        System.out.println("  ┌──────────┬──────────────────────────────────────────────┐");
        System.out.println("  │ 阶段      │ 做了什么                                      │");
        System.out.println("  ├──────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 1.加载    │ 通过类全名获取字节流 → 转为方法区的运行时数据    │");
        System.out.println("  │ (Loading) │ → 生成 java.lang.Class 对象（堆中）             │");
        System.out.println("  │          │ 字节流来源: .class文件/jar/网络/动态代理生成      │");
        System.out.println("  ├──────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 2.验证    │ 确保字节码合法安全                              │");
        System.out.println("  │ (Verify)  │ → 文件格式: 魔数 CAFEBABE、版本号               │");
        System.out.println("  │          │ → 元数据: 是否有父类、是否继承 final 类          │");
        System.out.println("  │          │ → 字节码: 指令是否合法、类型转换是否安全          │");
        System.out.println("  │          │ → 符号引用: 引用的类/方法/字段是否存在            │");
        System.out.println("  ├──────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 3.准备    │ 为 static 变量分配内存并设「零值」              │");
        System.out.println("  │ (Prepare) │ → int → 0, boolean → false, 引用 → null       │");
        System.out.println("  │          │ → static final 常量直接赋值（编译期确定的值）    │");
        System.out.println("  │          │ ★ 注意: 只是零值，不是代码中写的初始值！         │");
        System.out.println("  ├──────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 4.解析    │ 把符号引用替换为直接引用                        │");
        System.out.println("  │ (Resolve) │ → 符号引用: 类全名字符串 \"java/lang/Object\"    │");
        System.out.println("  │          │ → 直接引用: 内存中的指针/偏移量                  │");
        System.out.println("  ├──────────┼──────────────────────────────────────────────┤");
        System.out.println("  │ 5.初始化  │ 执行 <clinit>() 方法                           │");
        System.out.println("  │ (Init)   │ → 按代码顺序合并: static 变量赋值 + static {} 块 │");
        System.out.println("  │          │ → JVM 保证 <clinit> 线程安全（加锁，只执行一次）  │");
        System.out.println("  │          │ → 父类的 <clinit> 先于子类执行                   │");
        System.out.println("  └──────────┴──────────────────────────────────────────────┘\n");
    }

    // =============================================================
    // 二、准备阶段 vs 初始化阶段
    // =============================================================
    static class PrepareDemo {
        // 准备阶段: value = 0（int 的零值）
        // 初始化阶段: value = 123（执行 <clinit> 赋值）
        static int value = 123;

        // static final + 编译期常量 → 准备阶段就直接赋值 456
        static final int CONSTANT = 456;

        // static final + 运行期才确定的值 → 初始化阶段才赋值
        static final int RUNTIME_CONSTANT = new java.util.Random().nextInt(100);
    }

    private static void testPrepareVsInit() {
        System.out.println("=== 二、准备阶段 vs 初始化阶段 ===\n");

        System.out.println("  static int value = 123;");
        System.out.println("    准备阶段 → value = 0（零值）");
        System.out.println("    初始化阶段 → value = 123（代码赋值）");
        System.out.println("    实际值: " + PrepareDemo.value);
        System.out.println();

        System.out.println("  static final int CONSTANT = 456;");
        System.out.println("    准备阶段就赋值 456（编译期常量，存在常量池中）");
        System.out.println("    实际值: " + PrepareDemo.CONSTANT);
        System.out.println();

        System.out.println("  static final int RUNTIME_CONSTANT = new Random().nextInt(100);");
        System.out.println("    准备阶段 → 0（零值，因为运行期才能确定）");
        System.out.println("    初始化阶段 → 随机值");
        System.out.println("    实际值: " + PrepareDemo.RUNTIME_CONSTANT);
        System.out.println();

        System.out.println("  ★ 结论:");
        System.out.println("    static final + 编译期常量 → 准备阶段直接赋值（ConstantValue 属性）");
        System.out.println("    其他 static 变量 → 准备阶段零值，初始化阶段才赋值\n");
    }

    // =============================================================
    // 三、类初始化的触发时机（主动引用）
    // =============================================================
    static class InitDemo {
        static {
            System.out.println("    → InitDemo 的 <clinit> 被执行了！");
        }
        static int value = 42;
    }

    static class SubInitDemo extends InitDemo {
        static {
            System.out.println("    → SubInitDemo 的 <clinit> 被执行了！");
        }
        static int subValue = 100;
    }

    private static void testActiveReference() {
        System.out.println("=== 三、主动引用（会触发初始化）===\n");

        System.out.println("  6 种触发类初始化的场景（主动引用）:");
        System.out.println("  ┌───┬──────────────────────────────────────────────────┐");
        System.out.println("  │ # │ 场景                                              │");
        System.out.println("  ├───┼──────────────────────────────────────────────────┤");
        System.out.println("  │ 1 │ new 对象、读写 static 字段、调用 static 方法       │");
        System.out.println("  │ 2 │ 反射调用: Class.forName(\"xxx\")                   │");
        System.out.println("  │ 3 │ 初始化子类时，父类未初始化则先初始化父类            │");
        System.out.println("  │ 4 │ 虚拟机启动时，初始化 main() 所在的类               │");
        System.out.println("  │ 5 │ JDK 7+ MethodHandle 解析到的类                    │");
        System.out.println("  │ 6 │ 接口定义 default 方法，实现类初始化时触发接口初始化  │");
        System.out.println("  └───┴──────────────────────────────────────────────────┘\n");

        System.out.println("  演示1: new 对象触发初始化");
        InitDemo obj = new InitDemo();
        System.out.println("    InitDemo.value = " + InitDemo.value);
        System.out.println();

        System.out.println("  演示2: 子类初始化会先触发父类初始化");
        System.out.println("    访问 SubInitDemo.subValue:");
        int v = SubInitDemo.subValue;
        System.out.println("    (注意: 父类 clinit 先于子类执行)\n");
    }

    // =============================================================
    // 四、被动引用（不会触发初始化）
    // =============================================================
    static class PassiveParent {
        static int parentValue = 1;
        static {
            System.out.println("    → PassiveParent 被初始化了！");
        }
    }

    static class PassiveChild extends PassiveParent {
        static int childValue = 2;
        static {
            System.out.println("    → PassiveChild 被初始化了！");
        }
    }

    static class ConstantClass {
        static final String GREETING = "hello";
        static {
            System.out.println("    → ConstantClass 被初始化了！");
        }
    }

    private static void testPassiveReference() {
        System.out.println("=== 四、被动引用（不会触发初始化）===\n");

        System.out.println("  场景1: 通过子类访问父类 static 字段 → 只初始化父类，不初始化子类");
        System.out.println("    PassiveChild.parentValue:");
        int v1 = PassiveChild.parentValue;
        System.out.println("    (PassiveChild 没有被初始化！)\n");

        System.out.println("  场景2: 数组定义 → 不触发元素类的初始化");
        System.out.println("    new PassiveParent[10]:");
        PassiveParent[] arr = new PassiveParent[10];
        System.out.println("    (只创建了数组对象，PassiveParent 不会再次初始化)\n");

        System.out.println("  场景3: 引用 static final 编译期常量 → 不触发类初始化");
        System.out.println("    ConstantClass.GREETING:");
        String s = ConstantClass.GREETING;
        System.out.println("    值 = " + s);
        System.out.println("    (ConstantClass 没有被初始化！常量在编译期已内联到调用方)\n");

        System.out.println("  ★ 被动引用三条总结:");
        System.out.println("    1. 子类引用父类 static 字段 → 只初始化父类");
        System.out.println("    2. 数组定义引用类 → 不初始化元素类");
        System.out.println("    3. 引用编译期 static final 常量 → 不初始化该类\n");
    }

    // =============================================================
    // 五、<clinit> 执行顺序（经典笔试题）
    // =============================================================
    static class OrderDemo {
        static int a = 1;           // ① 先执行
        static {                     // ② 再执行
            a = 2;
            b = 2;                   // 可以赋值（b 在准备阶段已分配内存）
            // int x = b;            // 编译错误！不能在声明前读取（非法前向引用）
        }
        static int b = 1;           // ③ 最后执行（覆盖 static {} 中的赋值）
    }

    static class ParentInit {
        static int A = 1;
        static { A = 2; }
    }

    static class ChildInit extends ParentInit {
        static int B = A;   // 此时父类已初始化完，A = 2
    }

    private static void testClinitOrder() {
        System.out.println("=== 五、<clinit> 执行顺序（笔试经典）===\n");

        System.out.println("  代码顺序:");
        System.out.println("    static int a = 1;     // ① a=1");
        System.out.println("    static { a=2; b=2; }  // ② a=2, b=2");
        System.out.println("    static int b = 1;     // ③ b=1（覆盖了②中的 b=2）");
        System.out.println();
        System.out.println("  结果: a=" + OrderDemo.a + ", b=" + OrderDemo.b);
        System.out.println("  → a=2, b=1（按代码从上到下的顺序执行）\n");

        System.out.println("  父子类 clinit 顺序:");
        System.out.println("    ParentInit: A=1, static{A=2}  → A=2");
        System.out.println("    ChildInit:  B=A                → B=2（父类先初始化）");
        System.out.println("  结果: ChildInit.B = " + ChildInit.B + "\n");

        System.out.println("  ★ clinit 线程安全:");
        System.out.println("    JVM 保证 <clinit>() 在多线程环境下被正确地加锁同步");
        System.out.println("    → 这就是「静态内部类单例」线程安全的原因！");
        System.out.println("    → 如果 clinit 中有耗时操作，可能导致其他线程阻塞\n");
    }

    // =============================================================
    // 六、总结
    // =============================================================
    private static void showSummary() {
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");

        System.out.println("  Q: 类加载的过程？");
        System.out.println("  A: 加载 → 验证 → 准备 → 解析 → 初始化");
        System.out.println("     (\"加眼准姐出\" 谐音记忆)\n");

        System.out.println("  Q: 准备阶段做了什么？");
        System.out.println("  A: 给 static 变量分配内存设零值");
        System.out.println("     static final 编译期常量直接赋值\n");

        System.out.println("  Q: 什么时候触发类初始化？");
        System.out.println("  A: new / 读写static字段 / 反射 / 子类触发父类 / main类\n");

        System.out.println("  Q: <clinit>() 线程安全吗？");
        System.out.println("  A: 安全。JVM 加锁保证只执行一次，这是静态内部类单例的原理");
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * 类加载过程 → 引出两个关联知识：
 * → 准备阶段零值 + 初始化阶段赋值 → 影响 static 变量的可见性
 * → clinit 线程安全 → 静态内部类单例模式 → W02 并发编程
 *
 * 接下来看双亲委派 → ParentDelegationDemo.java
 */