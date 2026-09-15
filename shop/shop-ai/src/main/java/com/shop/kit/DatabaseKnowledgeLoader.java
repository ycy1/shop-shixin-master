package com.shop.kit;

import com.shop.entity.ConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;

/**
 * 数据库知识加载器：读取数据库表结构作为 AI 知识上下文
 * @Date 2026/7/17
 */
@Slf4j
@Component
public class DatabaseKnowledgeLoader {

    /**
     * 加载数据库结构信息
     */
    public String load(ConfigSource config) {
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isEmpty()) {
            return "数据库连接信息不完整";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("数据库结构信息：\n");
        Connection conn = null;

        try {
            Class.forName(driverClass(config.getJdbcUrl()));
            conn = DriverManager.getConnection(
                    config.getJdbcUrl(),
                    config.getDbUsername(),
                    config.getDbPassword()
            );
            DatabaseMetaData meta = conn.getMetaData();
            // 只扫描当前数据库（JDBC URL 中指定的库）下的表
            String catalog = conn.getCatalog();

            try (ResultSet tables = meta.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableCatalog = tables.getString("TABLE_CAT");
                    // 只显示指定库的表
                    if (catalog != null && !catalog.equals(tableCatalog)) continue;
                    sb.append("\n表：").append(tableName).append("\n");

                    try (ResultSet cols = meta.getColumns(catalog, null, tableName, "%")) {
                        while (cols.next()) {
                            sb.append("  - ").append(cols.getString("COLUMN_NAME"))
                              .append(" (").append(cols.getString("TYPE_NAME"))
                              .append(")");
                            String remark = cols.getString("REMARKS");
                            if (remark != null && !remark.isEmpty()) {
                                sb.append(" ").append(remark);
                            }
                            sb.append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("数据库知识加载失败", e);
            sb.append("数据库连接或读取失败：").append(e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }

        return sb.toString();
    }

    private static final java.util.Set<String> READ_ONLY_PREFIXES =
            java.util.Set.of("SELECT", "SHOW", "WITH", "DESC", "DESCRIBE", "EXPLAIN");

    /**
     * 校验是否为只读查询（逐条检查，防止多语句注入）
     */
    public static void validateReadOnly(String sql) {
        // 按分号分割多条 SQL
        String[] statements = sql.split(";");
        for (String stmt : statements) {
            String trimmed = stmt.trim().toUpperCase();
            if (trimmed.isEmpty()) continue;
            // 过滤掉注释
            if (trimmed.startsWith("--") || trimmed.startsWith("/*")) continue;
            boolean isReadOnly = READ_ONLY_PREFIXES.stream().anyMatch(trimmed::startsWith);
            if (!isReadOnly) {
                throw new IllegalArgumentException(
                        "仅支持 SELECT/SHOW/DESCRIBE/EXPLAIN 等只读查询，不允许执行修改操作。非法语句: " + trimmed.substring(0, Math.min(50, trimmed.length())));
            }
        }
    }

    /**
     * 执行 SQL 查询并返回结果集的文本表示
     */
    public String executeQuery(ConfigSource config, String sql) throws ClassNotFoundException {
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isEmpty()) {
            return "数据库连接信息不完整";
        }

        // 只读校验
        validateReadOnly(sql);

        log.info("执行 SQL 查询: {}", sql);

        StringBuilder sb = new StringBuilder();
        sb.append("SQL：").append(sql).append("\n查询结果：\n");

        Class.forName(driverClass(config.getJdbcUrl()));
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getDbUsername(), config.getDbPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            // 列名
            for (int i = 1; i <= colCount; i++) {
                sb.append(meta.getColumnLabel(i)).append("\t");
            }
            sb.append("\n");

            // 数据行（最多返回 50 行）
            int rowCount = 0;
            while (rs.next() && rowCount < 50) {
                for (int i = 1; i <= colCount; i++) {
                    String val = rs.getString(i);
                    sb.append(val != null ? val : "NULL").append("\t");
                }
                sb.append("\n");
                rowCount++;
            }
            if (rowCount >= 50 && rs.next()) {
                sb.append("...(仅显示前 50 行)\n");
            }
            sb.append("共 ").append(rowCount).append(" 行数据\n");

        } catch (SQLException e) {
            log.error("SQL 执行失败: {}", sql, e);
            sb.append("查询失败：").append(e.getMessage());
        }

        return sb.toString();
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection(ConfigSource config) throws SQLException {
        try {
            Class.forName(driverClass(config.getJdbcUrl()));
        } catch (ClassNotFoundException e) {
            throw new SQLException("数据库驱动加载失败：" + e.getMessage());
        }
        return DriverManager.getConnection(
                config.getJdbcUrl(), config.getDbUsername(), config.getDbPassword());
    }

    private String driverClass(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (jdbcUrl.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (jdbcUrl.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        return "com.mysql.cj.jdbc.Driver";
    }
}
