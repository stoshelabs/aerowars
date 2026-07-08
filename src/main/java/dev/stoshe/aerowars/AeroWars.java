package dev.stoshe.aerowars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.stoshe.aerowars.command.AeroWarsCommand;
import dev.stoshe.aerowars.integration.EconomyService;
import dev.stoshe.aerowars.integration.TaleGuardBridge;
import dev.stoshe.aerowars.manager.ArenaManager;
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
        this.arenaManager = new ArenaManager(dataDir);
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

    /** Persists the in-memory config back to config.json (used by admin commands like /aerowars setlobby). */
    public void saveConfig() {
        File configFile = new File(dataDir, "config.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
        } catch (Exception e) {
            Console.error("Failed to save config: " + e.getMessage());
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
        registry.registerSystem(new dev.stoshe.aerowars.system.UpdateNotificationSystem(this));
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
