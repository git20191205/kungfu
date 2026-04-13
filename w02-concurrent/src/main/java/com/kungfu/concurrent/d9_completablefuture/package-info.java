/**
 * D17 - CompletableFuture
 *
 * <h2>知识点总结</h2>
 *
 * <h3>一、创建异步任务</h3>
 * <pre>
 * supplyAsync：有返回值的异步任务
 *   CompletableFuture.supplyAsync(() -> compute())
 *     → 默认使用 ForkJoinPool.commonPool()
 *     → 也可传入自定义 ExecutorService
 *
 * runAsync：无返回值的异步任务
 *   CompletableFuture.runAsync(() -> doSomething())
 *     → 适用于 fire-and-forget 场景
 *
 * 线程池选择：
 *   - 默认：ForkJoinPool.commonPool()（共享池，适合 CPU 密集型）
 *   - 生产环境建议自定义线程池，避免任务间相互影响
 * </pre>
 *
 * <h3>二、链式处理</h3>
 * <pre>
 * thenApply(Function)：转换结果（类似 Stream.map）
 *   future.thenApply(s -> s.length())
 *
 * thenAccept(Consumer)：消费结果，无返回值
 *   future.thenAccept(s -> System.out.println(s))
 *
 * thenRun(Runnable)：执行动作，不访问结果
 *   future.thenRun(() -> log("done"))
 *
 * thenCompose(Function)：扁平化（类似 Stream.flatMap）
 *   future.thenCompose(s -> anotherAsyncCall(s))
 *   与 thenApply 区别：
 *     thenApply  → 返回 CompletableFuture&lt;CompletableFuture&lt;T&gt;&gt;（嵌套）
 *     thenCompose → 返回 CompletableFuture&lt;T&gt;（扁平）
 * </pre>
 *
 * <h3>三、组合多个任务</h3>
 * <pre>
 * thenCombine：合并两个任务的结果
 *   future1.thenCombine(future2, (r1, r2) -> r1 + r2)
 *
 * allOf：等待所有任务完成
 *   CompletableFuture.allOf(f1, f2, f3).join()
 *   注意：allOf 返回 CompletableFuture&lt;Void&gt;，需手动获取各任务结果
 *
 * anyOf：等待任一任务完成（竞速）
 *   CompletableFuture.anyOf(f1, f2, f3)
 *   返回最先完成的任务的结果
 * </pre>
 *
 * <h3>四、异常处理</h3>
 * <pre>
 * exceptionally(Function)：只处理异常，提供降级值
 *   future.exceptionally(ex -> "default")
 *
 * handle(BiFunction)：同时处理成功和异常
 *   future.handle((result, ex) -> {
 *       if (ex != null) return "fallback";
 *       return result;
 *   })
 *
 * whenComplete(BiConsumer)：副作用操作，不改变结果
 *   future.whenComplete((result, ex) -> log(result, ex))
 *
 * 异常传播：
 *   链中某环节异常 → 后续 thenApply 被跳过 → 直到遇到异常处理方法
 * </pre>
 *
 * <h3>五、实战场景</h3>
 * <pre>
 * 并行调用多个服务（微服务场景）：
 *   串行调用：getUserInfo → getOrders → getPoints（耗时累加）
 *   并行调用：三个服务同时发起，allOf 等待全部完成（耗时取最大值）
 *
 * 典型收益：
 *   串行 1000ms → 并行 500ms（取决于最慢的服务）
 * </pre>
 *
 * <h3>六、下一步</h3>
 * <p>d10_readwritelock 读写锁</p>
 *
 * @author kungfu
 * @since D17
 */
package com.kungfu.concurrent.d9_completablefuture;
