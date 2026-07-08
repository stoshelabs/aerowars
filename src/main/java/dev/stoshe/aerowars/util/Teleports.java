package dev.stoshe.aerowars.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.model.WorldPos;

import java.util.UUID;

/**
 * Cross-world teleport helper. Teleporting a Hytale entity means adding a
 * {@link Teleport} component to it; that must happen on the entity's current
 * world thread, so we marshal via {@link World#execute(Runnable)}.
 */
public final class Teleports {

    private Teleports() {
    }

    /**
     * Resolves the lobby world by name, falling back to the universe's default world when the
     * configured name doesn't exist (e.g. config says "world" but the world is named "default").
     * Returning a real world prevents players from being stranded in a just-deleted match world.
     */
    public static World resolveLobby(String name) {
        World world = (name != null && !name.isBlank()) ? Universe.get().getWorld(name) : null;
        return world != null ? world : Universe.get().getDefaultWorld();
    }

    /**
     * Chooses the lobby spawn: the configured "x,y,z" if set, otherwise the world's OWN spawn
     * point (on the ground) — never a hardcoded mid-air Y that drops the player from the sky.
     */
    public static WorldPos lobbySpawn(World world, String configuredSpawn, UUID uuid) {
        WorldPos parsed = WorldPos.parse(configuredSpawn);
        if (parsed != null) {
            return parsed;
        }
        if (world != null) {
            try {
                ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
                if (provider != null) {
                    Transform t = provider.getSpawnPoint(world, uuid);
                    if (t != null) {
                        WorldPos wp = Locations.fromTransform(t);
                        if (wp != null) {
                            return wp;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new WorldPos(0.5, 100, 0.5);
    }

    /**
     * Relocates a player to {@code target}+{@code pos} IMMEDIATELY (synchronously) via
     * {@link PlayerRef#updatePosition}, instead of adding a {@link Teleport} component that only applies on
     * the next world tick. Used during server shutdown / world removal, where the world stops ticking so a
     * Teleport component would never process — that was leaving players saved at the (deleted) arena's
     * mid-air coordinates and falling from the sky on the next login.
     */
    public static void immediate(PlayerRef playerRef, World target, WorldPos pos) {
        if (playerRef == null || target == null || pos == null) {
            return;
        }
        try {
            playerRef.updatePosition(target, Locations.toTransform(pos), Locations.toRotation(pos));
        } catch (Exception e) {
            Console.warning("Immediate teleport failed for " + playerRef.getUsername() + ": " + e.getMessage());
        }
    }

    public static void to(PlayerRef playerRef, World target, WorldPos pos) {
        if (playerRef == null || target == null || pos == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        UUID worldUuid = playerRef.getWorldUuid();
        World current = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
        if (current == null) {
            current = target;
        }
        World cw = current;
        cw.execute(() -> {
            try {
                Store<EntityStore> store = cw.getEntityStore().getStore();
                Teleport teleport = new Teleport(target, Locations.toJomlPos(pos), Locations.toRotation(pos));
                store.addComponent(ref, Teleport.getComponentType(), teleport);
            } catch (Exception e) {
                Console.warning("Teleport failed for " + playerRef.getUsername() + ": " + e.getMessage());
            }
        });
    }
}
