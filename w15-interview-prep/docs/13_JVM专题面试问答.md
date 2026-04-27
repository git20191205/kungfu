# JVM 专题 — 高频面试问答

> 基于 w01-jvm 项目经验整理的 20 个高频面试题

## 一、内存模型（5 题）

### 1. JVM 内存结构有哪些？

**答**：
- **堆（Heap）**：存储对象实例，GC 主要区域
- **方法区（Method Area）**：存储类信息、常量、静态变量
- **虚拟机栈（VM Stack）**：存储局部变量、操作数栈、方法出口
- **本地方法栈（Native Method Stack）**：执行 Native 方法
- **程序计数器（PC Register）**：当前线程执行的字节码行号

**追问**：堆和栈的区别？
- 堆：线程共享，存对象，GC 管理
- 栈：线程私有，存局部变量，自动回收

### 2. 什么是 OOM？有哪些类型？

**答**：
- **堆 OOM**：`java.lang.OutOfMemoryError: Java heap space`
  - 原因：对象太多，堆内存不足
  - 解决：增大堆内存 `-Xmx`，排查内存泄漏
  
- **栈 OOM**：`java.lang.StackOverflowError`
  - 原因：递归太深，栈帧太多
  - 解决：增大栈大小 `-Xss`，优化递归

- **方法区 OOM**：`java.lang.OutOfMemoryError: Metaspace`
  - 原因：类太多，方法区不足
  - 解决：增大 Metaspace `-XX:MaxMetaspaceSize`

### 3. 什么是内存泄漏？如何排查？

**答**：
- **定义**：对象无法被 GC 回收，但实际已不再使用
- **常见场景**：
  - 集合类未清理（List/Map 持续增长）
  - 监听器未注销
  - ThreadLocal 未清理
  - 连接未关闭

**排查步骤**：
```bash
# 1. 生成堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# 2. 使用 MAT 分析
# - 查看 Dominator Tree（支配树）
# - 查看 Leak Suspects（泄漏嫌疑）
# - 查看 GC Roots 引用链
```

### 4. 强引用、软引用、弱引用、虚引用的区别？

**答**：
- **强引用（Strong）**：`Object obj = new Object()`
  - 永不回收，即使 OOM
  
- **软引用（Soft）**：`SoftReference<Object> ref = new SoftReference<>(obj)`
  - 内存不足时回收，适合缓存
  
- **弱引用（Weak）**：`WeakReference<Object> ref = new WeakReference<>(obj)`
  - GC 时回收，适合 ThreadLocal
  
- **虚引用（Phantom）**：`PhantomReference<Object> ref = new PhantomReference<>(obj, queue)`
  - 无法通过引用获取对象，用于监控对象回收

### 5. 什么是逃逸分析？

**答**：
- **定义**：分析对象作用域，判断对象是否逃逸出方法
- **优化**：
  - **栈上分配**：对象不逃逸，分配在栈上，方法结束自动回收
  - **标量替换**：对象不逃逸，拆分为基本类型，分配在栈上
  - **锁消除**：对象不逃逸，消除同步锁

```java
// 逃逸：对象被返回
public User getUser() {
    User user = new User();
    return user;  // 逃逸
}

// 不逃逸：对象只在方法内使用
public void printUser() {
    User user = new User();
    System.out.println(user);  // 不逃逸，可以栈上分配
}
```

## 二、垃圾回收（5 题）

### 6. 有哪些垃圾回收算法？

**答**：
- **标记-清除（Mark-Sweep）**：标记垃圾，清除，产生碎片
- **标记-复制（Mark-Copy）**：复制存活对象到另一半，无碎片，浪费空间
- **标记-整理（Mark-Compact）**：标记垃圾，整理存活对象，无碎片，慢
- **分代收集（Generational）**：年轻代用复制，老年代用标记-整理

### 7. 有哪些垃圾回收器？如何选择？

**答**：
- **Serial**：单线程，适合单核 CPU
- **Parallel**：多线程，吞吐量优先，适合后台任务
- **CMS**：并发标记清除，停顿时间短，适合 Web 应用
- **G1**：分区回收，停顿可控，适合大堆内存（> 4GB）
- **ZGC**：超低延迟（< 10ms），适合超大堆内存（> 100GB）

**选择建议**：
- 堆 < 4GB：Parallel GC
- 堆 4-100GB：G1 GC（推荐）
- 堆 > 100GB：ZGC

### 8. G1 GC 的原理是什么？

**答**：
- **分区（Region）**：堆分为 2048 个 Region，每个 1-32 MB
- **分代**：Region 分为 Eden、Survivor、Old、Humongous
- **Mixed GC**：回收年轻代 + 部分老年代（垃圾最多的 Region）
- **停顿可控**：通过 `-XX:MaxGCPauseMillis` 控制停顿时间

**优点**：
- 停顿可控
- 无碎片（整理）
- 适合大堆

### 9. 什么是 Full GC？如何避免？

**答**：
- **定义**：回收整个堆（年轻代 + 老年代），停顿时间长
- **触发条件**：
  - 老年代空间不足
  - 方法区空间不足
  - System.gc() 调用

**避免方法**：
- 增大堆内存
- 优化对象生命周期（减少晋升到老年代）
- 使用 G1 GC（Mixed GC 代替 Full GC）
- 避免大对象

### 10. 如何调优 GC？

**答**：
```bash
# 1. 查看 GC 日志
-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log

# 2. 分析 GC 日志
# - Minor GC 频率
# - Full GC 频率
# - 停顿时间

# 3. 调优参数
-Xms8g -Xmx8g              # 固定堆大小
-XX:+UseG1GC               # 使用 G1 GC
-XX:MaxGCPauseMillis=200   # 最大停顿 200ms
-XX:G1HeapRegionSize=16m   # Region 大小 16MB
```

## 三、类加载（5 题）

### 11. 类加载过程有哪些步骤？

**答**：
1. **加载（Loading）**：读取 .class 文件，生成 Class 对象
2. **验证（Verification）**：验证字节码合法性
3. **准备（Preparation）**：分配内存，初始化静态变量为默认值
4. **解析（Resolution）**：符号引用转为直接引用
5. **初始化（Initialization）**：执行静态代码块，初始化静态变量

### 12. 什么是双亲委派模型？为什么需要？

**答**：
- **定义**：类加载器收到加载请求，先委派给父加载器，父加载器无法加载才自己加载
- **层次**：
  - Bootstrap ClassLoader（启动类加载器）：加载 JDK 核心类
  - Extension ClassLoader（扩展类加载器）：加载 ext 目录
  - Application ClassLoader（应用类加载器）：加载 classpath

**为什么需要**：
- 避免类重复加载
- 保护核心类（防止自定义 java.lang.String）

### 13. 如何打破双亲委派？

**答**：
- 重写 `loadClass()` 方法
- 使用线程上下文类加载器（JDBC、JNDI）
- OSGi 模块化（每个模块独立类加载器）

### 14. 什么是类的生命周期？

**答**：
加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载

**初始化时机**：
- new 对象
- 访问静态变量/方法
- 反射调用
- 初始化子类（先初始化父类）
- main 方法所在类

### 15. 什么是热部署？如何实现？

**答**：
- **定义**：不重启应用，动态替换类
- **实现**：
  - 自定义类加载器
  - 监听文件变化
  - 重新加载类

```java
public class HotSwapClassLoader extends ClassLoader {
    public Class<?> loadClass(String name, byte[] classBytes) {
        return defineClass(name, classBytes, 0, classBytes.length);
    }
}
```

## 四、性能调优（5 题）

### 16. 有哪些 JVM 监控工具？

**答**：
- **jps**：查看 Java 进程
- **jstat**：查看 GC 统计
- **jmap**：生成堆转储
- **jstack**：生成线程转储
- **jconsole**：图形化监控
- **VisualVM**：图形化监控 + 分析
- **Arthas**：在线诊断工具

### 17. 如何排查 CPU 100% 问题？

**答**：
```bash
# 1. 找到 Java 进程
jps

# 2. 找到占用 CPU 最高的线程
top -Hp <pid>

# 3. 转换线程 ID 为 16 进制
printf "%x\n" <tid>

# 4. 生成线程转储
jstack <pid> > thread.dump

# 5. 查找对应线程
grep <tid_hex> thread.dump
```

### 18. 如何排查内存泄漏？

**答**：
```bash
# 1. 生成堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# 2. 使用 MAT 分析
# - Leak Suspects：泄漏嫌疑
# - Dominator Tree：支配树
# - Histogram：对象统计

# 3. 查看 GC Roots 引用链
# - 找到无法回收的对象
# - 分析为什么无法回收
```

### 19. 如何优化 JVM 参数？

**答**：
```bash
# 堆内存
-Xms8g -Xmx8g              # 固定堆大小，避免动态扩容

# GC 选择
-XX:+UseG1GC               # 使用 G1 GC
-XX:MaxGCPauseMillis=200   # 最大停顿 200ms

# GC 日志
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:gc.log

# OOM 时生成堆转储
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

### 20. 秒杀系统的 JVM 调优经验？

**答**（基于 w09-seckill 项目）：
```bash
# 配置
-Xms8g -Xmx8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-XX:G1HeapRegionSize=16m
-XX:+HeapDumpOnOutOfMemoryError

# 效果
- QPS：6711
- P99 延迟：28ms
- GC 停顿：< 50ms
- 无 Full GC
```

**优化建议**：
- 固定堆大小（避免动态扩容）
- 使用 G1 GC（停顿可控）
- 连接池预热（避免冷启动）
- 监控 GC 日志（及时发现问题）

## 五、总结

JVM 面试核心考点：
1. **内存模型**：堆、栈、方法区
2. **垃圾回收**：GC 算法、GC 器、G1 原理
3. **类加载**：双亲委派、类加载过程
4. **性能调优**：监控工具、问题排查、参数优化

**面试技巧**：
- 结合项目经验（w09-seckill QPS 6711）
- 说出具体数据（堆 8GB、GC 停顿 < 50ms）
- 展示排查思路（jps → top → jstack）

**下一步**：D106 并发专题面试问答
