package java.com.kungfu.concurrent;
/*

● Agent "Create ThreadPoolDemo.java" completed

● ThreadPoolDemo.java is done. 4 out of 8 agents complete. Waiting for the remaining 4...

● Agent "Create VolatileDemo.java" completed

● VolatileDemo.java is done. 5/8 agents complete — waiting for SynchronizedDemo, ReentrantLockDemo, ConcurrentHashMapDemo, and package-info files...

● Agent "Create SynchronizedDemo.java" completed

● SynchronizedDemo.java is done. 6/8 — waiting for ReentrantLockDemo, ConcurrentHashMapDemo, and package-info files...

● Agent "Create ReentrantLockDemo.java" completed

● ReentrantLockDemo.java is done. 7/8 — just waiting for ConcurrentHashMapDemo and package-info files...

● Agent "Create ConcurrentHashMapDemo" completed

● ConcurrentHashMapDemo.java is done. All 7 Demo files complete! Just waiting for the package-info agent...

● Agent "Create package-info files" completed

● All 8 agents complete! Let me verify the final file structure.

  Searched for 1 pattern (ctrl+o to expand)

● All 12 files created successfully. Let me do a quick compilation check.

● Bash(cd "D:/workspace/kungfu" && mvn compile -pl w02-concurrent -q 2>&1 | tail -30)
  ⎿  (No output)

● All files compile cleanly. Here's the summary:

● W02 并发编程核心 — 全部完成

  12 个文件已创建并编译通过：

  ┌─────────────────────────┬──────────────────────────┬──────────────────────────────────────────────┐
  │           包            │           文件           │                     内容                     │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │ d1_thread_basis         │ package-info.java        │ JMM、volatile、synchronized 知识总结         │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │                         │ VolatileDemo.java        │ 可见性、非原子性、DCL 单例、对比表           │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │                         │ SynchronizedDemo.java    │ 三种用法、锁升级、Monitor、生产者消费者      │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │ d2_lock                 │ package-info.java        │ Lock、AQS、CLH 队列知识总结                  │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ReentrantLockDemo.java   │ 公平锁、可中断、Condition 精确唤醒、AQS 原理 │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │ d3_concurrent_util      │ package-info.java        │ 三大工具类知识总结                           │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ConcurrentToolsDemo.java │ CountDownLatch/CyclicBarrier/Semaphore 实战  │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │ d4_thread_pool          │ package-info.java        │ 线程池知识总结                               │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ThreadPoolDemo.java      │ 7 参数、4 拒绝策略、执行流程、配置建议       │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │                         │ MiniThreadPool.java      │ 手写简化版线程池                             │
  ├─────────────────────────┼──────────────────────────┼──────────────────────────────────────────────┤
  │ d5_concurrent_container │ package-info.java        │ 并发容器知识总结                             │
  └─────────────────────────┴──────────────────────────┴──────────────────────────────────────────────┘

────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────── write-w02-concurrent-demos ──
❯ 
────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
  ⏵⏵ accept edits on (meta+m to cycle) · esc to interrupt                                                                                                                                                   ⧉ In package-info.java
╭─── Claude Code v2.1.79 ──────────────────────────────────────────────────────────────────────────────╮
│                                  │ Tips for getting started                                          │
│           Welcome back!          │ Run /init to create a CLAUDE.md file with instructions for Claude │
│                                  │ ───────────────────────────────────────────────────────────────── │
│              ▐▛███▜▌             │ Recent activity                                                   │
│             ▝▜█████▛▘            │ No recent activity                                                │
│               ▘▘ ▝▝              │                                                                   │
│                                  │                                                                   │
│   Opus 4.6 · API Usage Billing   │                                                                   │
│       D:\workspace\kungfu        │                                                                   │
╰──────────────────────────────────────────────────────────────────────────────────────────────────────╯

  ↑ Install the PyCharm plugin from the JetBrains Marketplace: https://docs.claude.com/s/claude-code-jetbrains

╭──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
│ Plan to implement                                                                                                                                                                                                                │
│                                                                                                                                                                                                                                  │
│ W02 并发编程核心 — Demo 代码编写                                                                                                                                                                                                 │
│                                                                                                                                                                                                                                  │
│ Context                                                                                                                                                                                                                          │
│                                                                                                                                                                                                                                  │
│ W01-JVM 四个模块（D1~D4）已全部完成。按照学习路线，下一步是 W02 并发编程核心（D8-D13）。                                                                                                                                         │
│ 模块目录 w02-concurrent/src/main/java/com/kungfu/concurrent/ 已建好，但无代码。                                                                                                                                                  │
│ 需要按照 W01 的编码风格创建 6 个 Demo。                                                                                                                                                                                          │
│                                                                                                                                                                                                                                  │
│ 文件结构                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ w02-concurrent/src/main/java/com/kungfu/concurrent/                                                                                                                                                                              │
│ ├── d1_thread_basis/                          # 线程基础与内存可见性                                                                                                                                                             │
│ │   ├── package-info.java                                                                                                                                                                                                        │
│ │   ├── VolatileDemo.java                     # volatile 原理与内存可见性                                                                                                                                                        │
│ │   └── SynchronizedDemo.java                 # synchronized 原理与锁升级                                                                                                                                                        │
│ │                                                                                                                                                                                                                                │
│ ├── d2_lock/                                  # Lock 与 AQS                                                                                                                                                                      │
│ │   ├── package-info.java                                                                                                                                                                                                        │
│ │   └── ReentrantLockDemo.java                # ReentrantLock + AQS 原理                                                                                                                                                         │
│ │                                                                                                                                                                                                                                │
│ ├── d3_concurrent_util/                       # 并发工具类                                                                                                                                                                       │
│ │   ├── package-info.java                                                                                                                                                                                                        │
│ │   └── ConcurrentToolsDemo.java              # CountDownLatch/CyclicBarrier/Semaphore                                                                                                                                           │
│ │                                                                                                                                                                                                                                │
│ ├── d4_thread_pool/                           # 线程池                                                                                                                                                                           │
│ │   ├── package-info.java                                                                                                                                                                                                        │
│ │   ├── ThreadPoolDemo.java                   # 线程池7参数 + 拒绝策略                                                                                                                                                           │
│ │   └── MiniThreadPool.java                   # 手写简化版线程池                                                                                                                                                                 │
│ │                                                                                                                                                                                                                                │
│ └── d5_concurrent_container/                  # 并发容器                                                                                                                                                                         │
│     ├── package-info.java                                                                                                                                                                                                        │
│     └── ConcurrentHashMapDemo.java            # CHM 1.7 vs 1.8                                                                                                                                                                   │
│                                                                                                                                                                                                                                  │
│ 各 Demo 设计                                                                                                                                                                                                                     │
│                                                                                                                                                                                                                                  │
│ Demo 1: VolatileDemo.java                                                                                                                                                                                                        │
│                                                                                                                                                                                                                                  │
│ 一、内存可见性问题                                                                                                                                                                                                               │
│    → 启动线程读 flag，主线程改 flag，演示不加 volatile 线程看不到变化                                                                                                                                                            │
│    → 加 volatile 后线程立即看到变化                                                                                                                                                                                              │
│                                                                                                                                                                                                                                  │
│ 二、volatile 不保证原子性                                                                                                                                                                                                        │
│    → 多线程 i++ 演示（volatile int count）→ 结果 < 预期                                                                                                                                                                          │
│    → 对比 AtomicInteger → 结果正确                                                                                                                                                                                               │
│                                                                                                                                                                                                                                  │
│ 三、指令重排序                                                                                                                                                                                                                   │
│    → 双重检查锁单例 → 为什么 instance 要加 volatile                                                                                                                                                                              │
│    → happens-before 规则列表                                                                                                                                                                                                     │
│                                                                                                                                                                                                                                  │
│ 四、volatile vs synchronized 对比表                                                                                                                                                                                              │
│                                                                                                                                                                                                                                  │
│ 面试速记                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ Demo 2: SynchronizedDemo.java                                                                                                                                                                                                    │
│                                                                                                                                                                                                                                  │
│ 一、三种使用方式                                                                                                                                                                                                                 │
│    → 同步实例方法 / 同步静态方法 / 同步代码块                                                                                                                                                                                    │
│    → 锁对象分别是什么                                                                                                                                                                                                            │
│                                                                                                                                                                                                                                  │
│ 二、锁升级过程（JDK 6+）                                                                                                                                                                                                         │
│    → 无锁 → 偏向锁 → 轻量级锁 → 重量级锁                                                                                                                                                                                         │
│    → 打印对象头演示锁状态（如果 JOL 可用）或用知识表格                                                                                                                                                                           │
│                                                                                                                                                                                                                                  │
│ 三、synchronized 底层原理                                                                                                                                                                                                        │
│    → monitorenter / monitorexit 指令                                                                                                                                                                                             │
│    → Monitor 对象的 EntryList / WaitSet                                                                                                                                                                                          │
│                                                                                                                                                                                                                                  │
│ 四、wait/notify 机制                                                                                                                                                                                                             │
│    → 生产者消费者模型演示                                                                                                                                                                                                        │
│                                                                                                                                                                                                                                  │
│ 面试速记                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ Demo 3: ReentrantLockDemo.java                                                                                                                                                                                                   │
│                                                                                                                                                                                                                                  │
│ 一、ReentrantLock vs synchronized 对比                                                                                                                                                                                           │
│    → 功能对比表                                                                                                                                                                                                                  │
│                                                                                                                                                                                                                                  │
│ 二、公平锁 vs 非公平锁                                                                                                                                                                                                           │
│    → 演示公平锁的排队效果                                                                                                                                                                                                        │
│                                                                                                                                                                                                                                  │
│ 三、可中断锁                                                                                                                                                                                                                     │
│    → lockInterruptibly() 演示                                                                                                                                                                                                    │
│                                                                                                                                                                                                                                  │
│ 四、Condition 条件变量                                                                                                                                                                                                           │
│    → 精确唤醒演示（多个 Condition）                                                                                                                                                                                              │
│                                                                                                                                                                                                                                  │
│ 五、AQS 原理（知识输出）                                                                                                                                                                                                         │
│    → CLH 队列 + state 变量                                                                                                                                                                                                       │
│    → acquire/release 流程图                                                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ 面试速记                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ Demo 4: ConcurrentToolsDemo.java                                                                                                                                                                                                 │
│                                                                                                                                                                                                                                  │
│ 一、CountDownLatch — 等待多个任务完成                                                                                                                                                                                            │
│    → 模拟多个微服务健康检查，全部完成后启动                                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ 二、CyclicBarrier — 多线程到齐后一起出发                                                                                                                                                                                         │
│    → 模拟赛跑，所有选手就绪后同时起跑                                                                                                                                                                                            │
│                                                                                                                                                                                                                                  │
│ 三、Semaphore — 限流/资源池                                                                                                                                                                                                      │
│    → 模拟停车场限流（3个车位，5辆车）                                                                                                                                                                                            │
│                                                                                                                                                                                                                                  │
│ 四、三者对比表                                                                                                                                                                                                                   │
│                                                                                                                                                                                                                                  │
│ 面试速记                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ Demo 5: ThreadPoolDemo.java                                                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ 一、线程池 7 个核心参数                                                                                                                                                                                                          │
│    → 逐一演示每个参数的作用                                                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ 二、4 种拒绝策略                                                                                                                                                                                                                 │
│    → AbortPolicy / CallerRunsPolicy / DiscardPolicy / DiscardOldestPolicy                                                                                                                                                        │
│                                                                                                                                                                                                                                  │
│ 三、线程池执行流程                                                                                                                                                                                                               │
│    → core → queue → max → reject 的完整流程演示                                                                                                                                                                                  │
│                                                                                                                                                                                                                                  │
│ 四、常见线程池陷阱                                                                                                                                                                                                               │
│    → newFixedThreadPool 的 OOM 风险（无界队列）                                                                                                                                                                                  │
│    → newCachedThreadPool 的线程爆炸风险                                                                                                                                                                                          │
│                                                                                                                                                                                                                                  │
│ 五、线上线程池配置建议                                                                                                                                                                                                           │
│    → CPU 密集型 vs IO 密集型                                                                                                                                                                                                     │
│                                                                                                                                                                                                                                  │
│ 面试速记                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ Demo 6: MiniThreadPool.java                                                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ 手写简化版线程池                                                                                                                                                                                                                 │
│    → 核心线程数 + 阻塞队列 + Worker 线程                                                                                                                                                                                         │
│    → execute() 方法：判断核心线程 → 入队 → 拒绝                                                                                                                                                                                  │
│    → shutdown() 方法：中断所有 Worker                                                                                                                                                                                            │
│    → 测试用例验证                                                                                                                                                                                                                │
│                                                                                                                                                                                                                                  │
│ Demo 7: ConcurrentHashMapDemo.java                                                                                                                                                                                               │
│                                                                                                                                                                                                                                  │
│ 一、HashMap 线程不安全演示                                                                                                                                                                                                       │
│    → 多线程 put 导致数据丢失                                                                                                                                                                                                     │
│                                                                                                                                                                                                                                  │
│ 二、ConcurrentHashMap 1.7 原理                                                                                                                                                                                                   │
│    → Segment 分段锁 + 知识图                                                                                                                                                                                                     │
│                                                                                                                                                                                                                                  │
│ 三、ConcurrentHashMap 1.8 原理                                                                                                                                                                                                   │
│    → CAS + synchronized + 红黑树                                                                                                                                                                                                 │
│    → 与 1.7 的对比表                                                                                                                                                                                                             │
│                                                                                                                                                                                                                                  │
│ 四、size() 的实现演变                                                                                                                                                                                                            │
│                                                                                                                                                                                                                                  │
│ 面试速记                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ 编码风格（与 W01 保持一致）                                                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ - 类 Javadoc: 【Demo】标题 + 演示什么 + 为什么重要 + 运行方式                                                                                                                                                                    │
│ - 方法命名: testXxx() / demonstrateXxx() / showXxx()                                                                                                                                                                             │
│ - 输出格式: ======== 分隔 + === 一、标题 === 子节 + 知识表格                                                                                                                                                                     │
│ - 每个 Demo 末尾有面试速记和知识串联注释                                                                                                                                                                                         │
│ - package-info.java 有完整知识总结                                                                                                                                                                                               │
│ - @author kungfu + @since D8 - 并发编程                                                                                                                                                                                          │
│                                                                                                                                                                                                                                  │
│ 实施顺序                                                                                                                                                                                                                         │
│                                                                                                                                                                                                                                  │
│ 1. 创建 package-info.java（5 个包）                                                                                                                                                                                              │
│ 2. 按 Demo 1~7 顺序创建                                                                                                                                                                                                          │
│ 3. 每个 Demo 创建后用户在 IDEA 运行 → 分享日志 → 写分析文档                                                                                                                                                                      │
│                                                                                                                                                                                                                                  │
│ 验证                                                                                                                                                                                                                             │
│                                                                                                                                                                                                                                  │
│ - 每个 Demo 直接在 IDEA 中运行 main 方法                                                                                                                                                                                         │
│ - 输出应包含完整的知识表格、演示结果和面试速记                                                                                                                                                                                   │
│ - 特别注意并发 Demo 的线程安全问题（等待线程结束后再输出结论）                                                                                                                                                                   │
╰──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯

● I'll start by exploring the existing codebase structure and W01 style, then create all files.

● Explore(Explore W01 code style)
  ⎿  Done (9 tool uses · 36.9k tokens · 40s)
  (ctrl+o to expand)

● Now let me check the existing w02 directory structure.

  Searched for 1 pattern, read 1 file (ctrl+o to expand)

● The directory exists but is empty. Let me create all subdirectories first, then write all 12 files.

● Bash(mkdir -p "D:/workspace/kungfu/w02-concurrent/src/main/java/com/kungfu/concurrent/d1_thread_basis" "D:/workspace/kungfu/w02-concurrent/src/main/java/com/kungfu/c…)
  ⎿  Done

● Now I'll create all files in parallel using multiple agents.

● 8 agents launched (ctrl+o to expand)
   ├─ Create package-info files
   │  ⎿  Running in the background
   ├─ Create VolatileDemo.java
   │  ⎿  Running in the background
   ├─ Create SynchronizedDemo.java
   │  ⎿  Running in the background
   ├─ Create ReentrantLockDemo.java
   │  ⎿  Running in the background
   ├─ Create ConcurrentToolsDemo.java
   │  ⎿  Running in the background
   ├─ Create ThreadPoolDemo.java
   │  ⎿  Running in the background
   ├─ Create MiniThreadPool.java
   │  ⎿  Running in the background
   └─ Create ConcurrentHashMapDemo
      ⎿  Running in the background

● All 8 agents are running in parallel — creating the 5 package-info.java files and 7 Demo Java files. I'll verify everything once they complete.

● Waiting for all agents to finish writing the files...

✻ Cogitated for 3m 10s · 8 background tasks still running (↓ to manage)

● Agent "Create ConcurrentToolsDemo.java" completed

● ConcurrentToolsDemo.java is done. Waiting for the remaining agents...

● Agent "Create MiniThreadPool.java" completed

● MiniThreadPool.java is done. Waiting for the rest...

● Agent "Create ThreadPoolDemo.java" completed

● ThreadPoolDemo.java is done. 4 out of 8 agents complete. Waiting for the remaining 4...

● Agent "Create VolatileDemo.java" completed

● VolatileDemo.java is done. 5/8 agents complete — waiting for SynchronizedDemo, ReentrantLockDemo, ConcurrentHashMapDemo, and package-info files...

● Agent "Create SynchronizedDemo.java" completed

● SynchronizedDemo.java is done. 6/8 — waiting for ReentrantLockDemo, ConcurrentHashMapDemo, and package-info files...

● Agent "Create ReentrantLockDemo.java" completed

● ReentrantLockDemo.java is done. 7/8 — just waiting for ConcurrentHashMapDemo and package-info files...

● Agent "Create ConcurrentHashMapDemo" completed

● ConcurrentHashMapDemo.java is done. All 7 Demo files complete! Just waiting for the package-info agent...

● Agent "Create package-info files" completed

● All 8 agents complete! Let me verify the final file structure.

  Searched for 1 pattern (ctrl+o to expand)

● All 12 files created successfully. Let me do a quick compilation check.

● Bash(cd "D:/workspace/kungfu" && mvn compile -pl w02-concurrent -q 2>&1 | tail -30)
  ⎿  (No output)

● All files compile cleanly. Here's the summary:

● W02 并发编程核心 — 全部完成

  12 个文件已创建并编译通过：

  ┌─────────────────────────┬────────────────────────────┬──────────────────────────────────────────────┐
  │           包            │            文件            │                     内容                     │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │ d1_thread_basis         │ package-info.java          │ JMM、volatile、synchronized 知识总结         │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ VolatileDemo.java          │ 可见性、非原子性、DCL 单例、对比表           │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ SynchronizedDemo.java      │ 三种用法、锁升级、Monitor、生产者消费者      │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │ d2_lock                 │ package-info.java          │ Lock、AQS、CLH 队列知识总结                  │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ReentrantLockDemo.java     │ 公平锁、可中断、Condition 精确唤醒、AQS 原理 │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │ d3_concurrent_util      │ package-info.java          │ 三大工具类知识总结                           │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ConcurrentToolsDemo.java   │ CountDownLatch/CyclicBarrier/Semaphore 实战  │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │ d4_thread_pool          │ package-info.java          │ 线程池知识总结                               │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ThreadPoolDemo.java        │ 7 参数、4 拒绝策略、执行流程、配置建议       │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ MiniThreadPool.java        │ 手写简化版线程池                             │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │ d5_concurrent_container │ package-info.java          │ 并发容器知识总结                             │
  ├─────────────────────────┼────────────────────────────┼──────────────────────────────────────────────┤
  │                         │ ConcurrentHashMapDemo.java │ HashMap 不安全、CHM 1.7 vs 1.8、size() 演变  │
  └─────────────────────────┴────────────────────────────┴──────────────────────────────────────────────┘

  每个 Demo 都可以直接在 IDEA 中运行 main 方法。建议按 D1→D7 顺序逐个运行，分享日志后写分析文档。

❯ 哪来的D7

● 抱歉，笔误。应该是按 Demo 1→Demo 7 的顺序（即 VolatileDemo → SynchronizedDemo → ReentrantLockDemo → ConcurrentToolsDemo → ThreadPoolDemo → MiniThreadPool → ConcurrentHashMapDemo），不是"D7"。

 对应的天数是 D8~D13，没有 D7。

* */

/*
 *
 * ● W02 从 VolatileDemo 开始跑，完整顺序是：
 *
 *   ┌─────────────────────────┬───────────────────────┬────────────────────────────────────────┐
 *   │           包            │         Demo          │                  主题                  │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d1_thread_basis         │ VolatileDemo          │ volatile 可见性、有序性                │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d1_thread_basis         │ SynchronizedDemo      │ synchronized 原理                      │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d2_lock                 │ ReentrantLockDemo     │ 可重入锁、AQS                          │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d3_concurrent_util      │ ConcurrentToolsDemo   │ CountDownLatch/CyclicBarrier/Semaphore │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d4_thread_pool          │ ThreadPoolDemo        │ 线程池                                 │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d5_concurrent_container │ ConcurrentHashMapDemo │ 并发容器                               │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d6_cas_atomic           │ CASAtomicDemo         │ CAS 与原子类                           │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d7_threadlocal          │ ThreadLocalDemo       │ ThreadLocal                            │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d8_blockingqueue        │ BlockingQueueDemo     │ 阻塞队列                               │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d9_completablefuture    │ CompletableFutureDemo │ CompletableFuture                      │
 *   ├─────────────────────────┼───────────────────────┼────────────────────────────────────────┤
 *   │ d10_readwritelock       │ ReadWriteLockDemo     │ 读写锁                                 │
 *   └─────────────────────────┴───────────────────────┴────────────────────────────────────────┘
 *
 *   VolatileDemo 刚跑过，运行正常。刚才 5 个新 Demo 也都跑通了，没有运行时错误。
 * */