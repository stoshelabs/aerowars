package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.model.WorldPos;
import dev.stoshe.aerowars.util.Sounds;

import java.util.UUID;

/**
 * Handles block breaking: doubles as the setup-wizard spawn capture (breaking a
 * block marks a spawn point) and as match protection (no breaking before the
 * cages drop, and none by spectators).
 */
public class BreakInteractionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final MatchManager matchManager;
    private final SetupSessionManager setupManager;

    public BreakInteractionSystem(MatchManager matchManager, SetupSessionManager setupManager) {
        super(BreakBlockEvent.class);
        this.matchManager = matchManager;
        this.setupManager = setupManager;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();

        // Setup wizard: only a break WITH the spawn wand marks a spawn (and never actually
        // breaks the block). Without the wand, breaking is allowed so the admin can freely
        // edit the throwaway, base-only setup world (it gets deleted afterwards).
        if (setupManager.hasSession(uuid)) {
            if (setupManager.isHoldingWand(event.getItemInHand())) {
                event.setCancelled(true);
                Vector3i pos = event.getTargetBlock();
                if (pos != null) {
                    setupManager.handleSpawnClick(playerRef, new WorldPos(pos.x, pos.y, pos.z));
                }
            }
            return;
        }

        Match match = matchManager.getPlayerMatch(uuid);
        if (match == null) {
            return;
        }
        // Spectator hotbar: LEFT-click a block with a spec tool fires its action (tracker/lobby).
        if (matchManager.trySpectatorItemClick(playerRef, store)) {
            event.setCancelled(true);
            return;
        }
        if (!match.state.isRunning() || match.spectators.contains(uuid)) {
            event.setCancelled(true);
            return;
        }
        // Break allowed (running match, live player): if it's one of our glass blocks, play the break cue.
        try {
            var blockType = event.getBlockType();
            String id = blockType == null ? null : blockType.getId();
            if (id != null && id.startsWith("Glass_Block")) {
                Sounds.play(playerRef, Sounds.GLASS_BREAK, 1.0f, 1.0f);
            }
        } catch (Exception ignored) {
            // Never let a sound lookup interfere with the break itself.
        }
    }
}
