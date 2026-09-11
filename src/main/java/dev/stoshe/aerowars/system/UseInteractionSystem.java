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
import dev.stoshe.aerowars.game.Match;
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

        // Spectators (dead-player OR hidden admin) can't USE blocks — no opening chests/doors. Checked first
        // (an admin spectator has no playerMatch entry). Fire their hotbar-tool action, then swallow the use.
        if (matchManager.isAnySpectator(uuid)) {
            matchManager.trySpectatorItemClick(playerRef, store);
            event.setCancelled(true);
            return;
        }

        // Waiting hotbar: RIGHT-click a block with a kit tool fires its action instead of a block use.
        if (matchManager.tryWaitingItemClick(playerRef, store)) {
            event.setCancelled(true);
            return;
        }

        if (setupManager.hasSession(uuid)) {
            InteractionType type = event.getInteractionType();
            if (type == InteractionType.Secondary || type == InteractionType.Use) {
                Ref<EntityStore> ref = playerRef.getReference();
                Player player = store.getComponent(ref, Player.getComponentType());
                // Swallow the block-aimed right-click so the wand doesn't trigger a use; spawn removal is
                // handled by the interaction watcher (MatchManager.onInteraction) so it works in air too.
                if (player != null && setupManager.isHoldingWand(player.getInventory().getItemInHand())) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Before the cages drop (waiting/countdown), players can't open chests/doors either.
        Match match = matchManager.getPlayerMatch(uuid);
        if (match != null && !match.state.isRunning()) {
            event.setCancelled(true);
        }
    }
}
