package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;
import dev.stoshe.aerowars.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells admins (players with the general AeroWars admin permission) that a newer version is available a
 * few seconds after they join — same idea as the Plots plugin. The version info is fetched once at startup
 * and held on the {@link AeroWars} plugin; this system only reads it.
 */
public class UpdateNotificationSystem extends EntityTickingSystem<EntityStore> {
    private static final float NOTIFICATION_DELAY = 3.0f; // seconds after join

    private final AeroWars plugin;
    /** Seconds tracked since an admin joined; set to a sentinel once notified so it only fires once. */
    private final Map<UUID, Float> joinTime = new HashMap<>();

    public UpdateNotificationSystem(AeroWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();

        if (!joinTime.containsKey(uuid)) {
            // Only track (and later notify) admins.
            if (PermissionUtil.isAdmin(uuid)) {
                joinTime.put(uuid, 0.0f);
            }
            return;
        }

        float t = joinTime.get(uuid);
        if (t < NOTIFICATION_DELAY) {
            joinTime.put(uuid, t + dt);
            return;
        }
        if (t < NOTIFICATION_DELAY + 1.0f) {
            if (plugin.isUpdateAvailable()) {
                playerRef.sendMessage(ChatUtil.warning(Tr.t("update.available", "version", plugin.getLatestVersion())));
                playerRef.sendMessage(ChatUtil.info(Tr.t("update.download", "url", UpdateChecker.RELEASES_URL)));
            }
            joinTime.put(uuid, 999999.0f); // notified — don't repeat
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }
}
