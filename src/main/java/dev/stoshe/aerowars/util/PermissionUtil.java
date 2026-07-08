package dev.stoshe.aerowars.util;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.UUID;

/** Central permission-node definitions and checks for AeroWars. */
public final class PermissionUtil {
    public static final String PERM_BASE = "aerowars";
    public static final String PERM_PLAY = "aerowars.play";
    public static final String PERM_ADMIN = "aerowars.admin";

    private PermissionUtil() {
    }

    public static boolean has(UUID player, String node, boolean def) {
        if (player == null) {
            return false;
        }
        try {
            return PermissionsModule.get().hasPermission(player, node, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean isAdmin(UUID player) {
        return has(player, PERM_ADMIN, false);
    }

    public static boolean canPlay(UUID player) {
        return has(player, PERM_PLAY, true);
    }
}
