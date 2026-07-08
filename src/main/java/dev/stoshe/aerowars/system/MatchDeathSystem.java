package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.manager.MatchManager;

import java.util.UUID;

/**
 * Detects player deaths: the engine adds a {@link DeathComponent} to an entity
 * when it dies, so we hook its addition and forward victim + killer to the
 * {@link MatchManager}.
 */
public class MatchDeathSystem extends RefChangeSystem<EntityStore, DeathComponent> {
    private final MatchManager matchManager;

    public MatchDeathSystem(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> victimRef, DeathComponent death,
            Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        PlayerRef victim = store.getComponent(victimRef, PlayerRef.getComponentType());
        if (victim == null) {
            return;
        }
        UUID killerUuid = null;
        Damage info = death.getDeathInfo();
        if (info != null && info.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> killerRef = source.getRef();
            if (killerRef != null) {
                PlayerRef killer = store.getComponent(killerRef, PlayerRef.getComponentType());
                if (killer != null && !killer.getUuid().equals(victim.getUuid())) {
                    killerUuid = killer.getUuid();
                }
            }
        }
        matchManager.handleDeath(victim.getUuid(), killerUuid);
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, DeathComponent oldValue, DeathComponent newValue,
            Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        // no-op
    }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, DeathComponent value,
            Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        // no-op
    }
}
