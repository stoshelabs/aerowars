package dev.stoshe.aerowars.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent definition of an arena/map. Match instances are spawned from a
 * copy of {@link #worldTemplate}. Spawn points are individual island spots; in
 * {@link GameMode#TEAMS} they are grouped into teams of {@link #teamSize}.
 */
public final class Arena {
    public String name;
    public String displayName;
    public String worldTemplate;
    public GameMode mode = GameMode.SOLO;
    public int teamSize = 1;

    public List<WorldPos> spawnPoints = new ArrayList<>();
    public List<ChestLocation> normalChests = new ArrayList<>();
    public List<ChestLocation> middleChests = new ArrayList<>();
    public WorldPos spectatorSpawn;

    /**
     * Draft arenas are hidden from matchmaking ({@link #isPlayable()} is false) even if structurally
     * complete. An arena drops into draft when its chests are cleared (a chest-less SkyWars map is not
     * meant to be played) and leaves draft when it is saved again with at least one chest.
     */
    public boolean draft;

    public Arena() {
    }

    public Arena(String name, String worldTemplate) {
        this.name = name;
        this.displayName = name;
        this.worldTemplate = worldTemplate;
    }

    public GameMode mode() {
        return mode == null ? GameMode.SOLO : mode;
    }

    public int effectiveTeamSize() {
        return mode() == GameMode.TEAMS ? Math.max(1, teamSize) : 1;
    }

    /** Number of team slots (islands) available. */
    public int teamCount() {
        int slots = spawnPoints == null ? 0 : spawnPoints.size();
        return mode() == GameMode.TEAMS ? slots / effectiveTeamSize() : slots;
    }

    public int getMaxPlayers() {
        int slots = spawnPoints == null ? 0 : spawnPoints.size();
        if (mode() == GameMode.TEAMS) {
            return teamCount() * effectiveTeamSize();
        }
        return slots;
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

    /** A minimally playable arena needs at least 2 spawns and a spectator spawn. */
    public boolean isComplete() {
        return spawnPoints != null
                && spawnPoints.size() >= 2
                && spectatorSpawn != null
                && (mode() == GameMode.SOLO || spawnPoints.size() >= effectiveTeamSize() * 2);
    }

    /** Structurally complete AND not a draft — i.e. eligible to be picked for a real match. */
    public boolean isPlayable() {
        return !draft && isComplete();
    }

    public String displayName() {
        return (displayName == null || displayName.isBlank()) ? name : displayName;
    }
}
