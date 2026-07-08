package dev.stoshe.aerowars.util;

import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.stoshe.aerowars.model.WorldPos;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws floating debug cubes at spawn points during setup so the admin can see
 * each marked spawn (the "corner" visual), without placing real blocks. Uses the
 * client debug-shape packets, refreshed periodically by the setup scheduler.
 */
public final class SpawnMarkers {
    private static final Vector3f SPAWN_COLOR = new Vector3f(0.2f, 1.0f, 0.3f); // green (spawns)
    private static final Vector3f SPECTATOR_COLOR = new Vector3f(0.25f, 0.55f, 1.0f); // vibrant blue
    // Player-sized box (a touch bigger) so a marker is easy to spot: wide/deep ~0.9, tall ~2.0.
    private static final float SCALE_XZ = 0.9f;
    private static final float SCALE_Y = 2.0f;
    // Slightly longer than the 500ms refresh so cubes never blink out between ticks.
    private static final float DISPLAY_TIME = 1.0f;

    private SpawnMarkers() {
    }

    /** Sends one green cube per spawn point to the given admin. */
    public static void draw(PlayerRef playerRef, List<WorldPos> spawns) {
        if (playerRef == null || spawns == null || spawns.isEmpty()) {
            return;
        }
        for (WorldPos sp : spawns) {
            drawCube(playerRef, sp, SPAWN_COLOR);
        }
    }

    /** Sends a single blue cube marking the spectator spawn. */
    public static void drawSpectator(PlayerRef playerRef, WorldPos pos) {
        drawCube(playerRef, pos, SPECTATOR_COLOR);
    }

    private static void drawCube(PlayerRef playerRef, WorldPos pos, Vector3f color) {
        if (playerRef == null || pos == null) {
            return;
        }
        try {
            float x = pos.blockX() + 0.5f;
            // Rest the box on the block the marker sits on (feet at blockY), like a player.
            float y = pos.blockY() + SCALE_Y / 2.0f;
            float z = pos.blockZ() + 0.5f;
            float[] matrix = new float[16];
            matrix[0] = SCALE_XZ;
            matrix[5] = SCALE_Y;
            matrix[10] = SCALE_XZ;
            matrix[15] = 1.0f;
            matrix[12] = x;
            matrix[13] = y;
            matrix[14] = z;
            DisplayDebug packet = new DisplayDebug(
                    DebugShape.Cube, matrix, color, DISPLAY_TIME, (byte) 1, null, 1.0f);
            playerRef.getPacketHandler().write(packet);
        } catch (Exception ignored) {
        }
    }

    /** Clears all debug shapes for the admin (e.g. after a spawn is removed). */
    public static void clear(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        try {
            playerRef.getPacketHandler().write(new ClearDebugShapes());
        } catch (Exception ignored) {
        }
    }
}
