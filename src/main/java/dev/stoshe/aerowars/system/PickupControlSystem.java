package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.manager.MatchManager;

/**
 * Stops spectators (dead-player OR hidden admin) from picking up dropped items — they observe the match,
 * they don't loot it. Live/waiting players are unaffected.
 */
public class PickupControlSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {
    private final MatchManager matchManager;

    public PickupControlSystem(MatchManager matchManager) {
        super(InteractivelyPickupItemEvent.class);
        this.matchManager = matchManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, InteractivelyPickupItemEvent event) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        if (matchManager.isAnySpectator(playerRef.getUuid())) {
            event.setCancelled(true);
        }
    }
}
