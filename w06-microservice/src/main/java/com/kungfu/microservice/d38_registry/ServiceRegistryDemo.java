package com.kungfu.microservice.d38_registry;

import java.util.*;
import java.util.concurrent.*;

/**
 * 【Demo】手写 Mini 注册中心 — 理解服务注册发现原理
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>手写简化版注册中心（类似 Eureka/Nacos 核心）</li>
 *   <li>服务注册、服务发现、心跳续约、超时剔除</li>
 *   <li>模拟多个服务实例注册和发现</li>
 *   <li>Nacos vs Eureka vs ZooKeeper 对比</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D38 - 服务注册发现
 */
public class ServiceRegistryDemo {

    // =============================================================
    // Mini 注册中心实现
    // =============================================================

    static class ServiceInstance {
        String serviceName;
        String instanceId;
        String host;
        int port;
        long lastHeartbeat;
        Map<String, String> metadata;

        ServiceInstance(String serviceName, String host, int port) {
            this.serviceName = serviceName;
            this.instanceId = serviceName + "#" + host + ":" + port;
            this.host = host;
            this.port = port;
            this.lastHeartbeat = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }

        @Override
        public String toString() {
            return instanceId;
        }
    }

    static class MiniRegistry {
        // 注册表: serviceName → List<ServiceInstance>
        private final ConcurrentHashMap<String, List<ServiceInstance>> registry = new ConcurrentHashMap<>();
        // 心跳超时时间
        private static final long HEARTBEAT_TIMEOUT_MS = 5000;
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        MiniRegistry() {
            // 启动定时剔除任务（每 3 秒检查一次）
            scheduler.scheduleAtFixedRate(this::evictExpired, 3, 3, TimeUnit.SECONDS);
        }

        /** 服务注册 */
        void register(ServiceInstance instance) {
            registry.computeIfAbsent(instance.serviceName, k -> new CopyOnWriteArrayList<>()).add(instance);
            System.out.println("    [注册] " + instance.instanceId + " 注册成功");
        }

        /** 服务发现 */
        List<ServiceInstance> discover(String serviceName) {
            return registry.getOrDefault(serviceName, Collections.emptyList());
        }

        /** 心跳续约 */
        void heartbeat(String instanceId) {
            registry.values().forEach(instances ->
                instances.stream()
                    .filter(i -> i.instanceId.equals(instanceId))
                    .forEach(i -> i.lastHeartbeat = System.currentTimeMillis())
            );
        }

        /** 剔除超时实例 */
        void evictExpired() {
            long now = System.currentTimeMillis();
            registry.forEach((name, instances) -> {
                Iterator<ServiceInstance> it = instances.iterator();
                while (it.hasNext()) {
                    ServiceInstance inst = it.next();
                    if (now - inst.lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        it.remove();
                        System.out.println("    [剔除] " + inst.instanceId + " 心跳超时，已剔除");
                    }
                }
            });
        }

        void shutdown() {
            scheduler.shutdown();
        }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  手写 Mini 注册中心");
        System.out.println("========================================\n");

        showPrinciple();
        demoRegistryFlow();
        showComparison();
    }

    private static void showPrinciple() {
        System.out.println("=== 一、服务注册发现原理 ===\n");

        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    注册中心 (Nacos/Eureka)                    │");
        System.out.println("  │  ┌─────────────────────────────────────────────────────────┐│");
        System.out.println("  │  │ 注册表: {                                               ││");
        System.out.println("  │  │   \"order-service\": [192.168.1.1:8080, 192.168.1.2:8080] ││");
        System.out.println("  │  │   \"stock-service\": [192.168.1.3:8081]                   ││");
        System.out.println("  │  │ }                                                       ││");
        System.out.println("  │  └─────────────────────────────────────────────────────────┘│");
        System.out.println("  └──────────────────────────┬──────────────────────────────────┘");
        System.out.println("           ↑ 注册/心跳        ↓ 发现/订阅");
        System.out.println("  ┌────────────────┐    ┌────────────────┐");
        System.out.println("  │ 服务提供者     │    │ 服务消费者     │");
        System.out.println("  │ (order-service)│    │ (api-gateway)  │");
        System.out.println("  └────────────────┘    └────────────────┘\n");

        System.out.println("  核心流程：");
        System.out.println("    1. 服务启动 → 向注册中心注册（IP + 端口 + 元数据）");
        System.out.println("    2. 消费者 → 从注册中心获取服务实例列表");
        System.out.println("    3. 服务定时发送心跳（默认 5s/次）");
        System.out.println("    4. 注册中心检测心跳超时 → 剔除实例");
        System.out.println("    5. 实例变更 → 推送通知消费者（或消费者定时拉取）\n");
    }

    private static void demoRegistryFlow() throws Exception {
        System.out.println("=== 二、模拟注册发现流程 ===\n");

        MiniRegistry registry = new MiniRegistry();

        // 注册 3 个 order-service 实例
        System.out.println("  Step 1: 注册服务实例");
        ServiceInstance order1 = new ServiceInstance("order-service", "192.168.1.1", 8080);
        ServiceInstance order2 = new ServiceInstance("order-service", "192.168.1.2", 8080);
        ServiceInstance order3 = new ServiceInstance("order-service", "192.168.1.3", 8080);
        registry.register(order1);
        registry.register(order2);
        registry.register(order3);
        System.out.println();

        // 服务发现
        System.out.println("  Step 2: 服务发现");
        List<ServiceInstance> instances = registry.discover("order-service");
        System.out.println("    发现 order-service 实例: " + instances);
        System.out.println("    实例数: " + instances.size() + "\n");

        // 模拟心跳
        System.out.println("  Step 3: 心跳续约");
        registry.heartbeat(order1.instanceId);
        registry.heartbeat(order2.instanceId);
        // order3 不发心跳（模拟宕机）
        System.out.println("    order1, order2 续约成功");
        System.out.println("    order3 未续约（模拟宕机）\n");

        // 等待超时剔除
        System.out.println("  Step 4: 等待心跳超时（6 秒）...");
        Thread.sleep(6000);

        // 再次发现
        instances = registry.discover("order-service");
        System.out.println("  Step 5: 再次发现 order-service");
        System.out.println("    剩余实例: " + instances);
        System.out.println("    → order3 因心跳超时被剔除\n");

        registry.shutdown();
    }

    private static void showComparison() {
        System.out.println("=== 三、注册中心对比 ===\n");

        System.out.println("  ┌──────────────┬──────────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("  │              │ Nacos ★     │ Eureka       │ ZooKeeper    │ Consul       │");
        System.out.println("  ├──────────────┼──────────────┼──────────────┼──────────────┼──────────────┤");
        System.out.println("  │ CAP          │ AP + CP 可选 │ AP           │ CP           │ CP           │");
        System.out.println("  │ 健康检查     │ TCP/HTTP/SQL │ 心跳         │ 长连接       │ TCP/HTTP     │");
        System.out.println("  │ 推送方式     │ 长轮询+推送  │ 定时拉取     │ Watch 推送   │ 长轮询       │");
        System.out.println("  │ 配置中心     │ ✓ 内置       │ ✗            │ ✗            │ ✓ KV Store  │");
        System.out.println("  │ 雪崩保护     │ ✓            │ ✓ 自我保护   │ ✗            │ ✗            │");
        System.out.println("  │ 适用场景     │ 阿里系/通用  │ Netflix 系   │ Dubbo 系     │ HashiCorp 系│");
        System.out.println("  └──────────────┴──────────────┴──────────────┴──────────────┴──────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Nacos 和 Eureka 的区别？");
        System.out.println("    A: 1) Nacos 支持 AP+CP 切换，Eureka 只有 AP");
        System.out.println("       2) Nacos 有配置中心功能，Eureka 没有");
        System.out.println("       3) Nacos 用长轮询+推送，Eureka 用定时拉取（30s）");
        System.out.println("       4) Nacos 支持临时实例(AP)+持久实例(CP)");
        System.out.println();
    }
}
