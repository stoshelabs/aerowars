package dev.stoshe.aerowars.interaction;

/**
 * Receives {@link PlayerInteractionEvent}s from the {@link InteractionService}. Implementations run on the
 * network/IO thread — marshal onto the world thread (e.g. {@code world.execute(...)}) before touching
 * entities, inventories or UI.
 */
@FunctionalInterface
public interface InteractionListener {
    void onInteraction(PlayerInteractionEvent event);
}
