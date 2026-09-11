package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import dev.stoshe.aerowars.game.SpectatorMode;
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
    private final dev.stoshe.aerowars.manager.MapManager mapManager;
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
        this.mapManager = plugin.getMapManager();
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
        // Deferred cleanup of leftover ephemeral (match/setup) worlds from a previous run: give players who
        // were mid-match when the server stopped time to reconnect FIRST — they load their stale match world
        // and the connect handler reroutes them to the lobby (rewriting their saved world), so nobody hits
        // the engine's "failed to find ... reset to default". By now those worlds are empty, so sweep them.
        scheduler.schedule(worldManager::cleanupEphemeralWorlds, 90, TimeUnit.SECONDS);
        // Periodic stats flush so a crash/kill (not a clean shutdown) loses at most one interval of
        // kills/deaths instead of everything since boot. save() is a no-op unless something is dirty,
        // and it runs on this same scheduler thread as the record* calls, so there's no data race.
        int autoSave = Math.max(30, config.General.AutoSaveIntervalSeconds);
        scheduler.scheduleAtFixedRate(this::autoSaveData, autoSave, autoSave, TimeUnit.SECONDS);
        // Cosmetic movement trails run well above the 1 Hz match tick so they read as a dense trail
        // coming off the body (point-emitting particles at ~7 Hz).
        scheduler.scheduleAtFixedRate(this::tickTrails, 500, 150, TimeUnit.MILLISECONDS);
        // Arrow/projectile trails sample even faster since projectiles move quickly.
        scheduler.scheduleAtFixedRate(this::tickArrowTrails, 500, 100, TimeUnit.MILLISECONDS);
    }

    /** Periodic best-effort flush of in-memory stats to disk/SQL (no-op when nothing changed). */
    private void autoSaveData() {
        try {
            plugin.getStatsManager().save();
        } catch (Exception e) {
            Console.warning("[autosave] Failed to flush stats: " + e.getMessage());
        }
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
        // Each match clones one map from the arena's pool; its layout (spawns/chests) lives per-map.
        String template = arena.pickTemplate(random);
        dev.stoshe.aerowars.model.MapLayout layout = mapManager.getLayout(template);
        if (layout == null) {
            Console.error("Cannot create match on arena " + arena.name + ": map '" + template
                    + "' has no layout (run /aerowars setup start " + template + ").");
            return null;
        }
        World world = worldManager.createMatchWorld(id, template);
        if (world == null) {
            return null;
        }
        // Point the match world's own spawn at the spectator spawn, so the engine's death→respawn
        // (which we don't fully control) lands dead players AT the spectator area rather than the
        // world origin — a robust backstop for the death→spectator flow (see eliminate()).
        WorldPos specSpawn = layout.spectatorSpawn;
        if (specSpawn != null) {
            try {
                WorldPos c = Locations.centerOfBlock(specSpawn);
                world.getWorldConfig().setSpawnProvider(
                        new com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider(
                                new com.hypixel.hytale.math.vector.Transform(c.x, c.y, c.z)));
            } catch (Exception e) {
                Console.warning("Could not set match-world spawn to the spectator spawn: " + e.getMessage());
            }
        }
        plugin.suppressJoinMessagesFor(world.getName());
        Match match = new Match(id, arena, world, layout);
        buildTeams(match);
        matches.put(id, match);
        // Cages are built per player as they join (see addPlayer) and removed when their island
        // empties (see eliminate) — an empty island shows no cage.
        Console.info("Created match " + id + " on arena " + arena.name + " (map " + template + ")");
        return match;
    }

    private void buildTeams(Match match) {
        Arena arena = match.arena;
        int teamCount = Math.max(1, match.layout.teamCount(arena.mode(), arena.effectiveTeamSize()));
        // The physical island (spawn point) each team occupies. Normally teams take the islands in
        // order (team 0 -> island 1, team 1 -> island 2, ...). With Match.RandomizeSpawns the island
        // each team gets is shuffled per match, so players don't always start on the first spawns.
        List<Integer> spawnIndexes = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            spawnIndexes.add(arena.mode() == GameMode.TEAMS ? i * arena.effectiveTeamSize() : i);
        }
        if (config.Match.RandomizeSpawns) {
            java.util.Collections.shuffle(spawnIndexes, random);
        }
        for (int i = 0; i < teamCount; i++) {
            match.teams.add(new Team(i, spawnIndexes.get(i)));
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
        if (match == null) {
            return false;
        }
        // Can't be building an arena AND playing — auto-finish any setup session before joining a match.
        plugin.getSetupSessionManager().endActiveSession(playerRef);
        UUID uuid = playerRef.getUuid();

        // Reserve the slot atomically: checking capacity and adding the player must be one critical
        // section, or two simultaneous /join calls can both pass the check and overfill the match/team.
        Team team;
        synchronized (match) {
            if (!match.hasRoom()) {
                return false;
            }

            team = pickTeam(match, forcedTeam);
            if (team == null) {
                return false;
            }

            team.add(uuid);
            match.playerTeam.put(uuid, team);
            match.alive.add(uuid);
        }

        match.names.put(uuid, playerRef.getUsername());
        playerMatch.put(uuid, match.id);

        // Pre-select the player's remembered kit (Kits.PersistLastKit) so the kit menu shows it as chosen
        // and they start with it unless they pick another. Only when still unlocked; else the default wins.
        if (config.Kits.Enabled && config.Kits.PersistLastKit && !match.selectedKits.containsKey(uuid)) {
            String last = kitManager.getLastKit(uuid);
            Kit lastKit = last == null ? null : kitManager.getKit(last);
            if (lastKit != null && kitManager.isUnlocked(uuid, lastKit)) {
                match.selectedKits.put(uuid, last);
            }
        }

        WorldPos spawn = spawnFor(match, team.spawnIndex);
        if (spawn != null) {
            teleport(playerRef, match.world, Locations.centerOfBlock(spawn));
            // After the teleport settles (player in the match world, spawn chunk loaded):
            // save+clear inventory / switch to Adventure, and (re)build this player's cage on
            // the now-loaded chunk (the up-front build in createMatch can miss unloaded chunks).
            World matchWorld = match.world;
            WorldPos cageSpawn = spawn;
            int cageSpawnIndex = team.spawnIndex;
            scheduler.schedule(() -> {
                if (config.Match.SaveInventory) {
                    inventoryVault.enterMatch(matchWorld, uuid);
                }
                if (!match.state.isRunning()) {
                    // Which cage this player gets: their own cosmetic wins; else the arena's default;
                    // else the default cage prefab. See resolveCageValue / buildCage.
                    String cageValue = resolveCageValue(uuid, match.arena);
                    matchWorld.execute(() -> buildCage(match, matchWorld, cageSpawn, cageSpawnIndex, cageValue));
                    // Locked hotbar for the wait: kit-selector + return-to-lobby (removed at match start).
                    giveWaitingItems(matchWorld, uuid);
                }
                // Full health on entry — refill (and sync to the client) the moment they're in the match
                // world, so nobody spawns into their cage carrying damage from before.
                matchWorld.execute(() -> {
                    try {
                        PlayerRef live = Universe.get().getPlayer(uuid);
                        if (live != null) {
                            healToFull(live.getReference(), matchWorld.getEntityStore().getStore());
                        }
                    } catch (Exception ignored) {
                    }
                });
                // Attach the HUD/scoreboard only now that the player is actually in the match
                // world — attaching before the join teleport settles skips the .ui append and
                // later set-commands crash the client ("#AeroWarsRoot not found").
                updateHud(match);
            }, 1, TimeUnit.SECONDS);
        }
        // Tell the OTHER participants that this player joined; the joiner only gets their own
        // colored "you joined" line below (not the third-person broadcast about themselves).
        broadcastExcept(match, uuid, Tr.t("match.player_joined", "player", playerRef.getUsername(),
                "current", match.totalPlayers(), "max", match.maxPlayers()));
        playerRef.sendMessage(ChatUtil.success(Tr.t("match.joined",
                "current", match.totalPlayers(), "max", match.maxPlayers())));
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
                    : mapManager.maxPlayersFor(arena) >= size;
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
                    "current", match.totalPlayers(), "max", match.maxPlayers()));
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
        WorldPos spec = match.layout.spectatorSpawn;
        WorldPos target = spec != null ? spec
                : (match.layout.spawnPoints.isEmpty() ? null : match.layout.spawnPoints.get(0));
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
        // Disconnecting from inside an ephemeral match/setup world: rewrite their SAVED world to the lobby
        // now (config data is persisted on disconnect), so their next login loads the lobby instead of the
        // arena — which is deleted on boot ("failed to find aerowars_match_...").
        persistLobbyWorld(pr);
        if (config.Match.SaveInventory) {
            inventoryVault.restoreOnDisconnect(pr);
        }
        Match match = getPlayerMatch(uuid);
        if (match != null) {
            announceLeft(match, uuid);
            eliminate(match, uuid, null, true, false);
        }
    }

    /**
     * Persists the LOBBY as this player's saved world ({@link com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData#setWorld})
     * IF they're currently inside an ephemeral AeroWars match/setup world — so a disconnect or server stop
     * mid-match doesn't leave the arena as their saved world (which is deleted on boot, giving the engine's
     * "failed to find ... world has been reset to default"). Marshalled onto the world thread, like
     * {@code InventoryVault.restoreOnDisconnect}, where the config data is saved from.
     */
    private void persistLobbyWorld(PlayerRef pr) {
        if (pr == null || pr.getWorldUuid() == null) {
            // A player who aborted login has no world yet; Universe.getWorld backs onto a ConcurrentHashMap,
            // whose get(null) throws NPE — so never call it with a null world uuid.
            return;
        }
        World world = Universe.get().getWorld(pr.getWorldUuid());
        if (world == null || plugin.getWorldManager() == null
                || !plugin.getWorldManager().isAeroWarsWorld(world.getName())) {
            return;
        }
        World lobby = dev.stoshe.aerowars.util.Teleports.resolveLobby(config.General.LobbyWorld);
        if (lobby == null) {
            return;
        }
        String lobbyName = lobby.getName();
        Ref<EntityStore> ref = pr.getReference();
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null && player.getPlayerConfigData() != null) {
                    player.getPlayerConfigData().setWorld(lobbyName);
                    Console.info("Saved lobby as " + pr.getUsername() + "'s world (was in "
                            + world.getName() + ") so they don't log back into a dead arena.");
                }
            } catch (Exception ignored) {
            }
        });
    }

    // ---------------------------------------------------------------- countdown/start

    private void maybeStartCountdown(Match match) {
        boolean ready = match.state == MatchState.WAITING
                && match.totalPlayers() >= minPlayers(match)
                && populatedTeams(match) >= 2;

        if (ready) {
            match.state = MatchState.COUNTDOWN;
            match.countdownRemaining = config.Match.CountdownSeconds;
            broadcast(match, Tr.t("match.countdown_started", "seconds", match.countdownRemaining));
        }
    }

    private int minPlayers(Match match) {
        return Math.max(2, Math.min(config.Match.MinPlayers, match.maxPlayers()));
    }

    /**
     * Number of teams that currently have at least one player. A match needs ≥2 to be an actual
     * contest — without this a keep-together party that all lands on one team (or a single forced
     * cluster) would start, then idle out the whole duration because no elimination can ever occur.
     */
    private int populatedTeams(Match match) {
        int n = 0;

        for (Team team : match.teams) {
            if (!team.members().isEmpty()) {
                n++;
            }
        }

        return n;
    }

    private void startMatch(Match match) {
        match.state = MatchState.ACTIVE;
        match.secondsElapsed = 0;
        World world = match.world;
        // Drop EVERY cage so players can leave their island. Force-load each cage's chunk first
        // (only occupied islands are loaded), then clear the shell on the world thread.
        for (Team team : match.teams) {
            WorldPos spawn = spawnFor(match, team.spawnIndex);
            if (spawn == null) {
                continue;
            }
            World w = world;
            int spawnIndex = team.spawnIndex;
            long key = ChunkUtil.indexChunkFromBlock(spawn.blockX(), spawn.blockZ());
            w.getChunkAsync(key).thenRun(() -> w.execute(() -> openCage(match, w, spawnIndex)));
        }
        if (config.Loot.FillOnStart) {
            prepareChests(match, true);
            match.chestsFilled = true;
        }
        buildEventSchedule(match);
        // Count a game played only now that the match actually starts — recording it on join
        // inflated games/losses for anyone who joined and left (or a match that never started).
        // Kits + full heal for each living player.
        for (UUID uuid : new ArrayList<>(match.alive)) {
            plugin.getStatsManager().recordGame(uuid, match.names.get(uuid));
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
        java.util.List<ChestLocation> chests = match.layout.allChests();
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
            for (ChestLocation chest : match.layout.allChests()) {
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
                WorldPos spawn = spawnFor(match, team.spawnIndex);
                if (spawn != null) {
                    World w = match.world;
                    int spawnIndex = team.spawnIndex;
                    w.execute(() -> openCage(match, w, spawnIndex));
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
            WorldPos spec = match.layout.spectatorSpawn;
            clearInventory(pr, match.world);
            // Teleport to the spectator spawn with full god-mode (invisible/invulnerable) and GUARANTEED
            // flight on arrival (so they hover at the spawn instead of dropping).
            sendToSpectatorSpawn(match, uuid, pr, spec);
            // Re-assert the spectator spawn shortly after: on some deaths (notably a FALL/void death) the
            // engine's own death→respawn sequence fires just after this and drops the player at the world
            // respawn point instead of our spectator spawn. A delayed re-teleport overrides that. If the
            // first teleport already stuck, this lands on the same block (no visible effect).
            if (spec != null) {
                scheduler.schedule(() -> {
                    if (match.spectators.contains(uuid)) {
                        PlayerRef again = Universe.get().getPlayer(uuid);
                        if (again != null) {
                            sendToSpectatorSpawn(match, uuid, again, spec);
                        }
                    }
                }, 600, TimeUnit.MILLISECONDS);
            }
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
        // Remembered so the celebration fireworks keep launching every tick until the room closes.
        match.endWinner = winner;

        if (winner != null) {
            for (UUID uuid : winner.members()) {
                plugin.getStatsManager().recordWin(uuid, match.names.get(uuid));
                awardEconomy(uuid, config.Rewards.Economy.WinAmount, "economy.reward_win");
            }
        }

        // Persist the match's kills/deaths/wins immediately so a crash before the next auto-save
        // or clean shutdown doesn't drop this game's results (leaderboards stay accurate).
        autoSaveData();

        if (winner == null) {
            match.resultText = Tr.t("status.draw");
            broadcast(match, Tr.t("match.draw"));
        } else if (match.arena.mode() == GameMode.TEAMS) {
            match.resultText = Tr.t("status.win_team", "team", winner.name);
            broadcast(match, Tr.t("match.win_team", "team", winner.name));
            announceWinner(match, Tr.t("match.win_announce_team", "team", winner.name,
                    "arena", match.arena.displayName()));
        } else {
            UUID winnerUuid = winner.members().stream().findFirst().orElse(null);
            String name = winnerUuid != null ? match.nameOf(winnerUuid) : winner.name;
            match.resultText = Tr.t("status.win_solo", "player", name);
            broadcast(match, Tr.t("match.win_solo", "player", name));
            announceWinner(match, Tr.t("match.win_announce_solo", "player", name,
                    "arena", match.arena.displayName()));
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
                    Ref<EntityStore> ref = pr.getReference();
                    if (SpectatorMode.isIn(ref, store)) {
                        // The native mode hides spectators from everyone still playing. Leaving it reveals them for
                        // the celebration but also drops flight and invulnerability, so re-apply those by hand
                        // (the per-tick re-assert keeps them flying from here on).
                        SpectatorMode.exit(ref, store);
                        if (store.getComponent(ref, Invulnerable.getComponentType()) == null) {
                            store.addComponent(ref, Invulnerable.getComponentType());
                        }
                        grantFlight(pr, ref, store, true);
                    }
                    // Match over — reveal the spectators to everyone again (undo per-viewer hiding).
                    clearSpectatorVisibility(uuid);
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
        List<WorldPos> spawns = match.layout.spawnPoints;
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

    /**
     * Tells everyone on the server who isn't in this match who won it ({@code Match.AnnounceWinner}). The match's
     * own players already got the in-match line, so they're skipped.
     */
    private void announceWinner(Match match, String message) {
        if (!config.Match.AnnounceWinner) {
            return;
        }
        java.util.Set<UUID> inMatch = new java.util.HashSet<>(allParticipants(match));
        var line = ChatUtil.plain(message);
        for (PlayerRef pr : Universe.get().getPlayers()) {
            if (pr != null && !inMatch.contains(pr.getUuid())) {
                pr.sendMessage(line);
            }
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
        try {
            reassertSpectatorFlight();
        } catch (Exception ignored) {
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
                if (match.totalPlayers() < minPlayers(match) || populatedTeams(match) < 2) {
                    match.state = MatchState.WAITING;
                    broadcast(match, Tr.t("match.countdown_cancelled"));
                    updateHud(match);
                    return;
                }

                // Early start: once the arena is full there's no reason to wait out the full timer —
                // shorten the remaining countdown so a packed lobby gets going quickly.
                if (match.totalPlayers() >= match.maxPlayers() && match.countdownRemaining > 5) {
                    match.countdownRemaining = 5;
                    broadcast(match, Tr.t("match.countdown_shortened", "seconds", match.countdownRemaining));
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
                // Keep the celebration fireworks going for the WHOLE end window (until the room closes),
                // however long EndCelebrationSeconds is — not just a single burst at the end.
                if (match.endWinner != null) {
                    launchFireworks(match, match.endWinner);
                }
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
        WorldPos spec = match.layout.spectatorSpawn;
        if (spec == null) {
            return;
        }
        double voidY = arenaVoidY(match);
        // Alive players who fall into the void die reliably here (routes through handleDeath → spectator,
        // so even the LAST player lands at the spectator spawn flying instead of falling forever).
        if (match.state == MatchState.ACTIVE) {
            for (UUID uuid : new ArrayList<>(match.alive)) {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (isBelowVoid(pr, voidY)) {
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

    /**
     * True when the player is loaded and currently below the void plane. A null transform (e.g. a
     * cross-world teleport in flight) counts as "not below" so the 1&nbsp;Hz tick never NPEs.
     */
    private boolean isBelowVoid(PlayerRef pr, double voidY) {
        if (pr == null) {
            return false;
        }
        var t = pr.getTransform();
        return t != null && Locations.fromTransform(t).y < voidY;
    }

    private void respawnIfBelow(Match match, UUID uuid, double voidY, WorldPos spec) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (isBelowVoid(pr, voidY)) {
            // Re-arm the full spectator state after the void return (flight/invuln/invis), guaranteeing
            // flight on arrival so they don't immediately fall back into the void.
            sendToSpectatorSpawn(match, uuid, pr, spec);
        }
    }

    /** Puts a spectator back at the spectator spawn and re-arms their god-mode (used on spectator "death"). */
    private void respawnSpectator(Match match, UUID uuid) {
        WorldPos spec = match.layout.spectatorSpawn;
        if (spec == null) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr != null) {
            sendToSpectatorSpawn(match, uuid, pr, spec);
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
        if (match.layout.spawnPoints != null) {
            for (WorldPos p : match.layout.spawnPoints) {
                minY = Math.min(minY, p.y);
            }
        }
        return (minY == Double.MAX_VALUE ? 100 : minY) - 20;
    }

    // ---------------------------------------------------------------- entity helpers

    private void teleport(PlayerRef playerRef, World targetWorld, WorldPos pos) {
        Teleports.to(playerRef, targetWorld, pos);
    }

    /**
     * The kit a player starts with, by precedence: the kit they selected for THIS match; else (when
     * {@code Kits.PersistLastKit}) their last-used kit if it still exists and is unlocked; else the global
     * {@code Kits.DefaultKit}. A stale/locked remembered kit safely falls through to the default.
     */
    private String resolveKit(Match match, UUID uuid) {
        String selected = match.selectedKits.get(uuid);
        if (selected != null && kitManager.getKit(selected) != null) {
            return selected;
        }
        if (config.Kits.PersistLastKit) {
            String last = kitManager.getLastKit(uuid);
            Kit lastKit = last == null ? null : kitManager.getKit(last);
            if (lastKit != null && kitManager.isUnlocked(uuid, lastKit)) {
                return last;
            }
        }
        return config.Kits.DefaultKit;
    }

    /**
     * The kit id currently in effect for a player, for display in the kit picker: their pick for THIS match
     * if any, else (when {@code Kits.PersistLastKit}) their remembered unlocked kit, else {@code DefaultKit}.
     * Works whether or not the player is currently in a match.
     */
    public String selectedKitId(UUID uuid) {
        Match match = uuid == null ? null : getPlayerMatch(uuid);
        if (match != null) {
            return resolveKit(match, uuid);
        }
        if (config.Kits.PersistLastKit && uuid != null) {
            String last = kitManager.getLastKit(uuid);
            Kit lastKit = last == null ? null : kitManager.getKit(last);
            if (lastKit != null && kitManager.isUnlocked(uuid, lastKit)) {
                return last;
            }
        }
        return config.Kits.DefaultKit;
    }

    private void prepareForPlay(PlayerRef playerRef, Match match) {
        Ref<EntityStore> ref = playerRef.getReference();
        World world = match.world;
        String kitId = config.Kits.Enabled ? resolveKit(match, playerRef.getUuid()) : null;
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    // Free the locked waiting-item slots first so applyKit's clear() can empty them and the
                    // kit fills slot 0 cleanly; then hand out the kit.
                    unlockWaitingSlots(player);
                    if (kitId != null) {
                        applyKit(player, kitId);
                    }
                }
                healToFull(ref, store);
            } catch (Exception e) {
                Console.warning("prepareForPlay failed: " + e.getMessage());
            }
        });
    }

    /**
     * Sets a player's health back to full and SYNCS it to the client. Uses the {@code Predictable.ALL}
     * variant of {@code maximizeStatValue} — the plain overload only updated the server value, so the client
     * kept showing the old (partial) health bar.
     */
    private void healToFull(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.maximizeStatValue(EntityStatMap.Predictable.ALL, DefaultEntityStatTypes.getHealth());
            }
        } catch (Exception ignored) {
        }
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
            } else if (c != null && item.container != KitItem.HOTBAR) {
                // Armor/utility/tools/etc. with no explicit slot: place it into its OWN section so armor
                // auto-equips to the matching body slot, instead of dumping it loose into the backpack.
                c.addItemStack(stack);
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

    // ------------------------------------------------------------ waiting-phase hotbar

    /**
     * Gives a waiting player (in their cage, before the match starts) their locked hotbar tools: a
     * kit-selector item and a return-to-lobby item, both locked in place so they can't be dropped or
     * moved. Mirrors {@link #giveSpectatorItems}. Cleared when the match starts (see prepareForPlay).
     */
    private void giveWaitingItems(World world, UUID uuid) {
        if (!config.Kits.WaitingItems || world == null || uuid == null) {
            return;
        }
        world.execute(() -> {
            try {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(pr.getReference(), Player.getComponentType());
                if (player == null) {
                    return;
                }
                ItemContainer hotbar = player.getInventory().getHotbar();
                if (hotbar == null) {
                    return;
                }
                int sSlot = clampSlot(config.Kits.SelectorSlot);
                int lSlot = clampSlot(config.Kits.LobbySlot);
                unlockSlot(hotbar, sSlot);
                unlockSlot(hotbar, lSlot);
                if (config.Kits.Enabled && config.Kits.SelectorItem != null && !config.Kits.SelectorItem.isBlank()) {
                    hotbar.setItemStackForSlot((short) sSlot, new ItemStack(config.Kits.SelectorItem, 1));
                    lockSlot(hotbar, sSlot);
                }
                if (config.Kits.LobbyItem != null && !config.Kits.LobbyItem.isBlank() && lSlot != sSlot) {
                    hotbar.setItemStackForSlot((short) lSlot, new ItemStack(config.Kits.LobbyItem, 1));
                    lockSlot(hotbar, lSlot);
                }
            } catch (Exception e) {
                Console.warning("giveWaitingItems failed: " + e.getMessage());
            }
        });
    }

    /** Unlocks the waiting-item slots so {@link #applyKit} can clear/rebuild the hotbar at match start. */
    private void unlockWaitingSlots(Player player) {
        if (!config.Kits.WaitingItems || player == null) {
            return;
        }
        try {
            ItemContainer hotbar = player.getInventory().getHotbar();
            if (hotbar != null) {
                unlockSlot(hotbar, clampSlot(config.Kits.SelectorSlot));
                unlockSlot(hotbar, clampSlot(config.Kits.LobbySlot));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Routes the WAITING-phase hotbar tools via the native interaction watcher
     * ({@link dev.stoshe.aerowars.interaction.InteractionService}): the kit-selector item opens the kit
     * menu, the return-to-lobby item leaves the match. Spectator tools keep their own handler — this only
     * fires for a living, pre-match player, so the shared lobby item never double-handles.
     */
    public void onInteraction(dev.stoshe.aerowars.interaction.PlayerInteractionEvent event) {
        if (event == null || !event.isClickOrUse()) {
            return;
        }
        String itemId = event.itemInHandId();
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        UUID uuid = event.player();

        // Setup wand — both clicks come through here now that the item maps Primary AND Secondary to the
        // discrete Stick_Attack interaction (a plain stick's right-click is a non-discrete block the watcher
        // can't see). A wand click has no hit point, so we act at the ADMIN's own position: LEFT marks the
        // current step here (works in open air), RIGHT removes the spawn in front.
        var setup = plugin.getSetupSessionManager();
        if (setup != null && setup.hasSession(uuid) && (event.isLeftClick() || event.isRightClick())) {
            String wand = setup.getSpawnWand();
            if (wand != null && wand.equals(itemId)) {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr != null) {
                    if (event.isLeftClick()) {
                        setup.handleWandAirClick(pr);
                    } else {
                        setup.handleWandRemoveClick(pr);
                    }
                }
                return;
            }
        }

        // Spectator hotbar tools (tracker / return-to-lobby) via the discrete interaction watcher: the items
        // are now weapons mapping BOTH clicks to Stick_Attack, so left- OR right-click (block or open air)
        // reaches us here — the same discrete path the setup wand uses. The shared debounce in
        // handleSpectatorItem dedupes this against the raw-mouse/ECS fallback paths.
        if (isSpectator(uuid)
                && (itemId.equals(config.Spectator.TrackerItem) || itemId.equals(config.Spectator.LobbyItem))) {
            final String specItem = itemId;
            PlayerRef pr = Universe.get().getPlayer(uuid);
            World w = pr == null ? null : Universe.get().getWorld(pr.getWorldUuid());
            if (w != null) {
                w.execute(() -> {
                    try {
                        PlayerRef live = Universe.get().getPlayer(uuid);
                        if (live == null) {
                            return;
                        }
                        Store<EntityStore> store = w.getEntityStore().getStore();
                        Ref<EntityStore> ref = live.getReference();
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            handleSpectatorItem(live, player, ref, store, specItem);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
            return;
        }

        // Waiting-phase hotbar tools (kit selector / return-to-lobby) via the discrete watcher. The raw-mouse
        // and block-ECS fallback paths cover a non-discrete configured item; the shared debounce dedupes.
        if (itemId.equals(config.Kits.SelectorItem) || itemId.equals(config.Kits.LobbyItem)) {
            final String waitItem = itemId;
            PlayerRef pr = Universe.get().getPlayer(uuid);
            World w = pr == null ? null : Universe.get().getWorld(pr.getWorldUuid());
            if (w != null) {
                w.execute(() -> {
                    try {
                        PlayerRef live = Universe.get().getPlayer(uuid);
                        if (live == null) {
                            return;
                        }
                        Store<EntityStore> store = w.getEntityStore().getStore();
                        Ref<EntityStore> ref = live.getReference();
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            handleWaitingItem(live, player, ref, store, waitItem);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    /** True if the player is a (dead-player) spectator of a live match. */
    public boolean isSpectator(UUID uuid) {
        Match match = getPlayerMatch(uuid);
        return match != null && match.spectators.contains(uuid);
    }

    /**
     * True for ANY spectator — a dead-player spectator (in a match's spectator set) OR a hidden admin
     * spectator (who has no {@code playerMatch} entry, so {@link #isSpectator} alone misses them). Used to
     * block all world interaction (break/use/place/pickup) for anyone spectating.
     */
    public boolean isAnySpectator(UUID uuid) {
        return isSpectator(uuid) || isAdminSpectator(uuid);
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
        // A teleport can drop flight; re-grant it once the move settles so the spectator keeps hovering.
        UUID specUuid = spectator.getUuid();
        scheduler.schedule(() -> applyFlight(match.world, specUuid), 300, TimeUnit.MILLISECONDS);
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
        String id = held == null ? null : held.getItemId();
        // TEMP diagnostic: shows what a spectator is actually holding when they click, vs the configured
        // tracker/lobby ids — pinpoints why the spectator hotbar might not fire. Remove once verified.
        System.out.println("[spec] click held='" + id + "' tracker='" + config.Spectator.TrackerItem
                + "' lobby='" + config.Spectator.LobbyItem + "'");
        if (held == null || held.isEmpty()) {
            return false;
        }
        if (id != null && (id.equals(config.Spectator.TrackerItem) || id.equals(config.Spectator.LobbyItem))) {
            handleSpectatorItem(pr, player, ref, store, id);
            return true;
        }
        return false;
    }

    private static final long HOTBAR_ACTION_DEBOUNCE_MS = 300L;
    private final Map<UUID, Long> lastHotbarAction = new ConcurrentHashMap<>();

    /**
     * Returns true (and records "now") if enough time has passed since this player's last hotbar action.
     * Several triggers can fire for one physical click — the discrete interaction watcher, the raw
     * {@code PlayerMouseButtonEvent}, and the block ECS systems — so both the spectator and waiting-phase
     * handlers gate on this to run the action exactly once (Pressed can also arrive in quick pairs).
     */
    private boolean debounceHotbar(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastHotbarAction.get(uuid);
        if (last != null && now - last < HOTBAR_ACTION_DEBOUNCE_MS) {
            return false;
        }
        lastHotbarAction.put(uuid, now);
        return true;
    }

    /**
     * Spectator hotbar via the raw {@code PlayerMouseButtonEvent} — unlike the block break/use/place events,
     * this fires even when clicking OPEN AIR, so the tracker/lobby tools work no matter where the spectator
     * aims. Marshalled onto the world thread. One of several triggers (discrete watcher + this + block ECS);
     * the shared {@link #debounceHotbar} gate makes the action run once per physical click.
     */
    public void handleSpectatorMouse(PlayerRef pr) {
        // TEMP diagnostic: for anyone in a match, shows the raw mouse path reached us and whether they read as
        // a spectator — distinguishes "no mouse event" from "not detected as spectator". Remove once verified.
        if (pr != null && getPlayerMatch(pr.getUuid()) != null) {
            System.out.println("[spec] mouse path: player=" + pr.getUsername() + " isSpectator="
                    + isSpectator(pr.getUuid()) + " specEnabled=" + config.Spectator.Enabled);
        }
        if (pr == null || !config.Spectator.Enabled || !isSpectator(pr.getUuid())) {
            return;
        }
        World world = Universe.get().getWorld(pr.getWorldUuid());
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                trySpectatorItemClick(pr, world.getEntityStore().getStore());
            } catch (Exception ignored) {
            }
        });
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
        // Run once per physical click even when several triggers (watcher + mouse + ECS) fire together.
        if (!debounceHotbar(pr.getUuid())) {
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
     * Waiting-phase hotbar CLICK handler (mirror of {@link #trySpectatorItemClick} for pre-match players):
     * fires the kit-selector / return-to-lobby action when a living, pre-match player clicks holding one of
     * those items. Returns true so the caller cancels the underlying block break/use/place. This is the
     * FALLBACK for a non-discrete configured item; a discrete (weapon-style) item also reaches the same
     * action through the interaction watcher, deduped by {@link #debounceHotbar}.
     */
    public boolean tryWaitingItemClick(PlayerRef pr, Store<EntityStore> store) {
        if (pr == null) {
            return false;
        }
        UUID uuid = pr.getUuid();
        Match match = getPlayerMatch(uuid);
        if (match == null || match.state.isRunning() || match.spectators.contains(uuid)) {
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
        if (id == null || (!id.equals(config.Kits.SelectorItem) && !id.equals(config.Kits.LobbyItem))) {
            return false;
        }
        handleWaitingItem(pr, player, ref, store, id);
        return true;
    }

    /**
     * Waiting-phase hotbar via the raw {@code PlayerMouseButtonEvent} — fires the kit-selector /
     * return-to-lobby action on an OPEN-AIR click (block-aimed clicks come through the ECS systems). Mirror
     * of {@link #handleSpectatorMouse}; the shared {@link #debounceHotbar} gate dedupes against the discrete
     * watcher path.
     */
    public void handleWaitingMouse(PlayerRef pr) {
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        Match match = getPlayerMatch(uuid);
        if (match == null || match.state.isRunning() || match.spectators.contains(uuid)) {
            return;
        }
        World world = Universe.get().getWorld(pr.getWorldUuid());
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                tryWaitingItemClick(pr, world.getEntityStore().getStore());
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Routes a waiting player's hotbar action: the kit-selector item opens the kit menu, the return-to-lobby
     * item leaves the match. Shared by the discrete interaction watcher and the raw-mouse / block-ECS
     * fallback paths, gated by {@link #debounceHotbar} so one physical click acts once. No-ops unless the
     * player is a living, pre-match (non-spectator) member of a match holding the matching item.
     */
    public void handleWaitingItem(PlayerRef pr, Player player, Ref<EntityStore> ref, Store<EntityStore> store,
            String itemId) {
        if (pr == null || player == null || itemId == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        Match match = getPlayerMatch(uuid);
        if (match == null || match.state.isRunning() || match.spectators.contains(uuid)) {
            return;
        }
        boolean selector = itemId.equals(config.Kits.SelectorItem);
        boolean lobby = itemId.equals(config.Kits.LobbyItem);
        if (!selector && !lobby) {
            return;
        }
        if (!debounceHotbar(uuid)) {
            return;
        }
        if (lobby) {
            removePlayer(pr);
            return;
        }
        if (config.Kits.Enabled) {
            World world = match.world;
            if (world != null) {
                dev.stoshe.aerowars.ui.KitSelectPage.open(player, ref, store, pr, world, plugin);
            }
        }
    }

    /**
     * Hides a spectator from the LIVING players of their match (per-viewer, via each living player's
     * {@link com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager}) while leaving them
     * visible to everyone else — so spectators see each other but not to players still fighting. Also makes
     * the spectator see everyone again (unhiding anyone they'd hidden while alive). Idempotent.
     */
    private void applySpectatorVisibility(UUID specUuid) {
        Match match = specUuid == null ? null : getPlayerMatch(specUuid);
        if (match == null && specUuid != null) {
            // Hidden admin spectators have no playerMatch entry — without this they were never hidden at all.
            String adminMatchId = adminSpectatorMatch.get(specUuid);
            match = adminMatchId == null ? null : matches.get(adminMatchId);
        }
        if (match == null) {
            return;
        }
        try {
            for (UUID living : match.alive) {
                PlayerRef lr = Universe.get().getPlayer(living);
                if (lr != null && lr.getHiddenPlayersManager() != null) {
                    lr.getHiddenPlayersManager().hidePlayer(specUuid);
                }
            }
            PlayerRef pr = Universe.get().getPlayer(specUuid);
            if (pr != null && pr.getHiddenPlayersManager() != null) {
                for (UUID other : allParticipants(match)) {
                    if (!other.equals(specUuid)) {
                        pr.getHiddenPlayersManager().showPlayer(other);
                    }
                }
            }
        } catch (Exception e) {
            Console.warning("applySpectatorVisibility failed: " + e.getMessage());
        }
    }

    /**
     * Undoes {@link #applySpectatorVisibility}: every match participant can see {@code uuid} again, and
     * {@code uuid} sees every participant again — called when a spectator leaves / the match ends so no
     * hidden state leaks into the lobby.
     */
    private void clearSpectatorVisibility(UUID uuid) {
        if (uuid == null) {
            return;
        }
        try {
            // Unhide this player for EVERYONE online (they're leaving the match / it ended → visible again),
            // and clear this player's own hidden list. Match-independent, so it's safe whatever order the
            // leave/teardown runs in.
            PlayerRef self = Universe.get().getPlayer(uuid);
            for (PlayerRef other : Universe.get().getPlayers()) {
                if (other == null || other.getUuid().equals(uuid)) {
                    continue;
                }
                if (other.getHiddenPlayersManager() != null) {
                    other.getHiddenPlayersManager().showPlayer(uuid);
                }
                if (self != null && self.getHiddenPlayersManager() != null) {
                    self.getHiddenPlayersManager().showPlayer(other.getUuid());
                }
            }
        } catch (Exception e) {
            Console.warning("clearSpectatorVisibility failed: " + e.getMessage());
        }
    }

    /**
     * Puts a dead player (or hidden admin) into spectator god-mode. Preferably Hytale's native spectator
     * ({@link SpectatorMode}, when {@code Spectator.NativeMode} is on): the engine then enforces flight, no-clip,
     * invulnerability and hiding from living players by itself. Otherwise — or if the mode can't be entered —
     * the hand-rolled equivalent: hidden from living players only (see {@link #applySpectatorVisibility}),
     * {@link Invulnerable} and creative-style flight. Cleared by {@link #clearSpectatorState} when they leave
     * the match. All best-effort and off-thread-safe (wrapped in world.execute + try/catch).
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
                if (config.Spectator.NativeMode && SpectatorMode.enter(ref, store)) {
                    return;
                }
                if (store.getComponent(ref, Invulnerable.getComponentType()) == null) {
                    store.addComponent(ref, Invulnerable.getComponentType());
                }
                // Hide this spectator from LIVING players only (per-viewer, via each viewer's
                // HiddenPlayersManager) instead of the blanket HiddenFromAdventurePlayers — so spectators
                // still SEE each other while staying invisible to players still in the fight. Keeps them in
                // their normal game mode (just flight + invulnerability, no creative tools).
                applySpectatorVisibility(uuid);
                // Grant REAL flight (FlyMode.Allowed on the MovementManager, like Creative) while keeping the
                // player's current game mode — applyMovementStates alone does NOT enable flight in Adventure.
                grantFlight(pr, ref, store, true);
            } catch (Exception e) {
                Console.warning("applySpectatorState failed: " + e.getMessage());
            }
        });
    }

    /**
     * Teleports a spectator to the spectator spawn and GUARANTEES they arrive already flying. The teleport
     * applies on the next world tick and the arrival/landing can clear the active {@code flying} flag, so
     * flight is re-asserted a few ticks after the move settles — a spectator must never fall at the spawn.
     */
    private void sendToSpectatorSpawn(Match match, UUID uuid, PlayerRef pr, WorldPos spec) {
        if (match == null || pr == null) {
            return;
        }
        if (spec != null) {
            teleport(pr, match.world, Locations.centerOfBlock(spec));
        }
        applySpectatorState(match.world, uuid);
        scheduler.schedule(() -> applyFlight(match.world, uuid), 350, TimeUnit.MILLISECONDS);
    }

    /** (Re)applies creative-style flight (fly ability + active flying) to a player, keeping their mode. */
    private void applyFlight(World world, UUID uuid) {
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
                grantFlight(pr, pr.getReference(), store, true);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Grants or revokes REAL flight independent of game mode by setting {@code MovementSettings.fly} to
     * {@link FlyMode#Allowed} on the player's {@link MovementManager} — the exact field the engine sets when
     * switching to Creative — and the active flying state. {@code Player.applyMovementStates} alone only sends
     * the flag and does NOT enable flight in Adventure, which is why spectators couldn't actually fly. Revoke
     * restores the game-mode defaults (Adventure → Disabled) so flight never leaks back to the lobby.
     */
    private void grantFlight(PlayerRef pr, Ref<EntityStore> ref, Store<EntityStore> store, boolean enable) {
        if (pr == null || ref == null || store == null) {
            return;
        }
        if (enable && SpectatorMode.isIn(ref, store)) {
            return; // the native spectator mode owns movement (forced flight, no-clip) — don't fight it
        }
        try {
            MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
            if (mm != null) {
                if (enable) {
                    mm.getSettings().fly = FlyMode.Allowed;
                    mm.getDefaultSettings().fly = FlyMode.Allowed;
                    mm.update(pr.getPacketHandler());
                } else {
                    mm.resetDefaultsAndUpdate(ref, store);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            MovementStates active = new MovementStates();
            active.flying = enable;
            Player.applyMovementStates(ref, new SavedMovementStates(enable), active, store);
        } catch (Exception ignored) {
        }
    }

    /**
     * Once per match tick, re-asserts spectator state that got lost — the engine's death→respawn, a tracker
     * teleport, or a state re-sync can clear it, which is why spectators were "losing" flight mid-spectate.
     * While the match is live, a native spectator who dropped out of the mode is put back in. Otherwise (legacy
     * mode, or the end celebration, where spectators are deliberately revealed) flight is re-granted only when
     * it's actually off, so it never spams movement packets or causes a hover flicker.
     */
    private void reassertSpectatorFlight() {
        for (Match match : new ArrayList<>(matches.values())) {
            if (match.world == null || match.spectators.isEmpty()) {
                continue;
            }
            boolean live = match.state == MatchState.ACTIVE;
            for (UUID uuid : new ArrayList<>(match.spectators)) {
                reassertFlightIfLost(match.world, uuid, live);
            }
        }
        for (Map.Entry<UUID, String> e : new ArrayList<>(adminSpectatorMatch.entrySet())) {
            PlayerRef pr = Universe.get().getPlayer(e.getKey());
            if (pr != null) {
                Match match = matches.get(e.getValue());
                reassertFlightIfLost(Universe.get().getWorld(pr.getWorldUuid()), e.getKey(),
                        match != null && match.state == MatchState.ACTIVE);
            }
        }
    }

    /**
     * Re-asserts one spectator's state: back into the native spectator mode if the match is {@code live} and
     * they fell out of it, else re-grants flight only if their MovementManager currently shows
     * {@code fly=Disabled}.
     */
    private void reassertFlightIfLost(World world, UUID uuid, boolean live) {
        if (world == null || uuid == null) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (SpectatorMode.isIn(ref, store)
                        || (live && config.Spectator.NativeMode && SpectatorMode.enter(ref, store))) {
                    return;
                }
                MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
                if (mm != null && mm.getSettings() != null && mm.getSettings().fly == FlyMode.Disabled) {
                    grantFlight(pr, ref, store, true);
                }
            } catch (Exception ignored) {
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
                // Leave the native spectator mode first: exiting undoes its flight, no-clip, invulnerability and
                // hiding. The hand-rolled cleanup below is then a no-op for a native spectator.
                SpectatorMode.exit(ref, store);
                store.removeComponentIfExists(ref, Invulnerable.getComponentType());
                // Reveal them to everyone again (undo the per-viewer spectator hiding).
                clearSpectatorVisibility(playerRef.getUuid());
                // Revoke flight (restore game-mode defaults) so nothing leaks into the lobby.
                grantFlight(playerRef, ref, store, false);
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

    /**
     * Resolves which cage value to build for a player, by precedence: the player's own selected cage
     * cosmetic first, then the arena's {@link Arena#defaultCage}, then {@code null} (the build falls
     * back to the default cage prefab). Blanks are skipped at each level. The value is a cage-prefab
     * reference — {@code "prefab:<name>"} or a bare {@code "<name>"} (see {@link CageManager}).
     */
    private String resolveCageValue(UUID uuid, Arena arena) {
        String cosmetic = plugin.getCosmeticsManager()
                .selectedValue(uuid, dev.stoshe.aerowars.model.CosmeticCategory.CAGE);
        if (cosmetic != null && !cosmetic.isBlank()) {
            return cosmetic;
        }
        if (arena != null && arena.defaultCage != null && !arena.defaultCage.isBlank()) {
            return arena.defaultCage;
        }
        return null;
    }

    private static String blockKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static int[] parseKey(String key) {
        int a = key.indexOf(','), b = key.indexOf(',', a + 1);
        return new int[]{
                Integer.parseInt(key.substring(0, a)),
                Integer.parseInt(key.substring(a + 1, b)),
                Integer.parseInt(key.substring(b + 1))
        };
    }

    /**
     * Pastes a cage around a spawn using the engine's native prefab placement — the SAME path as the 3D
     * preview — so block ids, per-block rotations and the anchor are reproduced exactly (the old hand-rolled
     * paste dropped rotations and mishandled a zero anchor). Every position the cage occupies is snapshotted
     * under this spawn index first (anchor lands on the spawn block: {@code world = spawn + (local - anchor)})
     * so {@link #openCage} restores the arena exactly. Optional whole-cage rotation from the cage cosmetic.
     */
    private void buildCage(Match match, World world, WorldPos spawn, int spawnIndex, String cageValue) {
        if (!config.Cages.Enabled || match == null || spawn == null) {
            return;
        }
        dev.stoshe.aerowars.manager.CageManager cages = plugin.getCageManager();
        // Resolve the cage name, falling back to the default cage when the referenced one is missing.
        String name = cages.exists(cageValue)
                ? dev.stoshe.aerowars.manager.CageManager.normalize(cageValue)
                : dev.stoshe.aerowars.manager.CageManager.DEFAULT_CAGE;
        com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection sel;
        try {
            sel = com.hypixel.hytale.server.core.prefab.PrefabStore.get().getPrefab(cages.pathFor(name));
        } catch (Exception e) {
            Console.warning("Cage build: could not load prefab '" + name + "': " + e.getMessage());
            return;
        }
        if (sel == null || sel.getBlockCount() == 0) {
            return;
        }
        // Rotation = the cage cosmetic's base 'rotation' (calibrates which way the front points at 0°) plus,
        // when its 'faceMiddle' is on, the turn needed to point that front at the arena centre. Native rotate,
        // so it matches the 3D preview exactly (positions + facings).
        int quarters = cageRotationQuarters(name);
        if (cageFaceMiddle(name)) {
            quarters = (quarters + faceMiddleQuarters(match, spawn)) % 4;
        }
        if (quarters != 0) {
            try {
                sel = sel.rotate(com.hypixel.hytale.math.Axis.Y, quarters);
            } catch (Exception ignored) {
            }
        }
        final int bx = spawn.blockX();
        final int by = spawn.blockY();
        final int bz = spawn.blockZ();
        final int ax = sel.getAnchorX();
        final int ay = sel.getAnchorY();
        final int az = sel.getAnchorZ();
        var snaps = match.cageSnapshots.computeIfAbsent(spawnIndex,
                k -> new java.util.concurrent.ConcurrentHashMap<>());
        // Snapshot originals BEFORE placing (same coordinate mapping the native place() uses).
        sel.forEachBlock((lx, ly, lz, holder) -> {
            int wx = bx + (lx - ax);
            int wy = by + (ly - ay);
            int wz = bz + (lz - az);
            String key = blockKey(wx, wy, wz);
            if (!snaps.containsKey(key)) {
                String origId = "Empty";
                try {
                    var bt = world.getBlockType(wx, wy, wz);
                    if (bt != null && bt.getId() != null) {
                        origId = bt.getId();
                    }
                } catch (Exception ignored) {
                }
                // Cages overwrite ground/air, whose rotation is irrelevant — restore with 0.
                snaps.put(key, new dev.stoshe.aerowars.model.BlockSnapshot(origId, 0));
            }
        });
        // Native paste: anchor aligned to the spawn block, ids + rotations reproduced by the engine.
        try {
            sel.place((com.hypixel.hytale.server.core.command.system.CommandSender) null, world,
                    new org.joml.Vector3i(bx, by, bz),
                    (com.hypixel.hytale.server.core.prefab.selection.mask.BlockMask) null);
            // The anchor cell IS the player's spawn point, so keep it clear — a prefab that captured a block
            // there (the spot the author stood on) would otherwise spawn the player inside it.
            world.setBlock(bx, by, bz, "Empty", 0);
        } catch (Exception e) {
            Console.warning("Cage build: native place failed for '" + name + "': " + e.getMessage());
        }
    }

    /**
     * The quarter-turns needed to point a cage's front toward the arena centre from {@code spawn} — the centre
     * being the middle-chests' average (else the spawns' average). Cardinal-quantised (0=−Z, 1=+X, 2=+Z,
     * 3=−X, clockwise), matching {@link #cageRotationQuarters}. Returns 0 when there's no usable centre.
     */
    private int faceMiddleQuarters(Match match, WorldPos spawn) {
        if (match == null || match.layout == null || spawn == null) {
            return 0;
        }
        WorldPos centre = arenaCentre(match.layout);
        if (centre == null) {
            return 0;
        }
        double dx = centre.x - spawn.x;
        double dz = centre.z - spawn.z;
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
            return 0;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? 1 : 3; // East / West
        }
        return dz > 0 ? 2 : 0; // South / North
    }

    /** Arena centre for cage facing: the middle-chests' centroid, else the spawns' centroid, else null. */
    private WorldPos arenaCentre(dev.stoshe.aerowars.model.MapLayout layout) {
        if (layout == null) {
            return null;
        }
        if (layout.middleChests != null && !layout.middleChests.isEmpty()) {
            double sx = 0, sy = 0, sz = 0;
            int n = 0;
            for (dev.stoshe.aerowars.model.ChestLocation c : layout.middleChests) {
                if (c != null && c.pos != null) {
                    sx += c.pos.x;
                    sy += c.pos.y;
                    sz += c.pos.z;
                    n++;
                }
            }
            if (n > 0) {
                return new WorldPos(sx / n, sy / n, sz / n);
            }
        }
        if (layout.spawnPoints != null && !layout.spawnPoints.isEmpty()) {
            double sx = 0, sy = 0, sz = 0;
            int n = 0;
            for (WorldPos p : layout.spawnPoints) {
                if (p != null) {
                    sx += p.x;
                    sy += p.y;
                    sz += p.z;
                    n++;
                }
            }
            if (n > 0) {
                return new WorldPos(sx / n, sy / n, sz / n);
            }
        }
        return null;
    }

    /** The cage's base rotation (from its cage cosmetic's {@code rotation}), as 0..3 quarter-turns (clockwise). */
    public int cageRotationQuarters(String cageRef) {
        int degrees = 0;
        try {
            dev.stoshe.aerowars.model.Cosmetic c = plugin.getCosmeticsManager().cageCosmeticFor(cageRef);
            if (c != null) {
                degrees = c.rotation;
            }
        } catch (Exception ignored) {
        }
        return ((degrees / 90) % 4 + 4) % 4;
    }

    /** Whether this cage should auto-face the arena centre (from its cage cosmetic's {@code faceMiddle}). */
    private boolean cageFaceMiddle(String cageRef) {
        try {
            dev.stoshe.aerowars.model.Cosmetic c = plugin.getCosmeticsManager().cageCosmeticFor(cageRef);
            return c != null && c.faceMiddle;
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Opens (removes) the cage at a spawn by restoring every snapshotted block, leaving the arena untouched. */
    private void openCage(Match match, World world, int spawnIndex) {
        if (match == null) {
            return;
        }
        var snaps = match.cageSnapshots.remove(spawnIndex);
        if (snaps == null) {
            return;
        }
        for (var entry : snaps.entrySet()) {
            dev.stoshe.aerowars.model.BlockSnapshot snap = entry.getValue();
            int[] c = parseKey(entry.getKey());
            try {
                world.setBlock(c[0], c[1], c[2], snap.id(), snap.rotation());
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- misc

    private WorldPos spawnFor(Match match, int index) {
        return match.layout == null ? null : match.layout.spawnAt(index);
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

    /**
     * Admin abort: force-ends the match the caller is currently in — whether they're playing or
     * hidden-spectating it. Returns false if they aren't associated with any live match.
     */
    public boolean forceStop(UUID uuid) {
        Match match = getPlayerMatch(uuid);
        if (match != null) {
            return adminEndMatch(match.id);
        }

        String spectating = adminSpectatorMatch.get(uuid);
        if (spectating != null) {
            return adminEndMatch(spectating);
        }

        return false;
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
        // Remember the choice across matches (Kits.PersistLastKit). buyKit funnels through here too.
        if (config.Kits.PersistLastKit) {
            kitManager.setLastKit(playerRef.getUuid(), kit.id);
        }
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
        // Unreachable for the can't-charge case (isUnlocked already grants those free above), but kept
        // as a defensive guard so a purchase is never silently attempted without charging support.
        if (!plugin.getEconomyService().canCharge()) {
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
