package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.game.Team;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.ChestType;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.Kit;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.model.MatchState;
import dev.stoshe.aerowars.model.WorldPos;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Console;
import dev.stoshe.aerowars.util.Locations;
import dev.stoshe.aerowars.util.Fireworks;
import dev.stoshe.aerowars.util.Sounds;
import dev.stoshe.aerowars.util.Teleports;
import dev.stoshe.aerowars.util.Titles;
import dev.stoshe.aerowars.util.Tr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the full match lifecycle: creation, join/leave, cage/countdown/start,
 * kit application, chest loot, death handling, win detection and cleanup.
 *
 * <p>A single 1&nbsp;Hz scheduler drives phase timers; every world/entity
 * mutation is marshalled onto the owning {@link World}'s thread via
 * {@link World#execute(Runnable)}.
 */
public class MatchManager {
    private final AeroWars plugin;
    private final AeroWarsConfig config;
    private final WorldManager worldManager;
    private final ArenaManager arenaManager;
    private final KitManager kitManager;
    private final LootManager lootManager;

    private final Map<String, Match> matches = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerMatch = new ConcurrentHashMap<>();
    /** Admins secretly spectating a match: uuid -> matchId. NOT counted in totals, no broadcasts. */
    private final Map<UUID, String> adminSpectatorMatch = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final InventoryVault inventoryVault = new InventoryVault();
    private ScheduledExecutorService scheduler;

    public MatchManager(AeroWars plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.worldManager = plugin.getWorldManager();
        this.arenaManager = plugin.getArenaManager();
        this.kitManager = plugin.getKitManager();
        this.lootManager = plugin.getLootManager();
    }

    public void start() {
        // Guarantee no match world is ever deleted out from under players: WorldManager runs this before
        // removing ANY world, so a live match on it is cancelled and everyone is sent to the lobby first.
        worldManager.setDeleteGuard(this::evacuateWorld);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AeroWars-Match-Scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tickAll, 1, 1, TimeUnit.SECONDS);
        // Cosmetic movement trails run well above the 1 Hz match tick so they read as a dense trail
        // coming off the body (point-emitting particles at ~7 Hz).
        scheduler.scheduleAtFixedRate(this::tickTrails, 500, 150, TimeUnit.MILLISECONDS);
        // Arrow/projectile trails sample even faster since projectiles move quickly.
        scheduler.scheduleAtFixedRate(this::tickArrowTrails, 500, 100, TimeUnit.MILLISECONDS);
    }

    private int trailTick;

    /** In-flight projectiles to trail: arrow entity ref -> match id. Filled by ProjectileTrailSystem. */
    private final Map<Ref<EntityStore>, String> trackedArrows = new ConcurrentHashMap<>();

    /** Finds the match whose world entity-store is {@code store} (identity), or null. */
    private Match matchByStore(Store<EntityStore> store) {
        if (store == null) {
            return null;
        }
        for (Match m : matches.values()) {
            try {
                if (m.world != null && m.world.getEntityStore().getStore() == store) {
                    return m;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Called by {@link dev.stoshe.aerowars.system.ProjectileTrailSystem} when a projectile spawns. The
     * shooter isn't known yet (creator UUID is set slightly later), so we track by the match world the
     * projectile lives in and resolve the shooter live in {@link #tickArrowTrails}.
     */
    public void trackArrow(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null) {
            return;
        }
        Match match = matchByStore(store);
        if (match == null || match.state != MatchState.ACTIVE) {
            Console.info("[arrowtrail] projectile NOT in an active match world (matchByStore="
                    + (match == null ? "null" : match.state) + ")");
            return; // projectile not in an active match world
        }
        trackedArrows.put(ref, match.id);
        Console.info("[arrowtrail] tracking projectile in match " + match.id
                + " (tracked=" + trackedArrows.size() + ")");
    }

    /** Called when a projectile despawns. Stale refs are also pruned lazily in {@link #tickArrowTrails}. */
    public void untrackArrow(Ref<EntityStore> ref) {
        if (ref != null) {
            trackedArrows.remove(ref);
        }
    }

    /** Samples each tracked arrow's position + shooter on its world thread and spawns the shooter's trail. */
    private void tickArrowTrails() {
        if (trackedArrows.isEmpty()) {
            return;
        }
        for (Map.Entry<Ref<EntityStore>, String> e : new ArrayList<>(trackedArrows.entrySet())) {
            Ref<EntityStore> ref = e.getKey();
            Match match = matches.get(e.getValue());
            if (match == null || match.state != MatchState.ACTIVE) {
                trackedArrows.remove(ref);
                continue;
            }
            World w = match.world;
            w.execute(() -> {
                try {
                    Store<EntityStore> store = w.getEntityStore().getStore();
                    com.hypixel.hytale.server.core.entity.entities.ProjectileComponent proj =
                            store.getComponent(ref,
                                    com.hypixel.hytale.server.core.entity.entities.ProjectileComponent.getComponentType());
                    if (proj == null) {
                        trackedArrows.remove(ref); // projectile gone
                        return;
                    }
                    UUID shooter = proj.getCreatorUuid();
                    if (shooter == null) {
                        return; // not shot yet / no creator — keep tracking, try next tick
                    }
                    TransformComponent tf = store.getComponent(ref, TransformComponent.getComponentType());
                    if (tf == null) {
                        return;
                    }
                    var pos = tf.getPosition();
                    if (pos != null) {
                        spawnArrowTrail(shooter, pos.x, pos.y, pos.z);
                    }
                } catch (Exception ex) {
                    trackedArrows.remove(ref); // invalid/despawned ref
                }
            });
        }
    }

    /** Spawns each alive player's selected trail cosmetic at their feet, for everyone in the match. */
    private void tickTrails() {
        trailTick++;
        try {
            for (Match match : matches.values()) {
                if (match.state != MatchState.ACTIVE || match.alive.isEmpty()) {
                    continue;
                }
                List<PlayerRef> audience = new ArrayList<>();
                for (UUID u : allParticipants(match)) {
                    PlayerRef pr = Universe.get().getPlayer(u);
                    if (pr != null) {
                        audience.add(pr);
                    }
                }
                if (audience.isEmpty()) {
                    continue;
                }
                for (UUID uuid : match.alive) {
                    String value = plugin.getCosmeticsManager()
                            .selectedValue(uuid, dev.stoshe.aerowars.model.CosmeticCategory.TRAIL);
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    PlayerRef pr = Universe.get().getPlayer(uuid);
                    if (pr == null) {
                        continue;
                    }
                    WorldPos pos = Locations.fromTransform(pr.getTransform());
                    if (pos != null) {
                        spawnTrail(audience, value, pos);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Renders one trail puff. {@code value} is {@code "ParticleId|#RRGGBB"} or {@code "...|rainbow"}. */
    private void spawnTrail(List<PlayerRef> audience, String value, WorldPos pos) {
        String id = value;
        int r = 255, g = 255, b = 255;
        int pipe = value.indexOf('|');
        if (pipe >= 0) {
            id = value.substring(0, pipe);
            String c = value.substring(pipe + 1).trim();
            if (c.equalsIgnoreCase("rainbow")) {
                int[] rgb = hsvToRgb((trailTick * 18) % 360, 1f, 1f);
                r = rgb[0];
                g = rgb[1];
                b = rgb[2];
            } else if (c.startsWith("#") && c.length() == 7) {
                try {
                    r = Integer.parseInt(c.substring(1, 3), 16);
                    g = Integer.parseInt(c.substring(3, 5), 16);
                    b = Integer.parseInt(c.substring(5, 7), 16);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (id.isBlank()) {
            return;
        }
        // Full scale (0.5 shrank most systems to near-invisibility) and a slightly longer emitter
        // life so low-rate systems actually emit a visible puff each tick.
        Fireworks.burst(audience, pos.x, pos.y + 0.2, pos.z, id, r, g, b, 1.0f, 1.0f);
    }

    /**
     * Spawns the shooter's selected TRAIL cosmetic at an in-flight arrow's position, for the shooter's
     * match audience. Called every tick per live projectile by {@link
     * dev.stoshe.aerowars.system.ProjectileTrailSystem}; since the arrow moves, the per-tick puffs form a
     * streak along its flight. No-op when the shooter isn't in an ACTIVE match or has no trail selected.
     */
    public void spawnArrowTrail(UUID shooter, double x, double y, double z) {
        if (shooter == null) {
            return;
        }
        Match match = getPlayerMatch(shooter);
        if (match == null || match.state != MatchState.ACTIVE) {
            return;
        }
        String value = plugin.getCosmeticsManager()
                .selectedValue(shooter, dev.stoshe.aerowars.model.CosmeticCategory.TRAIL);
        if (value == null || value.isBlank()) {
            return;
        }
        List<PlayerRef> audience = new ArrayList<>();
        for (UUID u : allParticipants(match)) {
            PlayerRef pr = Universe.get().getPlayer(u);
            if (pr != null) {
                audience.add(pr);
            }
        }
        if (audience.isEmpty()) {
            return;
        }
        spawnTrail(audience, value, new WorldPos(x, y, z));
    }

    /** Minimal HSV(0-360,0-1,0-1) -> RGB(0-255) for the rainbow trail. */
    private static int[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float rp, gp, bp;
        if (h < 60) {
            rp = c; gp = x; bp = 0;
        } else if (h < 120) {
            rp = x; gp = c; bp = 0;
        } else if (h < 180) {
            rp = 0; gp = c; bp = x;
        } else if (h < 240) {
            rp = 0; gp = x; bp = c;
        } else if (h < 300) {
            rp = x; gp = 0; bp = c;
        } else {
            rp = c; gp = 0; bp = x;
        }
        return new int[]{Math.round((rp + m) * 255), Math.round((gp + m) * 255), Math.round((bp + m) * 255)};
    }

    public void shutdown() {
        // On server shutdown, end every match and send all participants (and hidden admin spectators)
        // back to the lobby if we can, so nobody is left stranded in a to-be-deleted match world.
        for (Match match : new ArrayList<>(matches.values())) {
            try {
                broadcast(match, Tr.t("match.server_closing"));
                List<UUID> all = new ArrayList<>(allParticipants(match));
                for (UUID uuid : new ArrayList<>(adminSpectatorMatch.keySet())) {
                    if (match.id.equals(adminSpectatorMatch.get(uuid)) && !all.contains(uuid)) {
                        all.add(uuid);
                    }
                }
                for (UUID uuid : all) {
                    PlayerRef pr = Universe.get().getPlayer(uuid);
                    if (pr != null) {
                        clearSpectatorState(pr);
                        // Restore the pre-match inventory INLINE (the scheduler is about to be killed, so
                        // sendToLobby's deferred restore would never run — that left winners logging back
                        // in with the kit) and teleport to the lobby so nobody is persisted mid-air in the
                        // about-to-be-deleted match world.
                        if (config.Match.SaveInventory) {
                            inventoryVault.restoreOnDisconnect(pr);
                        }
                        World lobby = Teleports.resolveLobby(config.General.LobbyWorld);
                        if (lobby != null) {
                            // Synchronous relocate — the scheduler/world thread won't tick again to process
                            // an async Teleport component, so they'd be saved mid-air in the match world.
                            Teleports.immediate(pr, lobby, Teleports.lobbySpawn(lobby, config.General.LobbySpawn, uuid));
                        }
                    }
                    removeHud(uuid);
                }
            } catch (Exception e) {
                Console.warning("Match shutdown evacuation failed for " + match.id + ": " + e.getMessage());
            }
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (Match match : new ArrayList<>(matches.values())) {
            worldManager.deleteWorld(match.world);
        }
        matches.clear();
        playerMatch.clear();
        adminSpectatorMatch.clear();
    }

    // ---------------------------------------------------------------- join/leave

    public Match getPlayerMatch(UUID uuid) {
        String id = playerMatch.get(uuid);
        return id == null ? null : matches.get(id);
    }

    /** Finds a waiting match with room, or creates one from a random playable arena. */
    public Match findOrCreateRandomMatch() {
        for (Match match : matches.values()) {
            if (match.hasRoom()) {
                return match;
            }
        }
        List<Arena> playable = arenaManager.getPlayableArenas();
        if (playable.isEmpty()) {
            return null;
        }
        Arena arena = playable.get(random.nextInt(playable.size()));
        return createMatch(arena);
    }

    /** Find-or-create a waiting match whose arena is the requested mode (SOLO/TEAMS). Null if none fits. */
    public Match findOrCreateMatchOfMode(dev.stoshe.aerowars.model.GameMode mode) {
        for (Match match : matches.values()) {
            if (match.hasRoom() && match.arena.mode() == mode) {
                return match;
            }
        }
        List<Arena> playable = new ArrayList<>();
        for (Arena a : arenaManager.getPlayableArenas()) {
            if (a.mode() == mode) {
                playable.add(a);
            }
        }
        if (playable.isEmpty()) {
            return null;
        }
        return createMatch(playable.get(random.nextInt(playable.size())));
    }

    private Match createMatch(Arena arena) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        World world = worldManager.createMatchWorld(id, arena.worldTemplate);
        if (world == null) {
            return null;
        }
        plugin.suppressJoinMessagesFor(world.getName());
        Match match = new Match(id, arena, world);
        buildTeams(match);
        matches.put(id, match);
        // Cages are built per player as they join (see addPlayer) and removed when their island
        // empties (see eliminate) — an empty island shows no cage.
        Console.info("Created match " + id + " on arena " + arena.name);
        return match;
    }

    private void buildTeams(Match match) {
        Arena arena = match.arena;
        int teamCount = Math.max(1, arena.teamCount());
        for (int i = 0; i < teamCount; i++) {
            int spawnIndex = arena.mode() == GameMode.TEAMS ? i * arena.effectiveTeamSize() : i;
            match.teams.add(new Team(i, spawnIndex));
        }
    }

    /** Adds a player to a match and teleports them into their cage. */
    public boolean addPlayer(Match match, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        return addPlayer(match, playerRef, null);
    }

    /** Adds a player, resolving their store/ref lazily (store/ref are unused by the join path). */
    public boolean addPlayer(Match match, PlayerRef playerRef) {
        return addPlayer(match, playerRef, null);
    }

    /**
     * Adds a player to a match and teleports them into their cage. When {@code forcedTeam}
     * is non-null (a party join in a TEAMS arena) the player is placed on that team if it
     * still has room, keeping party members together; otherwise the emptiest team is chosen.
     */
    public boolean addPlayer(Match match, PlayerRef playerRef, Team forcedTeam) {
        if (match == null || !match.hasRoom()) {
            return false;
        }
        // Can't be building an arena AND playing — auto-finish any setup session before joining a match.
        plugin.getSetupSessionManager().endActiveSession(playerRef);
        UUID uuid = playerRef.getUuid();
        Team team = pickTeam(match, forcedTeam);
        if (team == null) {
            return false;
        }
        team.add(uuid);
        match.playerTeam.put(uuid, team);
        match.alive.add(uuid);
        match.names.put(uuid, playerRef.getUsername());
        playerMatch.put(uuid, match.id);
        plugin.getStatsManager().recordGame(uuid, playerRef.getUsername());

        WorldPos spawn = spawnFor(match.arena, team.spawnIndex);
        if (spawn != null) {
            teleport(playerRef, match.world, Locations.centerOfBlock(spawn));
            // After the teleport settles (player in the match world, spawn chunk loaded):
            // save+clear inventory / switch to Adventure, and (re)build this player's cage on
            // the now-loaded chunk (the up-front build in createMatch can miss unloaded chunks).
            World matchWorld = match.world;
            WorldPos cageSpawn = spawn;
            scheduler.schedule(() -> {
                if (config.Match.SaveInventory) {
                    inventoryVault.enterMatch(matchWorld, uuid);
                }
                if (!match.state.isRunning()) {
                    // A player's selected cage cosmetic themes their own island cage.
                    String cageBlock = plugin.getCosmeticsManager()
                            .selectedValue(uuid, dev.stoshe.aerowars.model.CosmeticCategory.CAGE);
                    matchWorld.execute(() -> setCage(matchWorld, cageSpawn, false,
                            match.arena.effectiveTeamSize(), cageBlock));
                }
                // Attach the HUD/scoreboard only now that the player is actually in the match
                // world — attaching before the join teleport settles skips the .ui append and
                // later set-commands crash the client ("#AeroWarsRoot not found").
                updateHud(match);
            }, 1, TimeUnit.SECONDS);
        }
        // Tell the OTHER participants that this player joined; the joiner only gets their own
        // colored "you joined" line below (not the third-person broadcast about themselves).
        broadcastExcept(match, uuid, Tr.t("match.player_joined", "player", playerRef.getUsername(),
                "current", match.totalPlayers(), "max", match.arena.getMaxPlayers()));
        playerRef.sendMessage(ChatUtil.success(Tr.t("match.joined",
                "current", match.totalPlayers(), "max", match.arena.getMaxPlayers())));
        if (match.arena.mode() == GameMode.TEAMS) {
            playerRef.sendMessage(ChatUtil.info(Tr.t("team.assigned", "team", team.name)));
        }
        maybeStartCountdown(match);
        // Safe to call now: HudManager skips players not yet inside the match world (the new
        // joiner is still teleporting), so only settled participants refresh here. The joiner's
        // HUD attaches in the post-teleport scheduled block above.
        updateHud(match);
        return true;
    }

    private Team pickTeam(Match match, Team preferred) {
        if (match.arena.mode() == GameMode.SOLO) {
            // Solo is always 1-per-team FFA — party members share the arena but fight solo.
            for (Team team : match.teams) {
                if (team.members().isEmpty()) {
                    return team;
                }
            }
            return null;
        }
        int cap = match.arena.effectiveTeamSize();
        // TEAMS: honor the party's team if it still has room, keeping allies together.
        if (preferred != null && preferred.members().size() < cap) {
            return preferred;
        }
        // Otherwise fill the emptiest team that still has a free slot.
        Team best = null;
        int bestSize = Integer.MAX_VALUE;
        for (Team team : match.teams) {
            int size = team.members().size();
            if (size < cap && size < bestSize) {
                best = team;
                bestSize = size;
            }
        }
        return best;
    }

    /** Fullest team that still has a free slot — clusters a party into one team before spilling. */
    private Team teamToClusterInto(Match match) {
        int cap = match.arena.effectiveTeamSize();
        Team best = null;
        int bestSize = -1;
        for (Team team : match.teams) {
            int size = team.members().size();
            if (size < cap && size > bestSize) {
                best = team;
                bestSize = size;
            }
        }
        return best;
    }

    /**
     * Queues a whole party into one match. Members already in a match are skipped. In SOLO
     * arenas everyone shares the game as FFA rivals; in TEAMS arenas members are clustered
     * onto the same team (spilling into the next team when one fills — teams stay balanced).
     *
     * @return number of members placed, {@code 0} if none needed joining, {@code -1} if no
     *         match/arena can fit the party.
     */
    public int joinParty(List<PlayerRef> members, boolean keepTogether) {
        List<PlayerRef> toJoin = new ArrayList<>();
        for (PlayerRef pr : members) {
            if (getPlayerMatch(pr.getUuid()) == null) {
                toJoin.add(pr);
            }
        }
        if (toJoin.isEmpty()) {
            return 0;
        }
        // "Keep together" only bites for a real group of 2+; a lone leftover member can join anything.
        boolean keep = keepTogether && toJoin.size() > 1;
        Match match = findOrCreateMatchFor(toJoin.size(), keep);
        if (match == null) {
            // -2 = the party asked to stay together but no team arena can seat them all on one team;
            // -1 = no arena can fit the party at all. Either way the caller tells the leader (no waiting).
            return keep ? -2 : -1;
        }
        boolean teams = match.arena.mode() == GameMode.TEAMS;
        Team keepTeam = null;
        if (teams && keep) {
            // Force the WHOLE party onto a single team that can seat them all — never split.
            keepTeam = teamWithRoomFor(match, toJoin.size());
            if (keepTeam == null) {
                return -2;
            }
        } else if (teams) {
            // Splitting is allowed: randomize who ends up overflowing onto the other team(s).
            java.util.Collections.shuffle(toJoin, random);
        }
        int joined = 0;
        for (PlayerRef pr : toJoin) {
            if (!match.hasRoom()) {
                break;
            }
            Team forced = keepTeam != null ? keepTeam : (teams ? teamToClusterInto(match) : null);
            if (addPlayer(match, pr, forced)) {
                joined++;
            }
        }
        return joined;
    }

    /**
     * A waiting match with room for {@code size} players, else a fresh match on a fitting arena. When
     * {@code keepTogether} is set, only TEAMS arenas whose team size can seat the whole party (and, for
     * existing matches, a team that still has room for all of them) are eligible — so the party never
     * gets split. Returns null when nothing fits (the caller then informs the leader).
     */
    private Match findOrCreateMatchFor(int size, boolean keepTogether) {
        Match best = null;
        for (Match match : matches.values()) {
            if (!match.hasRoom()) {
                continue;
            }
            if (keepTogether) {
                if (match.arena.mode() != GameMode.TEAMS || match.arena.effectiveTeamSize() < size
                        || teamWithRoomFor(match, size) == null) {
                    continue;
                }
            } else if (match.remainingSlots() < size) {
                continue;
            }
            if (best == null || match.totalPlayers() > best.totalPlayers()) {
                best = match;
            }
        }
        if (best != null) {
            return best;
        }
        List<Arena> fits = new ArrayList<>();
        for (Arena arena : arenaManager.getPlayableArenas()) {
            boolean ok = keepTogether
                    ? arena.mode() == GameMode.TEAMS && arena.effectiveTeamSize() >= size
                    : arena.getMaxPlayers() >= size;
            if (ok) {
                fits.add(arena);
            }
        }
        if (fits.isEmpty()) {
            return null;
        }
        return createMatch(fits.get(random.nextInt(fits.size())));
    }

    /** A team in this match that still has room for {@code size} more members, or null. */
    private Team teamWithRoomFor(Match match, int size) {
        int cap = match.arena.effectiveTeamSize();
        for (Team team : match.teams) {
            if (cap - team.members().size() >= size) {
                return team;
            }
        }
        return null;
    }

    public void removePlayer(PlayerRef playerRef) {
        // A hidden admin spectator leaves cleanly without touching match state/counts.
        if (exitAdminSpectate(playerRef)) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("match.left")));
            return;
        }
        Match match = getPlayerMatch(playerRef.getUuid());
        if (match == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.not_in_match")));
            return;
        }
        UUID uuid = playerRef.getUuid();
        playerRef.sendMessage(ChatUtil.warning(Tr.t("match.left")));
        announceLeft(match, uuid);
        // quiet=true: we already announced the leave, so eliminate() doesn't also say "eliminated".
        eliminate(match, uuid, null, true, false);
    }

    /** Broadcasts a "left the match" line to the rest of the match (used for /leave and disconnect). */
    private void announceLeft(Match match, UUID uuid) {
        if (match == null || !(match.alive.contains(uuid) || match.spectators.contains(uuid))) {
            return;
        }
        int aliveAfter = Math.max(0, match.alive.size() - (match.alive.contains(uuid) ? 1 : 0));
        broadcastExcept(match, uuid, Tr.t("match.player_left_match", "player", match.nameOf(uuid),
                "alive", aliveAfter));
    }

    /**
     * Cleanly removes a player from any match / hidden admin-spectate they're in, WITHOUT bouncing them to
     * the lobby — used by {@code /setup start|edit} so they exit the match before being teleported straight
     * into the setup world. A player yanked out of a match world while the match still holds them left the
     * match (and the ephemeral-world lifecycle) in a broken state, which was crashing setup-world creation.
     * The pre-match inventory snapshot is discarded because the setup wizard clears/manages the inventory.
     */
    public void leaveForSetup(PlayerRef pr) {
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        clearSpectatorState(pr);
        adminSpectatorMatch.remove(uuid);
        inventoryVault.discard(uuid);
        Match match = getPlayerMatch(uuid);
        if (match == null) {
            removeHud(uuid);
            return;
        }
        boolean wasAlive = match.alive.remove(uuid);
        match.spectators.remove(uuid);
        Team team = match.playerTeam.remove(uuid);
        if (team != null) {
            team.eliminate(uuid);
        }
        playerMatch.remove(uuid);
        removeHud(uuid);
        if (wasAlive && !match.state.isRunning()) {
            broadcastExcept(match, uuid, Tr.t("match.player_left", "player", match.nameOf(uuid),
                    "current", match.totalPlayers(), "max", match.arena.getMaxPlayers()));
        }
        updateHud(match);
        checkWin(match);
    }

    /** All live matches (for the admin panel). */
    public java.util.Collection<Match> liveMatches() {
        return matches.values();
    }

    /**
     * True if any live match (waiting, counting down or running) is currently using this arena. Editing
     * or deleting such an arena is refused so a match in progress never has its definition pulled away.
     */
    public boolean isArenaInUse(String arenaName) {
        if (arenaName == null) {
            return false;
        }
        for (Match m : matches.values()) {
            if (m.arena != null && m.arena.name != null && m.arena.name.equalsIgnoreCase(arenaName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdminSpectator(UUID uuid) {
        return adminSpectatorMatch.containsKey(uuid);
    }

    /**
     * Drops an admin into a running match as a SECRET spectator: invisible, flying, invulnerable, but
     * NOT added to the match roster — no join broadcast and not counted in the player total.
     */
    public boolean adminSpectate(PlayerRef admin, String matchId) {
        Match match = matches.get(matchId);
        if (match == null) {
            return false;
        }
        UUID actor = admin.getUuid();
        // Can't spectate while you're a participant of a match, or already spectating one.
        if (getPlayerMatch(actor) != null || adminSpectatorMatch.containsKey(actor)) {
            admin.sendMessage(ChatUtil.error(Tr.t("admin.spectate_busy")));
            return false;
        }
        WorldPos spec = match.arena.spectatorSpawn;
        WorldPos target = spec != null ? spec
                : (match.arena.spawnPoints.isEmpty() ? null : match.arena.spawnPoints.get(0));
        if (target == null) {
            return false;
        }
        UUID uuid = admin.getUuid();
        adminSpectatorMatch.put(uuid, matchId);
        teleport(admin, match.world, Locations.centerOfBlock(target));
        World w = match.world;
        scheduler.schedule(() -> applySpectatorState(w, uuid), 1, TimeUnit.SECONDS);
        admin.sendMessage(ChatUtil.info(Tr.t("admin.spectating", "arena", match.arena.name)));
        return true;
    }

    /** Ends a hidden admin-spectate session: strips god-mode and returns them to the lobby. */
    public boolean exitAdminSpectate(PlayerRef playerRef) {
        if (playerRef == null || adminSpectatorMatch.remove(playerRef.getUuid()) == null) {
            return false;
        }
        clearSpectatorState(playerRef);
        sendToLobby(playerRef);
        return true;
    }

    /** Admin force-ends a running match (game-over for all, then the room closes). */
    public boolean adminEndMatch(String matchId) {
        Match match = matches.get(matchId);
        if (match == null) {
            return false;
        }
        broadcast(match, Tr.t("admin.match_ended"));
        if (match.state != MatchState.ENDING && match.state != MatchState.CLEANUP) {
            endMatch(match, null);
        }
        return true;
    }

    /**
     * Called when a player disconnects. Restores their pre-match inventory FIRST (inline,
     * using the live disconnect PlayerRef) so they don't log back in carrying the match kit,
     * then removes them from the match. Runs even if they weren't currently in a match, in
     * case a stale snapshot is still held.
     */
    public void handleDisconnect(PlayerRef pr) {
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        // A disconnecting admin spectator just drops out of the hidden-spectator map (their entity
        // and god-mode state die with the session; we never touched their inventory).
        adminSpectatorMatch.remove(uuid);
        if (config.Match.SaveInventory) {
            inventoryVault.restoreOnDisconnect(pr);
        }
        Match match = getPlayerMatch(uuid);
        if (match != null) {
            announceLeft(match, uuid);
            eliminate(match, uuid, null, true, false);
        }
    }

    // ---------------------------------------------------------------- countdown/start

    private void maybeStartCountdown(Match match) {
        if (match.state == MatchState.WAITING && match.totalPlayers() >= minPlayers(match)) {
            match.state = MatchState.COUNTDOWN;
            match.countdownRemaining = config.Match.CountdownSeconds;
            broadcast(match, Tr.t("match.countdown_started", "seconds", match.countdownRemaining));
        }
    }

    private int minPlayers(Match match) {
        return Math.max(2, Math.min(config.Match.MinPlayers, match.arena.getMaxPlayers()));
    }

    private void startMatch(Match match) {
        match.state = MatchState.ACTIVE;
        match.secondsElapsed = 0;
        World world = match.world;
        // Drop EVERY cage so players can leave their island. Force-load each cage's chunk first
        // (only occupied islands are loaded), then clear the shell on the world thread.
        int ts = match.arena.effectiveTeamSize();
        for (Team team : match.teams) {
            WorldPos spawn = spawnFor(match.arena, team.spawnIndex);
            if (spawn == null) {
                continue;
            }
            World w = world;
            WorldPos s = spawn;
            long key = ChunkUtil.indexChunkFromBlock(spawn.blockX(), spawn.blockZ());
            w.getChunkAsync(key).thenRun(() -> w.execute(() -> setCage(w, s, true, ts)));
        }
        if (config.Loot.FillOnStart) {
            prepareChests(match, true);
            match.chestsFilled = true;
        }
        buildEventSchedule(match);
        // Kits + full heal for each living player.
        for (UUID uuid : new ArrayList<>(match.alive)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                prepareForPlay(pr, match);
            }
        }
        broadcast(match, Tr.t("match.started"));
        broadcast(match, Tr.t("match.cages_open"));
        // The cages "shatter" open — play the glass-break cue to everyone.
        playSoundAll(match, Sounds.GLASS_BREAK, 1.0f, 1.0f);
        updateHud(match);
    }

    /**
     * Places (optional) and fills every chest, force-loading each chest's chunk first (solo matches
     * only load the local spawn area). getChunkAsync runs off the world thread to avoid a deadlock,
     * then the block work is marshalled back onto it. Filling clears the container first, so this
     * doubles as the mid-match refill.
     */
    private void prepareChests(Match match, boolean place) {
        World world = match.world;
        java.util.List<ChestLocation> chests = match.arena.allChests();
        for (ChestLocation chest : chests) {
            if (chest == null || chest.pos == null) {
                continue;
            }
            World w = world;
            ChestLocation c = chest;
            String override = tableOverrideFor(match, chest);
            long key = ChunkUtil.indexChunkFromBlock(chest.pos.blockX(), chest.pos.blockZ());
            if (place) {
                w.getChunkAsync(key).thenRun(() -> w.execute(() -> lootManager.populateChest(w, c)));
            }
            // The ItemContainerBlock component is created by a block system a tick or two after the
            // block is placed, so fillChest may not find a ready container on the first try. Retry a
            // few times (1s apart) until it succeeds — this is what fixes chests staying empty.
            scheduleChestFill(w, c, key, override, place ? 6 : 3, place ? 1 : 0);
        }
    }

    /** Retries {@link LootManager#fillChest} until the chest container is ready or attempts run out. */
    private void scheduleChestFill(World w, ChestLocation c, long chunkKey, String override,
            int attemptsLeft, long delaySeconds) {
        scheduler.schedule(
                () -> w.getChunkAsync(chunkKey).thenRun(() -> w.execute(() -> {
                    boolean done = lootManager.fillChest(w, c, override);
                    if (!done && attemptsLeft > 1) {
                        scheduleChestFill(w, c, chunkKey, override, attemptsLeft - 1, 1);
                    }
                })),
                delaySeconds, TimeUnit.SECONDS);
    }

    /** Common chests upgraded by a loot event roll the MIDDLE table; everything else uses its own tier. */
    private String tableOverrideFor(Match match, ChestLocation chest) {
        if (chest.type() == ChestType.NORMAL && match.upgradedChestKeys.contains(chestKey(chest))) {
            return "middle";
        }
        return null;
    }

    private static String chestKey(ChestLocation chest) {
        return chest.pos.blockX() + "," + chest.pos.blockY() + "," + chest.pos.blockZ();
    }

    /**
     * Builds THIS match's concrete event timeline from the unified event config. Called once at match
     * start. When randomize is off, events fire at their configured times; when on, the enabled events
     * are dropped at random moments (random order) spread across the match, kept inside the duration and
     * never closer than MinGapSeconds so they don't fire too fast. See {@link AeroWarsConfig.Loot.Events}.
     */
    private void buildEventSchedule(Match match) {
        match.eventSchedule.clear();
        match.nextEventIndex = 0;
        match.totalUpgradeEvents = 0;
        match.firedUpgradeEvents = 0;
        AeroWarsConfig.Loot.Events cfg = config.Loot.Events;
        if (cfg == null || !cfg.Enabled || cfg.List == null || cfg.List.isEmpty()) {
            return;
        }
        int duration = config.Match.MaxDurationSeconds;
        java.util.List<Match.ScheduledLootEvent> planned = new ArrayList<>();
        if (cfg.Randomize) {
            // Gather the enabled event types, shuffle them (random order), then drop them at random
            // moments that respect MinGapSeconds. If they can't all fit with that spacing, thin the list.
            java.util.List<dev.stoshe.aerowars.model.LootEventType> types = new ArrayList<>();
            for (AeroWarsConfig.Loot.Events.Event e : cfg.List) {
                if (e != null && e.Enabled) {
                    types.add(dev.stoshe.aerowars.model.LootEventType.fromId(e.Type));
                }
            }
            int gap = Math.max(15, cfg.MinGapSeconds);
            // Stop a little short of time-up so an event never lands on the final second.
            int windowEnd = Math.max(gap, duration - Math.max(10, gap / 2));
            int maxFit = Math.max(1, windowEnd / gap);
            java.util.Collections.shuffle(types, random);
            if (types.size() > maxFit) {
                types = new ArrayList<>(types.subList(0, maxFit));
            }
            int[] times = randomSpacedTimes(types.size(), gap, windowEnd);
            for (int i = 0; i < types.size(); i++) {
                planned.add(new Match.ScheduledLootEvent(times[i], types.get(i)));
            }
        } else {
            // Fixed timeline: fire each enabled event at its Time (skipping any past the match end).
            for (AeroWarsConfig.Loot.Events.Event e : cfg.List) {
                if (e == null || !e.Enabled) {
                    continue;
                }
                if (e.Time > 0 && e.Time < duration) {
                    planned.add(new Match.ScheduledLootEvent(e.Time,
                            dev.stoshe.aerowars.model.LootEventType.fromId(e.Type)));
                }
            }
            planned.sort(java.util.Comparator.comparingInt(a -> a.time));
        }
        match.eventSchedule.addAll(planned);
        for (Match.ScheduledLootEvent se : planned) {
            if (se.type == dev.stoshe.aerowars.model.LootEventType.LOOT_UPGRADE) {
                match.totalUpgradeEvents++;
            }
        }
    }

    /**
     * {@code count} strictly-increasing times inside {@code [gap, windowEnd]}, each at least {@code gap}
     * apart. Minimal packing is (i+1)*gap; the leftover slack is scattered randomly (and kept sorted) so
     * the spacing invariant always holds.
     */
    private int[] randomSpacedTimes(int count, int gap, int windowEnd) {
        int[] times = new int[count];
        if (count <= 0) {
            return times;
        }
        int slack = Math.max(0, windowEnd - count * gap);
        int[] r = new int[count];
        for (int i = 0; i < count; i++) {
            r[i] = slack == 0 ? 0 : random.nextInt(slack + 1);
        }
        java.util.Arrays.sort(r);
        for (int i = 0; i < count; i++) {
            times[i] = (i + 1) * gap + r[i];
        }
        return times;
    }

    /**
     * Fires one scheduled event. A LOOT_UPGRADE upgrades a growing share of the common chests to middle
     * loot (upgrade k of N upgrades ceil(k/N) of them) then refills; a REFILL just re-rolls every chest.
     * Only the event itself is shown (no "1/3" count).
     */
    private void fireEvent(Match match, dev.stoshe.aerowars.model.LootEventType type) {
        if (type == dev.stoshe.aerowars.model.LootEventType.LOOT_UPGRADE) {
            match.firedUpgradeEvents++;
            java.util.List<ChestLocation> normals = new ArrayList<>();
            for (ChestLocation chest : match.arena.allChests()) {
                if (chest != null && chest.pos != null && chest.type() == ChestType.NORMAL) {
                    normals.add(chest);
                }
            }
            int total = Math.max(1, match.totalUpgradeEvents);
            int target = (int) Math.ceil(normals.size() * (double) match.firedUpgradeEvents / total);
            for (int i = 0; i < Math.min(target, normals.size()); i++) {
                match.upgradedChestKeys.add(chestKey(normals.get(i)));
            }
        }
        prepareChests(match, false);
        broadcast(match, Tr.t(type.broadcastKey()));
        showTitleAll(match, Tr.t(type.titleKey()), Tr.t("status.event_sub"), "#ffdd55", 2.5f);
        // Event warning cue.
        playSoundAll(match, Sounds.CLICK, 1.0f, 0.8f);
    }

    // ---------------------------------------------------------------- death/win

    /** Invoked by {@code MatchDeathSystem} when a player entity gains a DeathComponent. */
    public void handleDeath(UUID victim, UUID killer) {
        Match match = getPlayerMatch(victim);
        if (match == null || match.state != MatchState.ACTIVE) {
            return;
        }
        // A spectator "dying" (e.g. drifting into the void) must NOT be eliminated — respawn them at
        // the spectator spawn and re-arm their god-mode. Same for hidden admin spectators.
        if (match.spectators.contains(victim) || adminSpectatorMatch.containsKey(victim)) {
            respawnSpectator(match, victim);
            return;
        }
        String killerName = killer != null ? match.names.get(killer) : null;
        // Credit the kill only if the killer is a live participant of this match.
        if (killer != null && match.alive.contains(killer)) {
            match.kills.merge(killer, 1, Integer::sum);
            plugin.getStatsManager().recordKill(killer, match.names.get(killer));
            awardEconomy(killer, config.Rewards.Economy.KillAmount, "economy.reward_kill");
            playKillEffect(match, killer, victim);
        }
        plugin.getStatsManager().recordDeath(victim, match.names.get(victim));
        eliminate(match, victim, killerName, false, true);
    }

    private void eliminate(Match match, UUID uuid, String killerName, boolean quiet, boolean allowSpectate) {
        boolean wasAlive = match.alive.remove(uuid);
        Team team = match.playerTeam.remove(uuid);
        if (team != null) {
            team.eliminate(uuid);
            // Clear the island cage once it empties before the match starts (no player, no cage).
            if (!match.state.isRunning() && team.members().isEmpty()) {
                WorldPos spawn = spawnFor(match.arena, team.spawnIndex);
                if (spawn != null) {
                    World w = match.world;
                    WorldPos cs = spawn;
                    w.execute(() -> setCage(w, cs, true, match.arena.effectiveTeamSize()));
                }
            }
        }
        playerMatch.remove(uuid);
        match.spectators.remove(uuid);

        if (wasAlive && !quiet) {
            String name = match.nameOf(uuid);
            int alive = match.alive.size();
            if (killerName != null) {
                broadcast(match, Tr.t("match.player_eliminated_by", "player", name, "killer", killerName, "alive", alive));
            } else {
                broadcast(match, Tr.t("match.player_eliminated", "player", name, "alive", alive));
            }
        }

        PlayerRef pr = Universe.get().getPlayer(uuid);
        boolean spectate = allowSpectate && wasAlive && config.Match.SpectateOnDeath && config.Match.AllowSpectators
                && match.state == MatchState.ACTIVE;
        if (spectate && pr != null) {
            match.spectators.add(uuid);
            playerMatch.put(uuid, match.id);
            pr.sendMessage(ChatUtil.error(Tr.t("match.you_died")));
            pr.sendMessage(ChatUtil.info(Tr.t("match.now_spectating")));
            WorldPos spec = match.arena.spectatorSpawn;
            clearInventory(pr, match.world);
            if (spec != null) {
                teleport(pr, match.world, Locations.centerOfBlock(spec));
            }
            // Full spectator god-mode: invisible to players still fighting, creative-style flight,
            // and invulnerable to all damage until they leave the match.
            applySpectatorState(match.world, uuid);
            // Hand out the spectator hotbar (tracker + return-to-lobby), locked in place.
            giveSpectatorItems(match.world, uuid);
            if (config.Spectator.Enabled && pr != null) {
                pr.sendMessage(ChatUtil.info(Tr.t("spectator.hint")));
            }
        } else if (pr != null) {
            sendToLobby(pr);
        }
        if (!spectate) {
            removeHud(uuid);
        }

        updateHud(match);
        checkWin(match);
    }

    private void checkWin(Match match) {
        if (match.state != MatchState.ACTIVE) {
            return;
        }
        List<Team> alive = match.aliveTeams();
        if (alive.size() > 1) {
            return;
        }
        Team winner = alive.isEmpty() ? null : alive.get(0);
        endMatch(match, winner);
    }

    /**
     * The match ran out of time. Only a single remaining team wins; if more than one team is still
     * alive (or none), nobody wins — everyone loses (draw / game over for all).
     */
    private void handleTimeUp(Match match) {
        List<Team> alive = match.aliveTeams();
        Team winner = alive.size() == 1 ? alive.get(0) : null;
        if (winner == null) {
            broadcast(match, Tr.t("match.time_up"));
        }
        endMatch(match, winner);
    }

    private void endMatch(Match match, Team winner) {
        match.state = MatchState.ENDING;
        match.countdownRemaining = Math.max(1, config.Match.EndCelebrationSeconds);
        if (winner != null) {
            for (UUID uuid : winner.members()) {
                plugin.getStatsManager().recordWin(uuid, match.names.get(uuid));
                awardEconomy(uuid, config.Rewards.Economy.WinAmount, "economy.reward_win");
            }
        }
        if (winner == null) {
            match.resultText = Tr.t("status.draw");
            broadcast(match, Tr.t("match.draw"));
        } else if (match.arena.mode() == GameMode.TEAMS) {
            match.resultText = Tr.t("status.win_team", "team", winner.name);
            broadcast(match, Tr.t("match.win_team", "team", winner.name));
        } else {
            UUID winnerUuid = winner.members().stream().findFirst().orElse(null);
            String name = winnerUuid != null ? match.nameOf(winnerUuid) : winner.name;
            match.resultText = Tr.t("status.win_solo", "player", name);
            broadcast(match, Tr.t("match.win_solo", "player", name));
            runWinnerRewards(match, name);
        }
        // On-screen victory / game-over banner for everyone still in the match.
        for (UUID uuid : allParticipants(match)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr == null) {
                continue;
            }
            boolean won = winner != null && winner.members().contains(uuid);
            if (won) {
                Titles.show(pr, Tr.t("title.victory"), Tr.t("title.victory_sub"), "#ffdd00", 5f);
            } else {
                Titles.show(pr, Tr.t("title.game_over"), Tr.t("title.game_over_sub"), "#ff5555", 5f);
            }
        }
        // Victory fanfare for everyone in the match when there's a winner (skip on a draw/no-winner).
        if (winner != null) {
            playSoundAll(match, Sounds.WIN, 1.0f, 1.0f);
        }
        launchFireworks(match, winner);
        buildPodium(match, winner);
        revealSpectatorsAndProtectWinners(match, winner);
        updateHud(match);
    }

    /**
     * End-of-match presentation: reveals every spectator to the still-living winner(s) (removes the
     * hide-from-adventure component so they're all visible during the celebration), and keeps the
     * winner(s) invulnerable for the whole celebration (until the room closes) — otherwise the winner
     * could still take damage while the arena is open. All of this is stripped by clearSpectatorState /
     * InventoryVault.restore when everyone returns to the lobby.
     */
    private void revealSpectatorsAndProtectWinners(Match match, Team winner) {
        World world = match.world;
        if (world == null) {
            return;
        }
        List<UUID> specs = new ArrayList<>(match.spectators);
        java.util.Set<UUID> winners = winner == null ? java.util.Collections.emptySet()
                : new java.util.HashSet<>(winner.members());
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (UUID uuid : specs) {
                    PlayerRef pr = Universe.get().getPlayer(uuid);
                    if (pr == null) {
                        continue;
                    }
                    store.removeComponentIfExists(pr.getReference(), HiddenFromAdventurePlayers.getComponentType());
                }
                for (UUID uuid : winners) {
                    PlayerRef pr = Universe.get().getPlayer(uuid);
                    if (pr == null) {
                        continue;
                    }
                    Ref<EntityStore> ref = pr.getReference();
                    if (store.getComponent(ref, Invulnerable.getComponentType()) == null) {
                        store.addComponent(ref, Invulnerable.getComponentType());
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Raises the winner(s) onto a small block podium above the arena centre for the end celebration.
     * The match world is ephemeral (deleted at cleanup), so the blocks need no teardown; the winners'
     * temporary invulnerability is stripped by {@link #clearSpectatorState} when they return to lobby.
     */
    private void buildPodium(Match match, Team winner) {
        if (!config.Effects.PodiumEnabled || match == null || winner == null) {
            return;
        }
        WorldPos center = arenaCenter(match);
        if (center == null) {
            return;
        }
        World world = match.world;
        int cx = (int) Math.floor(center.x);
        int cz = (int) Math.floor(center.z);
        int py = (int) Math.floor(center.y) + Math.max(2, config.Effects.PodiumHeight);
        String block = (config.Effects.PodiumBlock == null || config.Effects.PodiumBlock.isBlank())
                ? "Glass_Block_Yellow" : config.Effects.PodiumBlock;
        List<UUID> winners = new ArrayList<>(winner.members());
        long chunkKey = ChunkUtil.indexChunkFromBlock(cx, cz);
        world.getChunkAsync(chunkKey).thenRun(() -> world.execute(() -> {
            try {
                // A 3x3 floor with a raised centre step — the winner stands on top.
                for (int x = cx - 1; x <= cx + 1; x++) {
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        world.setBlock(x, py - 1, z, block, 0);
                    }
                }
                world.setBlock(cx, py, cz, block, 0);
            } catch (Exception ignored) {
            }
        }));
        // Once the platform exists, lift the winners onto it and make them safe up there.
        scheduler.schedule(() -> {
            int idx = 0;
            // Solo winner stands on the centre step; team winners spread across the 3x3 floor.
            for (UUID uuid : winners) {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    continue;
                }
                double tx = cx + 0.5;
                double tz = cz + 0.5;
                double ty = py + 1;
                if (winners.size() > 1) {
                    tx = cx - 1 + (idx % 3) + 0.5;
                    tz = cz - 1 + (idx / 3) + 0.5;
                    ty = py;
                }
                teleport(pr, world, new WorldPos(tx, ty, tz));
                addInvulnerable(world, uuid);
                idx++;
            }
        }, 600, TimeUnit.MILLISECONDS);
    }

    /** Adds the Invulnerable component (immune to all damage incl. fall) — used on the podium. */
    private void addInvulnerable(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return;
        }
        world.execute(() -> {
            try {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = pr.getReference();
                if (store.getComponent(ref, Invulnerable.getComponentType()) == null) {
                    store.addComponent(ref, Invulnerable.getComponentType());
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** A firework show: bursts rise from the arena centre to random heights, random colours/types. */
    private void launchFireworks(Match match, Team winner) {
        if (!config.Effects.VictoryFireworks) {
            return;
        }
        // The winner's selected victory cosmetic (if any) themes the whole show.
        runFireworkShow(arenaCenter(match), allParticipants(match), victoryParticleFor(winner));
    }

    /** The victory-cosmetic particle chosen by any winner (first one set), or {@code null}. */
    private String victoryParticleFor(Team winner) {
        if (winner == null) {
            return null;
        }
        for (UUID uuid : winner.members()) {
            String p = plugin.getCosmeticsManager()
                    .selectedValue(uuid, dev.stoshe.aerowars.model.CosmeticCategory.VICTORY);
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }

    /** Spawns the killer's selected kill-effect particle at the victim's position for all to see. */
    private void playKillEffect(Match match, UUID killer, UUID victim) {
        String particle = plugin.getCosmeticsManager()
                .selectedValue(killer, dev.stoshe.aerowars.model.CosmeticCategory.KILL);
        if (particle == null || particle.isBlank()) {
            return;
        }
        PlayerRef vpr = Universe.get().getPlayer(victim);
        if (vpr == null) {
            return;
        }
        WorldPos pos = Locations.fromTransform(vpr.getTransform());
        if (pos == null) {
            return;
        }
        List<PlayerRef> audience = new ArrayList<>();
        for (UUID u : allParticipants(match)) {
            PlayerRef pr = Universe.get().getPlayer(u);
            if (pr != null) {
                audience.add(pr);
            }
        }
        if (audience.isEmpty()) {
            return;
        }
        Fireworks.burst(audience, pos.x, pos.y + 1, pos.z, particle,
                220, 130, 60, config.Effects.FireworkScale, 1.5f);
    }

    /** Debug: one random firework burst at the player's feet (only they see it). */
    public void debugFireworkBurst(PlayerRef pr) {
        List<String> particles = config.Effects.FireworkParticles;
        if (pr == null || particles == null || particles.isEmpty()) {
            return;
        }
        WorldPos here = Locations.fromTransform(pr.getTransform());
        String particle = particles.get(random.nextInt(particles.size()));
        Fireworks.burst(List.of(pr), here.x, here.y + 3, here.z, particle,
                64 + random.nextInt(192), 64 + random.nextInt(192), 64 + random.nextInt(192),
                config.Effects.FireworkScale, 2.5f);
    }

    /** Debug: run the full victory firework show centred on the player (only they see it). */
    public void debugFireworkShow(PlayerRef pr) {
        if (pr == null) {
            return;
        }
        runFireworkShow(Locations.fromTransform(pr.getTransform()), List.of(pr.getUuid()));
    }

    private void runFireworkShow(WorldPos center, List<UUID> audienceUuids) {
        runFireworkShow(center, audienceUuids, null);
    }

    private void runFireworkShow(WorldPos center, List<UUID> audienceUuids, String particleOverride) {
        List<String> particles = config.Effects.FireworkParticles;
        if (particles == null || particles.isEmpty() || center == null) {
            return;
        }
        int bursts = Math.max(1, config.Effects.FireworkBursts);
        int durMs = Math.max(1, config.Effects.FireworkDurationSeconds) * 1000;
        int maxRise = Math.max(2, config.Effects.FireworkMaxRise);
        float scale = config.Effects.FireworkScale;
        for (int i = 0; i < bursts; i++) {
            long delayMs = (long) ((double) i / bursts * durMs);
            scheduler.schedule(() -> {
                List<PlayerRef> audience = new ArrayList<>();
                for (UUID u : audienceUuids) {
                    PlayerRef pr = Universe.get().getPlayer(u);
                    if (pr != null) {
                        audience.add(pr);
                    }
                }
                if (audience.isEmpty()) {
                    return;
                }
                double ox = center.x + (random.nextDouble() * 8 - 4);
                double oz = center.z + (random.nextDouble() * 8 - 4);
                double oy = center.y + 3 + random.nextInt(maxRise); // random, limited height
                String particle = (particleOverride != null && !particleOverride.isBlank())
                        ? particleOverride
                        : particles.get(random.nextInt(particles.size())); // cosmetic override, else random
                int r = 64 + random.nextInt(192);
                int g = 64 + random.nextInt(192);
                int b = 64 + random.nextInt(192); // random vibrant colour
                Fireworks.burst(audience, ox, oy, oz, particle, r, g, b, scale, 2.5f);
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    /** Centre of the arena = centroid of its spawn points (at their average height). */
    private WorldPos arenaCenter(Match match) {
        List<WorldPos> spawns = match.arena.spawnPoints;
        if (spawns == null || spawns.isEmpty()) {
            return null;
        }
        double sx = 0, sy = 0, sz = 0;
        for (WorldPos p : spawns) {
            sx += p.x;
            sy += p.y;
            sz += p.z;
        }
        int n = spawns.size();
        return new WorldPos(sx / n, sy / n, sz / n);
    }

    /**
     * Pay an economy reward to a player and (best-effort) tell them. No-op when the
     * economy reward is disabled, the amount is non-positive, or no economy provider
     * is installed. {@code langKey} takes an {@code amount} placeholder.
     */
    private void awardEconomy(UUID uuid, int amount, String langKey) {
        if (uuid == null || amount <= 0 || !config.Rewards.Economy.Enabled) {
            return;
        }
        if (!plugin.getEconomyService().reward(uuid, amount)) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr != null) {
            pr.sendMessage(ChatUtil.success(Tr.t(langKey, "amount", String.valueOf(amount))));
        }
    }

    private void runWinnerRewards(Match match, String winnerName) {
        if (config.Rewards.WinnerCommands == null) {
            return;
        }
        for (String cmd : config.Rewards.WinnerCommands) {
            String resolved = cmd.replace("{player}", winnerName).replace("{arena}", match.arena.displayName());
            plugin.dispatchConsoleCommand(resolved);
        }
    }

    // ---------------------------------------------------------------- cleanup

    private void cleanup(Match match) {
        match.state = MatchState.CLEANUP;
        List<UUID> everyone = new ArrayList<>();
        everyone.addAll(match.alive);
        everyone.addAll(match.spectators);
        for (UUID uuid : everyone) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                sendToLobby(pr);
            }
            removeHud(uuid);
            playerMatch.remove(uuid);
        }
        // Evict any hidden admin spectators of this match back to the lobby too.
        for (UUID uuid : new ArrayList<>(adminSpectatorMatch.keySet())) {
            if (match.id.equals(adminSpectatorMatch.get(uuid))) {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr != null) {
                    exitAdminSpectate(pr);
                } else {
                    adminSpectatorMatch.remove(uuid);
                }
            }
        }
        matches.remove(match.id);
        // Give teleports a moment to fire before we unload the world.
        World world = match.world;
        scheduler.schedule(() -> worldManager.deleteWorld(world), 2, TimeUnit.SECONDS);
        Console.info("Cleaned up match " + match.id);
    }

    /**
     * Safety net invoked by {@link WorldManager} right BEFORE it removes a world: if a live match is
     * running on it, the match is cancelled and every participant + hidden admin spectator is moved to the
     * lobby with their pre-match inventory restored. So a match world can never be deleted (for ANY reason
     * — orphan cleanup, name-collision recreate, forced removal) while players are still inside it. In the
     * normal {@link #cleanup} flow this is a no-op: the match is already off the map by the time the world
     * is deleted.
     */
    public void evacuateWorld(World world) {
        if (world == null) {
            return;
        }
        Match match = null;
        for (Match m : matches.values()) {
            if (m.world == world) {
                match = m;
                break;
            }
        }
        java.util.Set<UUID> toEvac = new java.util.LinkedHashSet<>();
        if (match != null) {
            if (match.state != MatchState.CLEANUP) {
                broadcast(match, Tr.t("match.world_removed"));
            }
            toEvac.addAll(allParticipants(match));
            match.state = MatchState.CLEANUP; // stop it ticking on the doomed world
        }
        for (java.util.Map.Entry<UUID, String> e : new ArrayList<>(adminSpectatorMatch.entrySet())) {
            Match am = matches.get(e.getValue());
            if ((match != null && match.id.equals(e.getValue())) || (am != null && am.world == world)) {
                toEvac.add(e.getKey());
            }
        }
        if (match == null && toEvac.isEmpty()) {
            return;
        }
        World lobby = Teleports.resolveLobby(config.General.LobbyWorld);
        for (UUID uuid : toEvac) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                clearSpectatorState(pr);
                if (config.Match.SaveInventory) {
                    inventoryVault.restoreOnDisconnect(pr);
                }
                if (lobby != null && lobby != world) {
                    // Synchronous relocate (see shutdown) so a removal never leaves them saved mid-air.
                    Teleports.immediate(pr, lobby, Teleports.lobbySpawn(lobby, config.General.LobbySpawn, uuid));
                }
            }
            removeHud(uuid);
            playerMatch.remove(uuid);
            adminSpectatorMatch.remove(uuid);
        }
        if (match != null) {
            matches.remove(match.id);
            Console.info("Evacuated + cancelled match " + match.id + " because its world was removed.");
        }
    }

    // ---------------------------------------------------------------- tick

    private QueueManager queueManager;

    public void setQueueManager(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    private void tickAll() {
        for (Match match : new ArrayList<>(matches.values())) {
            try {
                tick(match);
            } catch (Exception e) {
                Console.error("Tick error in match " + match.id + ": " + e.getMessage());
            }
        }
        if (queueManager != null) {
            try {
                queueManager.tick();
            } catch (Exception e) {
                Console.error("Queue tick error: " + e.getMessage());
            }
        }
    }

    private void tick(Match match) {
        // Auto-close a pre-game match that's sat empty too long (nobody joined, or everyone left).
        if (match.state.acceptsPlayers()) {
            if (match.totalPlayers() == 0) {
                match.emptySeconds++;
                if (config.Match.EmptyMatchDeletionSeconds > 0
                        && match.emptySeconds >= config.Match.EmptyMatchDeletionSeconds) {
                    Console.info("Closing empty match " + match.id + " (idle "
                            + match.emptySeconds + "s)");
                    cleanup(match);
                    return;
                }
            } else {
                match.emptySeconds = 0;
            }
        }
        switch (match.state) {
            case COUNTDOWN -> {
                if (match.totalPlayers() < minPlayers(match)) {
                    match.state = MatchState.WAITING;
                    broadcast(match, Tr.t("match.countdown_cancelled"));
                    updateHud(match);
                    return;
                }
                match.countdownRemaining--;
                if (match.countdownRemaining <= 0) {
                    startMatch(match);
                    return;
                }
                if (match.countdownRemaining <= 5) {
                    showCountTitleAll(match, String.valueOf(match.countdownRemaining),
                            Tr.t("status.countdown_sub"), "#7ae0ff");
                    playSoundAll(match, Sounds.CLICK, 0.7f, 1.4f);
                }
                if (match.countdownRemaining <= 5 || match.countdownRemaining % 5 == 0) {
                    broadcast(match, Tr.t("match.countdown", "seconds", match.countdownRemaining));
                }
                // Update the scoreboard countdown every second.
                updateHud(match);
            }
            case ACTIVE -> {
                match.secondsElapsed++;
                if (match.secondsElapsed >= config.Match.MaxDurationSeconds) {
                    handleTimeUp(match);
                    return;
                }
                voidCheck(match);
                // Fire any scheduled events whose time has arrived. The unified schedule (built at start)
                // replaces the old separate refill + loot-event timers; the while-loop covers ties/catch-up.
                while (match.nextEventIndex < match.eventSchedule.size()
                        && match.secondsElapsed >= match.eventSchedule.get(match.nextEventIndex).time) {
                    fireEvent(match, match.eventSchedule.get(match.nextEventIndex).type);
                    match.nextEventIndex++;
                }
                announceTime(match);
                // Update the scoreboard timer every second.
                updateHud(match);
            }
            case ENDING -> {
                match.countdownRemaining--;
                if (match.countdownRemaining <= 0) {
                    cleanup(match);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Void protection for SPECTATORS only. Alive players are NOT killed by a height threshold anymore —
     * falling into the void kills them naturally (the game's death fires {@code MatchDeathSystem} →
     * {@link #handleDeath}). Spectators can't die, so if one drifts below the arena we pull them back to
     * the spectator spawn instead of letting them fall forever.
     */
    private void voidCheck(Match match) {
        WorldPos spec = match.arena.spectatorSpawn;
        if (spec == null) {
            return;
        }
        double voidY = arenaVoidY(match);
        // Alive players who fall into the void die reliably here (routes through handleDeath → spectator,
        // so even the LAST player lands at the spectator spawn flying instead of falling forever).
        if (match.state == MatchState.ACTIVE) {
            for (UUID uuid : new ArrayList<>(match.alive)) {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr != null && Locations.fromTransform(pr.getTransform()).y < voidY) {
                    handleDeath(uuid, null);
                }
            }
        }
        for (UUID uuid : new ArrayList<>(match.spectators)) {
            respawnIfBelow(match, uuid, voidY, spec);
        }
        for (UUID uuid : new ArrayList<>(adminSpectatorMatch.keySet())) {
            if (match.id.equals(adminSpectatorMatch.get(uuid))) {
                respawnIfBelow(match, uuid, voidY, spec);
            }
        }
    }

    private void respawnIfBelow(Match match, UUID uuid, double voidY, WorldPos spec) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr != null && Locations.fromTransform(pr.getTransform()).y < voidY) {
            teleport(pr, match.world, Locations.centerOfBlock(spec));
            // Re-arm the full spectator state after the void return, as if they just started spectating
            // (flight/invuln/invis/creative), in case anything was lost while falling.
            applySpectatorState(match.world, uuid);
        }
    }

    /** Puts a spectator back at the spectator spawn and re-arms their god-mode (used on spectator "death"). */
    private void respawnSpectator(Match match, UUID uuid) {
        WorldPos spec = match.arena.spectatorSpawn;
        if (spec == null) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr != null) {
            teleport(pr, match.world, Locations.centerOfBlock(spec));
            applySpectatorState(match.world, uuid);
        }
    }

    /** Sends a transient fading event title to every participant. */
    private void showTitleAll(Match match, String main, String sub, String color, float seconds) {
        for (UUID uuid : allParticipants(match)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                Titles.show(pr, main, sub, color, seconds);
            }
        }
    }

    /** Plays a 2D sound to every participant of a match. */
    private void playSoundAll(Match match, String soundId, float volume, float pitch) {
        for (UUID uuid : allParticipants(match)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                Sounds.play(pr, soundId, volume, pitch);
            }
        }
    }

    /** Title with explicit short fades — for the per-second countdown numbers (no overlap between ticks). */
    private void showCountTitleAll(Match match, String main, String sub, String color) {
        for (UUID uuid : allParticipants(match)) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                Titles.show(pr, main, sub, color, 0.8f, 0.1f, 0.15f);
            }
        }
    }

    /** Flashes a fading "time remaining" title at milestones (5m/2m/1m/30s/10s) and the final 5s. */
    private void announceTime(Match match) {
        int remaining = Math.max(0, config.Match.MaxDurationSeconds - match.secondsElapsed);
        boolean milestone = remaining == 300 || remaining == 120 || remaining == 60
                || remaining == 30 || remaining == 10;
        boolean finalCount = remaining >= 1 && remaining <= 5;
        if (!milestone && !finalCount) {
            return;
        }
        String main = finalCount ? String.valueOf(remaining)
                : String.format("%d:%02d", remaining / 60, remaining % 60);
        if (finalCount) {
            showCountTitleAll(match, main, Tr.t("status.active_sub"), "#ff6666");
        } else {
            showTitleAll(match, main, Tr.t("status.active_sub"), "#ffdd55", 1.6f);
        }
        // Time-warning tick (higher pitch for the final 5s).
        playSoundAll(match, Sounds.CLICK, 0.8f, finalCount ? 1.5f : 1.0f);
    }

    /** Void threshold: 20 blocks below the lowest island spawn. */
    private double arenaVoidY(Match match) {
        double minY = Double.MAX_VALUE;
        if (match.arena.spawnPoints != null) {
            for (WorldPos p : match.arena.spawnPoints) {
                minY = Math.min(minY, p.y);
            }
        }
        return (minY == Double.MAX_VALUE ? 100 : minY) - 20;
    }

    // ---------------------------------------------------------------- entity helpers

    private void teleport(PlayerRef playerRef, World targetWorld, WorldPos pos) {
        Teleports.to(playerRef, targetWorld, pos);
    }

    private void prepareForPlay(PlayerRef playerRef, Match match) {
        Ref<EntityStore> ref = playerRef.getReference();
        World world = match.world;
        String kitId = config.Kits.Enabled
                ? match.selectedKits.getOrDefault(playerRef.getUuid(), config.Kits.DefaultKit)
                : null;
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null && kitId != null) {
                    applyKit(player, kitId);
                }
                EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
                if (stats != null) {
                    stats.maximizeStatValue(DefaultEntityStatTypes.getHealth());
                }
            } catch (Exception e) {
                Console.warning("prepareForPlay failed: " + e.getMessage());
            }
        });
    }

    private void applyKit(Player player, String kitId) {
        Kit kit = kitManager.getKit(kitId);
        Inventory inv = player.getInventory();
        inv.clear();
        if (kit == null) {
            return;
        }
        for (KitItem item : kit.items) {
            if (item == null || item.itemId == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item.itemId, Math.max(1, item.count));
            ItemContainer c = kitContainer(inv, item.container);
            if (c != null && item.slot >= 0) {
                c.setItemStackForSlot((short) item.slot, stack);
            } else {
                inv.getCombinedBackpackStorageHotbar().addItemStack(stack);
            }
        }
    }

    /** Maps a {@link KitItem} container id back to the live inventory section. */
    private ItemContainer kitContainer(Inventory inv, int container) {
        return switch (container) {
            case KitItem.STORAGE -> inv.getStorage();
            case KitItem.BACKPACK -> inv.getBackpack();
            case KitItem.ARMOR -> inv.getArmor();
            case KitItem.UTILITY -> inv.getUtility();
            case KitItem.TOOLS -> inv.getTools();
            default -> inv.getHotbar();
        };
    }

    private void clearInventory(PlayerRef playerRef, World world) {
        Ref<EntityStore> ref = playerRef.getReference();
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getInventory().clear();
                }
            } catch (Exception ignored) {
            }
        });
    }

    // ------------------------------------------------------------ spectator hotbar

    /** Clamp a configured hotbar slot into the valid 0-8 range. */
    private static int clampSlot(int slot) {
        if (slot < 0) {
            return 0;
        }
        return Math.min(slot, 8);
    }

    private static void lockSlot(ItemContainer hotbar, int slot) {
        short s = (short) slot;
        hotbar.setSlotFilter(FilterActionType.REMOVE, s, SlotFilter.DENY);
        hotbar.setSlotFilter(FilterActionType.DROP, s, SlotFilter.DENY);
        hotbar.setSlotFilter(FilterActionType.ADD, s, SlotFilter.DENY);
    }

    private static void unlockSlot(ItemContainer hotbar, int slot) {
        short s = (short) slot;
        hotbar.setSlotFilter(FilterActionType.ADD, s, SlotFilter.ALLOW);
        hotbar.setSlotFilter(FilterActionType.REMOVE, s, SlotFilter.ALLOW);
        hotbar.setSlotFilter(FilterActionType.DROP, s, SlotFilter.ALLOW);
    }

    /**
     * Gives a fresh spectator their Hypixel-style hotbar tools (living-player tracker + return-to-lobby),
     * locked into their configured slots so they can't be dropped or moved. The inventory is cleared first
     * (they just died) by the eliminate path. Runs on the match world thread.
     */
    private void giveSpectatorItems(World world, UUID uuid) {
        if (!config.Spectator.Enabled || world == null || uuid == null) {
            return;
        }
        world.execute(() -> {
            try {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = pr.getReference();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                ItemContainer hotbar = player.getInventory().getHotbar();
                if (hotbar == null) {
                    return;
                }
                int tSlot = clampSlot(config.Spectator.TrackerSlot);
                int lSlot = clampSlot(config.Spectator.LobbySlot);
                // Make sure the slots are writable, place the items, then lock them.
                unlockSlot(hotbar, tSlot);
                unlockSlot(hotbar, lSlot);
                if (config.Spectator.TrackerItem != null && !config.Spectator.TrackerItem.isBlank()) {
                    hotbar.setItemStackForSlot((short) tSlot, new ItemStack(config.Spectator.TrackerItem, 1));
                    lockSlot(hotbar, tSlot);
                }
                if (config.Spectator.LobbyItem != null && !config.Spectator.LobbyItem.isBlank() && lSlot != tSlot) {
                    hotbar.setItemStackForSlot((short) lSlot, new ItemStack(config.Spectator.LobbyItem, 1));
                    lockSlot(hotbar, lSlot);
                }
            } catch (Exception e) {
                Console.warning("giveSpectatorItems failed: " + e.getMessage());
            }
        });
    }

    /** True if the player is a (dead-player) spectator of a live match. */
    public boolean isSpectator(UUID uuid) {
        Match match = getPlayerMatch(uuid);
        return match != null && match.spectators.contains(uuid);
    }

    /** A living opponent the spectator can teleport to. */
    public record LivePlayer(UUID uuid, String name) {
    }

    /** Living players of the match the given spectator belongs to (empty if not spectating). */
    public List<LivePlayer> livePlayersFor(UUID spectatorUuid) {
        List<LivePlayer> out = new ArrayList<>();
        Match match = getPlayerMatch(spectatorUuid);
        if (match == null || !match.spectators.contains(spectatorUuid)) {
            return out;
        }
        for (UUID uuid : match.alive) {
            out.add(new LivePlayer(uuid, match.nameOf(uuid)));
        }
        return out;
    }

    /** Teleports a spectator to a living player of their match (called from the tracker modal). */
    public void teleportSpectatorTo(PlayerRef spectator, UUID targetUuid) {
        if (spectator == null || targetUuid == null) {
            return;
        }
        Match match = getPlayerMatch(spectator.getUuid());
        if (match == null || !match.spectators.contains(spectator.getUuid())) {
            return;
        }
        if (!match.alive.contains(targetUuid)) {
            spectator.sendMessage(ChatUtil.error(Tr.t("spectator.target_gone")));
            return;
        }
        PlayerRef target = Universe.get().getPlayer(targetUuid);
        if (target == null) {
            spectator.sendMessage(ChatUtil.error(Tr.t("spectator.target_gone")));
            return;
        }
        WorldPos pos = Locations.fromTransform(target.getTransform());
        if (pos == null) {
            return;
        }
        teleport(spectator, match.world, new WorldPos(pos.x, pos.y + 2, pos.z));
        spectator.sendMessage(ChatUtil.success(Tr.t("spectator.teleported", "player", match.nameOf(targetUuid))));
    }

    /**
     * Spectator hotbar CLICK handler: when a spectator LEFT- or RIGHT-clicks holding one of the spec tools
     * (tracker / return-to-lobby), fire its action. Returns true if it was a spec-item click so the caller
     * cancels the underlying block break/use/place. Called from the break/use/place interaction systems —
     * this replaces the old select-a-slot trigger (which fired on merely scrolling to the item).
     */
    public boolean trySpectatorItemClick(PlayerRef pr, Store<EntityStore> store) {
        if (pr == null || !config.Spectator.Enabled || !isSpectator(pr.getUuid())) {
            return false;
        }
        Ref<EntityStore> ref = pr.getReference();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInHand();
        if (held == null || held.isEmpty()) {
            return false;
        }
        String id = held.getItemId();
        if (id != null && (id.equals(config.Spectator.TrackerItem) || id.equals(config.Spectator.LobbyItem))) {
            handleSpectatorItem(pr, player, ref, store, id);
            return true;
        }
        return false;
    }

    /**
     * Routes a spectator's hotbar action: the tracker item opens the living-player teleport modal, the
     * lobby item returns them to the lobby.
     */
    public void handleSpectatorItem(PlayerRef pr, Player player, Ref<EntityStore> ref, Store<EntityStore> store,
            String itemId) {
        if (pr == null || itemId == null || !config.Spectator.Enabled || !isSpectator(pr.getUuid())) {
            return;
        }
        if (itemId.equals(config.Spectator.TrackerItem)) {
            // Always open the modal (it shows an empty-state label when nobody is alive) rather than
            // spamming a chat message — the spectator can still browse/close it.
            World world = Universe.get().getWorld(pr.getWorldUuid());
            dev.stoshe.aerowars.ui.SpectatorTrackerPage.open(player, ref, store, pr, world, plugin);
        } else if (itemId.equals(config.Spectator.LobbyItem)) {
            removePlayer(pr);
        }
    }

    /**
     * Puts a dead player into full spectator god-mode: {@link HiddenFromAdventurePlayers} (invisible to
     * players still fighting in Adventure), {@link Invulnerable} (immune to PvP/fall/void/all damage) and
     * creative-style flight (via {@link Player#applyMovementStates}). Cleared by {@link #clearSpectatorState}
     * when they leave the match. All best-effort and off-thread-safe (wrapped in world.execute + try/catch).
     */
    private void applySpectatorState(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return;
        }
        world.execute(() -> {
            try {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = pr.getReference();
                if (store.getComponent(ref, Invulnerable.getComponentType()) == null) {
                    store.addComponent(ref, Invulnerable.getComponentType());
                }
                if (store.getComponent(ref, HiddenFromAdventurePlayers.getComponentType()) == null) {
                    store.addComponent(ref, HiddenFromAdventurePlayers.getComponentType());
                }
                try {
                    // Grant flight AND put them actively airborne. Passing an empty MovementStates left
                    // flying=false, which overrode the saved "can fly" flag — that's why spectators
                    // couldn't fly. Both the saved (ability) and the active (current) flags must be true.
                    MovementStates active = new MovementStates();
                    active.flying = true;
                    Player.applyMovementStates(ref, new SavedMovementStates(true), active, store);
                    // Keep the player in their current (Adventure/survival) game mode — only the flight
                    // STATE is granted. We deliberately do NOT switch to Creative (the user doesn't want
                    // a creative spectator); Invulnerable already covers safety while flying.
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                Console.warning("applySpectatorState failed: " + e.getMessage());
            }
        });
    }

    /** Removes spectator god-mode (invisible/invulnerable/flight) using the player's current-world ref. */
    private void clearSpectatorState(PlayerRef playerRef) {
        if (playerRef == null || playerRef.getWorldUuid() == null) {
            return;
        }
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                store.removeComponentIfExists(ref, Invulnerable.getComponentType());
                store.removeComponentIfExists(ref, HiddenFromAdventurePlayers.getComponentType());
                try {
                    Player.applyMovementStates(ref, new SavedMovementStates(false), new MovementStates(), store);
                } catch (Exception ignored) {
                }
                // Unlock the spectator-item slots so the pre-match inventory can be restored freely.
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null && config.Spectator.Enabled) {
                    ItemContainer hotbar = player.getInventory().getHotbar();
                    if (hotbar != null) {
                        unlockSlot(hotbar, clampSlot(config.Spectator.TrackerSlot));
                        unlockSlot(hotbar, clampSlot(config.Spectator.LobbySlot));
                    }
                }
            } catch (Exception e) {
                Console.warning("clearSpectatorState failed: " + e.getMessage());
            }
        });
    }

    private void sendToLobby(PlayerRef playerRef) {
        // Strip any spectator god-mode (invisible/flight/invulnerable) BEFORE the cross-world
        // teleport, while the match-world ref is still valid, so nothing leaks into the lobby.
        clearSpectatorState(playerRef);
        // Fall back to the universe default world (and its ground spawn) when the configured
        // lobby world doesn't exist, so players are never stranded in the deleted match world.
        World lobby = Teleports.resolveLobby(config.General.LobbyWorld);
        if (lobby == null) {
            return;
        }
        WorldPos spawn = Teleports.lobbySpawn(lobby, config.General.LobbySpawn, playerRef.getUuid());
        teleport(playerRef, lobby, spawn);
        // Restore the saved inventory + game mode once they're back in the lobby. Retried until the
        // cross-world teleport settles (restore no-ops + keeps the snapshot until the player is really in
        // the lobby world) — otherwise a spectator kept the spec items and lost their original gear.
        if (config.Match.SaveInventory && inventoryVault.has(playerRef.getUuid())) {
            scheduleInventoryRestore(lobby, playerRef.getUuid(), 8);
        }
    }

    /** Retries {@link InventoryVault#restore} 1s apart until it applies (teleport settled) or attempts run out. */
    private void scheduleInventoryRestore(World world, UUID uuid, int attemptsLeft) {
        scheduler.schedule(() -> {
            boolean done = inventoryVault.restore(world, uuid);
            if (!done && attemptsLeft > 1) {
                scheduleInventoryRestore(world, uuid, attemptsLeft - 1);
            }
        }, 1, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------- cages

    /** Builds (air=false) or clears (air=true) a hollow cage around a spawn. */
    private void setCage(World world, WorldPos spawn, boolean air, int teamSize) {
        setCage(world, spawn, air, teamSize, null);
    }

    /** Vivid glass colours cycled for the "rainbow" cage cosmetic. */
    private static final String[] RAINBOW_GLASS = {
            "Glass_Block_Red", "Glass_Block_Orange", "Glass_Block_Yellow", "Glass_Block_Lime",
            "Glass_Block_Green", "Glass_Block_Cyan", "Glass_Block_Blue", "Glass_Block_Purple",
            "Glass_Block_Pink", "Glass_Block_Magenta"
    };

    private String randomGlassColor() {
        return RAINBOW_GLASS[random.nextInt(RAINBOW_GLASS.length)];
    }

    private void setCage(World world, WorldPos spawn, boolean air, int teamSize, String blockOverride) {
        if (!config.Cages.Enabled) {
            return;
        }
        int bx = spawn.blockX();
        int by = spawn.blockY();
        int bz = spawn.blockZ();
        // Grow the cage for coop/team spawns so it comfortably holds every teammate: a team of N
        // shares a spawn, so a radius of ~N gives an (2N+1)x(2N+1) floor.
        int r = Math.max(Math.max(1, config.Cages.Radius), Math.max(1, teamSize));
        int h = Math.max(2, config.Cages.Height);
        String glass = (blockOverride != null && !blockOverride.isBlank()) ? blockOverride
                : (config.Cages.BlockId == null || config.Cages.BlockId.isBlank())
                        ? "Glass_Block" : config.Cages.BlockId;
        // "rainbow" cage cosmetic: each shell block gets a random glass colour.
        boolean rainbow = "rainbow".equalsIgnoreCase(glass);
        for (int x = bx - r; x <= bx + r; x++) {
            for (int z = bz - r; z <= bz + r; z++) {
                for (int y = by - 1; y <= by + h; y++) {
                    boolean shell = x == bx - r || x == bx + r || z == bz - r || z == bz + r
                            || y == by - 1 || y == by + h;
                    if (!shell) {
                        continue;
                    }
                    try {
                        if (air) {
                            // Keep the floor beneath the player so they don't drop.
                            if (y == by - 1) {
                                continue;
                            }
                            world.setBlock(x, y, z, "Empty", 0);
                        } else {
                            world.setBlock(x, y, z, rainbow ? randomGlassColor() : glass, 0);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- misc

    private WorldPos spawnFor(Arena arena, int index) {
        if (arena.spawnPoints == null || index < 0 || index >= arena.spawnPoints.size()) {
            return null;
        }
        return arena.spawnPoints.get(index);
    }

    private void broadcast(Match match, String message) {
        broadcastExcept(match, null, message);
    }

    /** Broadcasts to every participant except {@code exceptUuid} (used so a joiner isn't told about themselves). */
    private void broadcastExcept(Match match, UUID exceptUuid, String message) {
        for (UUID uuid : allParticipants(match)) {
            if (uuid.equals(exceptUuid)) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                pr.sendMessage(ChatUtil.plain(message));
            }
        }
    }

    private List<UUID> allParticipants(Match match) {
        List<UUID> all = new ArrayList<>(match.alive);
        all.addAll(match.spectators);
        return all;
    }

    private void updateHud(Match match) {
        if (plugin.getHudManager() != null) {
            plugin.getHudManager().updateMatch(match);
        }
    }

    private void removeHud(UUID uuid) {
        if (plugin.getHudManager() != null) {
            plugin.getHudManager().remove(uuid);
        }
    }

    public List<Match> getMatches() {
        return new ArrayList<>(matches.values());
    }

    /** Admin force-start: skips the remaining countdown for a player's match. */
    /** Admin override: start the match now, even with a single player (e.g. for testing). */
    public boolean forceStart(UUID uuid) {
        Match match = getPlayerMatch(uuid);
        if (match == null || !match.state.acceptsPlayers() || match.totalPlayers() < 1) {
            return false;
        }
        startMatch(match);
        return true;
    }

    /** Selects a kit for a player, if allowed at the current phase. */
    public boolean selectKit(PlayerRef playerRef, String kitId) {
        Match match = getPlayerMatch(playerRef.getUuid());
        if (match == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.not_in_match")));
            return false;
        }
        if (match.state == MatchState.ACTIVE || match.state == MatchState.ENDING) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("kit.only_before_start")));
            return false;
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("kit.not_found", "kit", kitId)));
            return false;
        }
        if (!kitManager.isUnlocked(playerRef.getUuid(), kit)) {
            // Locked: purchasable kits tell you to buy; permission-only kits say no permission.
            if (kit.isPurchase()) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("kit.locked_buy", "cost", String.valueOf(kit.cost))));
            } else {
                playerRef.sendMessage(ChatUtil.error(Tr.t("kit.no_permission")));
            }
            return false;
        }
        match.selectedKits.put(playerRef.getUuid(), kit.id);
        playerRef.sendMessage(ChatUtil.success(Tr.t("kit.selected", "kit", kit.displayName())));
        return true;
    }

    /**
     * Buys a locked, purchasable kit (economy charge → grant ownership → select it). Free/owned/
     * permission-unlocked kits just select. Returns {@code true} when the kit ends up selected.
     */
    public boolean buyKit(PlayerRef playerRef, String kitId) {
        if (playerRef == null) {
            return false;
        }
        Match match = getPlayerMatch(playerRef.getUuid());
        if (match == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.not_in_match")));
            return false;
        }
        if (match.state == MatchState.ACTIVE || match.state == MatchState.ENDING) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("kit.only_before_start")));
            return false;
        }
        Kit kit = kitManager.getKit(kitId);
        if (kit == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("kit.not_found", "kit", kitId)));
            return false;
        }
        // Already available (free / owned / has permission) → just select it.
        if (kitManager.isUnlocked(playerRef.getUuid(), kit)) {
            return selectKit(playerRef, kitId);
        }
        // Permission-only kit that they don't hold: cannot be bought.
        if (!kit.isPurchase()) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("kit.no_permission")));
            return false;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cosmetics.msg_no_economy")));
            return false;
        }
        if (!plugin.getEconomyService().charge(playerRef.getUuid(), kit.cost)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cosmetics.msg_insufficient")));
            return false;
        }
        kitManager.grantKit(playerRef.getUuid(), kit.id);
        playerRef.sendMessage(ChatUtil.success(Tr.t("kit.bought", "kit", kit.displayName(),
                "cost", String.valueOf(kit.cost))));
        return selectKit(playerRef, kitId);
    }
}
