package dev.stoshe.aerowars.game;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.gamemode.GameModeType;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.util.Console;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Hytale 0.6's native spectator, through AeroWars' own game-mode type {@value #MODE_ID}
 * ({@code Server/Entity/GameMode/AeroWars_Spectator.json}).
 *
 * <p>Entering it makes the engine do what AeroWars used to hand-roll: flight and no-clip (the Spectator
 * movement config forces flying), invulnerability and intangibility, hiding the player from everyone who isn't
 * spectating — entity tracker and player list, while spectators still see each other — and the spectator voice
 * channel. Exiting undoes all of it.
 *
 * <p>The mode is the vanilla {@code Spectator} with two changes: the hotbar HUD is kept, and
 * {@code PreventInventoryAccess} is off — that marker also blocks SetActiveSlot, which would leave the tracker
 * and return-to-lobby tools unselectable. {@code OverrideAllInteractions} stays on, so spectators can't break,
 * place or use anything; the tools still fire because {@code PlayerMouseButtonEvent} is dispatched before any
 * interaction runs.
 *
 * <p>Game-mode types persist with the player, so someone who disconnects (or whose server stops) mid-spectate
 * logs back in still inside it — {@link #releaseIfStale} lets them out.
 */
public final class SpectatorMode {

    /** Asset id of our game mode (= file name under Server/Entity/GameMode). */
    public static final String MODE_ID = "AeroWars_Spectator";

    private SpectatorMode() {
    }

    /** True once the asset store has our game mode loaded. */
    public static boolean isAvailable() {
        try {
            return GameModeType.getAssetMap().getAsset(MODE_ID) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True if the player is currently in our spectator game mode. World thread. */
    public static boolean isIn(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) {
            return false;
        }
        try {
            return MODE_ID.equals(GameModeTypes.getCurrentTypeId(ref, store));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Puts the player into our spectator game mode. Returns true if they are in it afterwards (already being in
     * it counts). Refuses while they are in any other game-mode type — the engine won't stack them, and taking
     * over another plugin's mode isn't ours to do — so the caller falls back to the hand-rolled state. World thread.
     */
    public static boolean enter(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null || !isAvailable()) {
            return false;
        }
        try {
            String current = GameModeTypes.getCurrentTypeId(ref, store);
            if (MODE_ID.equals(current)) {
                return true;
            }
            return current == null && GameModeTypes.enter(ref, store, MODE_ID);
        } catch (Throwable t) {
            Console.warning("Could not enter the native spectator mode: " + t.getMessage());
            return false;
        }
    }

    /** Leaves our spectator game mode if the player is in it; anything else is left alone. World thread. */
    public static void exit(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!isIn(ref, store)) {
            return;
        }
        try {
            GameModeTypes.exit(ref, store);
        } catch (Throwable t) {
            Console.warning("Could not exit the native spectator mode: " + t.getMessage());
        }
    }

    /**
     * A player finished loading into a world: if they are still in our mode but no longer spectating anything
     * ({@code stillSpectating} says no), let them out — otherwise a mid-match disconnect leaves them flying and
     * invisible in the lobby.
     */
    public static void releaseIfStale(Ref<EntityStore> ref, Predicate<UUID> stillSpectating) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                if (!isIn(ref, store)) {
                    return;
                }
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                if (pr != null && !stillSpectating.test(pr.getUuid())) {
                    exit(ref, store);
                }
            } catch (Exception e) {
                Console.warning("Spectator mode recovery failed: " + e.getMessage());
            }
        });
    }
}
