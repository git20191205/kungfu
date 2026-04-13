package com.kungfu.concurrent.d9_completablefuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 【Demo】CompletableFuture — 异步编程利器
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>supplyAsync / runAsync 创建异步任务</li>
 *   <li>thenApply / thenAccept / thenRun 链式处理</li>
 *   <li>thenCombine / allOf / anyOf 组合多个任务</li>
 *   <li>exceptionally / handle / whenComplete 异常处理</li>
 *   <li>实战：并行调用多个服务并合并结果</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * CompletableFuture 是 JDK 8 引入的异步编程核心工具，
 * 解决了传统 Future 无法链式调用、无法组合的痛点，
 * 在微服务并行调用、异步任务编排等场景广泛使用。
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法即可
 *
 * @author kungfu
 * @since D17 - 并发编程
 */
public class CompletableFutureDemo {

    /**
     * 一、创建异步任务
     * - supplyAsync：有返回值（默认 ForkJoinPool）
     * - runAsync：无返回值（fire-and-forget）
     * - supplyAsync + 自定义线程池
     */
    private static void demonstrateCreateAsync() {
        System.out.println("=== 一、创建异步任务 ===\n");

        // 1. supplyAsync — 有返回值，默认使用 ForkJoinPool.commonPool()
        CompletableFuture<String> supplyFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("  [supplyAsync] 执行线程: " + Thread.currentThread().getName());
            return "Hello from supplyAsync";
        });
        String supplyResult = supplyFuture.join();
        System.out.println("  [supplyAsync] 返回值: " + supplyResult);
        System.out.println();

        // 2. runAsync — 无返回值，适合 fire-and-forget
        CompletableFuture<Void> runFuture = CompletableFuture.runAsync(() -> {
            System.out.println("  [runAsync] 执行线程: " + Thread.currentThread().getName());
            System.out.println("  [runAsync] 执行了一个无返回值的任务");
        });
        runFuture.join();
        System.out.println();

        // 3. supplyAsync + 自定义线程池
        ExecutorService customPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("custom-pool-thread");
            return t;
        });
        CompletableFuture<String> customFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("  [自定义线程池] 执行线程: " + Thread.currentThread().getName());
            return "Hello from custom pool";
        }, customPool);
        System.out.println("  [自定义线程池] 返回值: " + customFuture.join());
        customPool.shutdown();

        System.out.println();
    }

    /**
     * 二、链式处理
     * - thenApply：转换结果（map）
     * - thenAccept：消费结果
     * - thenRun：执行后续动作（不访问结果）
     * - thenCompose vs thenApply：flatMap vs map
     */
    private static void demonstrateChaining() {
        System.out.println("=== 二、链式处理 ===\n");

        // 1. thenApply — 转换结果（类似 map）
        String result1 = CompletableFuture.supplyAsync(() -> "Hello CompletableFuture")
                .thenApply(s -> {
                    System.out.println("  [thenApply-1] 字符串 → 长度: \"" + s + "\" → " + s.length());
                    return s.length();
                })
                .thenApply(len -> {
                    String msg = "长度是 " + len;
                    System.out.println("  [thenApply-2] 长度 → 描述: " + msg);
                    return msg;
                })
                .join();
        System.out.println("  最终结果: " + result1);
        System.out.println();

        // 2. thenAccept — 消费结果（无返回值）
        CompletableFuture.supplyAsync(() -> "消费我吧")
                .thenAccept(s -> System.out.println("  [thenAccept] 消费结果: " + s))
                .join();
        System.out.println();

        // 3. thenRun — 执行后续动作，无法访问上游结果
        CompletableFuture.supplyAsync(() -> "某个结果")
                .thenRun(() -> System.out.println("  [thenRun] 上游已完成，但我拿不到结果，只做后续动作"))
                .join();
        System.out.println();

        // 4. thenCompose vs thenApply — flatMap vs map
        System.out.println("  --- thenCompose vs thenApply ---");

        // thenApply: 返回嵌套的 CompletableFuture<CompletableFuture<String>>
        CompletableFuture<CompletableFuture<String>> nested = CompletableFuture.supplyAsync(() -> "data")
                .thenApply(s -> CompletableFuture.supplyAsync(() -> s + " → processed"));
        System.out.println("  [thenApply]   嵌套类型，需要两次 join: " + nested.join().join());

        // thenCompose: 返回扁平的 CompletableFuture<String>
        String flat = CompletableFuture.supplyAsync(() -> "data")
                .thenCompose(s -> CompletableFuture.supplyAsync(() -> s + " → processed"))
                .join();
        System.out.println("  [thenCompose] 扁平类型，一次 join:     " + flat);

        System.out.println();
    }

    /**
     * 三、组合多个任务
     * - thenCombine：合并两个任务结果
     * - allOf：等待所有任务完成
     * - anyOf：竞速，取最先完成的结果
     */
    private static void demonstrateCombination() {
        System.out.println("=== 三、组合多个任务 ===\n");

        // 1. thenCombine — 合并两个任务的结果
        CompletableFuture<String> priceTask = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "价格: 99.9";
        });
        CompletableFuture<String> discountTask = CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return "折扣: 8折";
        });
        String combined = priceTask.thenCombine(discountTask, (price, discount) ->
                price + " | " + discount + " → 最终: 79.92"
        ).join();
        System.out.println("  [thenCombine] " + combined);
        System.out.println();

        // 2. allOf — 等待所有任务完成
        CompletableFuture<String> task1 = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "任务A完成";
        });
        CompletableFuture<String> task2 = CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return "任务B完成";
        });
        CompletableFuture<String> task3 = CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return "任务C完成";
        });

        CompletableFuture.allOf(task1, task2, task3).join();
        // allOf 返回 Void，需手动获取各任务结果
        String allResult = task1.join() + " | " + task2.join() + " | " + task3.join();
        System.out.println("  [allOf] 全部完成: " + allResult);
        System.out.println();

        // 3. anyOf — 竞速，取最先完成的
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "慢任务(500ms)";
        });
        CompletableFuture<String> medium = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return "中任务(300ms)";
        });
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "快任务(100ms)";
        });

        Object winner = CompletableFuture.anyOf(slow, medium, fast).join();
        System.out.println("  [anyOf] 最先完成的: " + winner);

        System.out.println();
    }

    /**
     * 四、异常处理
     * - exceptionally：只处理异常，提供降级值
     * - handle：同时处理成功和异常
     * - whenComplete：副作用操作，不改变结果
     * - 异常在链中的传播
     */
    private static void demonstrateExceptionHandling() {
        System.out.println("=== 四、异常处理 ===\n");

        // 1. exceptionally — 只处理异常，提供降级值
        String r1 = CompletableFuture.supplyAsync(() -> {
            if (true) throw new RuntimeException("模拟异常");
            return "正常结果";
        }).exceptionally(ex -> {
            System.out.println("  [exceptionally] 捕获异常: " + ex.getMessage());
            return "降级结果";
        }).join();
        System.out.println("  [exceptionally] 最终结果: " + r1);
        System.out.println();

        // 2. handle — 同时处理成功和异常
        String r2 = CompletableFuture.supplyAsync(() -> {
            if (true) throw new RuntimeException("handle 测试异常");
            return "正常结果";
        }).handle((result, ex) -> {
            if (ex != null) {
                System.out.println("  [handle] 异常分支: " + ex.getMessage());
                return "handle 降级结果";
            }
            System.out.println("  [handle] 成功分支: " + result);
            return result;
        }).join();
        System.out.println("  [handle] 最终结果: " + r2);
        System.out.println();

        // handle — 成功情况
        String r3 = CompletableFuture.supplyAsync(() -> "一切正常")
                .handle((result, ex) -> {
                    if (ex != null) {
                        return "不会走到这里";
                    }
                    System.out.println("  [handle-成功] 成功分支: " + result);
                    return result + " (已处理)";
                }).join();
        System.out.println("  [handle-成功] 最终结果: " + r3);
        System.out.println();

        // 3. whenComplete — 副作用操作，不改变结果
        String r4 = CompletableFuture.supplyAsync(() -> "whenComplete 的结果")
                .whenComplete((result, ex) -> {
                    // 只做副作用（如日志），不改变结果
                    System.out.println("  [whenComplete] 观察结果: " + result + ", 异常: " + ex);
                }).join();
        System.out.println("  [whenComplete] 最终结果不变: " + r4);
        System.out.println();

        // 4. 异常在链中的传播
        String r5 = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("链头异常");
        }).thenApply(obj -> {
            // 这里不会执行，因为上游已异常
            System.out.println("  [传播] 这行不会打印");
            return "不会到这里";
        }).thenApply(s -> {
            // 这里也不会执行
            System.out.println("  [传播] 这行也不会打印");
            return "也不会到这里";
        }).exceptionally(ex -> {
            System.out.println("  [传播] 异常跳过了中间的 thenApply，在这里被捕获: " + ex.getMessage());
            return "异常恢复";
        }).join();
        System.out.println("  [传播] 最终结果: " + r5);

        System.out.println();
    }

    /**
     * 五、实战场景 — 并行调用多个服务
     * 对比串行 vs 并行的耗时差异
     */
    private static void demonstrateParallelServiceCalls() {
        System.out.println("=== 五、实战场景 — 并行调用多个服务 ===\n");

        // ===== 串行版本 =====
        System.out.println("  --- 串行调用 ---");
        long serialStart = System.currentTimeMillis();

        String userInfo = getUserInfo();
        String orders = getOrders();
        String points = getPoints();

        long serialTime = System.currentTimeMillis() - serialStart;
        System.out.println("  用户信息: " + userInfo);
        System.out.println("  订单列表: " + orders);
        System.out.println("  积分信息: " + points);
        System.out.println("  串行总耗时: " + serialTime + "ms (约 300+500+200 = 1000ms)");
        System.out.println();

        // ===== 并行版本 =====
        System.out.println("  --- 并行调用 (CompletableFuture.allOf) ---");
        long parallelStart = System.currentTimeMillis();

        CompletableFuture<String> userInfoFuture = CompletableFuture.supplyAsync(CompletableFutureDemo::getUserInfo);
        CompletableFuture<String> ordersFuture = CompletableFuture.supplyAsync(CompletableFutureDemo::getOrders);
        CompletableFuture<String> pointsFuture = CompletableFuture.supplyAsync(CompletableFutureDemo::getPoints);

        // 等待所有任务完成
        CompletableFuture.allOf(userInfoFuture, ordersFuture, pointsFuture).join();

        long parallelTime = System.currentTimeMillis() - parallelStart;
        System.out.println("  用户信息: " + userInfoFuture.join());
        System.out.println("  订单列表: " + ordersFuture.join());
        System.out.println("  积分信息: " + pointsFuture.join());
        System.out.println("  并行总耗时: " + parallelTime + "ms (约 max(300,500,200) = 500ms)");
        System.out.println();

        // ===== 对比 =====
        System.out.println("  --- 耗时对比 ---");
        System.out.println("  串行: " + serialTime + "ms");
        System.out.println("  并行: " + parallelTime + "ms");
        System.out.println("  提升: 约 " + (serialTime - parallelTime) + "ms，并行版本快 "
                + String.format("%.1f", (double) serialTime / Math.max(parallelTime, 1)) + " 倍");

        System.out.println();
    }

    // ========== 模拟服务调用 ==========

    private static String getUserInfo() {
        sleep(300);
        return "{name: '张三', age: 28}";
    }

    private static String getOrders() {
        sleep(500);
        return "[订单1, 订单2, 订单3]";
    }

    private static String getPoints() {
        sleep(200);
        return "{积分: 2580}";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================
    //  主方法
    // ========================================
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  CompletableFuture — 异步编程利器");
        System.out.println("========================================\n");

        demonstrateCreateAsync();
        demonstrateChaining();
        demonstrateCombination();
        demonstrateExceptionHandling();
        demonstrateParallelServiceCalls();

        // ========================================
        //  面试速记
        // ========================================
        System.out.println("========================================");
        System.out.println("  面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. supplyAsync 有返回值，runAsync 无返回值");
        System.out.println("  2. thenApply = map, thenCompose = flatMap");
        System.out.println("  3. allOf 等所有完成，anyOf 等任一完成");
        System.out.println("  4. exceptionally 只处理异常，handle 处理成功和异常");
        System.out.println("  5. 默认线程池是 ForkJoinPool.commonPool()，生产环境建议自定义线程池");
        System.out.println();
        System.out.println("========================================");
    }
}

/*
 * 【知识串联】
 * CompletableFuture → ForkJoinPool → 函数式编程 → 响应式编程
 */
