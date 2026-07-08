package dev.stoshe.aerowars.game;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.MatchState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Live state of a single running match. Mutated only from the match scheduler / world thread. */
public final class Match {
    public final String id;
    public final Arena arena;
    public final World world;

    public MatchState state = MatchState.WAITING;
    public int countdownRemaining;
    public int secondsElapsed;
    public boolean cagesBuilt;
    public boolean chestsFilled;
    /** Headline result shown on the status HUD once the match ends (winner / draw). */
    public String resultText = "";

    /** Seconds the match has sat empty (no players) before the game starts — used to auto-close it. */
    public int emptySeconds;

    /**
     * The concrete timeline of events for THIS match, built once at start from the unified event config
     * (fixed times, or randomized moments). Driven by {@code MatchManager.tick}: the event at
     * {@link #nextEventIndex} fires once {@code secondsElapsed} reaches its time, then the index advances.
     */
    public final List<ScheduledLootEvent> eventSchedule = new ArrayList<>();
    public int nextEventIndex;
    /** How many LOOT_UPGRADE events are in the schedule / have fired — drives the gradual upgrade share. */
    public int totalUpgradeEvents;
    public int firedUpgradeEvents;
    /** Keys of common chests already upgraded to middle loot. */
    public final Set<String> upgradedChestKeys = new LinkedHashSet<>();

    /** One planned event: fire {@link #type} once the match reaches {@link #time} seconds. */
    public static final class ScheduledLootEvent {
        public final int time;
        public final dev.stoshe.aerowars.model.LootEventType type;

        public ScheduledLootEvent(int time, dev.stoshe.aerowars.model.LootEventType type) {
            this.time = time;
            this.type = type;
        }
    }

    public final Set<UUID> alive = new LinkedHashSet<>();
    public final Set<UUID> spectators = new LinkedHashSet<>();
    public final List<Team> teams = new ArrayList<>();
    public final Map<UUID, Team> playerTeam = new ConcurrentHashMap<>();
    public final Map<UUID, String> selectedKits = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    /** Names captured on join so kill/leave messages survive disconnect. */
    public final Map<UUID, String> names = new ConcurrentHashMap<>();

    public Match(String id, Arena arena, World world) {
        this.id = id;
        this.arena = arena;
        this.world = world;
    }

    public GameMode mode() {
        return arena.mode();
    }

    public int totalPlayers() {
        return alive.size() + spectators.size();
    }

    public boolean isFull() {
        return totalPlayers() >= arena.getMaxPlayers();
    }

    public boolean hasRoom() {
        return state.acceptsPlayers() && !isFull();
    }

    /** Free player slots if the match still accepts players, else 0. */
    public int remainingSlots() {
        if (!state.acceptsPlayers()) {
            return 0;
        }
        return Math.max(0, arena.getMaxPlayers() - totalPlayers());
    }

    public Team teamOf(UUID player) {
        return playerTeam.get(player);
    }

    /** Teams that still have at least one living member. */
    public List<Team> aliveTeams() {
        List<Team> result = new ArrayList<>();
        for (Team team : teams) {
            if (team.isAlive()) {
                result.add(team);
            }
        }
        return result;
    }

    public String nameOf(UUID player) {
        return names.getOrDefault(player, "?");
    }
}
