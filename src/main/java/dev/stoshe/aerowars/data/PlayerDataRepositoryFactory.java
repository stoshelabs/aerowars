package dev.stoshe.aerowars.data;

import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.util.Console;

import java.io.File;

/**
 * Picks a per-player store backend from the {@code Database} config (shared with the stats repository).
 * When the DB is enabled AND HikariCP + the JDBC driver are on the classpath, a {@link
 * SqlPlayerDataRepository} (with the given table) is returned; otherwise it degrades gracefully to a
 * {@link JsonPlayerDataRepository} on {@code <dataDir>/<fileName>}. Drivers are NOT bundled — an admin
 * drops them on the classpath.
 */
public final class PlayerDataRepositoryFactory {

    private PlayerDataRepositoryFactory() {
    }

    public static IPlayerDataRepository create(File dataDir, AeroWarsConfig config, String fileName, String table) {
        AeroWarsConfig.Database db = config.Database;
        if (db != null && db.Enabled) {
            String jdbcUrl = resolveUrl(dataDir, db.JdbcUrl);
            try {
                Class.forName("com.zaxxer.hikari.HikariDataSource");
                String driver = driverClassFor(jdbcUrl);
                if (driver != null) {
                    Class.forName(driver);
                }
                IPlayerDataRepository repo = new SqlPlayerDataRepository(table, jdbcUrl, db.Username,
                        db.Password, db.MaxPoolSize);
                Console.success("AeroWars " + table + ": SQL repository active.");
                return repo;
            } catch (Throwable t) {
                Console.warning("SQL " + table + " unavailable (" + t.getClass().getSimpleName()
                        + "); falling back to " + fileName + ".");
            }
        }
        return new JsonPlayerDataRepository(new File(dataDir, fileName));
    }

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
