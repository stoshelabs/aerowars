package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.SetupSession;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.ChestType;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.SetupStep;
import dev.stoshe.aerowars.model.WorldPos;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Console;
import dev.stoshe.aerowars.util.Locations;
import dev.stoshe.aerowars.util.SpawnMarkers;
import dev.stoshe.aerowars.util.Teleports;
import dev.stoshe.aerowars.util.Tr;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives the in-game arena setup wizard: teleports the admin into a cloned
 * setup world, captures spawn points and chest placements, and writes the
 * finished {@link Arena} to disk.
 */
public class SetupSessionManager {
    private static final long TIMEOUT_MINUTES = 15;

    private final AeroWars plugin;
    private final WorldManager worldManager;
    private final ArenaManager arenaManager;
    private final Map<UUID, SetupSession> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public SetupSessionManager(AeroWars plugin) {
        this.plugin = plugin;
        this.worldManager = plugin.getWorldManager();
        this.arenaManager = plugin.getArenaManager();
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AeroWars-Setup-Timeout");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 1, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::renderSpawnMarkers, 500, 500, TimeUnit.MILLISECONDS);
    }

    /** Redraws the floating spawn markers for every admin on the spawn-points step. */
    private void renderSpawnMarkers() {
        for (SetupSession session : sessions.values()) {
            PlayerRef pr = Universe.get().getPlayer(session.playerId);
            if (pr == null) {
                continue;
            }
            if (session.step == SetupStep.SPAWN_POINTS && !session.spawnPoints.isEmpty()) {
                SpawnMarkers.draw(pr, session.spawnPoints);
            } else if (session.step == SetupStep.SPECTATOR_SPAWN && session.spectatorSpawn != null) {
                SpawnMarkers.drawSpectator(pr, session.spectatorSpawn);
            }
        }
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (SetupSession session : sessions.values()) {
            worldManager.deleteWorld(session.setupWorld);
        }
        sessions.clear();
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * If the player is in a setup session, ends it automatically (cleans the setup world + state) so they
     * can do something else — e.g. join a match. Returns true if a session was actually ended. Used by the
     * join/queue commands so an admin never joins a game while still "inside" a setup.
     */
    public boolean endActiveSession(PlayerRef pr) {
        if (pr == null) {
            return false;
        }
        SetupSession session = sessions.get(pr.getUuid());
        if (session == null) {
            return false;
        }
        pr.sendMessage(ChatUtil.warning(Tr.t("setup.auto_ended")));
        closeSession(pr, session);
        return true;
    }

    public boolean startSession(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef,
            String arenaName, String worldTemplate) {
        UUID uuid = playerRef.getUuid();
        if (sessions.containsKey(uuid)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.already_in_session")));
            return false;
        }
        // Leave any match/spectate FIRST — setup teleports into a fresh world, and being pulled straight
        // out of a live match world otherwise breaks the match + crashes setup-world creation.
        plugin.getMatchManager().leaveForSetup(playerRef);
        World setupWorld = worldManager.createSetupWorld(arenaName, worldTemplate);
        if (setupWorld == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.world_failed")));
            return false;
        }
        plugin.suppressJoinMessagesFor(setupWorld.getName());
        SetupSession session = new SetupSession(uuid, arenaName, worldTemplate, setupWorld,
                System.currentTimeMillis());
        sessions.put(uuid, session);
        Teleports.to(playerRef, setupWorld, worldManager.templateSpawnPos());
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.session_started", "arena", arenaName)));
        // Wand notice ABOVE the step instructions so the flow reads top-to-bottom (item arrives shortly).
        playerRef.sendMessage(ChatUtil.info(Tr.t("setup.wand_given")));
        sendStepInstructions(playerRef, session);
        // Clear the inventory and hand the locked spawn wand once the teleport settles.
        // Resolve the player/entity ref inside the setup world (the ECS ref changes on teleport).
        scheduler.schedule(() -> equipForSetup(setupWorld, uuid), 1, TimeUnit.SECONDS);
        return true;
    }

    /**
     * Begins an EDIT session for an existing arena: clones the arena's template into a fresh setup world
     * and pre-fills the session with its spawns/chests/spectator so an admin can adjust and re-save it.
     * Refused when a live match is already using the arena (can't edit an open/in-progress arena).
     */
    public boolean editSession(PlayerRef playerRef, Arena arena) {
        UUID uuid = playerRef.getUuid();
        if (arena == null) {
            return false;
        }
        if (sessions.containsKey(uuid)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.already_in_session")));
            return false;
        }
        if (plugin.getMatchManager().isArenaInUse(arena.name)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.arena_in_use", "arena", arena.name)));
            return false;
        }
        // Leave any match/spectate first (see startSession).
        plugin.getMatchManager().leaveForSetup(playerRef);
        World setupWorld = worldManager.createSetupWorld(arena.name, arena.worldTemplate);
        if (setupWorld == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.world_failed")));
            return false;
        }
        plugin.suppressJoinMessagesFor(setupWorld.getName());
        SetupSession session = new SetupSession(uuid, arena.name, arena.worldTemplate, setupWorld,
                System.currentTimeMillis());
        session.seedFrom(arena);
        sessions.put(uuid, session);
        Teleports.to(playerRef, setupWorld, worldManager.templateSpawnPos());
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.edit_started", "arena", arena.name)));
        playerRef.sendMessage(ChatUtil.info(Tr.t("setup.wand_given")));
        sendStepInstructions(playerRef, session);
        scheduler.schedule(() -> equipForSetup(setupWorld, uuid), 1, TimeUnit.SECONDS);
        return true;
    }

    /**
     * {@code /aerowars setup mode <solo|coop|teams> [size]} — sets whether the arena is Solo (FFA) or a
     * co-op Teams arena and, for teams, how many players share each island/team. Usable any time during
     * the session (before saving).
     */
    public void setMode(PlayerRef playerRef, String modeArg, int size) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.not_in_session")));
            return;
        }
        String m = modeArg == null ? "" : modeArg.trim().toLowerCase();
        boolean teams = m.equals("teams") || m.equals("coop") || m.equals("team");
        if (teams) {
            session.mode = GameMode.TEAMS;
            session.teamSize = Math.max(2, size);
        } else {
            session.mode = GameMode.SOLO;
            session.teamSize = 1;
        }
        String modeName = session.mode == GameMode.TEAMS
                ? Tr.t("scoreboard.mode_teams") : Tr.t("scoreboard.mode_solo");
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.mode_set", "mode", modeName, "size", session.teamSize)));
    }

    /** The configured spawn-wand item id (clicking a block with it marks a spawn). */
    public String getSpawnWand() {
        return plugin.getConfig().Setup.SpawnWand;
    }

    /** True if {@code held} is the configured spawn wand. */
    public boolean isHoldingWand(ItemStack held) {
        if (held == null) {
            return false;
        }
        String wand = getSpawnWand();
        return wand != null && wand.equals(held.getItemId());
    }

    private static final short WAND_SLOT = 8;

    /** Clears the admin's inventory and gives the spawn wand, locked into its slot. */
    private void equipForSetup(World world, UUID uuid) {
        if (world == null) {
            return;
        }
        String wand = getSpawnWand();
        world.execute(() -> {
            try {
                PlayerRef playerRef = Universe.get().getPlayer(uuid);
                if (playerRef == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
                if (player == null) {
                    return;
                }
                Inventory inv = player.getInventory();
                inv.clear();
                if (wand != null && !wand.isBlank()) {
                    ItemContainer hotbar = inv.getHotbar();
                    if (hotbar != null) {
                        hotbar.setItemStackForSlot(WAND_SLOT, new ItemStack(wand, 1));
                        // Lock the slot: the wand can't be removed, dropped, or overwritten.
                        hotbar.setSlotFilter(FilterActionType.REMOVE, WAND_SLOT, SlotFilter.DENY);
                        hotbar.setSlotFilter(FilterActionType.DROP, WAND_SLOT, SlotFilter.DENY);
                        hotbar.setSlotFilter(FilterActionType.ADD, WAND_SLOT, SlotFilter.DENY);
                    }
                }
            } catch (Exception e) {
                Console.warning("Failed to equip setup wand: " + e.getMessage());
            }
        });
    }

    /**
     * {@code /aerowars wand} — hands the setup wand back into slot 8 (locked), WITHOUT clearing the rest of
     * the inventory (so it's safe to recover the wand mid-chest-placement). Only works inside a session.
     */
    public void giveWand(PlayerRef playerRef) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.not_in_session")));
            return;
        }
        String wand = getSpawnWand();
        if (wand == null || wand.isBlank()) {
            return;
        }
        World world = session.setupWorld;
        UUID uuid = playerRef.getUuid();
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
                hotbar.setSlotFilter(FilterActionType.ADD, WAND_SLOT, SlotFilter.ALLOW);
                hotbar.setItemStackForSlot(WAND_SLOT, new ItemStack(wand, 1));
                hotbar.setSlotFilter(FilterActionType.REMOVE, WAND_SLOT, SlotFilter.DENY);
                hotbar.setSlotFilter(FilterActionType.DROP, WAND_SLOT, SlotFilter.DENY);
                hotbar.setSlotFilter(FilterActionType.ADD, WAND_SLOT, SlotFilter.DENY);
                pr.sendMessage(ChatUtil.success(Tr.t("setup.wand_recovered")));
            } catch (Exception e) {
                Console.warning("giveWand failed: " + e.getMessage());
            }
        });
    }

    /** Unlocks the wand slot and clears the admin's inventory when setup ends. */
    private void unequipAfterSetup(World world, UUID uuid) {
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                PlayerRef playerRef = Universe.get().getPlayer(uuid);
                if (playerRef == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
                if (player == null) {
                    return;
                }
                ItemContainer hotbar = player.getInventory().getHotbar();
                if (hotbar != null) {
                    // Unlock before clearing so the wand can actually be removed.
                    hotbar.setSlotFilter(FilterActionType.ADD, WAND_SLOT, SlotFilter.ALLOW);
                    hotbar.setSlotFilter(FilterActionType.REMOVE, WAND_SLOT, SlotFilter.ALLOW);
                    hotbar.setSlotFilter(FilterActionType.DROP, WAND_SLOT, SlotFilter.ALLOW);
                }
                player.getInventory().clear();
            } catch (Exception e) {
                Console.warning("Failed to clear setup inventory: " + e.getMessage());
            }
        });
    }

    /** Called by the break-block system: records the clicked block as a spawn point. */
    public void handleSpawnClick(PlayerRef playerRef, WorldPos pos) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null || session.step != SetupStep.SPAWN_POINTS) {
            return;
        }
        session.spawnPoints.add(Locations.centerOfBlock(pos));
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.spawn_added", "n", session.spawnPoints.size())));
    }

    /** Called by the place-block system: records a placed chest. */
    public void handleChestPlace(PlayerRef playerRef, WorldPos pos, String blockId, int rotationIndex) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            return;
        }
        ChestType type;
        if (session.step == SetupStep.NORMAL_CHESTS) {
            type = ChestType.NORMAL;
        } else if (session.step == SetupStep.MIDDLE_CHESTS) {
            type = ChestType.MIDDLE;
        } else {
            return;
        }
        // Capture position, block (chest type) and rotation so loot can key off them later.
        ChestLocation chest = new ChestLocation(Locations.centerOfBlock(pos), blockId, type, rotationIndex);
        session.addChest(chest);
        int count = type == ChestType.MIDDLE ? session.middleChests.size() : session.normalChests.size();
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.chest_added", "type", type.name(), "n", count)));
    }

    /** /aerowars setup set — captures the admin's current position for the active step. */
    public void handleSet(PlayerRef playerRef) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.not_in_session")));
            return;
        }
        WorldPos here = Locations.fromTransform(playerRef.getTransform());
        switch (session.step) {
            case SPAWN_POINTS -> {
                session.spawnPoints.add(here);
                playerRef.sendMessage(ChatUtil.success(Tr.t("setup.spawn_added", "n", session.spawnPoints.size())));
            }
            case SPECTATOR_SPAWN -> {
                session.spectatorSpawn = here;
                // Clear the old blue marker so the render loop redraws it at the new spot;
                // don't auto-advance so the admin can see/adjust it, then /setup done.
                SpawnMarkers.clear(playerRef);
                playerRef.sendMessage(ChatUtil.success(Tr.t("setup.spectator_set")));
            }
            default -> playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.step_" + session.step.name().toLowerCase())));
        }
    }

    public void handleCommand(PlayerRef playerRef, String action) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.not_in_session")));
            return;
        }
        switch (action) {
            case "done" -> handleDone(playerRef, session);
            case "undo" -> handleUndo(playerRef, session);
            case "skip" -> handleSkip(playerRef, session);
            case "save" -> handleSave(playerRef, session);
            case "cancel" -> handleCancel(playerRef, session);
            default -> {
            }
        }
    }

    /** Removes the last entry captured for the active step (spawn or chest). */
    private void handleUndo(PlayerRef playerRef, SetupSession session) {
        switch (session.step) {
            case SPAWN_POINTS -> {
                if (session.spawnPoints.isEmpty()) {
                    playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.nothing_to_undo")));
                } else {
                    session.spawnPoints.remove(session.spawnPoints.size() - 1);
                    SpawnMarkers.clear(playerRef);
                    playerRef.sendMessage(ChatUtil.success(Tr.t("setup.spawn_removed", "n", session.spawnPoints.size())));
                }
            }
            case NORMAL_CHESTS -> undoLastChest(playerRef, session.normalChests);
            case MIDDLE_CHESTS -> undoLastChest(playerRef, session.middleChests);
            default -> playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.nothing_to_undo")));
        }
    }

    private void undoLastChest(PlayerRef playerRef, List<ChestLocation> chests) {
        if (chests.isEmpty()) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.nothing_to_undo")));
        } else {
            chests.remove(chests.size() - 1);
            playerRef.sendMessage(ChatUtil.success(Tr.t("setup.chest_removed", "n", chests.size())));
        }
    }

    /** Right-clicking a block near a spawn with the wand removes that spawn. */
    public void handleSpawnRemoveAt(PlayerRef playerRef, WorldPos pos) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null || session.step != SetupStep.SPAWN_POINTS || pos == null) {
            return;
        }
        int bestIndex = -1;
        long bestDistSq = Long.MAX_VALUE;
        for (int i = 0; i < session.spawnPoints.size(); i++) {
            WorldPos sp = session.spawnPoints.get(i);
            long dx = sp.blockX() - pos.blockX();
            long dy = sp.blockY() - pos.blockY();
            long dz = sp.blockZ() - pos.blockZ();
            long distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIndex = i;
            }
        }
        // Only remove when the click is on/adjacent to a marked spawn (radius 2 blocks).
        if (bestIndex >= 0 && bestDistSq <= 4) {
            session.spawnPoints.remove(bestIndex);
            SpawnMarkers.clear(playerRef);
            playerRef.sendMessage(ChatUtil.success(Tr.t("setup.spawn_removed", "n", session.spawnPoints.size())));
        } else {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.no_spawn_here")));
        }
    }

    private void handleDone(PlayerRef playerRef, SetupSession session) {
        if (session.step == SetupStep.SPAWN_POINTS && session.spawnPoints.size() < 2) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.need_more_spawns")));
            return;
        }
        if (session.step == SetupStep.SPECTATOR_SPAWN && session.spectatorSpawn == null) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.step_spectator")));
            return;
        }
        advanceStep(playerRef, session);
    }

    private void handleSkip(PlayerRef playerRef, SetupSession session) {
        if (session.step.isOptional()) {
            advanceStep(playerRef, session);
        } else {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.step_" + session.step.name().toLowerCase())));
        }
    }

    private void advanceStep(PlayerRef playerRef, SetupSession session) {
        SetupStep next = session.step.next();
        if (next == null) {
            return;
        }
        if (session.step == SetupStep.SPAWN_POINTS || session.step == SetupStep.SPECTATOR_SPAWN) {
            SpawnMarkers.clear(playerRef);
        }
        session.step = next;
        if (next == SetupStep.CONFIRMATION) {
            sendConfirmation(playerRef, session);
        } else {
            sendStepInstructions(playerRef, session);
        }
    }

    private void handleSave(PlayerRef playerRef, SetupSession session) {
        if (!session.isValid()) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.need_more_spawns")));
            return;
        }
        Arena arena = session.toArena();
        if (!arena.isComplete()) {
            // Only reachable for a TEAMS arena without enough islands for two full teams.
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.need_more_team_spawns", "n", session.teamSize * 2)));
            return;
        }
        // Editing must still refuse if a match spun up on this arena mid-session.
        if (session.editing && plugin.getMatchManager().isArenaInUse(arena.name)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.arena_in_use", "arena", arena.name)));
            return;
        }
        arenaManager.saveArena(arena);
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.saved", "arena", session.arenaName)));
        closeSession(playerRef, session);
    }

    private void handleCancel(PlayerRef playerRef, SetupSession session) {
        playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.cancelled")));
        closeSession(playerRef, session);
    }

    /** Ends a setup session on disconnect: clears the world so nothing is orphaned. */
    public void handleDisconnect(UUID uuid) {
        SetupSession session = sessions.remove(uuid);
        if (session != null) {
            scheduler.schedule(() -> worldManager.deleteWorld(session.setupWorld), 3, TimeUnit.SECONDS);
            Console.info("Setup session for arena " + session.arenaName + " ended (disconnect).");
        }
    }

    private void closeSession(PlayerRef playerRef, SetupSession session) {
        SpawnMarkers.clear(playerRef);
        unequipAfterSetup(session.setupWorld, session.playerId);
        sessions.remove(session.playerId);
        // Return the admin to the lobby before we tear down the setup world.
        World lobby = Teleports.resolveLobby(plugin.getConfig().General.LobbyWorld);
        if (lobby != null) {
            WorldPos spawn = Teleports.lobbySpawn(lobby, plugin.getConfig().General.LobbySpawn,
                    playerRef.getUuid());
            Teleports.to(playerRef, lobby, spawn);
        }
        World world = session.setupWorld;
        scheduler.schedule(() -> worldManager.deleteWorld(world), 3, TimeUnit.SECONDS);
    }

    private void sendStepInstructions(PlayerRef playerRef, SetupSession session) {
        // Split on newlines and send one chat line each, so the multi-line step layout renders reliably.
        for (String line : Tr.t(session.step.instructionKey()).split("\n")) {
            playerRef.sendMessage(ChatUtil.plain(line));
        }
    }

    private void sendConfirmation(PlayerRef playerRef, SetupSession session) {
        String mode = session.mode == GameMode.TEAMS ? "Teams x" + session.teamSize : "Solo";
        playerRef.sendMessage(ChatUtil.info("Mode: " + mode
                + " | Spawns: " + session.spawnPoints.size()
                + " | Normal: " + session.normalChests.size()
                + " | Middle: " + session.middleChests.size()
                + " | Spectator: " + (session.spectatorSpawn != null ? "ok" : "-")));
        playerRef.sendMessage(ChatUtil.plain(Tr.t("setup.confirmation")));
    }

    private void checkTimeouts() {
        for (SetupSession session : sessions.values()) {
            if (session.elapsedMinutes() >= TIMEOUT_MINUTES) {
                PlayerRef pr = Universe.get().getPlayer(session.playerId);
                if (pr != null) {
                    pr.sendMessage(ChatUtil.warning(Tr.t("setup.timed_out")));
                    closeSession(pr, session);
                } else {
                    sessions.remove(session.playerId);
                    worldManager.deleteWorld(session.setupWorld);
                }
                Console.info("Setup session for arena " + session.arenaName + " timed out.");
            }
        }
    }

    public List<String> listTemplates() {
        return worldManager.listWorldTemplates();
    }
}
