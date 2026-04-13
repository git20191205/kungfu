/**
 * D13 - 并发容器
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、HashMap 线程不安全</h3>
 * <pre>
 * JDK 7 问题：
 *   多线程 put → 扩容 resize → 头插法 → 链表成环 → 死循环
 *
 * JDK 8 改进：
 *   尾插法解决了链表成环问题
 *   但仍然线程不安全：
 *     - 多线程 put 数据覆盖（丢失更新）
 *     - size 不准确
 *     - modCount 导致 ConcurrentModificationException
 *
 * 常见错误用法：
 *   Collections.synchronizedMap(map)
 *     → 全表加锁，性能差
 *     → 复合操作（check-then-act）仍不安全
 * </pre>
 *
 * <h3>二、ConcurrentHashMap 1.7（Segment 分段锁）</h3>
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │           ConcurrentHashMap (JDK 7)          │
 * │                                              │
 * │  Segment[] (默认16个，继承 ReentrantLock)      │
 * │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐        │
 * │  │Seg[0]│ │Seg[1]│ │Seg[2]│ │ ...  │        │
 * │  │ Lock │ │ Lock │ │ Lock │ │      │        │
 * │  └──┬───┘ └──┬───┘ └──┬───┘ └──────┘        │
 * │     ▼        ▼        ▼                      │
 * │  HashEntry[] HashEntry[] HashEntry[]          │
 * │  ┌─┬─┬─┐  ┌─┬─┬─┐  ┌─┬─┬─┐                 │
 * │  │ │ │ │  │ │ │ │  │ │ │ │                   │
 * │  └─┴─┴─┘  └─┴─┴─┘  └─┴─┴─┘                 │
 * └──────────────────────────────────────────────┘
 *
 * 特点：
 *   - 分段加锁，不同 Segment 可并行写入
 *   - 并发度 = Segment 数量（默认16）
 *   - get() 不加锁（value 用 volatile 修饰）
 *   - put() 只锁对应的 Segment
 * </pre>
 *
 * <h3>三、ConcurrentHashMap 1.8（CAS + synchronized）</h3>
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │         ConcurrentHashMap (JDK 8)            │
 * │                                              │
 * │  Node[] table （与 HashMap 结构相同）          │
 * │  ┌─────┬─────┬─────┬─────┬─────┐            │
 * │  │  0  │  1  │  2  │  3  │ ... │            │
 * │  └──┬──┴─────┴──┬──┴─────┴─────┘            │
 * │     ▼           ▼                            │
 * │   Node        Node → Node → Node            │
 * │              (链表，长度≥8 转红黑树)            │
 * └──────────────────────────────────────────────┘
 *
 * put 流程：
 *   1. 桶为空   → CAS 写入新节点
 *   2. 桶非空   → synchronized(头节点) 加锁写入
 *   3. 链表≥8   → treeifyBin 转红黑树
 *   4. 正在扩容  → helpTransfer 协助扩容
 *
 * 对比 JDK 7：
 * ┌─────────┬───────────────┬────────────────────┐
 * │         │ JDK 7          │ JDK 8              │
 * ├─────────┼───────────────┼────────────────────┤
 * │ 锁粒度   │ Segment（段）  │ Node（桶头节点）    │
 * │ 数据结构  │ 数组+链表      │ 数组+链表+红黑树   │
 * │ 加锁方式  │ ReentrantLock │ CAS + synchronized │
 * │ 并发度    │ Segment 数    │ 桶的数量（更细粒度） │
 * └─────────┴───────────────┴────────────────────┘
 * </pre>
 *
 * <h3>四、size() 实现演变</h3>
 * <pre>
 * JDK 7：
 *   1. 先不加锁统计两次，比较是否一致
 *   2. 不一致 → 锁住所有 Segment 再统计
 *   问题：全部加锁代价高
 *
 * JDK 8：
 *   baseCount + CounterCell[]（类似 LongAdder 思想）
 *
 *   ┌──────────┐
 *   │ baseCount│ ←── CAS 更新（无竞争时）
 *   └──────────┘
 *   ┌────────┬────────┬────────┬────────┐
 *   │Cell[0] │Cell[1] │Cell[2] │Cell[3] │ ←── 竞争时分散计数
 *   └────────┴────────┴────────┴────────┘
 *
 *   size() = baseCount + Σ CounterCell[i].value
 *
 *   优势：高并发下避免 CAS 热点竞争
 * </pre>
 *
 * <h3>五、下一步</h3>
 * <p>W03 Spring 框架 —— 从并发编程进入企业级框架，掌握 IoC、AOP 与 Spring 生态。</p>
 *
 * @author kungfu
 * @since D13
 */
package com.kungfu.concurrent.d5_concurrent_container;
