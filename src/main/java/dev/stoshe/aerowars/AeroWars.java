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
        this.worldManager.cleanupEphemeralWorlds();
        this.mapManager = new MapManager(dataDir);
        this.mapManager.loadMaps();
        this.arenaManager = new ArenaManager(dataDir, mapManager);
        this.arenaManager.loadArenas();
        this.kitManager = new KitManager(dataDir);
        this.kitManager.loadKits();
        this.kitManager.loadUnlocks();
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

    /** Async GitHub check on startup; logs the result to the terminal (same scheme as Plots). */
    private void checkForUpdates() {
        UpdateChecker.checkForUpdates().thenAccept(latest -> {
            this.latestVersion = latest;
            if (UpdateChecker.isNewerVersion(getVersion(), latest)) {
                Console.warning("A new version is available: " + latest + " (you have " + getVersion() + ").");
                Console.warning("Download: " + UpdateChecker.RELEASES_URL);
            } else if (latest != null) {
                Console.success("You are running the latest version (" + getVersion() + ").");
            }
        });
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
            arenaManager.loadArenas();
            Console.success("AeroWars reloaded (config, language, loot, kits, maps, arenas).");
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
    }

    /**
     * Fires on EVERY mouse click (air, block, or entity) — the reliable server-side interaction event. We
     * use it to trigger a spectator's hotbar tools (tracker/lobby) even when they click open sky, which the
     * block-only break/use/place events can't do. Filtered to a fresh button press (ignoring the paired
     * release) to run the action once per click.
     */
    private long lastMouseDebugMs;

    private void onMouseButton(PlayerMouseButtonEvent event) {
        try {
            MouseButtonEvent button = event.getMouseButton();
            // TEMP diagnostic (throttled): confirms the event is delivered to the plugin and shows the raw
            // button/state/target values so we can wire air interactions correctly. Remove once verified.
            long now = System.currentTimeMillis();
            if (now - lastMouseDebugMs > 1500) {
                lastMouseDebugMs = now;
                Console.info("[mousedbg] fired: btn=" + (button == null ? "?" : button.mouseButtonType)
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
            // Setup: LEFT-clicking the wand in the AIR (no block aimed at) marks the current step at the
            // admin's own position. Block-aimed wand clicks are handled by BreakInteractionSystem, so we
            // only take the air case here (targetBlock == null) to avoid double-marking.
            // Only handle AIR clicks here (targetBlock == null). Block-aimed clicks are handled by the ECS
            // break/use/place systems, so gating on air avoids firing an action twice for the same click.
            if (event.getTargetBlock() != null) {
                return;
            }
            if (button.mouseButtonType == MouseButtonType.Left
                    && setupSessionManager != null && setupSessionManager.hasSession(pr.getUuid())) {
                setupSessionManager.handleWandAirClick(pr);
                return;
            }
            if (matchManager != null) {
                matchManager.handleSpectatorMouse(pr);
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

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef pr = event.getPlayerRef();
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        if (matchManager != null) {
            matchManager.handleDisconnect(pr);
        }
        if (partyManager != null) {
            partyManager.handleDisconnect(uuid);
        }
        if (queueManager != null) {
            queueManager.handleDisconnect(uuid);
        }
        if (setupSessionManager != null) {
            setupSessionManager.handleDisconnect(uuid);
        }
        if (updateNotificationSystem != null) {
            updateNotificationSystem.forget(uuid);
        }
        if (hudManager != null) {
            hudManager.remove(uuid);
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
