package dev.stoshe.aerowars.integration;

import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.util.Console;
import dev.stoshe.aerowars.util.PermissionUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional integration with the shared TaleGuard protection bridge. TaleGuard's
 * mixins discover hooks reflectively from a cross-classloader registry stored in
 * {@code System.getProperties()}, so there is no compile-time dependency: we
 * simply drop an adapter object exposing the method surface TaleGuard calls.
 *
 * <p>The hook lets TaleGuard enforce, in AeroWars worlds, the interaction rules
 * our own ECS systems don't cover (item pickup/use/drop, etc.) — no building or
 * interacting before the cages drop, nothing for spectators — plus basic lobby
 * build protection for non-admins.
 */
public final class TaleGuardBridge {
    private static final String REGISTRY_KEY = "taleguard.hook.registry";
    private static final String HOOK_KEY = "aerowars";

    private TaleGuardBridge() {
    }

    @SuppressWarnings("unchecked")
    public static void register(MatchManager matchManager, SetupSessionManager setupManager, AeroWarsConfig config) {
        try {
            Map<String, Object> registry = (Map<String, Object>) System.getProperties().get(REGISTRY_KEY);
            if (registry == null) {
                registry = new ConcurrentHashMap<>();
                System.getProperties().put(REGISTRY_KEY, registry);
            }
            registry.put(HOOK_KEY, new AeroWarsProtectionHook(matchManager, setupManager, config));
            Console.info("AeroWars protection hook registered with TaleGuard bridge.");
        } catch (Exception e) {
            Console.warning("Failed to register AeroWars TaleGuard hook: " + e.getMessage());
        }
    }

    /** Adapter matching the reflective surface TaleGuard invokes. */
    public static final class AeroWarsProtectionHook {
        private final MatchManager matchManager;
        private final SetupSessionManager setupManager;
        private final AeroWarsConfig config;

        AeroWarsProtectionHook(MatchManager matchManager, SetupSessionManager setupManager, AeroWarsConfig config) {
            this.matchManager = matchManager;
            this.setupManager = setupManager;
            this.config = config;
        }

        public int getPriority() {
            return 5;
        }

        public boolean isAllowed(UUID playerUuid, String worldName, double x, double y, double z, String type) {
            if (playerUuid == null || type == null) {
                return true;
            }
            // Admins mid-setup build freely.
            if (setupManager.hasSession(playerUuid)) {
                return true;
            }
            Match match = matchManager.getPlayerMatch(playerUuid);
            if (match != null) {
                if (match.spectators.contains(playerUuid)) {
                    return false;
                }
                // Before the cages drop, nothing destructive/interactive is allowed.
                return match.state.isRunning();
            }
            // Lobby build protection for non-admins.
            if (worldName != null && config.General.LobbyWorld != null
                    && worldName.equalsIgnoreCase(config.General.LobbyWorld)
                    && isBuildType(type) && !PermissionUtil.isAdmin(playerUuid)) {
                return false;
            }
            return true;
        }

        public void notifyDenied(UUID playerUuid, String worldName, double x, double y, double z, String type) {
            // Player-facing feedback is handled by our own ECS systems; keep quiet here.
        }

        private boolean isBuildType(String type) {
            switch (type) {
                case "BREAK":
                case "PLACE":
                case "HARVEST":
                case "FIRE":
                case "BUILDER":
                case "HAMMER":
                    return true;
                default:
                    return false;
            }
        }
    }
}
