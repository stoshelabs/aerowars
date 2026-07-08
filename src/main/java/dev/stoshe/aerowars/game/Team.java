package dev.stoshe.aerowars.game;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Runtime team within a match. In SOLO mode each player is their own team. */
public final class Team {
    public final int index;
    public final String name;
    public final String colorHex;
    public final int spawnIndex;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();

    private static final String[] NAMES = {
            "Vermelho", "Azul", "Verde", "Amarelo", "Rosa", "Ciano", "Laranja", "Branco"
    };
    private static final String[] COLORS = {
            "#ff5555", "#5599ff", "#55ff55", "#ffff55", "#ff55ff", "#55ffff", "#ffaa00", "#ffffff"
    };

    public Team(int index, int spawnIndex) {
        this.index = index;
        this.spawnIndex = spawnIndex;
        this.name = NAMES[index % NAMES.length];
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
