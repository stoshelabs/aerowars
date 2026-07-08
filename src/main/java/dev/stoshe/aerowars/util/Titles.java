package dev.stoshe.aerowars.util;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Sends an on-screen event title/subtitle to a player (e.g. the victory banner). */
public final class Titles {
    private Titles() {
    }

    public static void show(PlayerRef playerRef, String primary, String secondary, String color, float seconds) {
        show(playerRef, primary, secondary, color, seconds, 0.3f, 0.6f);
    }

    /**
     * Same, with explicit fade-in/out. Use short fades for the per-second countdown numbers so each one
     * shows and clears cleanly within its 1s tick instead of overlapping the next (the "5..1 title doesn't
     * show right" bug).
     */
    public static void show(PlayerRef playerRef, String primary, String secondary, String color, float seconds,
            float fadeIn, float fadeOut) {
        if (playerRef == null) {
            return;
        }
        try {
            FormattedMessage p = new FormattedMessage();
            p.rawText = primary == null ? "" : primary;
            if (color != null) {
                p.color = color;
            }
            FormattedMessage s = null;
            if (secondary != null && !secondary.isBlank()) {
                s = new FormattedMessage();
                s.rawText = secondary;
            }
            ShowEventTitle packet = new ShowEventTitle(fadeIn, fadeOut, seconds, null, true, p, s);
            playerRef.getPacketHandler().write(packet);
        } catch (Exception ignored) {
        }
    }
}
