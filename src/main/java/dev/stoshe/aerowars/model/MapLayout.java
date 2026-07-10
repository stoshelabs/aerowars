package dev.stoshe.aerowars.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The physical layout of a map, keyed by its world {@link #template}. Owns the island spawn points,
 * chest placements and spectator spawn — stored PER MAP so several arenas can share one map (and so a
 * random-map pool works: each match resolves the layout of the map it actually cloned).
 *
 * <p>Game rules (mode, team size) live on the {@link Arena}, not here; capacity is a function of BOTH
 * (this layout's spawn count and the arena's mode/team size).
 */
public final class MapLayout {
    public String template;

    public List<WorldPos> spawnPoints = new ArrayList<>();
    public List<ChestLocation> normalChests = new ArrayList<>();
    public List<ChestLocation> middleChests = new ArrayList<>();
    public WorldPos spectatorSpawn;

    public MapLayout() {
    }

    public MapLayout(String template) {
        this.template = template;
    }

    /** Number of island spawn points on this map. */
    public int spawnCount() {
        return spawnPoints == null ? 0 : spawnPoints.size();
    }

    /** The spawn point at {@code index}, or null when out of range. */
    public WorldPos spawnAt(int index) {
        if (spawnPoints == null || index < 0 || index >= spawnPoints.size()) {
            return null;
        }
        return spawnPoints.get(index);
    }

    public List<ChestLocation> allChests() {
        List<ChestLocation> all = new ArrayList<>();

        if (normalChests != null) {
            all.addAll(normalChests);
        }

        if (middleChests != null) {
            all.addAll(middleChests);
        }

        return all;
    }

    /** Team slots (islands) available for the given rules. */
    public int teamCount(GameMode mode, int teamSize) {
        int slots = spawnCount();
        int size = mode == GameMode.TEAMS ? Math.max(1, teamSize) : 1;
        return mode == GameMode.TEAMS ? slots / size : slots;
    }

    /** Maximum players this map supports under the given rules. */
    public int maxPlayers(GameMode mode, int teamSize) {
        int size = mode == GameMode.TEAMS ? Math.max(1, teamSize) : 1;
        return mode == GameMode.TEAMS ? teamCount(mode, teamSize) * size : spawnCount();
    }

    /** A minimally playable map needs at least 2 spawns and a spectator spawn. */
    public boolean isComplete(GameMode mode, int teamSize) {
        int size = mode == GameMode.TEAMS ? Math.max(1, teamSize) : 1;
        return spawnPoints != null
                && spawnPoints.size() >= 2
                && spectatorSpawn != null
                && (mode != GameMode.TEAMS || spawnPoints.size() >= size * 2);
    }

    /** Geometry-only completeness (≥2 spawns + a spectator spawn), independent of game mode. */
    public boolean hasBaseGeometry() {
        return spawnPoints != null && spawnPoints.size() >= 2 && spectatorSpawn != null;
    }
}
