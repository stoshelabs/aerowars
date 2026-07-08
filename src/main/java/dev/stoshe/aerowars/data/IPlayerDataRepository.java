package dev.stoshe.aerowars.data;

import com.google.gson.JsonElement;

import java.util.Map;
import java.util.UUID;

/**
 * Generic per-player persistence: each player's state is stored as a raw JSON value keyed by UUID. Two
 * implementations — {@link JsonPlayerDataRepository} (a file, the default) and {@link
 * SqlPlayerDataRepository} (HikariCP + JDBC) — are chosen by {@link PlayerDataRepositoryFactory} from the
 * same {@code Database} config the stats repository uses. Callers convert their own model to/from the
 * {@link JsonElement} blob, so one repository serves any per-player store (kit unlocks, cosmetics, …).
 */
public interface IPlayerDataRepository {
    /** Loads every player's raw JSON blob, keyed by UUID. */
    Map<UUID, JsonElement> loadAll();

    /** Persists the full per-player map (upsert). */
    void saveAll(Map<UUID, JsonElement> data);

    /** Releases resources (e.g. the connection pool). No-op for the file backend. */
    default void close() {
    }
}
