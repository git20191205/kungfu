/**
 * D2 - GC 算法与垃圾回收器
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、三种 GC 算法</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │           标记-清除（Mark-Sweep）                     │
 * │  标记存活对象 → 清除未标记对象                         │
 * │  优点：简单    缺点：内存碎片                          │
 * ├─────────────────────────────────────────────────────┤
 * │           标记-复制（Copying）                        │
 * │  存活对象复制到另一半 → 原区清空                       │
 * │  优点：无碎片  缺点：浪费一半空间                      │
 * │  → 新生代使用（Eden:S0:S1 = 8:1:1 优化空间利用率）    │
 * ├─────────────────────────────────────────────────────┤
 * │           标记-整理（Mark-Compact）                   │
 * │  存活对象向一端移动 → 清理边界外空间                   │
 * │  优点：无碎片不浪费  缺点：需移动对象，STW 更长        │
 * │  → 老年代使用                                        │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>二、垃圾回收器家族</h3>
 * <pre>
 *         新生代                    老年代
 *   ┌──────────────┐        ┌──────────────┐
 *   │   Serial     │───────→│  Serial Old  │  单线程，简单
 *   ├──────────────┤        ├──────────────┤
 *   │   ParNew     │───────→│     CMS      │  低延迟组合
 *   ├──────────────┤        ├──────────────┤
 *   │Parallel Scav │───────→│ Parallel Old │  高吞吐组合（JDK8默认）
 *   └──────────────┘        └──────────────┘
 *
 *   ┌──────────────────────────────────────┐
 *   │              G1（整堆）               │  JDK9+ 默认
 *   └──────────────────────────────────────┘
 *   ┌──────────────────────────────────────┐
 *   │           ZGC / Shenandoah           │  超低延迟（JDK11+）
 *   └──────────────────────────────────────┘
 * </pre>
 *
 * <h3>三、判断对象存活</h3>
 * <pre>
 *   引用计数法 → 有循环引用问题，JVM 不用
 *   可达性分析 → 从 GC Roots 沿引用链遍历（JVM 采用）
 *
 *   GC Roots:
 *   1. 虚拟机栈局部变量
 *   2. 方法区静态变量
 *   3. 方法区常量
 *   4. JNI 引用
 *   5. synchronized 持有的对象
 * </pre>
 *
 * <h3>四、面试高频题</h3>
 * <pre>
 *   Q: Minor GC / Major GC / Full GC 区别？
 *   A: Minor GC = 新生代 GC（频繁、快）
 *      Major GC = 老年代 GC（不常用这个叫法）
 *      Full GC  = 整个堆 + 方法区（慢，要避免）
 *
 *   Q: 什么时候触发 Full GC？
 *   A: 1. 老年代空间不足
 *      2. 方法区空间不足
 *      3. System.gc()（建议，不保证）
 *      4. CMS 并发失败（Concurrent Mode Failure）
 *      5. 老年代担保失败
 * </pre>
 *
 * @see GCAlgorithmDemo          三种算法的模拟演示
 * @see GarbageCollectorDemo     回收器对比实战
 * @see GCRootsDemo              GC Roots 可达性分析
 */
package com.kungfu.jvm.d2_gc;



/*继续学习gc相关

● D2 的代码已经创建好了，按顺序来：

  D2 学习路径

  第一步：运行 GCAlgorithmDemo.java（直接运行，无需参数）

  这个用 ■□ 方块可视化展示三种算法：
  - 标记-清除：垃圾清掉了但有碎片（□ 不连续）
  - 标记-复制：存活对象复制到另一半，无碎片但浪费空间
  - 标记-整理：存活对象向左压缩，无碎片也不浪费

  第二步：运行 GarbageCollectorDemo.java（切换参数跑 4 次）

  分别用这 4 组 VM options 运行，对比 GC 日志差异：

  ① Serial:       -Xms20m -Xmx20m -Xmn10m -XX:+PrintGCDetails -XX:+UseSerialGC
  ② ParNew+CMS:   -Xms20m -Xmx20m -Xmn10m -XX:+PrintGCDetails -XX:+UseConcMarkSweepGC
  ③ Parallel(默认): -Xms20m -Xmx20m -Xmn10m -XX:+PrintGCDetails -XX:+UseParallelGC
  ④ G1:           -Xms20m -Xmx20m -XX:+PrintGCDetails -XX:+UseG1GC

  重点观察：回收器名称、GC 次数、耗时

  第三步：运行 GCRootsDemo.java

  VM options: -Xms20m -Xmx20m -XX:+PrintGCDetails -XX:+UseSerialGC

  演示哪些东西是 GC Root、引用链断裂后对象怎么被回收。

  第四步：读 package-info.java 巩固

  先从 GCAlgorithmDemo 开始跑，把日志贴过来我帮你解读。
*
* */