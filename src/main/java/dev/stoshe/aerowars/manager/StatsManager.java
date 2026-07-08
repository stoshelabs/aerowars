package dev.stoshe.aerowars.manager;

import dev.stoshe.aerowars.data.IStatsRepository;
import dev.stoshe.aerowars.model.PlayerStats;
import dev.stoshe.aerowars.util.Console;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps per-player kills / deaths / wins / games in memory and persists them through an
 * {@link IStatsRepository} (JSON file by default, or SQL/HikariCP when the database is enabled).
 */
public class StatsManager {
    private final IStatsRepository repository;
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public StatsManager(IStatsRepository repository) {
        this.repository = repository;
    }

    public void load() {
        stats.clear();
        stats.putAll(repository.loadAll());
        Console.info("Loaded stats for " + stats.size() + " player(s).");
    }

    public void save() {
        if (!dirty) {
            return;
        }
        repository.saveAll(stats);
        dirty = false;
    }

    /** Flushes pending changes and releases the backend (e.g. the SQL connection pool). */
    public void shutdown() {
        save();
        repository.close();
    }

    private PlayerStats of(UUID uuid, String name) {
        PlayerStats s = stats.computeIfAbsent(uuid, k -> new PlayerStats());
        if (name != null && !name.isBlank()) {
            s.name = name;
        }
        dirty = true;
        return s;
    }

    public void recordGame(UUID uuid, String name) {
        of(uuid, name).gamesPlayed++;
    }

    public void recordKill(UUID uuid, String name) {
        of(uuid, name).kills++;
    }

    public void recordDeath(UUID uuid, String name) {
        of(uuid, name).deaths++;
    }

    public void recordWin(UUID uuid, String name) {
        of(uuid, name).wins++;
    }

    public PlayerStats get(UUID uuid) {
        return stats.get(uuid);
    }

    /** Top players by the given metric ("kills" | "wins", default kills), highest first. */
    public List<Map.Entry<UUID, PlayerStats>> top(String metric, int limit) {
        Comparator<Map.Entry<UUID, PlayerStats>> cmp = "wins".equalsIgnoreCase(metric)
                ? Comparator.comparingInt(e -> e.getValue().wins)
                : Comparator.comparingInt(e -> e.getValue().kills);
        List<Map.Entry<UUID, PlayerStats>> list = new ArrayList<>(stats.entrySet());
        list.sort(cmp.reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }
}
