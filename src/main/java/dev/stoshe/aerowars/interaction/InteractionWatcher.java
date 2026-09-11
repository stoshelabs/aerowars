package dev.stoshe.aerowars.interaction;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;

import java.util.UUID;

/**
 * Watches inbound {@link SyncInteractionChains} packets and forwards each chain (with the sending player's
 * UUID) to the {@link InteractionAggregator}. Registered via {@code PacketAdapters.registerInbound(...)} by
 * {@link InteractionService}. Runs on the network thread.
 */
final class InteractionWatcher implements PacketWatcher {
    private final InteractionAggregator aggregator;

    InteractionWatcher(InteractionAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public void accept(PacketHandler handler, Packet packet) {
        if (!(packet instanceof SyncInteractionChains chains) || chains.updates == null) {
            return;
        }
        UUID uuid = uuidOf(handler);
        if (uuid == null) {
            return;
        }
        for (SyncInteractionChain chain : chains.updates) {
            if (chain != null) {
                aggregator.accept(uuid, chain);
            }
        }
    }

    private static UUID uuidOf(PacketHandler handler) {
        try {
            PlayerAuthentication auth = handler == null ? null : handler.getAuth();
            return auth == null ? null : auth.getUuid();
        } catch (Exception e) {
            return null;
        }
    }
}
