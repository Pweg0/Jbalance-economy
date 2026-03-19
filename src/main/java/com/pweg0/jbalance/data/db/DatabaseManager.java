package com.pweg0.jbalance.data.db;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.neoforged.fml.loading.FMLPaths;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Manages HikariCP connection pool with MySQL/SQLite routing and schema migration.
 * Reads USE_MYSQL config value at construction time to decide which database to use.
 */
public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final boolean isMysql;

    public DatabaseManager() {
        HikariConfig hc = new HikariConfig();
        this.isMysql = JBalanceConfig.USE_MYSQL.get();

        if (isMysql) {
            String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                JBalanceConfig.DB_HOST.get(),
                JBalanceConfig.DB_PORT.get(),
                JBalanceConfig.DB_NAME.get()
            );
            hc.setJdbcUrl(url);
            hc.setUsername(JBalanceConfig.DB_USER.get());
            hc.setPassword(JBalanceConfig.DB_PASSWORD.get());
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setMaximumPoolSize(10);
            hc.setMinimumIdle(2);
            JBalance.LOGGER.info("[JBalance] Using MySQL database at {}:{}/{}",
                JBalanceConfig.DB_HOST.get(),
                JBalanceConfig.DB_PORT.get(),
                JBalanceConfig.DB_NAME.get());
        } else {
            Path dbPath = FMLPaths.GAMEDIR.get().resolve("jbalance.db");
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            hc.setJdbcUrl(url);
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setMaximumPoolSize(1); // SQLite: single writer mandatory
            JBalance.LOGGER.info("[JBalance] Using SQLite database at {}", url);
        }

        hc.setConnectionTimeout(5000);
        hc.setPoolName("JBalance-DB");

        this.dataSource = new HikariDataSource(hc);
        runMigrations();
    }

    private void runMigrations() {
        String resourcePath = isMysql ? "/schema/schema_mysql.sql" : "/schema/schema_sqlite.sql";
        String schema = readResource(resourcePath);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(schema);
            JBalance.LOGGER.info("[JBalance] Schema migration completed successfully");
        } catch (SQLException e) {
            throw new RuntimeException("[JBalance] Schema migration failed", e);
        }
    }

    private String readResource(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("[JBalance] Schema resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("[JBalance] Failed to read schema resource: " + resourcePath, e);
        }
    }

    /**
     * Returns the underlying DataSource for repository use.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Returns true if MySQL is being used, false if SQLite.
     */
    public boolean isMysql() {
        return isMysql;
    }

    /**
     * Closes the HikariCP connection pool cleanly.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            JBalance.LOGGER.info("[JBalance] Database connection pool closed");
        }
    }
}
