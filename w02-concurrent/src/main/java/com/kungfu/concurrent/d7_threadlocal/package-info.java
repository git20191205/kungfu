/**
 * D15 - ThreadLocal
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、ThreadLocal 基本原理</h3>
 * <pre>
 * ThreadLocal 提供线程局部变量，每个线程持有自己的独立副本，互不干扰。
 *
 * 核心机制：
 *   - 每个 Thread 对象内部持有一个 ThreadLocalMap
 *   - ThreadLocal.set(value) 实际是往当前线程的 ThreadLocalMap 中存值
 *   - ThreadLocal.get() 实际是从当前线程的 ThreadLocalMap 中取值
 *   - key = ThreadLocal 实例本身，value = 用户设置的值
 *
 * 常用 API：
 *   ThreadLocal&lt;T&gt; tl = new ThreadLocal&lt;&gt;();
 *   tl.set(value);        // 设置当前线程的值
 *   tl.get();             // 获取当前线程的值
 *   tl.remove();          // 移除当前线程的值（防止内存泄漏）
 *   ThreadLocal.withInitial(() -&gt; initValue);  // 带初始值的工厂方法
 * </pre>
 *
 * <h3>二、ThreadLocalMap 结构</h3>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  Thread 对象                                                │
 * │  ┌────────────────────────────────────────────────────────┐  │
 * │  │  ThreadLocal.ThreadLocalMap threadLocals                │  │
 * │  │                                                        │  │
 * │  │  Entry[] table (数组，开放地址法解决冲突)               │  │
 * │  │  ┌──────────────────┬──────────────────┬──────────┐    │  │
 * │  │  │    Entry[0]      │    Entry[1]      │   ...    │    │  │
 * │  │  ├──────────────────┼──────────────────┼──────────┤    │  │
 * │  │  │ key (WeakRef)    │ key (WeakRef)    │          │    │  │
 * │  │  │   ↓              │   ↓              │          │    │  │
 * │  │  │ ThreadLocal实例  │ ThreadLocal实例  │          │    │  │
 * │  │  │                  │                  │          │    │  │
 * │  │  │ value (强引用)   │ value (强引用)   │          │    │  │
 * │  │  │   ↓              │   ↓              │          │    │  │
 * │  │  │ 用户设置的值     │ 用户设置的值     │          │    │  │
 * │  │  └──────────────────┴──────────────────┴──────────┘    │  │
 * │  └────────────────────────────────────────────────────────┘  │
 * └──────────────────────────────────────────────────────────────┘
 *
 * Entry 继承 WeakReference&lt;ThreadLocal&lt;?&gt;&gt;：
 *   static class Entry extends WeakReference&lt;ThreadLocal&lt;?&gt;&gt; {
 *       Object value;  // 强引用
 *   }
 *
 * 注意：Entry 的 key 是弱引用，value 是强引用！
 * </pre>
 *
 * <h3>三、内存泄漏问题</h3>
 * <pre>
 * 引用链分析：
 *   ThreadLocal 变量 ──(强引用)──→ ThreadLocal 实例
 *   Entry.key ──────(弱引用)──→ ThreadLocal 实例
 *   Entry.value ────(强引用)──→ 用户对象
 *   Thread ─────────(强引用)──→ ThreadLocalMap ──→ Entry[]
 *
 * 泄漏场景（线程池中）：
 *   1. ThreadLocal 变量被置为 null（外部强引用消失）
 *   2. GC 回收 ThreadLocal 实例（因为 Entry.key 是弱引用）
 *   3. Entry.key = null，但 Entry.value 仍然被强引用
 *   4. 线程池中线程不会销毁 → ThreadLocalMap 不会被回收
 *   5. Entry.value 对应的对象无法被 GC → 内存泄漏！
 *
 * 解决方案：
 *   用完必须调用 remove()，推荐 try-finally 模式：
 *   try {
 *       threadLocal.set(value);
 *       // 业务逻辑
 *   } finally {
 *       threadLocal.remove();
 *   }
 * </pre>
 *
 * <h3>四、InheritableThreadLocal</h3>
 * <pre>
 * 功能：
 *   子线程可以继承父线程的 ThreadLocal 值。
 *
 * 原理：
 *   Thread 内部还有一个 inheritableThreadLocals 字段
 *   创建子线程时，会把父线程的 inheritableThreadLocals 复制一份给子线程
 *
 * 局限性：
 *   - 只在线程创建时复制，之后父子线程的值互相独立
 *   - 线程池场景下不可靠（线程被复用，不会重新继承）
 *   - 阿里的 TransmittableThreadLocal (TTL) 解决了线程池传值问题
 * </pre>
 *
 * <h3>五、实际应用场景</h3>
 * <pre>
 * 1. Web 请求上下文传递：
 *    Spring 的 RequestContextHolder 使用 ThreadLocal 存储当前请求信息
 *    在 Controller → Service → DAO 调用链中随时获取请求上下文
 *
 * 2. 数据库连接/事务管理：
 *    Spring 事务管理器将 Connection 绑定到 ThreadLocal
 *    保证同一事务内的操作使用同一个数据库连接
 *
 * 3. 日期格式化：
 *    SimpleDateFormat 非线程安全，每个线程一个实例避免并发问题
 *    ThreadLocal&lt;SimpleDateFormat&gt; df = ThreadLocal.withInitial(
 *        () -&gt; new SimpleDateFormat("yyyy-MM-dd"));
 *
 * 4. 用户身份信息传递：
 *    登录后将用户信息存入 ThreadLocal，后续方法调用中随时获取
 *    避免在每个方法签名中传递 userId 参数
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>d8_blockingqueue 阻塞队列 —— 深入理解生产者-消费者模式的核心组件。</p>
 *
 * @author kungfu
 * @since D15
 */
package com.kungfu.concurrent.d7_threadlocal;
