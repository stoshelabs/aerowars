package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matchmaking queue: players queue for a mode (SOLO / TEAMS) and are auto-placed into a fitting match as
 * soon as an arena of that mode has room (an existing waiting match, else a freshly created one). Drained
 * once per match tick by {@link MatchManager#tickAll()}. If no arena of the mode is available the players
 * simply wait in the queue. Instant join (`/aerowars join`) still exists; this adds an explicit,
 * mode-aware waiting line.
 */
public class QueueManager {
    private final MatchManager matchManager;
    /** mode -> queued players, insertion-ordered so it's first-come-first-served. */
    private final Map<GameMode, LinkedHashSet<UUID>> queues = new EnumMap<>(GameMode.class);
    /** Mode-agnostic queue used as the fallback for {@code /aerowars join} (any playable arena). */
    private final LinkedHashSet<UUID> anyQueue = new LinkedHashSet<>();
    /** Last announced queue position per player, so we only message them when they move UP (no spam). */
    private final Map<UUID, Integer> lastPos = new java.util.HashMap<>();

    public QueueManager(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    /** Number of players waiting in the given mode's queue (for placeholders/HUDs). */
    public synchronized int queueSize(GameMode mode) {
        LinkedHashSet<UUID> q = queues.get(mode);
        return q == null ? 0 : q.size();
    }

    /** Total players waiting across all queues (mode-specific + the any-mode fallback). */
    public synchronized int totalQueued() {
        int n = anyQueue.size();
        for (LinkedHashSet<UUID> q : queues.values()) {
            n += q.size();
        }
        return n;
    }

    public synchronized boolean isQueued(UUID uuid) {
        if (anyQueue.contains(uuid)) {
            return true;
        }
        for (LinkedHashSet<UUID> q : queues.values()) {
            if (q.contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queues the player for ANY mode/arena — the fallback when {@code /aerowars join} can't place them
     * immediately (no arena free yet). They're auto-placed as soon as any playable arena has room.
     */
    public synchronized void enqueueAny(PlayerRef pr) {
        UUID uuid = pr.getUuid();
        if (matchManager.getPlayerMatch(uuid) != null) {
            return;
        }
        removeFromQueues(uuid);
        anyQueue.add(uuid);
        pr.sendMessage(ChatUtil.info(Tr.t("queue.joined_any", "n", anyQueue.size())));
        drainAny();
    }

    /** Adds the player to the given mode's queue (leaving any other queue first), then tries to place them. */
    public synchronized void enqueue(PlayerRef pr, GameMode mode) {
        UUID uuid = pr.getUuid();
        if (matchManager.getPlayerMatch(uuid) != null) {
            pr.sendMessage(ChatUtil.error(Tr.t("match.already_in_match")));
            return;
        }
        removeFromQueues(uuid);
        queues.computeIfAbsent(mode, k -> new LinkedHashSet<>()).add(uuid);
        pr.sendMessage(ChatUtil.success(Tr.t("queue.joined", "mode", modeName(mode),
                "n", queues.get(mode).size())));
        drain(mode);
    }

    /** Removes the player from every queue (used by /leave and on disconnect). */
    public synchronized void removeFromQueues(UUID uuid) {
        anyQueue.remove(uuid);
        lastPos.remove(uuid);
        for (LinkedHashSet<UUID> q : queues.values()) {
            q.remove(uuid);
        }
    }

    /** Leaves the queue with feedback; returns true if the player was actually queued. */
    public synchronized boolean leave(PlayerRef pr) {
        UUID uuid = pr.getUuid();
        if (!isQueued(uuid)) {
            return false;
        }
        removeFromQueues(uuid);
        pr.sendMessage(ChatUtil.info(Tr.t("queue.left")));
        return true;
    }

    public synchronized void handleDisconnect(UUID uuid) {
        removeFromQueues(uuid);
    }

    /** Drains every mode's queue (and the any-mode fallback queue) into available matches. Per tick. */
    public synchronized void tick() {
        for (GameMode mode : new ArrayList<>(queues.keySet())) {
            drain(mode);
        }
        drainAny();
        announcePositions();
    }

    /** Messages a queued player only when their position IMPROVES (someone ahead matched/left). No spam. */
    private void announcePositions() {
        List<LinkedHashSet<UUID>> all = new ArrayList<>(queues.values());
        all.add(anyQueue);
        for (LinkedHashSet<UUID> q : all) {
            int pos = 0;
            for (UUID uuid : q) {
                pos++;
                Integer prev = lastPos.put(uuid, pos);
                if (prev != null && pos < prev) {
                    PlayerRef pr = Universe.get().getPlayer(uuid);
                    if (pr != null) {
                        pr.sendMessage(ChatUtil.info(Tr.t("queue.position", "n", pos)));
                    }
                }
            }
        }
    }

    private void drainAny() {
        if (anyQueue.isEmpty()) {
            return;
        }
        for (UUID uuid : new ArrayList<>(anyQueue)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr == null) {
                anyQueue.remove(uuid);
                continue;
            }
            if (matchManager.getPlayerMatch(uuid) != null) {
                anyQueue.remove(uuid);
                continue;
            }
            Match match = matchManager.findOrCreateRandomMatch();
            if (match == null) {
                break; // no playable arena at all — keep waiting
            }
            if (matchManager.addPlayer(match, pr)) {
                anyQueue.remove(uuid);
                pr.sendMessage(ChatUtil.success(Tr.t("queue.matched")));
            } else {
                break;
            }
        }
    }

    private void drain(GameMode mode) {
        LinkedHashSet<UUID> q = queues.get(mode);
        if (q == null || q.isEmpty()) {
            return;
        }
        for (UUID uuid : new ArrayList<>(q)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr == null) {
                q.remove(uuid); // went offline
                continue;
            }
            if (matchManager.getPlayerMatch(uuid) != null) {
                q.remove(uuid); // already placed by something else
                continue;
            }
            Match match = matchManager.findOrCreateMatchOfMode(mode);
            if (match == null) {
                break; // no arena of this mode available right now — keep the whole queue waiting
            }
            if (matchManager.addPlayer(match, pr)) {
                q.remove(uuid);
                pr.sendMessage(ChatUtil.success(Tr.t("queue.matched")));
            } else {
                break; // couldn't place (full/failed) — retry next tick
            }
        }
    }

    private String modeName(GameMode mode) {
        return mode == GameMode.TEAMS ? Tr.t("queue.mode_teams") : Tr.t("queue.mode_solo");
    }
}
