package dev.stoshe.aerowars.interaction;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionType;

import org.joml.Vector3fc;

import java.util.UUID;

/**
 * A completed player interaction, surfaced by {@link InteractionService}. This is a native re-implementation
 * of the {@code PlayerInteractLib} plugin's event (we read its behaviour and rebuilt it here instead of
 * depending on it): the server streams interactions as multi-part {@code SyncInteractionChain}s, and the
 * {@link InteractionAggregator} merges each chain and emits ONE event when it reaches its terminal state.
 *
 * @param type   what the player did (e.g. {@link InteractionType#Primary} left-click,
 *               {@link InteractionType#Secondary} right-click, {@link InteractionType#Use}).
 * @param player the interacting player's UUID.
 * @param itemInHandId the id of the item held when the interaction completed (may be {@code null}/empty).
 * @param data   the interaction's positional/target payload (hit location, aimed block, hit entity) — may be
 *               {@code null}. Lets tools act on WHERE the click landed, even in open air (no block).
 */
public record PlayerInteractionEvent(InteractionType type, UUID player, String itemInHandId,
        InteractionChainData data) {

    /** True when the held item id equals {@code id} (null-safe). */
    public boolean isHolding(String id) {
        return id != null && id.equals(itemInHandId);
    }

    /** True for the common "the player clicked/used" interactions a hotbar tool should react to. */
    public boolean isClickOrUse() {
        return type == InteractionType.Primary
                || type == InteractionType.Secondary
                || type == InteractionType.Use;
    }

    public boolean isLeftClick() {
        return type == InteractionType.Primary;
    }

    public boolean isRightClick() {
        return type == InteractionType.Secondary || type == InteractionType.Use;
    }

    /** The aimed block, or {@code null} for an open-air click. */
    public BlockPosition blockPosition() {
        return data == null ? null : data.blockPosition;
    }

    /** The point in space the click hit (works in open air), or {@code null} if unknown. */
    public Vector3fc hitLocation() {
        return data == null ? null : data.hitLocation;
    }

    /** The entity the click landed on ({@code <= 0} if none). */
    public int hitEntityId() {
        return data == null ? 0 : data.entityId;
    }
}
