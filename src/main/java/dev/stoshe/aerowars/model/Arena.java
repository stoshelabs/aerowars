package dev.stoshe.aerowars.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Persistent definition of an arena. An arena is a lightweight wrapper of game rules (mode, team size)
 * over one or more MAPS: each match clones one map from its pool and reads that map's {@link MapLayout}
 * (spawns/chests/spectator). The physical layout lives PER MAP ({@code maps/<template>.json}), so several
 * arenas can share the same map.
 */
public final class Arena {
    public String name;
    public String displayName;
    /** Primary map — the template this arena was created on (its layout is the representative one). */
    public String worldTemplate;
    /**
     * Alternate map templates for random rotation. Each match clones a random map from the full pool
     * ({@link #worldTemplate} + these) WHEN {@link #randomMaps} is on. Every map here must have its own
     * saved {@link MapLayout}.
     */
    public List<String> extraTemplates = new ArrayList<>();
    /**
     * Off by default. When true (and the pool has more than one map), each match picks a random map
     * from {@link #templatePool()}; otherwise every match uses the primary {@link #worldTemplate}.
     */
    public boolean randomMaps = false;
    public GameMode mode = GameMode.SOLO;
    public int teamSize = 1;

    /**
     * Draft arenas are hidden from matchmaking even if their map is complete. Toggled via
     * {@code /aerowars arena <name> enable|disable} (and set when an arena's chests are cleared).
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

    /** The full random-map pool: the primary map plus any alternates (deduped, non-blank). */
    public List<String> templatePool() {
        List<String> pool = new ArrayList<>();

        if (worldTemplate != null && !worldTemplate.isBlank()) {
            pool.add(worldTemplate);
        }

        if (extraTemplates != null) {
            for (String t : extraTemplates) {
                if (t != null && !t.isBlank() && !pool.contains(t)) {
                    pool.add(t);
                }
            }
        }

        return pool;
    }

    /**
     * The map a new match should clone. When {@link #randomMaps} is off (the default) this is always
     * the primary {@link #worldTemplate}; when on, it's a random pick from the pool.
     */
    public String pickTemplate(Random random) {
        List<String> pool = templatePool();

        if (!randomMaps || pool.size() <= 1) {
            return worldTemplate;
        }

        return pool.get(random.nextInt(pool.size()));
    }

    public String displayName() {
        return (displayName == null || displayName.isBlank()) ? name : displayName;
    }
}
