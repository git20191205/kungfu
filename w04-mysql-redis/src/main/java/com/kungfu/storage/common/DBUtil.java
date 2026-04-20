package com.kungfu.storage.common;

import java.sql.*;

/**
 * MySQL 连接工具类
 *
 * <p>统一管理数据库连接配置，避免每个 Demo 重复写连接代码。
 * 修改下方常量即可切换到不同的 MySQL 实例。</p>
 *
 * @author kungfu
 * @since W04 - MySQL+Redis实战
 */
public class DBUtil {

    /** MySQL 连接地址（默认本地） */
    private static final String URL = "jdbc:mysql://localhost:3306"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
            + "&characterEncoding=UTF-8&rewriteBatchedStatements=true";

    /** 用户名 */
    private static final String USER = "root";

    /** 密码 */
    private static final String PASS = "123456";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL 驱动加载失败，请检查 pom.xml 中的 mysql-connector-java 依赖", e);
        }
    }

    /** 获取数据库连接 */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /** 获取指定 autoCommit 的连接（用于事务/锁 Demo） */
    public static Connection getConnection(boolean autoCommit) throws SQLException {
        Connection conn = getConnection();
        conn.setAutoCommit(autoCommit);
        return conn;
    }

    /** 静默关闭 Connection */
    public static void close(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    /** 静默关闭 Statement */
    public static void close(Statement stmt) {
        if (stmt != null) {
            try { stmt.close(); } catch (SQLException ignored) {}
        }
    }

    /** 静默关闭 ResultSet */
    public static void close(ResultSet rs) {
        if (rs != null) {
            try { rs.close(); } catch (SQLException ignored) {}
        }
    }

    /** 执行 DDL/DML（不返回结果） */
    public static void execute(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /** 执行查询并打印 ResultSet（调试用） */
    public static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();

        // 打印列名
        StringBuilder header = new StringBuilder("    ");
        for (int i = 1; i <= cols; i++) {
            header.append(String.format("%-20s", meta.getColumnLabel(i)));
        }
        System.out.println(header);

        // 打印每行
        int rowCount = 0;
        while (rs.next()) {
            StringBuilder row = new StringBuilder("    ");
            for (int i = 1; i <= cols; i++) {
                String val = rs.getString(i);
                row.append(String.format("%-20s", val == null ? "NULL" : val));
            }
            System.out.println(row);
            rowCount++;
        }
        System.out.println("    (" + rowCount + " rows)\n");
    }

    /** 执行 EXPLAIN 并格式化输出 */
    public static void explain(Connection conn, String sql) throws SQLException {
        System.out.println("    SQL: " + sql);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
            printResultSet(rs);
        }
    }
}
