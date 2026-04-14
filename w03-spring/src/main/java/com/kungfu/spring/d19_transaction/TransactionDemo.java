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
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 【Demo】真实 Spring @Transactional 实战
 *
 * <h3>这个 Demo 演示什么？</h3>
 * <ul>
 *   <li>使用 H2 嵌入式数据库验证 @Transactional 的真实行为</li>
 *   <li>事务的默认回滚规则（只回滚 RuntimeException）</li>
 *   <li>rollbackFor 自定义回滚规则</li>
 *   <li>事务失效的经典场景：this 调用</li>
 * </ul>
 *
 * <h3>为什么重要？</h3>
 * <ul>
 *   <li>@Transactional 是日常开发使用频率最高的 Spring 特性之一</li>
 *   <li>事务失效是生产环境常见坑，必须掌握</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * 直接运行 main 方法（使用 H2 内存数据库，无需额外配置）
 *
 * @author kungfu
 * @since D19 - Spring源码精髓
 */
public class TransactionDemo {

    // ==================== 配置类 ====================

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {

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
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public AccountService accountService(JdbcTemplate jdbcTemplate) {
            return new AccountService(jdbcTemplate);
        }
    }

    // ==================== 业务类 ====================

    @Service
    static class AccountService {

        private final JdbcTemplate jdbc;

        public AccountService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        /** 查询余额 */
        public Double getBalance(String name) {
            return jdbc.queryForObject("SELECT balance FROM account WHERE name = ?",
                    Double.class, name);
        }

        /** 场景 1：正常转账（事务成功提交） */
        @Transactional
        public void transferOk(String from, String to, double amount) {
            jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
            jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
        }

        /** 场景 2：异常转账（RuntimeException 触发回滚） */
        @Transactional
        public void transferWithException(String from, String to, double amount) {
            jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
            if (true) throw new RuntimeException("模拟异常！");
            jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
        }

        /** 场景 3：checked 异常默认不回滚 */
        @Transactional
        public void transferWithCheckedException(String from, String to, double amount) throws Exception {
            jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
            if (true) throw new Exception("业务异常！");
            jdbc.update("UPDATE account SET balance = balance + ? WHERE name = ?", amount, to);
        }

        /** 场景 4：rollbackFor 强制 checked 异常回滚 */
        @Transactional(rollbackFor = Exception.class)
        public void transferRollbackForException(String from, String to, double amount) throws Exception {
            jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
            if (true) throw new Exception("业务异常！");
        }

        /** 场景 5：this 调用导致事务失效 */
        public void outerMethod(String from, String to, double amount) {
            // 此处的 this 调用不会触发 AOP 代理，事务注解失效
            this.innerTxMethod(from, to, amount);
        }

        @Transactional
        public void innerTxMethod(String from, String to, double amount) {
            jdbc.update("UPDATE account SET balance = balance - ? WHERE name = ?", amount, from);
            throw new RuntimeException("内部方法异常，但事务不会生效！");
        }
    }

    // ==================== main ====================

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   真实 Spring @Transactional Demo");
        System.out.println("========================================\n");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TxConfig.class);
        AccountService service = ctx.getBean(AccountService.class);
        EmbeddedDatabase db = (EmbeddedDatabase) ctx.getBean(DataSource.class);

        // 打印初始余额
        printBalances(service);

        // 场景 1：正常转账
        System.out.println("=== 场景 1：正常转账（应提交） ===\n");
        service.transferOk("Alice", "Bob", 100.0);
        System.out.println("  ✓ 转账完成");
        printBalances(service);

        // 场景 2：RuntimeException 回滚
        System.out.println("=== 场景 2：RuntimeException（应回滚） ===\n");
        try {
            service.transferWithException("Alice", "Bob", 200.0);
        } catch (RuntimeException e) {
            System.out.println("  捕获异常: " + e.getMessage());
        }
        printBalances(service);

        // 场景 3：checked 异常默认不回滚
        System.out.println("=== 场景 3：checked Exception（默认不回滚！） ===\n");
        try {
            service.transferWithCheckedException("Alice", "Bob", 200.0);
        } catch (Exception e) {
            System.out.println("  捕获异常: " + e.getMessage());
        }
        printBalances(service);
        System.out.println("  ⚠ 注意: Alice 余额已减少，但 Bob 未增加（非原子！）");
        System.out.println();

        // 场景 4：rollbackFor = Exception.class
        System.out.println("=== 场景 4：rollbackFor=Exception.class（强制回滚） ===\n");
        try {
            service.transferRollbackForException("Alice", "Bob", 300.0);
        } catch (Exception e) {
            System.out.println("  捕获异常: " + e.getMessage());
        }
        printBalances(service);

        // 场景 5：this 调用事务失效
        System.out.println("=== 场景 5：this 调用导致事务失效 ===\n");
        try {
            service.outerMethod("Alice", "Bob", 400.0);
        } catch (RuntimeException e) {
            System.out.println("  捕获异常: " + e.getMessage());
        }
        printBalances(service);
        System.out.println("  ⚠ 注意: 事务未生效，Alice 余额变化未回滚！");
        System.out.println();

        db.shutdown();
        ctx.close();

        // 面试速记
        System.out.println("========================================");
        System.out.println("   面试速记");
        System.out.println("========================================\n");
        System.out.println("  1. @Transactional 默认只回滚 RuntimeException 和 Error");
        System.out.println("  2. checked Exception 不回滚！需要 rollbackFor=Exception.class");
        System.out.println("  3. 事务失效场景：");
        System.out.println("     ✗ this 自调用（不走代理）");
        System.out.println("     ✗ private / static / final 方法（无法被代理）");
        System.out.println("     ✗ 注解加在非 public 方法上");
        System.out.println("     ✗ 异常被业务代码 try-catch 吞掉");
        System.out.println("     ✗ 类未被 Spring 管理");
        System.out.println("  4. 使用 try-catch 后仍要回滚：手动 TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()");
        System.out.println();
    }

    private static void printBalances(AccountService service) {
        System.out.println("  当前余额: Alice=" + service.getBalance("Alice")
                + ", Bob=" + service.getBalance("Bob"));
        System.out.println();
    }
}

/*
 * 【知识串联】
 * 理解了 @Transactional 基本用法后，下一步：
 * → 7 种事务传播行为（见 TransactionPropagationDemo.java）
 * → 实际项目中事务的最佳实践
 */
