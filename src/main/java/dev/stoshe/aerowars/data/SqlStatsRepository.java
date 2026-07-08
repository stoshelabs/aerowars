package dev.stoshe.aerowars.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.stoshe.aerowars.model.PlayerStats;
import dev.stoshe.aerowars.util.Console;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQL stats backend (HikariCP + JDBC), mirroring the Plots SQL repository. Driver is chosen by the
 * JDBC URL. Upserts are done with a portable UPDATE-then-INSERT so the same code works across
 * SQLite / MySQL / MariaDB / PostgreSQL without dialect-specific ON CONFLICT syntax.
 */
public class SqlStatsRepository implements IStatsRepository {
    private static final String TABLE = "aerowars_stats";

    private final HikariDataSource dataSource;

    public SqlStatsRepository(String jdbcUrl, String username, String password, int maxPoolSize) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        if (username != null && !username.isEmpty()) {
            cfg.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            cfg.setPassword(password);
        }
        cfg.setMaximumPoolSize(Math.max(2, maxPoolSize));
        cfg.setPoolName("AeroWars-Hikari");
        this.dataSource = new HikariDataSource(cfg);
        ensureSchema();
    }

    private void ensureSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "name VARCHAR(64) NOT NULL, " +
                "kills INT NOT NULL, " +
                "deaths INT NOT NULL, " +
                "wins INT NOT NULL, " +
                "games INT NOT NULL, " +
                "PRIMARY KEY (uuid)" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize " + TABLE + " table", e);
        }
    }

    @Override
    public Map<UUID, PlayerStats> loadAll() {
        Map<UUID, PlayerStats> out = new HashMap<>();
        String sql = "SELECT uuid, name, kills, deaths, wins, games FROM " + TABLE;
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerStats s = new PlayerStats();
                    s.name = rs.getString("name");
                    s.kills = rs.getInt("kills");
                    s.deaths = rs.getInt("deaths");
                    s.wins = rs.getInt("wins");
                    s.gamesPlayed = rs.getInt("games");
                    out.put(uuid, s);
                } catch (IllegalArgumentException ignored) {
                    // Skip rows with a malformed UUID rather than aborting the whole load.
                }
            }
        } catch (SQLException e) {
            Console.error("Failed to load stats from SQL: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveAll(Map<UUID, PlayerStats> stats) {
        if (stats == null || stats.isEmpty()) {
            return;
        }
        String update = "UPDATE " + TABLE
                + " SET name = ?, kills = ?, deaths = ?, wins = ?, games = ? WHERE uuid = ?";
        String insert = "INSERT INTO " + TABLE
                + " (uuid, name, kills, deaths, wins, games) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            boolean prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement up = conn.prepareStatement(update);
                    PreparedStatement in = conn.prepareStatement(insert)) {
                for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
                    PlayerStats s = e.getValue();
                    String uuid = e.getKey().toString();
                    up.setString(1, s.name == null ? "" : s.name);
                    up.setInt(2, s.kills);
                    up.setInt(3, s.deaths);
                    up.setInt(4, s.wins);
                    up.setInt(5, s.gamesPlayed);
                    up.setString(6, uuid);
                    if (up.executeUpdate() == 0) {
                        in.setString(1, uuid);
                        in.setString(2, s.name == null ? "" : s.name);
                        in.setInt(3, s.kills);
                        in.setInt(4, s.deaths);
                        in.setInt(5, s.wins);
                        in.setInt(6, s.gamesPlayed);
                        in.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                Console.error("Failed to save stats to SQL (rolled back): " + e.getMessage());
            } finally {
                conn.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            Console.error("Failed to save stats to SQL: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
