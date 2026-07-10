package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.model.WorldPos;
import org.joml.Vector3i;

import java.util.UUID;

/**
 * Right-click handling: during setup, removes the nearest marked spawn point (companion to
 * {@code /aerowars setup undo}); for a spectator, fires their held hotbar tool's action.
 */
public class UseInteractionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private final MatchManager matchManager;
    private final SetupSessionManager setupManager;

    public UseInteractionSystem(MatchManager matchManager, SetupSessionManager setupManager) {
        super(UseBlockEvent.Pre.class);
        this.matchManager = matchManager;
        this.setupManager = setupManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, UseBlockEvent.Pre event) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        // Spectator hotbar: RIGHT-click a block with a spec tool fires its action (tracker/lobby). The AIR
        // case is handled by PlayerMouseButtonEvent; this is the block path.
        if (matchManager.trySpectatorItemClick(playerRef, store)) {
            event.setCancelled(true);
            return;
        }
        if (!setupManager.hasSession(uuid)) {
            return;
        }
        InteractionType type = event.getInteractionType();
        if (type != InteractionType.Secondary && type != InteractionType.Use) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || !setupManager.isHoldingWand(player.getInventory().getItemInHand())) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        if (pos == null) {
            return;
        }
        event.setCancelled(true);
        setupManager.handleSpawnRemoveAt(playerRef, new WorldPos(pos.x, pos.y, pos.z));
    }
}
