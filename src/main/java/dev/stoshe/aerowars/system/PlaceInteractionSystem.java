package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.model.WorldPos;

import java.util.UUID;

/**
 * Handles block placement: during setup, a placed block is captured as a chest
 * location for the active chest step; during a match, placement is blocked
 * before the cages drop and for spectators.
 */
public class PlaceInteractionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private final MatchManager matchManager;
    private final SetupSessionManager setupManager;

    public PlaceInteractionSystem(MatchManager matchManager, SetupSessionManager setupManager) {
        super(PlaceBlockEvent.class);
        this.matchManager = matchManager;
        this.setupManager = setupManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, PlaceBlockEvent event) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();

        // Setup wizard: a placed block registers a chest at that position.
        if (setupManager.hasSession(uuid)) {
            Vector3i pos = event.getTargetBlock();
            if (pos != null) {
                String blockId = null;
                ItemStack inHand = event.getItemInHand();
                if (inHand != null) {
                    blockId = inHand.getItemId();
                }
                RotationTuple rot = event.getRotation();
                int rotationIndex = rot != null ? rot.index() : 0;
                setupManager.handleChestPlace(playerRef, new WorldPos(pos.x, pos.y, pos.z), blockId, rotationIndex);
            }
            return;
        }

        Match match = matchManager.getPlayerMatch(uuid);
        if (match == null) {
            return;
        }
        // Spectator hotbar: RIGHT-click with a placeable spec tool (e.g. the bed) fires its action instead
        // of placing it (a bed's right-click is a PlaceBlockEvent, not a UseBlockEvent).
        if (matchManager.trySpectatorItemClick(playerRef, store)) {
            event.setCancelled(true);
            return;
        }
        if (!match.state.isRunning() || match.spectators.contains(uuid)) {
            event.setCancelled(true);
        }
    }
}
