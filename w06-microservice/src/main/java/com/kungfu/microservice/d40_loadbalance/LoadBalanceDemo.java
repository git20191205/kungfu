package com.kungfu.microservice.d40_loadbalance;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【Demo】五大负载均衡算法实战 — 理解 Ribbon/Nginx 核心
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ol>
 *   <li>Round Robin: 简单轮询</li>
 *   <li>Weighted Round Robin: 平滑加权轮询（Nginx 算法）</li>
 *   <li>Random: 随机选择</li>
 *   <li>Weighted Random: 加权随机</li>
 *   <li>Consistent Hash: 一致性哈希（虚拟节点）</li>
 * </ol>
 *
 * <h3>为什么重要？</h3>
 * <p>负载均衡是微服务高可用的基石，面试必考算法原理和适用场景。</p>
 *
 * <h3>运行方式</h3>
 * 纯 Java，直接运行 main
 *
 * @author kungfu
 * @since D40 - 负载均衡
 */
public class LoadBalanceDemo {

    // =============================================================
    // 服务实例
    // =============================================================

    static class Server {
        String address;
        int weight;

        Server(String address, int weight) {
            this.address = address;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return address;
        }
    }

    // =============================================================
    // 负载均衡接口
    // =============================================================

    interface LoadBalancer {
        String select(String requestKey);
        String name();
    }

    // =============================================================
    // 1. Round Robin — 简单轮询
    // =============================================================

    static class RoundRobinLB implements LoadBalancer {
        private final List<Server> servers;
        private final AtomicInteger counter = new AtomicInteger(0);

        RoundRobinLB(List<Server> servers) {
            this.servers = servers;
        }

        @Override
        public String select(String requestKey) {
            int idx = counter.getAndIncrement() % servers.size();
            return servers.get(idx).address;
        }

        @Override
        public String name() { return "Round Robin (轮询)"; }
    }

    // =============================================================
    // 2. Weighted Round Robin — 平滑加权轮询（Nginx 算法）
    // =============================================================

    static class WeightedRoundRobinLB implements LoadBalancer {
        private final List<Server> servers;
        private final int[] currentWeights;
        private final int totalWeight;

        WeightedRoundRobinLB(List<Server> servers) {
            this.servers = servers;
            this.currentWeights = new int[servers.size()];
            int total = 0;
            for (Server s : servers) total += s.weight;
            this.totalWeight = total;
        }

        @Override
        public String select(String requestKey) {
            // Nginx 平滑加权轮询算法
            // 1. 每个节点 currentWeight += weight
            // 2. 选 currentWeight 最大的
            // 3. 被选中的 currentWeight -= totalWeight
            int maxIdx = 0;
            for (int i = 0; i < servers.size(); i++) {
                currentWeights[i] += servers.get(i).weight;
                if (currentWeights[i] > currentWeights[maxIdx]) {
                    maxIdx = i;
                }
            }
            currentWeights[maxIdx] -= totalWeight;
            return servers.get(maxIdx).address;
        }

        @Override
        public String name() { return "Weighted Round Robin (加权轮询)"; }
    }

    // =============================================================
    // 3. Random — 随机
    // =============================================================

    static class RandomLB implements LoadBalancer {
        private final List<Server> servers;
        private final Random random = new Random(42);

        RandomLB(List<Server> servers) {
            this.servers = servers;
        }

        @Override
        public String select(String requestKey) {
            return servers.get(random.nextInt(servers.size())).address;
        }

        @Override
        public String name() { return "Random (随机)"; }
    }

    // =============================================================
    // 4. Weighted Random — 加权随机
    // =============================================================

    static class WeightedRandomLB implements LoadBalancer {
        private final List<Server> servers;
        private final int totalWeight;
        private final Random random = new Random(42);

        WeightedRandomLB(List<Server> servers) {
            this.servers = servers;
            int total = 0;
            for (Server s : servers) total += s.weight;
            this.totalWeight = total;
        }

        @Override
        public String select(String requestKey) {
            int r = random.nextInt(totalWeight);
            for (Server s : servers) {
                r -= s.weight;
                if (r < 0) return s.address;
            }
            return servers.get(servers.size() - 1).address;
        }

        @Override
        public String name() { return "Weighted Random (加权随机)"; }
    }

    // =============================================================
    // 5. Consistent Hash — 一致性哈希
    // =============================================================

    static class ConsistentHashLB implements LoadBalancer {
        private final TreeMap<Integer, String> ring = new TreeMap<>();
        private static final int VIRTUAL_NODES = 150;

        ConsistentHashLB(List<Server> servers) {
            for (Server s : servers) {
                for (int i = 0; i < VIRTUAL_NODES; i++) {
                    int hash = hash(s.address + "#VN" + i);
                    ring.put(hash, s.address);
                }
            }
        }

        @Override
        public String select(String requestKey) {
            int hash = hash(requestKey);
            Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
            if (entry == null) entry = ring.firstEntry();
            return entry.getValue();
        }

        private int hash(String key) {
            // FNV1_32_HASH
            final int p = 16777619;
            int hash = (int) 2166136261L;
            for (int i = 0; i < key.length(); i++) {
                hash = (hash ^ key.charAt(i)) * p;
            }
            hash += hash << 13;
            hash ^= hash >> 7;
            hash += hash << 3;
            hash ^= hash >> 17;
            hash += hash << 5;
            return Math.abs(hash);
        }

        @Override
        public String name() { return "Consistent Hash (一致性哈希)"; }
    }

    // =============================================================
    // Main
    // =============================================================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  五大负载均衡算法实战");
        System.out.println("========================================\n");

        showPrinciple();
        demoAlgorithms();
        showComparisonTable();
        showClientVsServer();
        showKnowledgeLink();
    }

    private static void showPrinciple() {
        System.out.println("=== 一、负载均衡原理 ===\n");

        System.out.println("  ┌──────────────────────────────────────────────────────────────┐");
        System.out.println("  │                    负载均衡示意                                │");
        System.out.println("  │                                                              │");
        System.out.println("  │              ┌─────────────────┐                             │");
        System.out.println("  │              │  Load Balancer   │                             │");
        System.out.println("  │              └────────┬────────┘                             │");
        System.out.println("  │           ┌───────────┼───────────┐                          │");
        System.out.println("  │           ▼           ▼           ▼                          │");
        System.out.println("  │  ┌──────────────┐┌──────────────┐┌──────────────┐           │");
        System.out.println("  │  │ Server-1     ││ Server-2     ││ Server-3     │           │");
        System.out.println("  │  │ weight=5     ││ weight=3     ││ weight=2     │           │");
        System.out.println("  │  └──────────────┘└──────────────┘└──────────────┘           │");
        System.out.println("  └──────────────────────────────────────────────────────────────┘\n");

        System.out.println("  服务器列表: ");
        System.out.println("    Server-1: 192.168.1.1:8080  weight=5");
        System.out.println("    Server-2: 192.168.1.2:8080  weight=3");
        System.out.println("    Server-3: 192.168.1.3:8080  weight=2\n");
    }

    private static void demoAlgorithms() {
        System.out.println("=== 二、五大算法 10000 次请求分布 ===\n");

        List<Server> servers = Arrays.asList(
            new Server("192.168.1.1:8080", 5),
            new Server("192.168.1.2:8080", 3),
            new Server("192.168.1.3:8080", 2)
        );

        LoadBalancer[] balancers = {
            new RoundRobinLB(servers),
            new WeightedRoundRobinLB(servers),
            new RandomLB(servers),
            new WeightedRandomLB(servers),
            new ConsistentHashLB(servers)
        };

        int total = 10000;

        for (LoadBalancer lb : balancers) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Server s : servers) counts.put(s.address, 0);

            for (int i = 0; i < total; i++) {
                String key = "request-" + i;
                String selected = lb.select(key);
                counts.merge(selected, 1, Integer::sum);
            }

            System.out.println("  ── " + lb.name() + " ──");
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                int count = e.getValue();
                double pct = count * 100.0 / total;
                int barLen = (int) (pct / 2);
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < barLen; i++) bar.append("█");
                System.out.printf("    %-20s %5d (%5.1f%%) %s%n",
                        e.getKey(), count, pct, bar);
            }
            System.out.println();
        }

        // 演示平滑加权轮询的分布细节
        System.out.println("  ★ 平滑加权轮询前 10 次选择（weight=5,3,2）:");
        WeightedRoundRobinLB wrr = new WeightedRoundRobinLB(servers);
        System.out.print("    ");
        for (int i = 0; i < 10; i++) {
            String s = wrr.select(null);
            String label = s.endsWith("1:8080") ? "S1" : s.endsWith("2:8080") ? "S2" : "S3";
            System.out.print(label + " ");
        }
        System.out.println("\n    → 分布均匀，不会出现连续选同一台（Nginx 算法优势）\n");
    }

    private static void showComparisonTable() {
        System.out.println("=== 三、算法对比 ===\n");

        System.out.println("  ┌──────────────────┬──────────────────┬──────────────────────────┐");
        System.out.println("  │ 算法             │ 适用场景         │ 优缺点                   │");
        System.out.println("  ├──────────────────┼──────────────────┼──────────────────────────┤");
        System.out.println("  │ Round Robin      │ 服务器性能相同   │ ✓简单 ✗不考虑权重       │");
        System.out.println("  │ Weighted RR ★   │ 服务器性能不同   │ ✓平滑分布 ✓Nginx默认   │");
        System.out.println("  │ Random           │ 大量请求时       │ ✓简单 ✗小样本不均匀     │");
        System.out.println("  │ Weighted Random  │ 性能不同+简单    │ ✓考虑权重 ✗不够平滑     │");
        System.out.println("  │ Consistent Hash  │ 有状态服务/缓存  │ ✓会话保持 ✓扩缩容友好  │");
        System.out.println("  └──────────────────┴──────────────────┴──────────────────────────┘\n");
    }

    private static void showClientVsServer() {
        System.out.println("=== 四、客户端 LB vs 服务端 LB ===\n");

        System.out.println("  ┌──────────────┬──────────────────────┬──────────────────────┐");
        System.out.println("  │              │ 客户端 LB (Ribbon)   │ 服务端 LB (Nginx)    │");
        System.out.println("  ├──────────────┼──────────────────────┼──────────────────────┤");
        System.out.println("  │ LB 位置      │ 调用方进程内         │ 独立的 LB 服务器     │");
        System.out.println("  │ 实例列表     │ 从注册中心拉取       │ 配置文件 upstream    │");
        System.out.println("  │ 单点故障     │ ✗ 无单点             │ ✓ LB 本身可能故障   │");
        System.out.println("  │ 性能         │ ✓ 少一跳             │ ✗ 多一跳转发        │");
        System.out.println("  │ 灵活性       │ ✓ 可定制算法         │ ✗ 算法有限          │");
        System.out.println("  │ 典型代表     │ Ribbon / LoadBalancer│ Nginx / F5 / HAProxy│");
        System.out.println("  └──────────────┴──────────────────────┴──────────────────────┘\n");

        System.out.println("  ★ 面试必背：");
        System.out.println("    Q: Ribbon 的负载均衡策略有哪些？");
        System.out.println("    A: RoundRobinRule（默认）、RandomRule、WeightedResponseTimeRule、");
        System.out.println("       BestAvailableRule、RetryRule、ZoneAvoidanceRule\n");
    }

    private static void showKnowledgeLink() {
        System.out.println("【知识串联】");
        System.out.println("  D39 Feign 调用 → D40 负载均衡 → D41 网关路由");
        System.out.println("  Feign 内部集成 Ribbon 做客户端负载均衡");
        System.out.println("  一致性哈希在 Redis Cluster 分片中也有应用（D26）");
    }
}