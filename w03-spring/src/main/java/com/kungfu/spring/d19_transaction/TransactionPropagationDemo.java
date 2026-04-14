package com.kungfu.spring.d19_transaction;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 【Demo】事务 7 种传播行为
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>REQUIRED（默认）：有事务加入，没有则新建</li>
 *   <li>REQUIRES_NEW：始终创建新事务，挂起当前事务</li>
 *   <li>NESTED：在当前事务内嵌套一个保存点</li>
 *   <li>SUPPORTS / NOT_SUPPORTED / MANDATORY / NEVER 的行为差异</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>传播行为决定了嵌套事务之间的关系，是事务管理的难点</li>
 *   <li>实际开发中选错传播行为会导致数据一致性问题</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法
 *
 * @author kungfu
 * @since D19 - Spring源码精髓
 */
public class TransactionPropagationDemo {

    // ==================== 配置 ====================

    @Configuration
    @EnableTransactionManagement
    static class PropConfig {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .addScript("schema-d19.sql")
                    .build();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public PlatformTransactionManager txManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public OuterService outerService(JdbcTemplate jdbc, InnerService inner) {
            return new OuterService(jdbc, inner);
        }

        @Bean
        public InnerService innerService(JdbcTemplate jdbc) {
            return new InnerService(jdbc);
        }
    }

    // ==================== 业务 ====================

    @Service
    static class OuterService {
        private final JdbcTemplate jdbc;
        private final InnerService inner;

        public OuterService(JdbcTemplate jdbc, InnerService inner) {
            this.jdbc = jdbc;
            this.inner = inner;
        }

        /** 场景 1：REQUIRED —— 内部方法加入外部事务，外部异常一起回滚 */
        @Transactional
        public void outerRequired(boolean throwAtOuter) {
            jdbc.update("UPDATE account SET balance = balance - 10 WHERE name = 'Alice'");
            inner.innerRequired();  // 加入外部事务
            if (throwAtOuter) throw new RuntimeException("外部方法异常");
        }

        /** 场景 2：REQUIRES_NEW —— 内部方法新建事务，外部异常不影响内部 */
        @Transactional
        public void outerRequiresNew(boolean throwAtOuter) {
            jdbc.update("UPDATE account SET balance = balance - 10 WHERE name = 'Alice'");
            inner.innerRequiresNew();  // 独立事务
            if (throwAtOuter) throw new RuntimeException("外部方法异常");
        }
    }

    @Service
    static class InnerService {
        private final JdbcTemplate jdbc;

        public InnerService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void innerRequired() {
            jdbc.update("UPDATE account SET balance = balance + 10 WHERE name = 'Bob'");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void innerRequiresNew() {
            jdbc.update("UPDATE account SET balance = balance + 10 WHERE name = 'Bob'");
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   事务 7 种传播行为 Demo");
        System.out.println("========================================\n");

        // 一、7 种传播行为对比表
        showPropagationTable();

        // 二、REQUIRED 与 REQUIRES_NEW 实战对比
        demonstrateRequiredVsNew();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. REQUIRED（默认）: 有则加入，无则新建 —— 最常用");
        System.out.println("  2. REQUIRES_NEW: 始终新建，挂起外部事务 —— 用于日志/审计");
        System.out.println("  3. NESTED: 嵌套事务（保存点），外部回滚内部跟着滚");
        System.out.println("  4. SUPPORTS: 有就加入，无就非事务执行");
        System.out.println("  5. NOT_SUPPORTED: 挂起当前事务，非事务执行");
        System.out.println("  6. MANDATORY: 必须有外部事务，否则抛异常");
        System.out.println("  7. NEVER: 必须没有外部事务，否则抛异常");
        System.out.println();
        System.out.println("  ⚠ REQUIRES_NEW 需要独立连接，性能开销较大");
        System.out.println("  ⚠ NESTED 依赖底层数据库的 savepoint 支持");
        System.out.println();
    }

    // ==================== 一、传播行为表 ====================

    private static void showPropagationTable() {
        System.out.println("=== 一、7 种传播行为对比 ===\n");

        System.out.println("  ┌──────────────────┬────────────────────┬──────────────────────────────────────┐");
        System.out.println("  │ 传播行为          │ 当前有事务          │ 当前无事务                            │");
        System.out.println("  ├──────────────────┼────────────────────┼──────────────────────────────────────┤");
        System.out.println("  │ REQUIRED (默认)  │ 加入当前事务        │ 新建事务                              │");
        System.out.println("  │ REQUIRES_NEW     │ 挂起当前，新建      │ 新建事务                              │");
        System.out.println("  │ NESTED           │ 嵌套保存点          │ 新建事务                              │");
        System.out.println("  │ SUPPORTS         │ 加入当前事务        │ 非事务执行                            │");
        System.out.println("  │ NOT_SUPPORTED    │ 挂起当前事务        │ 非事务执行                            │");
        System.out.println("  │ MANDATORY        │ 加入当前事务        │ 抛出 IllegalTransactionStateException │");
        System.out.println("  │ NEVER            │ 抛出异常            │ 非事务执行                            │");
        System.out.println("  └──────────────────┴────────────────────┴──────────────────────────────────────┘");
        System.out.println();

        System.out.println("  关键差异：");
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │ REQUIRED vs REQUIRES_NEW                                │");
        System.out.println("  │   REQUIRED:     共享一个事务 → 一荣俱荣 / 一损俱损      │");
        System.out.println("  │   REQUIRES_NEW: 独立事务 → 内部可独立提交/回滚          │");
        System.out.println("  │                                                         │");
        System.out.println("  │ REQUIRES_NEW vs NESTED                                  │");
        System.out.println("  │   REQUIRES_NEW: 独立事务 (完全隔离)                     │");
        System.out.println("  │   NESTED:       嵌套保存点 (外部回滚内部跟滚)           │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ==================== 二、实战 ====================

    private static void demonstrateRequiredVsNew() {
        System.out.println("=== 二、REQUIRED vs REQUIRES_NEW 实战 ===\n");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(PropConfig.class);
        OuterService outerService = ctx.getBean(OuterService.class);
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        EmbeddedDatabase db = (EmbeddedDatabase) ctx.getBean(DataSource.class);

        printBalances(jdbc, "初始状态");

        // 场景 1: REQUIRED，外部异常 → 内外一起回滚
        System.out.println("  【场景 1】REQUIRED + 外部抛异常");
        System.out.println("    期望：Alice 和 Bob 的变更都回滚\n");
        try {
            outerService.outerRequired(true);
        } catch (Exception e) {
            System.out.println("    捕获: " + e.getMessage());
        }
        printBalances(jdbc, "REQUIRED 异常后");

        // 重置
        resetBalances(jdbc);

        // 场景 2: REQUIRES_NEW，外部异常 → 内部已独立提交，不受外部影响
        System.out.println("  【场景 2】REQUIRES_NEW + 外部抛异常");
        System.out.println("    期望：Alice 回滚（外部），Bob 保留（内部独立提交）\n");
        try {
            outerService.outerRequiresNew(true);
        } catch (Exception e) {
            System.out.println("    捕获: " + e.getMessage());
        }
        printBalances(jdbc, "REQUIRES_NEW 异常后");

        db.shutdown();
        ctx.close();
    }

    private static void printBalances(JdbcTemplate jdbc, String label) {
        Double alice = jdbc.queryForObject("SELECT balance FROM account WHERE name='Alice'", Double.class);
        Double bob = jdbc.queryForObject("SELECT balance FROM account WHERE name='Bob'", Double.class);
        System.out.println("    [" + label + "] Alice=" + alice + ", Bob=" + bob);
        System.out.println();
    }

    private static void resetBalances(JdbcTemplate jdbc) {
        jdbc.update("UPDATE account SET balance = 1000 WHERE name IN ('Alice', 'Bob')");
    }
}

/*
 * 【知识串联】
 * 理解了事务传播行为后，下一步：
 * → SpringBoot 如何自动配置事务管理器？（见 D20 AutoConfigurationDemo.java）
 */
