package dev.stoshe.aerowars.integration;

import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.MatchState;
import dev.stoshe.aerowars.model.PlayerStats;

import java.util.List;
import java.util.UUID;

/**
 * PlaceholderAPI expansion exposing AeroWars data under the {@code aerowars} identifier — for external
 * holograms / scoreboards (SkyWarsReloaded-style). Placeholders (the part after {@code aerowars_}):
 *
 * <p>Global: {@code arenas} (playable), {@code arenas_total}, {@code matches}, {@code players} (in game),
 * {@code queue}, {@code queue_solo}, {@code queue_teams}.
 * <br>Player: {@code player_arena}, {@code player_status}, {@code player_kills}, {@code player_alive};
 * lifetime {@code kills}, {@code deaths}, {@code wins}, {@code games}, {@code kdr}.
 * <br>Per-arena: {@code arena_<name>_players}, {@code arena_<name>_max}, {@code arena_<name>_mode},
 * {@code arena_<name>_state}.
 */
public class AeroWarsPlaceholders extends PlaceholderExpansion {
    private final AeroWars plugin;

    public AeroWarsPlaceholders(AeroWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aerowars";
    }

    @Override
    public String getAuthor() {
        return "Stoshe Labs";
    }

    @Override
    public String getVersion() {
        return plugin.getVersion();
    }

    @Override
    public boolean persist() {
        return true; // survive PAPI reloads
    }

    @Override
    public List<String> getPlaceholders() {
        return List.of("arenas", "arenas_total", "matches", "matches_solo", "matches_teams", "players",
                "queue", "queue_solo", "queue_teams",
                "player_arena", "player_status", "player_mode", "player_kills", "player_alive",
                "in_arena", "in_queue", "is_spectator", "is_busy", "in_group", "is_leader",
                "group_size", "group_online", "group_leader",
                "kills", "deaths", "wins", "losses", "games", "kdr", "wlr",
                "top_<metric>_name_<pos>", "top_<metric>_value_<pos>",
                "arena_<name>_players", "arena_<name>_max", "arena_<name>_mode", "arena_<name>_state");
    }

    @Override
    public String onPlaceholderRequest(PlayerRef player, String params) {
        if (params == null) {
            return null;
        }
        String p = params.toLowerCase();
        switch (p) {
            case "arenas":
                return String.valueOf(plugin.getArenaManager().getPlayableArenas().size());
            case "arenas_total":
                return String.valueOf(plugin.getArenaManager().getAllArenas().size());
            case "matches":
                return String.valueOf(plugin.getMatchManager().liveMatches().size());
            case "players":
                return String.valueOf(totalPlayersInGame());
            case "queue":
                return String.valueOf(plugin.getQueueManager().totalQueued());
            case "queue_solo":
                return String.valueOf(plugin.getQueueManager().queueSize(GameMode.SOLO));
            case "queue_teams":
                return String.valueOf(plugin.getQueueManager().queueSize(GameMode.TEAMS));
            case "matches_solo":
                return String.valueOf(matchesOfMode(GameMode.SOLO));
            case "matches_teams":
                return String.valueOf(matchesOfMode(GameMode.TEAMS));
            default:
                break;
        }
        if (p.startsWith("top_")) {
            return topPlaceholder(p);
        }
        if (p.startsWith("arena_")) {
            return arenaPlaceholder(p);
        }
        // Player-scoped placeholders need a resolvable player.
        if (player == null) {
            return null;
        }
        return playerPlaceholder(player.getUuid(), p);
    }

    private String playerPlaceholder(UUID uuid, String p) {
        Match match = plugin.getMatchManager().getPlayerMatch(uuid);
        boolean spectator = plugin.getMatchManager().isSpectator(uuid);
        boolean queued = plugin.getQueueManager().isQueued(uuid);
        dev.stoshe.aerowars.game.Party party = plugin.getPartyManager().getParty(uuid);
        switch (p) {
            // --- current-match info ---
            case "player_arena", "arena_name":
                return match != null ? match.arena.name : "-";
            case "player_status":
                if (match == null) {
                    return "none";
                }
                if (spectator) {
                    return "spectating";
                }
                return match.state == MatchState.ACTIVE ? "playing" : "waiting";
            case "player_kills":
                return String.valueOf(match == null ? 0 : match.kills.getOrDefault(uuid, 0));
            case "player_alive":
                return String.valueOf(match == null ? 0 : match.alive.size());
            case "player_mode", "arena_type":
                return match == null ? "-" : (match.mode() == GameMode.TEAMS ? "teams" : "solo");
            // --- state booleans ---
            case "in_arena":
                return bool(match != null);
            case "in_queue":
                return bool(queued);
            case "is_spectator":
                return bool(spectator);
            case "is_busy":
                return bool(match != null || queued);
            case "in_group":
                return bool(party != null && party.size() > 1);
            case "is_leader":
                return bool(party != null && party.isLeader(uuid));
            // --- party / group ---
            case "group_size":
                return String.valueOf(party == null ? 0 : party.size());
            case "group_online":
                return String.valueOf(party == null ? 0 : plugin.getPartyManager().onlineMembers(party).size());
            case "group_leader":
                return party == null ? "-" : nameOf(party.leader());
            default:
                break;
        }
        // --- lifetime stats ---
        PlayerStats stats = plugin.getStatsManager().get(uuid);
        int wins = stats == null ? 0 : stats.wins;
        int games = stats == null ? 0 : stats.gamesPlayed;
        int losses = Math.max(0, games - wins);
        return switch (p) {
            case "kills" -> String.valueOf(stats == null ? 0 : stats.kills);
            case "deaths" -> String.valueOf(stats == null ? 0 : stats.deaths);
            case "wins" -> String.valueOf(wins);
            case "losses" -> String.valueOf(losses);
            case "games" -> String.valueOf(games);
            case "kdr" -> stats == null ? "0.00" : String.format("%.2f", stats.kdr());
            case "wlr" -> String.format("%.2f", wins / (double) Math.max(1, losses));
            default -> null;
        };
    }

    private static String bool(boolean b) {
        return b ? "true" : "false";
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        PlayerRef pr = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(uuid);
        if (pr != null && pr.getUsername() != null) {
            return pr.getUsername();
        }
        PlayerStats s = plugin.getStatsManager().get(uuid);
        return s != null && s.name != null && !s.name.isBlank() ? s.name : uuid.toString().substring(0, 8);
    }

    private int matchesOfMode(GameMode mode) {
        int n = 0;
        for (Match m : plugin.getMatchManager().liveMatches()) {
            if (m.arena != null && m.arena.mode() == mode) {
                n++;
            }
        }
        return n;
    }

    /** {@code top_<metric>_<name|value>_<pos>} — leaderboard entry; metric = kills/wins/deaths/games. */
    private String topPlaceholder(String p) {
        String[] parts = p.split("_");
        if (parts.length < 4) {
            return null;
        }
        String metric = parts[1];
        String field = parts[2];
        int pos;
        try {
            pos = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (pos < 1) {
            return null;
        }
        List<java.util.Map.Entry<UUID, PlayerStats>> top = plugin.getStatsManager().top(metric, pos);
        if (top == null || pos > top.size()) {
            return "-";
        }
        java.util.Map.Entry<UUID, PlayerStats> e = top.get(pos - 1);
        PlayerStats s = e.getValue();
        if ("name".equals(field)) {
            return s != null && s.name != null && !s.name.isBlank() ? s.name : nameOf(e.getKey());
        }
        if ("value".equals(field) && s != null) {
            return switch (metric) {
                case "kills" -> String.valueOf(s.kills);
                case "wins" -> String.valueOf(s.wins);
                case "deaths" -> String.valueOf(s.deaths);
                case "games" -> String.valueOf(s.gamesPlayed);
                default -> null;
            };
        }
        return null;
    }

    /** {@code arena_<name>_<field>} — name may itself contain underscores, so match by known suffixes. */
    private String arenaPlaceholder(String p) {
        String body = p.substring("arena_".length());
        String[] suffixes = {"_players", "_max", "_mode", "_state"};
        for (String suffix : suffixes) {
            if (body.endsWith(suffix)) {
                String name = body.substring(0, body.length() - suffix.length());
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) {
                    return null;
                }
                return switch (suffix) {
                    case "_players" -> String.valueOf(playersOnArena(name));
                    case "_max" -> String.valueOf(plugin.getMapManager().maxPlayersFor(arena));
                    case "_mode" -> arena.mode() == GameMode.TEAMS ? "teams" : "solo";
                    case "_state" -> arenaState(name);
                    default -> null;
                };
            }
        }
        return null;
    }

    private int totalPlayersInGame() {
        int n = 0;
        for (Match m : plugin.getMatchManager().liveMatches()) {
            n += m.totalPlayers();
        }
        return n;
    }

    private int playersOnArena(String arenaName) {
        int n = 0;
        for (Match m : plugin.getMatchManager().liveMatches()) {
            if (m.arena != null && arenaName.equalsIgnoreCase(m.arena.name)) {
                n += m.totalPlayers();
            }
        }
        return n;
    }

    /** State of a match on that arena (waiting/countdown/active/ending), or "idle" when none is running. */
    private String arenaState(String arenaName) {
        for (Match m : plugin.getMatchManager().liveMatches()) {
            if (m.arena != null && arenaName.equalsIgnoreCase(m.arena.name)) {
                return m.state.name().toLowerCase();
            }
        }
        return "idle";
    }
}
