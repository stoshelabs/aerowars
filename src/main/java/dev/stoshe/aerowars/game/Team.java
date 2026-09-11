package dev.stoshe.aerowars.game;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.stoshe.aerowars.util.Tr;

/** Runtime team within a match. In SOLO mode each player is their own team. */
public final class Team {
    public final int index;
    public final String name;
    public final String colorHex;
    public final int spawnIndex;
    // Concurrent: add() runs on the command thread (a player joining) while the 1 Hz match scheduler
    // concurrently reads/eliminates — a plain HashSet here risks a ConcurrentModificationException.
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();

    /** Lang keys ({@code team.name.<colour>}); the display name is resolved in the server's language. */
    private static final String[] NAME_KEYS = {
            "red", "blue", "green", "yellow", "pink", "cyan", "orange", "white"
    };
    private static final String[] COLORS = {
            "#ff5555", "#5599ff", "#55ff55", "#ffff55", "#ff55ff", "#55ffff", "#ffaa00", "#ffffff"
    };

    public Team(int index, int spawnIndex) {
        this.index = index;
        this.spawnIndex = spawnIndex;
        this.name = Tr.t("team.name." + NAME_KEYS[index % NAME_KEYS.length]);
        this.colorHex = COLORS[index % COLORS.length];
    }

    public void add(UUID player) {
        members.add(player);
        alive.add(player);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public void eliminate(UUID player) {
        alive.remove(player);
    }

    public boolean isAlive() {
        return !alive.isEmpty();
    }

    public Set<UUID> members() {
        return members;
    }

    public Set<UUID> aliveMembers() {
        return alive;
    }

    public String coloredName() {
        return "{" + colorHex + "}" + name;
    }
}
