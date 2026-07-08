package dev.stoshe.aerowars.data;

import dev.stoshe.aerowars.model.PlayerStats;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence backend for player stats. Two implementations exist: {@link JsonStatsRepository}
 * (default, a {@code stats.json} file) and {@link SqlStatsRepository} (HikariCP + JDBC). The active
 * one is chosen by {@link StatsRepositoryFactory} from the config, mirroring the Plots repository
 * pattern.
 */
public interface IStatsRepository {
    /** Loads every player's stats keyed by UUID. */
    Map<UUID, PlayerStats> loadAll();

    /** Persists the full stats map (upsert). Called when the plugin saves. */
    void saveAll(Map<UUID, PlayerStats> stats);

    /** Releases resources (e.g. the connection pool). No-op for the file backend. */
    default void close() {
    }
}
