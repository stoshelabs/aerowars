package dev.stoshe.aerowars.interaction;

import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import dev.stoshe.aerowars.util.Console;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Native, dependency-free replacement for the {@code PlayerInteractLib} plugin: it registers a packet
 * watcher for interaction chains, aggregates them (see {@link InteractionAggregator}) and fans each
 * completed {@link PlayerInteractionEvent} out to registered {@link InteractionListener}s.
 *
 * <p>Consumers (hotbar tools for kit/spectator, the setup wand, …) call {@link #addListener} and react to
 * the item held ({@link PlayerInteractionEvent#itemInHandId}). Listeners run on the network thread — hop to
 * the world thread before touching entities/inventories/UI.
 */
public final class InteractionService {
    private final List<InteractionListener> listeners = new CopyOnWriteArrayList<>();
    private final InteractionAggregator aggregator = new InteractionAggregator(this::dispatch);
    private volatile boolean registered;

    /** Registers the inbound packet watcher. Call once, on plugin enable. */
    public void register() {
        if (registered) {
            return;
        }
        try {
            PacketAdapters.registerInbound(new InteractionWatcher(aggregator));
            registered = true;
            // System.out is what the server captures into its log file as [SOUT] (Console's Logger and its
            // direct-fd-1 stream are NOT), so this diagnostic is actually readable from the server log.
            System.out.println("[AeroWars] Interaction watcher registered (native player-interaction events).");
        } catch (Throwable t) {
            System.out.println("[AeroWars] FAILED to register the interaction watcher: " + t);
            t.printStackTrace();
        }
    }

    public void addListener(InteractionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Purge a player's buffered chains (call on disconnect). */
    public void forget(UUID uuid) {
        aggregator.forget(uuid);
    }

    public void shutdown() {
        listeners.clear();
        aggregator.shutdown();
    }

    private void dispatch(PlayerInteractionEvent event) {
        for (InteractionListener l : listeners) {
            try {
                l.onInteraction(event);
            } catch (Exception ignored) {
                // one bad listener must not stop the others
            }
        }
    }
}
