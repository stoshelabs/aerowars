package dev.stoshe.aerowars.util;

import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;

/**
 * Plays AeroWars' own packaged 2D sound events to a player (UI-style feedback: victory, time/event
 * warnings, glass break). Assets live in {@code Common/Sounds/<id>.ogg} with a matching
 * {@code Server/Audio/SoundEvents/<id>.json}; ids resolve via {@link SoundEvent#getAssetMap()}.
 */
public final class Sounds {
    // Ids match the SoundEvent asset FILE names, which Hytale requires in capitalized-segment format
    // (Aerowars_Win, not aerowars_win — a lowercase key logs "incorrect format" and the sound won't resolve).
    /** Played to everyone in a match when it is won. */
    public static final String WIN = "Aerowars_Win";
    /** Short click used for time and event warnings. */
    public static final String CLICK = "Aerowars_Click";
    /** Played when one of our glass blocks is broken. */
    public static final String GLASS_BREAK = "Aerowars_Glass_Break";

    private Sounds() {
    }

    public static void play(PlayerRef pr, String soundId, float volume, float pitch) {
        if (pr == null || soundId == null) {
            return;
        }
        try {
            if (!pr.isValid()) {
                return;
            }
            int index = SoundEvent.getAssetMap().getIndex(soundId);
            if (index < 0) {
                return;
            }
            SoundUtil.playSoundEvent2dToPlayer(pr, index, SoundCategory.SFX, volume, pitch);
        } catch (Exception ignored) {
            // A missing/unregistered sound must never break gameplay.
        }
    }

    public static void play(PlayerRef pr, String soundId) {
        play(pr, soundId, 1f, 1f);
    }
}
