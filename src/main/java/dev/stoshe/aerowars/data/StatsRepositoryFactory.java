package dev.stoshe.aerowars.data;

import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.util.Console;

import java.io.File;

/**
 * Picks the stats persistence backend from config, mirroring the Plots repository selection. When the
 * database is enabled AND HikariCP + the JDBC driver are present on the server classpath, an
 * {@link SqlStatsRepository} is used; otherwise it degrades gracefully to {@link JsonStatsRepository}.
 * The driver classes are NOT bundled in the plugin jar — an admin drops them on the classpath.
 */
public final class StatsRepositoryFactory {

    private StatsRepositoryFactory() {
    }

    public static IStatsRepository create(File dataDir, AeroWarsConfig config) {
        AeroWarsConfig.Database db = config.Database;
        if (db != null && db.Enabled) {
            String jdbcUrl = resolveUrl(dataDir, db.JdbcUrl);
            try {
                // Verify Hikari + the driver are on the classpath BEFORE loading the SQL repo class,
                // so a missing dependency degrades to JSON instead of throwing NoClassDefFoundError.
                Class.forName("com.zaxxer.hikari.HikariDataSource");
                String driver = driverClassFor(jdbcUrl);
                if (driver != null) {
                    Class.forName(driver);
                }
                IStatsRepository repo = new SqlStatsRepository(jdbcUrl, db.Username, db.Password, db.MaxPoolSize);
                Console.success("AeroWars stats: SQL repository active (" + jdbcUrl + ").");
                return repo;
            } catch (Throwable t) {
                Console.warning("SQL stats unavailable (" + t.getClass().getSimpleName() + ": "
                        + t.getMessage() + "); falling back to stats.json. Drop HikariCP + the JDBC driver "
                        + "on the server classpath to enable it.");
            }
        }
        Console.info("AeroWars stats: JSON repository (stats.json).");
        return new JsonStatsRepository(new File(dataDir, "stats.json"));
    }

    /** Resolve a bare {@code jdbc:sqlite:<file>} URL to an absolute path under the data dir. */
    private static String resolveUrl(File dataDir, String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:")) {
            String path = jdbcUrl.substring("jdbc:sqlite:".length());
            if (!path.contains("/") && !path.contains("\\")) {
                return "jdbc:sqlite:" + new File(dataDir, path).getAbsolutePath();
            }
        }
        return jdbcUrl;
    }

    private static String driverClassFor(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        }
        if (url.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (url.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        return null;
    }
}
