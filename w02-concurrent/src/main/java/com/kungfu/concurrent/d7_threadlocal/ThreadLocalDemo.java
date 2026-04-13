package com.kungfu.concurrent.d7_threadlocal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 【Demo】ThreadLocal — 线程局部变量
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>ThreadLocal 每个线程独立副本的基本用法</li>
 *   <li>ThreadLocalMap 底层结构与内存泄漏原理</li>
 *   <li>InheritableThreadLocal 父子线程传值</li>
 *   <li>实际应用场景：用户上下文传递、日期格式化</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * ThreadLocal 是实现线程隔离的核心工具，广泛用于 Web 请求上下文、
 * 数据库连接管理等场景，也是面试中内存泄漏问题的高频考点。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可
 *
 *
 *
 * @author kungfu
 * @since D15 - 并发编程
 */
public class ThreadLocalDemo {

    // ========== ThreadLocal 实例 ==========
    private static final ThreadLocal<String> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 一、ThreadLocal 基本用法
     * 每个线程设置各自的值，互不干扰。
     */
    private static void demonstrateBasicUsage() throws InterruptedException {
        System.out.println("=== 一、ThreadLocal 基本用法 ===\n");

        System.out.println("  3 个线程各自设置不同的值，验证线程隔离性\n");

        CountDownLatch latch = new CountDownLatch(3);
        String[] names = {"Alice", "Bob", "Charlie"};

        for (int i = 0; i < 3; i++) {
            final String name = names[i];
            new Thread(() -> {
                // 每个线程设置自己的值
                THREAD_LOCAL.set(name);
                System.out.println("  " + Thread.currentThread().getName()
                        + " set: " + name);

                // 模拟业务处理
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // 读取自己的值（不会被其他线程影响）
                String value = THREAD_LOCAL.get();
                System.out.println("  " + Thread.currentThread().getName()
                        + " get: " + value + " (线程隔离，值未被覆盖)");

                // 用完必须 remove
                THREAD_LOCAL.remove();
                latch.countDown();
            }, "Thread-" + name).start();
        }

        latch.await();
        System.out.println();
    }

    /**
     * 二、ThreadLocal 原理
     * 通过 ASCII 图解释 Thread → ThreadLocalMap → Entry 结构。
     */
    private static void showThreadLocalStructure() {
        System.out.println("=== 二、ThreadLocal 底层结构 ===\n");

        System.out.println("  每个 Thread 持有一个 ThreadLocalMap，key 是 ThreadLocal 实例\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │  Thread                                                 │");
        System.out.println("  │  ┌────────────────────────────────────────────────────┐  │");
        System.out.println("  │  │  ThreadLocalMap threadLocals                       │  │");
        System.out.println("  │  │                                                    │  │");
        System.out.println("  │  │  Entry[] table                                     │  │");
        System.out.println("  │  │  ┌────────────────────┐  ┌────────────────────┐    │  │");
        System.out.println("  │  │  │ Entry[i]           │  │ Entry[j]           │    │  │");
        System.out.println("  │  │  │                    │  │                    │    │  │");
        System.out.println("  │  │  │ key: WeakRef ──┐   │  │ key: WeakRef ──┐   │    │  │");
        System.out.println("  │  │  │                │   │  │                │   │    │  │");
        System.out.println("  │  │  │       ThreadLocal  │  │       ThreadLocal  │    │  │");
        System.out.println("  │  │  │       实例 A       │  │       实例 B       │    │  │");
        System.out.println("  │  │  │                    │  │                    │    │  │");
        System.out.println("  │  │  │ value: 强引用      │  │ value: 强引用      │    │  │");
        System.out.println("  │  │  │   ↓                │  │   ↓                │    │  │");
        System.out.println("  │  │  │ 用户对象 A         │  │ 用户对象 B         │    │  │");
        System.out.println("  │  │  └────────────────────┘  └────────────────────┘    │  │");
        System.out.println("  │  └────────────────────────────────────────────────────┘  │");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("  关键源码结构：");
        System.out.println("    Thread {");
        System.out.println("        ThreadLocal.ThreadLocalMap threadLocals;");
        System.out.println("    }");
        System.out.println();
        System.out.println("    ThreadLocalMap {");
        System.out.println("        Entry[] table;");
        System.out.println("        // Entry 继承 WeakReference<ThreadLocal<?>>");
        System.out.println("        static class Entry extends WeakReference<ThreadLocal<?>> {");
        System.out.println("            Object value;  // value 是强引用！");
        System.out.println("        }");
        System.out.println("    }");
        System.out.println();
    }

    /**
     * 三、内存泄漏问题
     * 演示不正确使用（缺少 remove）和正确使用（try-finally 模式）。
     */
    private static void demonstrateMemoryLeak() throws InterruptedException {
        System.out.println("=== 三、内存泄漏问题 ===\n");

        // 引用关系图
        System.out.println("  引用关系（内存泄漏的根因）：");
        System.out.println();
        System.out.println("  ┌──────────────┐   强引用   ┌───────────────────┐");
        System.out.println("  │ ThreadLocal  │──────────→│ ThreadLocal 实例  │");
        System.out.println("  │ 变量(栈上)   │            │                   │");
        System.out.println("  └──────────────┘            └───────────────────┘");
        System.out.println("                                       ↑");
        System.out.println("                                  弱引用(key)");
        System.out.println("                                       │");
        System.out.println("  ┌───────────┐  强引用  ┌─────────────┴─────────┐");
        System.out.println("  │  Thread   │────────→│ ThreadLocalMap.Entry  │");
        System.out.println("  │ (线程池中 │          │                       │");
        System.out.println("  │  不会销毁)│          │ value ──(强引用)──→ 对象│");
        System.out.println("  └───────────┘          └───────────────────────┘");
        System.out.println();
        System.out.println("  当 ThreadLocal 变量 = null 后：");
        System.out.println("    - key (弱引用) 被 GC 回收 → Entry.key = null");
        System.out.println("    - value (强引用) 不会被回收 → 内存泄漏！");
        System.out.println();

        // 错误用法
        System.out.println("  【错误用法】线程池中不调用 remove()：");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ThreadLocal<String> leakyLocal = new ThreadLocal<>();

        pool.execute(() -> {
            leakyLocal.set("敏感数据-用户A");
            System.out.println("    " + Thread.currentThread().getName()
                    + " 设置了值，但没有 remove()");
            // 忘记 remove! 线程归还线程池后，value 仍然存在
        });

        Thread.sleep(200);

        pool.execute(() -> {
            String leaked = leakyLocal.get();
            System.out.println("    " + Thread.currentThread().getName()
                    + " 复用线程，读到残留值: " + leaked);
            // 可能读到上一个任务的值（数据泄露），也可能为 null
        });

        Thread.sleep(200);
        System.out.println();

        // 正确用法
        System.out.println("  【正确用法】try-finally 确保 remove()：");
        ThreadLocal<String> safeLocal = new ThreadLocal<>();

        pool.execute(() -> {
            try {
                safeLocal.set("安全数据-用户B");
                System.out.println("    " + Thread.currentThread().getName()
                        + " 设置了值，处理业务...");
                // 业务逻辑
            } finally {
                safeLocal.remove();
                System.out.println("    " + Thread.currentThread().getName()
                        + " finally 中 remove()，清理完毕");
            }
        });

        Thread.sleep(200);

        pool.execute(() -> {
            String value = safeLocal.get();
            System.out.println("    " + Thread.currentThread().getName()
                    + " 复用线程，读到: " + value + " (null 表示已正确清理)");
        });

        Thread.sleep(200);
        pool.shutdown();
        System.out.println();
    }

    /**
     * 四、InheritableThreadLocal
     * 父线程设置值，子线程可以继承读取；但线程池中不可靠。
     */
    private static void demonstrateInheritableThreadLocal() throws InterruptedException {
        System.out.println("=== 四、InheritableThreadLocal ===\n");

        InheritableThreadLocal<String> inheritableLocal = new InheritableThreadLocal<>();

        // 父线程设置值
        inheritableLocal.set("父线程的值");
        System.out.println("  父线程设置: " + inheritableLocal.get());

        // 子线程可以继承
        Thread child = new Thread(() -> {
            System.out.println("  子线程继承到: " + inheritableLocal.get()
                    + " (创建时从父线程复制)");

            // 子线程修改不影响父线程
            inheritableLocal.set("子线程修改后的值");
            System.out.println("  子线程修改为: " + inheritableLocal.get());
        }, "ChildThread");
        child.start();
        child.join();

        System.out.println("  父线程仍然是: " + inheritableLocal.get()
                + " (子线程修改不影响父线程)");
        System.out.println();

        // 线程池中的局限
        System.out.println("  【线程池中的局限】");
        ExecutorService pool = Executors.newFixedThreadPool(1);

        inheritableLocal.set("第一次任务的值");
        pool.execute(() -> System.out.println("    任务1 继承到: "
                + inheritableLocal.get()));

        Thread.sleep(200);

        // 修改父线程的值
        inheritableLocal.set("第二次任务的值(已更新)");
        pool.execute(() -> System.out.println("    任务2 读到: "
                + inheritableLocal.get()
                + " (线程被复用，不会重新继承新值！)"));

        Thread.sleep(200);
        pool.shutdown();
        inheritableLocal.remove();
        System.out.println();
    }

    /**
     * 五、实际应用场景
     * 模拟 Web 请求中用户上下文的传递。
     */
    private static void demonstrateUserContext() throws InterruptedException {
        System.out.println("=== 五、实际应用场景 — 用户上下文 ===\n");

        System.out.println("  模拟 3 个 Web 请求，每个请求在不同线程中处理\n");

        CountDownLatch latch = new CountDownLatch(3);

        for (int i = 1; i <= 3; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    // 模拟 Filter/Interceptor 设置用户上下文
                    UserContext user = new UserContext(
                            "user_" + requestId,
                            "用户" + requestId);
                    UserContext.HOLDER.set(user);
                    System.out.println("  [请求" + requestId + "] "
                            + Thread.currentThread().getName()
                            + " 设置用户: " + user);

                    // 模拟 Service 层获取用户信息（无需方法传参）
                    processService(requestId);

                    // 模拟 DAO 层获取用户信息
                    processDao(requestId);

                } finally {
                    // 模拟 Filter/Interceptor 清理
                    UserContext.HOLDER.remove();
                    System.out.println("  [请求" + requestId + "] 清理用户上下文");
                    latch.countDown();
                }
            }, "Request-" + requestId).start();
        }

        latch.await();
        System.out.println();
    }

    /** 模拟 Service 层：直接从 ThreadLocal 获取用户信息 */
    private static void processService(int requestId) {
        UserContext user = UserContext.HOLDER.get();
        System.out.println("  [请求" + requestId + "] Service 层获取用户: " + user);
    }

    /** 模拟 DAO 层：直接从 ThreadLocal 获取用户信息 */
    private static void processDao(int requestId) {
        UserContext user = UserContext.HOLDER.get();
        System.out.println("  [请求" + requestId + "] DAO 层获取用户: " + user);
    }

    /**
     * 用户上下文，使用 ThreadLocal 实现线程级隔离。
     * 类似 Spring 的 RequestContextHolder 设计。
     */
    static class UserContext {
        static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

        private final String userId;
        private final String userName;

        UserContext(String userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }

        @Override
        public String toString() {
            return "UserContext{userId='" + userId + "', userName='" + userName + "'}";
        }
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("  ThreadLocal — 线程局部变量");
        System.out.println("========================================\n");

        demonstrateBasicUsage();
        showThreadLocalStructure();
        demonstrateMemoryLeak();
        demonstrateInheritableThreadLocal();
        demonstrateUserContext();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. ThreadLocal 本质：每个 Thread 持有 ThreadLocalMap，key=ThreadLocal 实例");
        System.out.println();
        System.out.println("  2. 内存泄漏原因：key 是弱引用会被 GC，value 是强引用不会被 GC");
        System.out.println();
        System.out.println("  3. 最佳实践：用完必须 remove()，尤其在线程池场景");
        System.out.println();
        System.out.println("  4. InheritableThreadLocal 可父子传值，但线程池中不可靠");
        System.out.println();
        System.out.println("  5. 典型应用：Spring 的 RequestContextHolder、MyBatis 的 SqlSession");
        System.out.println();
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * ThreadLocal → 线程池中的陷阱 → Web 框架中的请求上下文
 * ThreadLocal 是线程隔离的核心工具，在 Spring、MyBatis 等框架中广泛使用。
 * 理解其内存泄漏原理是面试高频考点，也是正确使用的前提。
 */
