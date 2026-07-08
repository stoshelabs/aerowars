package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.util.Console;

/**
 * Gives arrows (any projectile) a cosmetic trail. The engine adds a {@link ProjectileComponent} to a
 * projectile entity when it is fired, so we hook that (a {@link RefChangeSystem}, the same reliable
 * mechanism {@code MatchDeathSystem} uses) to start TRACKING the arrow, and drop it when the component is
 * removed (projectile death). {@link MatchManager} runs a fast timer that samples each tracked arrow's
 * position and spawns the shooter's selected trail along its flight.
 *
 * <p>Earlier this was an {@code EntityTickingSystem}, but a ticking system registered via
 * {@code registerSystem} is never actually ticked (it needs a tick group), so the trail never fired.
 */
public class ProjectileTrailSystem extends RefChangeSystem<EntityStore, ProjectileComponent> {
    private final MatchManager matchManager;

    public ProjectileTrailSystem(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public ComponentType<EntityStore, ProjectileComponent> componentType() {
        return ProjectileComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(ProjectileComponent.getComponentType());
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, ProjectileComponent projectile,
            Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        try {
            // DIAGNOSTIC (temporary): confirms the RefChangeSystem actually fires on projectile spawn.
            Console.info("[arrowtrail] ProjectileComponent added to an entity");
            // The creator UUID isn't set yet when the ProjectileComponent is first added (shoot() sets it
            // a moment later), so we track by which match world the projectile is in and resolve the
            // shooter live each tick.
            matchManager.trackArrow(ref, store);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, ProjectileComponent oldValue, ProjectileComponent newValue,
            Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        // no-op
    }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, ProjectileComponent value,
            Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        try {
            matchManager.untrackArrow(ref);
        } catch (Exception ignored) {
        }
    }
}
