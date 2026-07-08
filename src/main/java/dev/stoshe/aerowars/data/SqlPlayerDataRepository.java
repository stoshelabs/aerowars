package dev.stoshe.aerowars.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.stoshe.aerowars.util.Console;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQL per-player store (HikariCP + JDBC): one table {@code (uuid PK, data TEXT)} holding each player's
 * JSON blob. Table name is passed in so several stores (kit unlocks, cosmetics, …) reuse this class.
 * Portable UPDATE-then-INSERT upsert works across SQLite / MySQL / MariaDB / PostgreSQL. Driver is chosen
 * by the JDBC URL by the factory; the pool is created here.
 */
public class SqlPlayerDataRepository implements IPlayerDataRepository {
    private final String table;
    private final HikariDataSource dataSource;

    public SqlPlayerDataRepository(String table, String jdbcUrl, String username, String password, int maxPoolSize) {
        this.table = table;
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        if (username != null && !username.isEmpty()) {
            cfg.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            cfg.setPassword(password);
        }
        cfg.setMaximumPoolSize(Math.max(2, maxPoolSize));
        cfg.setPoolName("AeroWars-Hikari-" + table);
        this.dataSource = new HikariDataSource(cfg);
        ensureSchema();
    }

    private void ensureSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "data TEXT NOT NULL, "
                + "PRIMARY KEY (uuid))";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize " + table + " table", e);
        }
    }

    @Override
    public Map<UUID, JsonElement> loadAll() {
        Map<UUID, JsonElement> out = new LinkedHashMap<>();
        String sql = "SELECT uuid, data FROM " + table;
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    out.put(uuid, JsonParser.parseString(rs.getString("data")));
                } catch (Exception ignored) {
                    // skip malformed rows rather than aborting the load
                }
            }
        } catch (SQLException e) {
            Console.error("Failed to load " + table + " from SQL: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveAll(Map<UUID, JsonElement> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        String update = "UPDATE " + table + " SET data = ? WHERE uuid = ?";
        String insert = "INSERT INTO " + table + " (uuid, data) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            boolean prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement up = conn.prepareStatement(update);
                    PreparedStatement in = conn.prepareStatement(insert)) {
                for (Map.Entry<UUID, JsonElement> e : data.entrySet()) {
                    String uuid = e.getKey().toString();
                    String json = e.getValue() == null ? "{}" : e.getValue().toString();
                    up.setString(1, json);
                    up.setString(2, uuid);
                    if (up.executeUpdate() == 0) {
                        in.setString(1, uuid);
                        in.setString(2, json);
                        in.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                Console.error("Failed to save " + table + " to SQL (rolled back): " + e.getMessage());
            } finally {
                conn.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            Console.error("Failed to save " + table + " to SQL: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
