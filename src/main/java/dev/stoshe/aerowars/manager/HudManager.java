package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.game.Team;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.MatchState;
import dev.stoshe.aerowars.util.Tr;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attaches and drives the per-player {@link dev.stoshe.aerowars.ui.MatchHud}.
 * The Hytale HUD manager surface is reached reflectively (same approach as the
 * Plots plugin) so we stay resilient to server-build differences.
 */
public class HudManager {
    private final AeroWars plugin;
    private final AeroWarsConfig config;
    private final Map<UUID, dev.stoshe.aerowars.ui.StatusHud> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, dev.stoshe.aerowars.ui.ScoreboardHud> boards = new ConcurrentHashMap<>();

    public HudManager(AeroWars plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void updateMatch(Match match) {
        if (match == null || (!config.Hud.Enabled && !config.Scoreboard.Enabled)) {
            return;
        }
        for (UUID uuid : match.alive) {
            updatePlayer(match, uuid);
        }
        for (UUID uuid : match.spectators) {
            updatePlayer(match, uuid);
        }
    }

    private void updatePlayer(Match match, UUID uuid) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null) {
            return;
        }
        // Only drive the HUD once the player is actually inside the match world. Attaching before
        // the join teleport settles skips the .ui append (player not resolvable in that world's
        // store), and the later set-commands then crash the client ("#AeroWarsRoot not found").
        if (pr.getWorldUuid() == null || Universe.get().getWorld(pr.getWorldUuid()) != match.world) {
            return;
        }
        World world = match.world;

        // Side scoreboard (detailed match data). Headline events (countdown, time-left, winner)
        // are shown as transient fading event titles by MatchManager, not a persistent panel.
        if (config.Scoreboard.Enabled) {
            String arena = Tr.t("scoreboard.arena", "arena", match.arena.name);
            String mode = Tr.t("scoreboard.mode", "mode", modeName(match));
            String sbTime = timerText(match);
            String sbTimeLabel = timeLabel(match);
            String sbAlive = Tr.t("scoreboard.alive", "n", aliveCount(match));
            String kills = Tr.t("scoreboard.kills", "n", match.kills.getOrDefault(uuid, 0));
            String sbKit = Tr.t("scoreboard.kit", "kit",
                    match.selectedKits.getOrDefault(uuid, config.Kits.DefaultKit));
            String eventName = nextEventName(match);
            String eventTime = nextEventTime(match);
            String footer = config.Scoreboard.Footer;
            dev.stoshe.aerowars.ui.ScoreboardHud board = boards.get(uuid);
            if (board == null) {
                dev.stoshe.aerowars.ui.ScoreboardHud created = new dev.stoshe.aerowars.ui.ScoreboardHud(pr);
                created.setData(true, arena, mode, sbTime, sbTimeLabel, sbAlive, kills, sbKit, eventName, eventTime, footer);
                boards.put(uuid, created);
                world.execute(() -> attach(world, pr, created));
            } else {
                board.setData(true, arena, mode, sbTime, sbTimeLabel, sbAlive, kills, sbKit, eventName, eventTime, footer);
                dev.stoshe.aerowars.ui.ScoreboardHud finalBoard = board;
                world.execute(finalBoard::requestUpdate);
            }
        }
    }

    private String modeName(Match match) {
        return match.mode() == GameMode.TEAMS ? Tr.t("scoreboard.mode_teams") : Tr.t("scoreboard.mode_solo");
    }

    private String statusMain(Match match, UUID uuid) {
        if (match.spectators.contains(uuid) && match.state == MatchState.ACTIVE) {
            return Tr.t("status.spectating");
        }
        return switch (match.state) {
            case WAITING -> Tr.t("status.waiting");
            case COUNTDOWN -> Tr.t("status.countdown", "seconds", Math.max(0, match.countdownRemaining));
            case ACTIVE -> timerText(match);
            case ENDING, CLEANUP -> match.resultText == null || match.resultText.isBlank()
                    ? Tr.t("status.ended") : match.resultText;
        };
    }

    private String statusSub(Match match, UUID uuid) {
        return switch (match.state) {
            case WAITING -> Tr.t("status.waiting_sub", "current", match.totalPlayers(), "min", minPlayersFor(match));
            case COUNTDOWN -> Tr.t("status.countdown_sub");
            case ACTIVE -> match.spectators.contains(uuid) ? Tr.t("status.spectating_sub") : Tr.t("status.active_sub");
            case ENDING, CLEANUP -> "";
        };
    }

    private int minPlayersFor(Match match) {
        return Math.max(2, Math.min(config.Match.MinPlayers, match.arena.getMaxPlayers()));
    }

    private String timeLabel(Match match) {
        return switch (match.state) {
            case COUNTDOWN -> Tr.t("scoreboard.time_starting");
            case ACTIVE -> Tr.t("scoreboard.time_left");
            default -> "";
        };
    }

    /** The next scheduled event that hasn't fired yet (from the match's own timeline), or null. */
    private Match.ScheduledLootEvent nextEvent(Match match) {
        if (match.state != MatchState.ACTIVE || match.nextEventIndex >= match.eventSchedule.size()) {
            return null;
        }
        return match.eventSchedule.get(match.nextEventIndex);
    }

    /** Countdown "mm:ss" to the next scheduled event, or "" when none is coming. */
    private String nextEventTime(Match match) {
        Match.ScheduledLootEvent e = nextEvent(match);
        if (e == null) {
            return "";
        }
        int s = Math.max(0, e.time - match.secondsElapsed);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Name of the upcoming event itself (e.g. "Loot Upgrade" / "Refill") — no "1/3" count. */
    private String nextEventName(Match match) {
        Match.ScheduledLootEvent e = nextEvent(match);
        return e == null ? "" : Tr.t("scoreboard.event_next", "event", Tr.t(e.type.nameKey()));
    }

    public void remove(UUID uuid) {
        dev.stoshe.aerowars.ui.StatusHud status = statuses.remove(uuid);
        dev.stoshe.aerowars.ui.ScoreboardHud board = boards.remove(uuid);
        if (status == null && board == null) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null || pr.getWorldUuid() == null) {
            return;
        }
        World world = Universe.get().getWorld(pr.getWorldUuid());
        if (world != null) {
            world.execute(() -> {
                // Hide first (pushes Visible:false to the client) so the HUD disappears even if the
                // reflective detach below silently fails or the player already changed worlds.
                if (status != null) {
                    status.hide();
                    detach(world, pr, status);
                }
                if (board != null) {
                    board.hide();
                    detach(world, pr, board);
                }
            });
        }
    }

    public void shutdown() {
        java.util.Set<UUID> all = new java.util.HashSet<>(statuses.keySet());
        all.addAll(boards.keySet());
        for (UUID uuid : all) {
            remove(uuid);
        }
        statuses.clear();
        boards.clear();
    }

    // ---------------------------------------------------------------- text

    private int aliveCount(Match match) {
        if (match.mode() == GameMode.TEAMS) {
            return match.aliveTeams().size();
        }
        return match.alive.size();
    }

    private String phaseText(Match match, UUID uuid) {
        if (match.spectators.contains(uuid)) {
            return Tr.t("hud.spectating");
        }
        return switch (match.state) {
            case WAITING -> Tr.t("hud.waiting");
            case COUNTDOWN -> Tr.t("hud.starting");
            case ACTIVE -> Tr.t("hud.fighting");
            case ENDING, CLEANUP -> Tr.t("hud.ended");
        };
    }

    private String timerText(Match match) {
        int seconds;
        if (match.state == MatchState.COUNTDOWN) {
            seconds = Math.max(0, match.countdownRemaining);
        } else if (match.state == MatchState.ACTIVE) {
            seconds = Math.max(0, config.Match.MaxDurationSeconds - match.secondsElapsed);
        } else if (match.state == MatchState.ENDING || match.state == MatchState.CLEANUP) {
            // The match is over — don't surface the end-celebration countdown as the match timer
            // (that made the clock "reset" to ~8-15s and tick down again). Freeze it at 00:00.
            return "00:00";
        } else {
            return "--:--";
        }
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private String kitText(Match match, UUID uuid) {
        if (!config.Kits.Enabled) {
            return "";
        }
        String kit = match.selectedKits.getOrDefault(uuid, config.Kits.DefaultKit);
        Team team = match.teamOf(uuid);
        if (match.mode() == GameMode.TEAMS && team != null) {
            return Tr.t("hud.team", "team", team.name) + "  " + Tr.t("hud.kit", "kit", kit);
        }
        return Tr.t("hud.kit", "kit", kit);
    }

    // ---------------------------------------------------------------- reflection glue

    private void attach(World world, PlayerRef playerRef, CustomUIHud hud) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> ref = playerRef.getReference();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            Object manager = player.getHudManager();
            if (!invoke(manager, "addCustomHud", playerRef, hud)) {
                invoke(manager, "setCustomHud", playerRef, hud);
            }
            // Direct call (like the Plots reference) so build() runs and appends the .ui layout
            // before any set-commands reach the client.
            hud.show();
        } catch (Exception ignored) {
        }
    }

    private void detach(World world, PlayerRef playerRef, CustomUIHud hud) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> ref = playerRef.getReference();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            Object manager = player.getHudManager();
            if (!invoke(manager, "removeCustomHud", playerRef, hud) && !invoke(manager, "removeCustomHud", playerRef)) {
                invoke(manager, "resetHud", playerRef);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean invoke(Object target, String name, Object... args) {
        if (target == null) {
            return false;
        }
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] types = method.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    if (args[i] != null && !types[i].isAssignableFrom(args[i].getClass())) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    method.invoke(target, args);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
