package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
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
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.ChestType;
import dev.stoshe.aerowars.model.MapLayout;
import dev.stoshe.aerowars.model.SetupStep;
import dev.stoshe.aerowars.model.WorldPos;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Console;
import dev.stoshe.aerowars.util.Locations;
import dev.stoshe.aerowars.util.Sounds;
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
    private final MapManager mapManager;
    private final Map<UUID, SetupSession> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public SetupSessionManager(AeroWars plugin) {
        this.plugin = plugin;
        this.worldManager = plugin.getWorldManager();
        this.mapManager = plugin.getMapManager();
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

    /**
     * Redraws EVERY marker (spawns, normal + middle chests, spectator) for every admin in setup, regardless
     * of the current step, so the whole layout stays visible until the session finishes — not just the marker
     * for the active step.
     */
    private void renderSpawnMarkers() {
        for (SetupSession session : sessions.values()) {
            PlayerRef pr = Universe.get().getPlayer(session.playerId);
            if (pr == null) {
                continue;
            }
            SpawnMarkers.draw(pr, session.spawnPoints);
            SpawnMarkers.drawChests(pr, session.normalChests, false);
            SpawnMarkers.drawChests(pr, session.middleChests, true);
            if (session.spectatorSpawn != null) {
                SpawnMarkers.drawSpectator(pr, session.spectatorSpawn);
            }
        }
    }

    /** Short UI click cue for a setup action (spawn/chest add/remove, set). */
    private void click(PlayerRef pr) {
        Sounds.play(pr, Sounds.CLICK, 0.8f, 1.2f);
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
            String template) {
        UUID uuid = playerRef.getUuid();
        if (sessions.containsKey(uuid)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.already_in_session")));
            return false;
        }
        // Leave any match/spectate FIRST — setup teleports into a fresh world, and being pulled straight
        // out of a live match world otherwise breaks the match + crashes setup-world creation.
        plugin.getMatchManager().leaveForSetup(playerRef);
        World setupWorld = worldManager.createSetupWorld(template, template);
        if (setupWorld == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.world_failed")));
            return false;
        }
        plugin.suppressJoinMessagesFor(setupWorld.getName());
        SetupSession session = new SetupSession(uuid, template, setupWorld, System.currentTimeMillis());

        if (mapManager.hasLayout(template)) {
            session.seedFrom(mapManager.getLayout(template));
        }

        sessions.put(uuid, session);
        Teleports.to(playerRef, setupWorld, worldManager.templateSpawnPos());
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.session_started", "arena", template)));
        // Wand notice ABOVE the step instructions so the flow reads top-to-bottom (item arrives shortly).
        playerRef.sendMessage(ChatUtil.info(Tr.t("setup.wand_given")));
        sendStepInstructions(playerRef, session);
        // Clear the inventory and hand the locked spawn wand once the teleport settles.
        // Resolve the player/entity ref inside the setup world (the ECS ref changes on teleport).
        scheduler.schedule(() -> equipForSetup(setupWorld, uuid), 1, TimeUnit.SECONDS);
        return true;
    }

    /**
     * Begins an EDIT session for an existing MAP: clones the template into a fresh setup world and
     * pre-fills the session with the map's saved spawns/chests/spectator so an admin can adjust and
     * re-save it. (Editing a map that live matches use only affects matches created afterwards.)
     */
    public boolean editSession(PlayerRef playerRef, String template) {
        UUID uuid = playerRef.getUuid();
        if (template == null) {
            return false;
        }
        if (sessions.containsKey(uuid)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.already_in_session")));
            return false;
        }

        MapLayout layout = mapManager.getLayout(template);
        if (layout == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.map_not_found", "template", template)));
            return false;
        }

        // Leave any match/spectate first (see startSession).
        plugin.getMatchManager().leaveForSetup(playerRef);
        World setupWorld = worldManager.createSetupWorld(template, template);
        if (setupWorld == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.world_failed")));
            return false;
        }
        plugin.suppressJoinMessagesFor(setupWorld.getName());
        SetupSession session = new SetupSession(uuid, template, setupWorld, System.currentTimeMillis());
        session.seedFrom(layout);
        sessions.put(uuid, session);
        Teleports.to(playerRef, setupWorld, worldManager.templateSpawnPos());
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.edit_started", "arena", template)));
        playerRef.sendMessage(ChatUtil.info(Tr.t("setup.wand_given")));
        sendStepInstructions(playerRef, session);
        scheduler.schedule(() -> equipForSetup(setupWorld, uuid), 1, TimeUnit.SECONDS);
        return true;
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

    /**
     * Removes every spawn wand from the admin's hotbar (unlocking the locked slot first). Called when the
     * spawns step is finished so the wand can't linger into the chest steps.
     */
    private void removeWands(World world, UUID uuid) {
        String wand = getSpawnWand();
        if (world == null || wand == null || wand.isBlank()) {
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
                // Unlock the wand slot so it can actually be removed.
                hotbar.setSlotFilter(FilterActionType.ADD, WAND_SLOT, SlotFilter.ALLOW);
                hotbar.setSlotFilter(FilterActionType.REMOVE, WAND_SLOT, SlotFilter.ALLOW);
                hotbar.setSlotFilter(FilterActionType.DROP, WAND_SLOT, SlotFilter.ALLOW);
                // Sweep the whole hotbar (0..8) and drop any stack of the wand item.
                for (short slot = 0; slot <= 8; slot++) {
                    ItemStack stack = hotbar.getItemStack(slot);
                    if (stack != null && wand.equals(stack.getItemId())) {
                        hotbar.removeItemStackFromSlot(slot);
                    }
                }
            } catch (Exception e) {
                Console.warning("Failed to remove setup wand: " + e.getMessage());
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
        click(playerRef);
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
        click(playerRef);
        // The PlaceBlockEvent rotation is the PROPOSED one; the engine may re-orient a directional block
        // (like a chest) AFTER our handler, so the value we captured wouldn't reproduce the placed
        // orientation in a match. Read the ACTUAL stored rotation a moment later and correct the record.
        syncChestRotation(session, chest);
        int count = type == ChestType.MIDDLE ? session.middleChests.size() : session.normalChests.size();
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.chest_added", "type", type.name(), "n", count)));
    }

    /** Reads the block's real rotation index from the world (once placed) and updates the chest record. */
    private void syncChestRotation(SetupSession session, ChestLocation chest) {
        if (session == null || chest == null || chest.pos == null) {
            return;
        }
        World world = session.setupWorld;
        int bx = chest.pos.blockX();
        int by = chest.pos.blockY();
        int bz = chest.pos.blockZ();
        scheduler.schedule(() -> world.execute(() -> {
            try {
                var accessor = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(bx, bz));
                if (accessor != null) {
                    int rot = accessor.getRotationIndex(bx, by, bz);
                    if (rot >= 0) {
                        chest.rotationIndex = rot;
                    }
                }
            } catch (Exception ignored) {
            }
        }), 300, TimeUnit.MILLISECONDS);
    }

    /**
     * Called by the break-block system when an admin breaks a block during setup: if that block is a recorded
     * chest, drop it from the session (with a message + click cue). Returns true if a chest was removed. Other
     * breaks are free edits of the throwaway world and produce no message.
     */
    public boolean handleChestBreak(PlayerRef playerRef, WorldPos pos) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null || pos == null) {
            return false;
        }
        if (removeChestAt(session.middleChests, pos)) {
            click(playerRef);
            playerRef.sendMessage(ChatUtil.success(Tr.t("setup.chest_broken", "type", ChestType.MIDDLE.name(),
                    "n", session.middleChests.size())));
            return true;
        }
        if (removeChestAt(session.normalChests, pos)) {
            click(playerRef);
            playerRef.sendMessage(ChatUtil.success(Tr.t("setup.chest_broken", "type", ChestType.NORMAL.name(),
                    "n", session.normalChests.size())));
            return true;
        }
        return false;
    }

    /** Removes the recorded chest sitting on {@code pos} (same block), if any. */
    private boolean removeChestAt(List<ChestLocation> chests, WorldPos pos) {
        for (int i = 0; i < chests.size(); i++) {
            WorldPos cp = chests.get(i).pos;
            if (cp != null && cp.blockX() == pos.blockX() && cp.blockY() == pos.blockY()
                    && cp.blockZ() == pos.blockZ()) {
                chests.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Left-clicking the wand in the AIR (no block aimed at) marks the current step at the admin's own
     * position — same as {@code /aerowars setup set}. Resolves the held item on the setup world thread and
     * only acts when the wand is actually in hand (so a bare air-swing does nothing).
     */
    public void handleWandAirClick(PlayerRef playerRef) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            return;
        }
        World world = session.setupWorld;
        UUID uuid = playerRef.getUuid();
        world.execute(() -> {
            try {
                PlayerRef live = Universe.get().getPlayer(uuid);
                if (live == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(live.getReference(), Player.getComponentType());
                if (player == null || !isHoldingWand(player.getInventory().getItemInHand())) {
                    return;
                }
                handleSet(live);
            } catch (Exception ignored) {
            }
        });
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
                click(playerRef);
                playerRef.sendMessage(ChatUtil.success(Tr.t("setup.spawn_added", "n", session.spawnPoints.size())));
            }
            case SPECTATOR_SPAWN -> {
                session.spectatorSpawn = here;
                // Clear the old blue marker so the render loop redraws it at the new spot;
                // don't auto-advance so the admin can see/adjust it, then /setup done.
                SpawnMarkers.clear(playerRef);
                click(playerRef);
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
            case "undo" -> handleUndo(playerRef, session, 1);
            case "skip" -> handleSkip(playerRef, session);
            case "save" -> handleSave(playerRef, session);
            case "cancel", "exit" -> handleCancel(playerRef, session);
            default -> {
            }
        }
    }

    /** {@code /aerowars setup undo [n]} — resolves the session, then undoes up to n entries of the active step. */
    public void handleUndo(PlayerRef playerRef, int count) {
        SetupSession session = sessions.get(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.not_in_session")));
            return;
        }
        handleUndo(playerRef, session, count);
    }

    /**
     * Removes up to {@code count} of the most recent entries captured for the active step (spawns or chests).
     * {@code count} defaults to 1; if it exceeds what's available, everything on the step is undone.
     */
    private void handleUndo(PlayerRef playerRef, SetupSession session, int count) {
        int want = Math.max(1, count);
        int removed;
        switch (session.step) {
            case SPAWN_POINTS -> removed = undoLast(session.spawnPoints, want);
            case NORMAL_CHESTS -> removed = undoLast(session.normalChests, want);
            case MIDDLE_CHESTS -> removed = undoLast(session.middleChests, want);
            default -> {
                playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.nothing_to_undo")));
                return;
            }
        }
        if (removed == 0) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.nothing_to_undo")));
            return;
        }
        int remaining = switch (session.step) {
            case SPAWN_POINTS -> session.spawnPoints.size();
            case NORMAL_CHESTS -> session.normalChests.size();
            case MIDDLE_CHESTS -> session.middleChests.size();
            default -> 0;
        };
        // Force an immediate marker refresh so the removed cubes disappear at once (not on the next redraw).
        SpawnMarkers.clear(playerRef);
        click(playerRef);
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.undone", "count", removed, "n", remaining)));
    }

    /** Pops up to {@code n} entries off the end of {@code list}; returns how many were actually removed. */
    private int undoLast(List<?> list, int n) {
        int removed = 0;
        while (removed < n && !list.isEmpty()) {
            list.remove(list.size() - 1);
            removed++;
        }
        return removed;
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
        // Leaving the spawns step: the wand's job is done, so strip it from the inventory (chests are placed
        // from the creative menu, not the wand). Markers are NOT cleared — the whole layout stays visible.
        if (session.step == SetupStep.SPAWN_POINTS) {
            removeWands(session.setupWorld, session.playerId);
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

        mapManager.saveMap(session.toLayout());
        playerRef.sendMessage(ChatUtil.success(Tr.t("setup.saved", "arena", session.template)));
        playerRef.sendMessage(ChatUtil.info(Tr.t("setup.saved_hint", "template", session.template)));
        // Persist the world's block edits back into the template (so they survive into future matches).
        closeSession(playerRef, session, true);
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
            Console.info("Setup session for map " + session.template + " ended (disconnect).");
        }
    }

    private void closeSession(PlayerRef playerRef, SetupSession session) {
        closeSession(playerRef, session, false);
    }

    /**
     * Ends a setup session: clears markers, unequips, returns the admin to the lobby, then (3s later, once
     * the teleport settles and the admin has left) either saves the world's block edits back into the
     * template ({@code saveToTemplate}, on a successful {@code save}) or just deletes the ephemeral world.
     */
    private void closeSession(PlayerRef playerRef, SetupSession session, boolean saveToTemplate) {
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
        String template = session.template;
        if (saveToTemplate) {
            scheduler.schedule(() -> worldManager.saveSetupToTemplate(world, template), 3, TimeUnit.SECONDS);
        } else {
            scheduler.schedule(() -> worldManager.deleteWorld(world), 3, TimeUnit.SECONDS);
        }
    }

    private void sendStepInstructions(PlayerRef playerRef, SetupSession session) {
        // Split on newlines and send one chat line each, so the multi-line step layout renders reliably.
        for (String line : Tr.t(session.step.instructionKey()).split("\n")) {
            playerRef.sendMessage(ChatUtil.plain(line));
        }
    }

    private void sendConfirmation(PlayerRef playerRef, SetupSession session) {
        playerRef.sendMessage(ChatUtil.info("Map: " + session.template
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
                Console.info("Setup session for map " + session.template + " timed out.");
            }
        }
    }

    public List<String> listTemplates() {
        return worldManager.listWorldTemplates();
    }
}
