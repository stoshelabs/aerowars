package dev.stoshe.aerowars.interaction;

import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Merges the multi-part interaction chains the client streams and emits ONE {@link PlayerInteractionEvent}
 * per completed interaction. A single logical click arrives as several {@link SyncInteractionChain} updates
 * that fill in fields over time (the held item can be on an early update, the {@code Finished} state on a
 * later one), so we accumulate per {@code (player, chainId)} and fire when the chain finishes.
 *
 * <p>Native re-implementation of the {@code PlayerInteractLib} aggregator (read from its code, rebuilt
 * here). A short-lived "finished" guard dedupes trailing duplicate updates, then is cleaned up so the
 * client can reuse the same {@code chainId} later.
 */
public final class InteractionAggregator {
    private static final long FINISHED_CLEANUP_MS = 2000L;

    private final InteractionListener listener;
    private final Map<Key, Pending> pending = new ConcurrentHashMap<>();
    private final Set<Key> finished = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService cleanup =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "AeroWars-Interaction-Cleanup");
                t.setDaemon(true);
                return t;
            });

    public InteractionAggregator(InteractionListener listener) {
        this.listener = listener;
    }

    /** Feeds one chain update for a player; emits an event when the chain reaches {@link InteractionState#Finished}. */
    public void accept(UUID uuid, SyncInteractionChain chain) {
        if (uuid == null || chain == null) {
            return;
        }
        Key key = new Key(uuid, chain.chainId);
        if (finished.contains(key)) {
            return; // already emitted for this chain; ignore trailing updates
        }
        Pending p = pending.computeIfAbsent(key, k -> new Pending());
        if (chain.itemInHandId != null && !chain.itemInHandId.isBlank()) {
            p.itemInHandId = chain.itemInHandId;
        }
        if (chain.interactionType != null) {
            p.type = chain.interactionType;
        }
        if (chain.data != null) {
            p.data = chain.data;
        }
        if (chain.state == InteractionState.Finished) {
            finished.add(key);
            pending.remove(key);
            emit(uuid, p);
            scheduleCleanup(key);
        }
    }

    private void emit(UUID uuid, Pending p) {
        try {
            listener.onInteraction(new PlayerInteractionEvent(p.type, uuid, p.itemInHandId, p.data));
        } catch (Exception ignored) {
            // A misbehaving listener must not poison the aggregator.
        }
    }

    private void scheduleCleanup(Key key) {
        try {
            cleanup.schedule(() -> finished.remove(key), FINISHED_CLEANUP_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    /** Drops all buffered state for a player (call on disconnect) so nothing leaks. */
    public void forget(UUID uuid) {
        pending.keySet().removeIf(k -> k.uuid().equals(uuid));
        finished.removeIf(k -> k.uuid().equals(uuid));
    }

    public void shutdown() {
        cleanup.shutdownNow();
        pending.clear();
        finished.clear();
    }

    private record Key(UUID uuid, int chainId) {
    }

    /** Accumulated, still-open interaction for a {@code (player, chainId)}. */
    private static final class Pending {
        private String itemInHandId;
        private InteractionType type;
        private InteractionChainData data;
    }
}
