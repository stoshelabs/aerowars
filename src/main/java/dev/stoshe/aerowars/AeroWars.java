package dev.stoshe.aerowars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.stoshe.aerowars.command.AeroWarsCommand;
import dev.stoshe.aerowars.integration.EconomyService;
import dev.stoshe.aerowars.integration.TaleGuardBridge;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.manager.MapManager;
import dev.stoshe.aerowars.manager.HudManager;
import dev.stoshe.aerowars.manager.KitManager;
import dev.stoshe.aerowars.manager.LootManager;
import dev.stoshe.aerowars.manager.PartyManager;
import dev.stoshe.aerowars.manager.StatsManager;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.manager.TranslationManager;
import dev.stoshe.aerowars.manager.WorldManager;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.system.BreakInteractionSystem;
import dev.stoshe.aerowars.system.CombatControlSystem;
import dev.stoshe.aerowars.system.MatchDeathSystem;
import dev.stoshe.aerowars.system.PlaceInteractionSystem;
import dev.stoshe.aerowars.system.UseInteractionSystem;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Console;
import dev.stoshe.aerowars.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.UUID;

/**
 * AeroWars — a SkyWars-style minigame for Hytale. Bootstraps configuration,
 * translations, managers, ECS systems, commands and the optional TaleGuard
 * protection bridge.
 */
public class AeroWars extends JavaPlugin {
    private static AeroWars instance;

    private File dataDir;
    private AeroWarsConfig config;
    private TranslationManager translationManager;
    private WorldManager worldManager;
    private ArenaManager arenaManager;
    private MapManager mapManager;
    private dev.stoshe.aerowars.manager.CageManager cageManager;
    private dev.stoshe.aerowars.manager.ChangelogManager changelogManager;
    private dev.stoshe.aerowars.system.UpdateNotificationSystem updateNotificationSystem;
    private KitManager kitManager;
    private LootManager lootManager;
    private StatsManager statsManager;
    private EconomyService economyService;
    private dev.stoshe.aerowars.manager.CosmeticsManager cosmeticsManager;
    private HudManager hudManager;
    private MatchManager matchManager;
    private PartyManager partyManager;
    private dev.stoshe.aerowars.manager.QueueManager queueManager;
    private SetupSessionManager setupSessionManager;
    private dev.stoshe.aerowars.interaction.InteractionService interactionService;

    public AeroWars(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    private static final String VERSION = "1.0.0";

    /** Native-logger startup banner (like Plots / LuckPerms), shown at the head of the plugin's boot. */
    private void printBanner() {
        Console.log("");
        Console.rainbow("AeroWars v" + VERSION);
        Console.rainbow("SkyWars minigame  |  Running on Hytale");
        Console.log("");
    }

    @Override
    protected void setup() {
        super.setup();
        printBanner();
        this.dataDir = getDataDirectory().toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        loadConfig();
        this.translationManager = new TranslationManager(dataDir, config.General.Language);

        this.worldManager = new WorldManager(dataDir, config);
        // NOTE: leftover ephemeral (match/setup) worlds are NOT deleted here anymore. Deleting them up front
        // means a player who was mid-match when the server stopped can't load their saved world on reconnect,
        // so the engine logs "failed to find ... reset to default" before our connect handler can reroute
        // them. Instead the cleanup is deferred (MatchManager.start) so those worlds still exist when players
        // reconnect — the connect handler then pulls them into the lobby — and are swept once everyone's in.
        this.mapManager = new MapManager(dataDir);
        this.mapManager.loadMaps();
        this.cageManager = new dev.stoshe.aerowars.manager.CageManager(dataDir);
        this.cageManager.loadCages();
        this.arenaManager = new ArenaManager(dataDir, mapManager);
        this.arenaManager.loadArenas();
        this.kitManager = new KitManager(dataDir);
        this.kitManager.loadKits();
        this.kitManager.loadUnlocks();
        this.kitManager.loadLastKits();
        this.lootManager = new LootManager(dataDir);
        this.lootManager.loadLootTables();
        this.statsManager = new StatsManager(
                dev.stoshe.aerowars.data.StatsRepositoryFactory.create(dataDir, config));
        this.statsManager.load();
        this.economyService = new EconomyService();
        this.cosmeticsManager = new dev.stoshe.aerowars.manager.CosmeticsManager(this, dataDir);
        this.cosmeticsManager.load();

        this.changelogManager = new dev.stoshe.aerowars.manager.ChangelogManager(dataDir);
        this.hudManager = new HudManager(this);
        this.matchManager = new MatchManager(this);
        this.partyManager = new PartyManager(this);
        this.queueManager = new dev.stoshe.aerowars.manager.QueueManager(matchManager);
        this.matchManager.setQueueManager(queueManager);
        this.setupSessionManager = new SetupSessionManager(this);

        registerSystems();
        registerCommands();
        registerListeners();

        TaleGuardBridge.register(matchManager, setupSessionManager, config);
        dev.stoshe.aerowars.integration.PlaceholderBridge.register(this);
        Console.success("AeroWars enabled.");
    }

    @Override
    protected void start() {
        super.start();
        matchManager.start();
        setupSessionManager.start();
        checkForUpdates();
        changelogManager.fetch(getVersion());
    }

    private volatile String latestVersion;

    /** The running plugin version (jar manifest, falling back to the compiled constant). */
    public String getVersion() {
        String v = getClass().getPackage().getImplementationVersion();
        return v != null ? v : VERSION;
    }

    /** Latest release found on GitHub (null until the async check finishes / if it failed). */
    public String getLatestVersion() {
        return latestVersion;
    }

    public boolean isUpdateAvailable() {
        return UpdateChecker.isNewerVersion(getVersion(), latestVersion);
    }

    private volatile boolean booted;
    private volatile boolean updateChecked;
    private volatile boolean updateAnnounced;

    /**
     * Async GitHub check on startup. The result is announced by {@link #maybeAnnounceUpdate()} — deferred
     * until the server has finished booting ({@link BootEvent}) so the "update available" banner lands at the
     * very END of the boot log where it's actually visible, not buried mid-startup.
     */
    private void checkForUpdates() {
        UpdateChecker.checkForUpdates().thenAccept(latest -> {
            this.latestVersion = latest;
            this.updateChecked = true;
            maybeAnnounceUpdate();
        });
    }

    /** Fired when the server finishes booting; lets the update banner print last (see checkForUpdates). */
    private void onBoot(BootEvent event) {
        this.booted = true;
        // The asset store is loaded by now, so this is when we can tell whether our spectator game mode made it in.
        if (config != null && config.Spectator.NativeMode) {
            if (dev.stoshe.aerowars.game.SpectatorMode.isAvailable()) {
                Console.info("Spectators: using the native spectator mode '"
                        + dev.stoshe.aerowars.game.SpectatorMode.MODE_ID + "'.");
            } else {
                Console.warning("Spectators: native spectator mode '" + dev.stoshe.aerowars.game.SpectatorMode.MODE_ID
                        + "' not loaded — falling back to AeroWars' own spectator state.");
            }
        }
        maybeAnnounceUpdate();
    }

    /**
     * Prints the update result once — and only once BOTH the async check has returned AND the server has
     * booted, so the banner is the last thing in the startup log. Whichever of the two finishes last triggers
     * it.
     */
    private synchronized void maybeAnnounceUpdate() {
        if (updateAnnounced || !booted || !updateChecked) {
            return;
        }
        updateAnnounced = true;
        String latest = this.latestVersion;
        if (UpdateChecker.isNewerVersion(getVersion(), latest)) {
            // Gold high-visibility banner (colour 220), same terminal treatment as the boot banner.
            Console.banner(220,
                    ">>  AeroWars UPDATE AVAILABLE",
                    "",
                    "    You have  v" + getVersion() + "   ->   latest is  v" + latest,
                    "    Download:  " + UpdateChecker.RELEASES_URL);
        } else if (latest != null) {
            Console.success("You are running the latest version (" + getVersion() + ").");
        }
    }

    @Override
    protected void shutdown() {
        if (hudManager != null) {
            hudManager.shutdown();
        }
        if (matchManager != null) {
            matchManager.shutdown();
        }
        if (setupSessionManager != null) {
            setupSessionManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.saveAll();
        }
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (cosmeticsManager != null) {
            cosmeticsManager.shutdown();
        }
        if (kitManager != null) {
            kitManager.shutdown();
        }
        if (interactionService != null) {
            interactionService.shutdown();
        }
        Console.info("AeroWars disabled.");
        super.shutdown();
    }

    /**
     * Runtime {@code /aerowars reload}: re-reads config.json, the language files, and the loot/kit
     * definition files without a server restart. Live matches keep the snapshot they started with;
     * changes apply to config values read live and to matches created afterwards. Returns false on a
     * hard failure. (Arena and cosmetic definitions still need a restart — they're more stateful.)
     */
    public boolean reload() {
        try {
            reloadConfig();
            translationManager.reload(config.General.Language);
            lootManager.loadLootTables();
            kitManager.loadKits();
            mapManager.loadMaps();
            cageManager.loadCages();
            arenaManager.loadArenas();
            Console.success("AeroWars reloaded (config, language, loot, kits, maps, cages, arenas).");
            return true;
        } catch (Exception e) {
            Console.error("Reload failed: " + e.getMessage());
            return false;
        }
    }

    /** Re-reads config.json into the SHARED config instance (so managers holding it see new values). */
    private void reloadConfig() {
        File configFile = new File(dataDir, "config.json");
        if (!configFile.exists()) {
            return;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Reader reader = new FileReader(configFile)) {
            AeroWarsConfig fresh = gson.fromJson(reader, AeroWarsConfig.class);

            if (fresh != null) {
                config.applyFrom(fresh);
                config.normalize();
            }
        } catch (Exception e) {
            Console.error("Failed to reload config: " + e.getMessage());
        }
    }

    private void loadConfig() {
        File configFile = new File(dataDir, "config.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!configFile.exists()) {
            this.config = AeroWarsConfig.getDefault();
            try (Writer writer = new FileWriter(configFile)) {
                gson.toJson(config, writer);
            } catch (Exception e) {
                Console.error("Failed to write default config: " + e.getMessage());
            }
            return;
        }
        try (Reader reader = new FileReader(configFile)) {
            this.config = gson.fromJson(reader, AeroWarsConfig.class);
        } catch (Exception e) {
            Console.error("Failed to read config, using defaults: " + e.getMessage());
            this.config = AeroWarsConfig.getDefault();
        }
        if (config == null) {
            config = AeroWarsConfig.getDefault();
        }
        config.normalize();
    }

    /**
     * Persists the in-memory config back to config.json (used by admin commands like /aerowars setlobby).
     * Merges the changed values INTO the existing file so the human-written "//" comment keys and the
     * key ordering survive — a plain re-serialize would strip every inline doc comment on first write.
     */
    public void saveConfig() {
        File configFile = new File(dataDir, "config.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject fresh = gson.toJsonTree(config).getAsJsonObject();
        JsonObject out = fresh;

        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile)) {
                JsonElement existing = JsonParser.parseReader(reader);

                if (existing != null && existing.isJsonObject()) {
                    JsonObject disk = existing.getAsJsonObject();
                    mergeInto(disk, fresh);
                    out = disk; // keeps "//" comments + original ordering, with updated values
                }
            } catch (Exception e) {
                // Corrupt/unreadable file — fall back to a clean serialize (comments are lost, but the
                // config is still saved correctly rather than not at all).
                Console.warning("Could not merge into existing config.json, rewriting it: " + e.getMessage());
            }
        }
        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(out, writer);
        } catch (Exception e) {
            Console.error("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Recursively overlays {@code fresh}'s values onto {@code disk}: existing keys are updated in place
     * (preserving position), nested objects merge recursively, and "//" comment keys present only in
     * {@code disk} are left untouched. New fields absent from {@code disk} are appended.
     */
    private static void mergeInto(JsonObject disk, JsonObject fresh) {
        for (Map.Entry<String, JsonElement> entry : fresh.entrySet()) {
            String key = entry.getKey();
            JsonElement freshVal = entry.getValue();
            JsonElement diskVal = disk.get(key);

            if (diskVal != null && diskVal.isJsonObject() && freshVal.isJsonObject()) {
                mergeInto(diskVal.getAsJsonObject(), freshVal.getAsJsonObject());
            } else {
                disk.add(key, freshVal);
            }
        }
    }

    private void registerSystems() {
        var registry = getEntityStoreRegistry();
        registry.registerSystem(new MatchDeathSystem(matchManager));
        registry.registerSystem(new CombatControlSystem(matchManager, config));
        registry.registerSystem(new BreakInteractionSystem(matchManager, setupSessionManager));
        registry.registerSystem(new PlaceInteractionSystem(matchManager, setupSessionManager));
        registry.registerSystem(new UseInteractionSystem(matchManager, setupSessionManager));
        registry.registerSystem(new dev.stoshe.aerowars.system.PickupControlSystem(matchManager));
        registry.registerSystem(new dev.stoshe.aerowars.system.ProjectileTrailSystem(matchManager));
        this.updateNotificationSystem = new dev.stoshe.aerowars.system.UpdateNotificationSystem(this);
        registry.registerSystem(this.updateNotificationSystem);
    }

    private void registerCommands() {
        AeroWarsCommand command = new AeroWarsCommand(this);
        command.addAliases("aw", "sw", "skywars");
        getCommandRegistry().registerCommand(command);
    }

    private void registerListeners() {
        // Native interaction events (our port of PlayerInteractLib). A temporary debug listener logs the
        // held item + interaction type so we can confirm on the server that events fire before hotbar tools
        // (kit/spectator) and the wand are routed through it.
        this.interactionService = new dev.stoshe.aerowars.interaction.InteractionService();
        this.interactionService.register();
        this.interactionService.addListener(this::onInteractionDebug);
        // Route the waiting-phase hotbar tools (kit selector / return-to-lobby) through the native events.
        this.interactionService.addListener(matchManager::onInteraction);

        try {
            getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);
        } catch (Exception e) {
            Console.warning("Failed to register connect listener: " + e.getMessage());
        }
        try {
            // Boot-complete signal — used to defer the "update available" banner to the end of the boot log.
            getEventRegistry().register(BootEvent.class, this::onBoot);
        } catch (Exception e) {
            Console.warning("Failed to register boot listener: " + e.getMessage());
        }
        try {
            getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        } catch (Exception e) {
            Console.warning("Failed to register disconnect listener: " + e.getMessage());
        }
        try {
            getEventRegistry().register(PlayerMouseButtonEvent.class, this::onMouseButton);
        } catch (Exception e) {
            Console.warning("Failed to register mouse listener: " + e.getMessage());
        }
        try {
            // Keyed by world, so global: every player finishing a load into any world. Game modes persist with
            // the player, so a spectator who disconnected mid-match logs back in still in our spectator mode.
            getEventRegistry().registerGlobal(
                    com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent.class,
                    event -> dev.stoshe.aerowars.game.SpectatorMode.releaseIfStale(event.getPlayerRef(),
                            matchManager::isAnySpectator));
        } catch (Exception e) {
            Console.warning("Failed to register ready listener: " + e.getMessage());
        }
    }

    /**
     * Fires on EVERY mouse click (air, block, or entity) — the reliable server-side interaction event. We
     * use it to trigger a spectator's hotbar tools (tracker/lobby) even when they click open sky, which the
     * block-only break/use/place events can't do. Filtered to a fresh button press (ignoring the paired
     * release) to run the action once per click.
     */
    private long lastMouseDebugMs;
    private long lastInteractDebugMs;

    /**
     * Temporary validation hook for the native interaction watcher: throttled log of the completed
     * interaction (type + held item). Confirms events fire with a correct itemInHandId before hotbar
     * tools / the wand are migrated onto {@link dev.stoshe.aerowars.interaction.InteractionService}.
     */
    private void onInteractionDebug(dev.stoshe.aerowars.interaction.PlayerInteractionEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastInteractDebugMs < 1000) {
            return;
        }
        lastInteractDebugMs = now;
        var block = event.blockPosition();
        var hit = event.hitLocation();
        // System.out.println → captured as [SOUT] in the server log file (Console's streams are not).
        System.out.println("[interact] type=" + event.type() + " item=" + event.itemInHandId()
                + " block=" + block + " hit=" + hit + " entity=" + event.hitEntityId());
    }

    private void onMouseButton(PlayerMouseButtonEvent event) {
        try {
            MouseButtonEvent button = event.getMouseButton();
            // TEMP diagnostic (throttled): confirms the event is delivered to the plugin and shows the raw
            // button/state/target values so we can wire air interactions correctly. Remove once verified.
            long now = System.currentTimeMillis();
            if (now - lastMouseDebugMs > 800) {
                lastMouseDebugMs = now;
                System.out.println("[mousedbg] btn=" + (button == null ? "?" : button.mouseButtonType)
                        + " state=" + (button == null ? "?" : button.state)
                        + " hasBlock=" + (event.getTargetBlock() != null));
            }
            if (button == null || button.state != MouseButtonState.Pressed) {
                return; // one action per click — skip the paired Released
            }
            PlayerRef pr = event.getPlayerRefComponent();
            if (pr == null) {
                return;
            }
            // (The setup wand is driven by the native interaction watcher now — its item maps both clicks
            // to a discrete interaction — so it's handled in MatchManager.onInteraction, not here.)
            // Spectator + waiting-phase hotbar tools, on EVERY click — air OR block-aimed. The raw mouse
            // event fires regardless of what's aimed at, so this reliably catches clicks even when the held
            // item is a weapon (whose attack doesn't raise a block use/break event). Each call no-ops unless
            // the player is in the matching state, and the shared debounce dedupes against the other paths.
            if (matchManager != null) {
                matchManager.handleSpectatorMouse(pr);
                matchManager.handleWaitingMouse(pr);
            }
        } catch (Exception e) {
            Console.warning("Mouse interaction handling failed: " + e.getMessage());
        }
    }

    /**
     * Suppresses the game's default "{player} has joined {world}" broadcast for a specific world
     * (AddPlayerToWorldEvent is keyed by world name). Called when a match/setup world is created;
     * AeroWars sends its own colored match join/leave messages to participants instead.
     */
    public void suppressJoinMessagesFor(String worldName) {
        if (worldName == null) {
            return;
        }
        try {
            getEventRegistry().register(AddPlayerToWorldEvent.class, worldName,
                    (AddPlayerToWorldEvent event) -> {
                        // Belt and suspenders: stop the broadcast AND blank the message, so neither the
                        // joining player nor the others get the default "X joined the world" line.
                        event.setBroadcastJoinMessage(false);
                        event.setJoinMessage(Message.raw(""));
                    });
            // Same for the "{player} has left {world}" line when players leave the match/setup world.
            getEventRegistry().register(
                    com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent.class, worldName,
                    (com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent event) -> {
                        event.setBroadcastLeaveMessage(false);
                        event.setLeaveMessage(Message.raw(""));
                    });
        } catch (Exception e) {
            Console.warning("Failed to register join-message suppression for " + worldName + ": " + e.getMessage());
        }
    }

    /**
     * A player whose saved "current world" is an ephemeral AeroWars match/setup world — because the server
     * stopped or crashed mid-match — would otherwise log back INTO that world. That world is deleted on boot
     * ({@code cleanupEphemeralWorlds}), so the engine can't find it and dumps them in the DEFAULT world
     * ("world has been reset to default"). We catch that here and pull them into the configured lobby.
     *
     * <p>By the time this fires the engine may have already reset {@code event.getWorld()} to default, so we
     * detect the stale world from the player's PERSISTED config data ({@link PlayerConfigData#getWorld()}) as
     * well, and rewrite that persisted pointer to the lobby so future logins are clean too.
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        try {
            if (event == null || worldManager == null || config == null) {
                return;
            }
            com.hypixel.hytale.server.core.universe.world.World world = event.getWorld();
            String currentName = world == null ? null : world.getName();

            com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData cfg = null;
            String savedName = null;
            com.hypixel.hytale.server.core.entity.entities.Player player = event.getPlayer();
            if (player != null) {
                cfg = player.getPlayerConfigData();
                if (cfg != null) {
                    savedName = cfg.getWorld();
                }
            }

            boolean stale = (currentName != null && worldManager.isAeroWarsWorld(currentName))
                    || (savedName != null && worldManager.isAeroWarsWorld(savedName));
            if (!stale) {
                return;
            }

            com.hypixel.hytale.server.core.universe.world.World lobby =
                    dev.stoshe.aerowars.util.Teleports.resolveLobby(config.General.LobbyWorld);
            if (lobby == null) {
                return;
            }
            // Place this connection in the lobby AND rewrite the persisted pointer, so the engine never
            // tries the dead arena world again on a later login.
            event.setWorld(lobby);
            if (cfg != null) {
                cfg.setWorld(lobby.getName());
            }
            PlayerRef pr = event.getPlayerRef();
            Console.info("Rerouted " + (pr != null ? pr.getUsername() : "a player")
                    + " out of stale world '" + (savedName != null ? savedName : currentName)
                    + "' into the lobby on connect.");
        } catch (Exception e) {
            Console.warning("Player connect reroute failed: " + e.getMessage());
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef pr = event.getPlayerRef();
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        // This runs during the engine's removePlayer dispatch — a throw here logs a SEVERE and aborts the
        // remaining cleanup, so isolate each handler: one failing must not skip the others or reach the engine.
        runDisconnectStep("match", () -> { if (matchManager != null) matchManager.handleDisconnect(pr); });
        runDisconnectStep("party", () -> { if (partyManager != null) partyManager.handleDisconnect(uuid); });
        runDisconnectStep("queue", () -> { if (queueManager != null) queueManager.handleDisconnect(uuid); });
        runDisconnectStep("setup", () -> { if (setupSessionManager != null) setupSessionManager.handleDisconnect(uuid); });
        runDisconnectStep("interaction", () -> { if (interactionService != null) interactionService.forget(uuid); });
        runDisconnectStep("update", () -> { if (updateNotificationSystem != null) updateNotificationSystem.forget(uuid); });
        runDisconnectStep("hud", () -> { if (hudManager != null) hudManager.remove(uuid); });
    }

    private void runDisconnectStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            Console.warning("Disconnect handler '" + name + "' failed: " + e);
        }
    }

    /** Runs a reward "command". Currently supports {@code broadcast <message>}. */
    public void dispatchConsoleCommand(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String trimmed = command.trim();
        if (trimmed.regionMatches(true, 0, "broadcast ", 0, 10)) {
            Message message = ChatUtil.plain(trimmed.substring(10));
            for (PlayerRef pr : Universe.get().getPlayers()) {
                pr.sendMessage(message);
            }
        } else {
            Console.warning("Unsupported reward command (only 'broadcast' is built in): " + trimmed);
        }
    }

    // ---------------------------------------------------------------- accessors

    public static AeroWars getInstance() {
        return instance;
    }

    public AeroWarsConfig getConfig() {
        return config;
    }

    public TranslationManager getTranslationManager() {
        return translationManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public dev.stoshe.aerowars.manager.CageManager getCageManager() {
        return cageManager;
    }

    public dev.stoshe.aerowars.interaction.InteractionService getInteractionService() {
        return interactionService;
    }

    public dev.stoshe.aerowars.manager.ChangelogManager getChangelogManager() {
        return changelogManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public dev.stoshe.aerowars.manager.CosmeticsManager getCosmeticsManager() {
        return cosmeticsManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public dev.stoshe.aerowars.manager.QueueManager getQueueManager() {
        return queueManager;
    }

    public SetupSessionManager getSetupSessionManager() {
        return setupSessionManager;
    }
}
