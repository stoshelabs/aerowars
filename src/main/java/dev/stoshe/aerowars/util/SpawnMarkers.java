package dev.stoshe.aerowars.util;

import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.WorldPos;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws floating debug cubes at setup markers (spawn points, chests, spectator spawn) so the admin can see
 * everything they've placed — the "corner" visual — without placing real blocks. Uses the client debug-shape
 * packets, refreshed periodically by the setup scheduler. Colours distinguish each kind (and chest tier).
 */
public final class SpawnMarkers {
    private static final Vector3f SPAWN_COLOR = new Vector3f(0.2f, 1.0f, 0.3f); // green (spawns / cages)
    private static final Vector3f SPECTATOR_COLOR = new Vector3f(0.25f, 0.55f, 1.0f); // vibrant blue
    private static final Vector3f NORMAL_CHEST_COLOR = new Vector3f(1.0f, 0.62f, 0.1f); // amber (normal chests)
    private static final Vector3f MIDDLE_CHEST_COLOR = new Vector3f(0.72f, 0.24f, 1.0f); // purple (middle chests)
    // Player-sized box (a touch bigger) so a spawn marker is easy to spot: wide/deep ~0.9, tall ~2.0.
    private static final float SPAWN_XZ = 0.9f;
    private static final float SPAWN_Y = 2.0f;
    // Chests are a single block, so their corner is a ~1-block cube centred on the block.
    private static final float BLOCK_SIZE = 1.02f;
    // Slightly longer than the 500ms refresh so cubes never blink out between ticks.
    private static final float DISPLAY_TIME = 1.0f;

    private SpawnMarkers() {
    }

    /** Sends one green player-height cube per spawn point to the given admin. */
    public static void draw(PlayerRef playerRef, List<WorldPos> spawns) {
        if (playerRef == null || spawns == null || spawns.isEmpty()) {
            return;
        }
        for (WorldPos sp : spawns) {
            drawCube(playerRef, sp, SPAWN_COLOR, SPAWN_XZ, SPAWN_Y, SPAWN_Y / 2.0f);
        }
    }

    /** Sends one block-sized cube per chest, coloured by tier (amber = normal, purple = middle). */
    public static void drawChests(PlayerRef playerRef, List<ChestLocation> chests, boolean middle) {
        if (playerRef == null || chests == null || chests.isEmpty()) {
            return;
        }
        Vector3f color = middle ? MIDDLE_CHEST_COLOR : NORMAL_CHEST_COLOR;
        for (ChestLocation chest : chests) {
            drawCube(playerRef, chest.pos, color, BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE / 2.0f);
        }
    }

    /** Sends a single blue cube marking the spectator spawn. */
    public static void drawSpectator(PlayerRef playerRef, WorldPos pos) {
        drawCube(playerRef, pos, SPECTATOR_COLOR, SPAWN_XZ, SPAWN_Y, SPAWN_Y / 2.0f);
    }

    private static void drawCube(PlayerRef playerRef, WorldPos pos, Vector3f color,
            float scaleXZ, float scaleY, float yOffset) {
        if (playerRef == null || pos == null) {
            return;
        }
        try {
            float x = pos.blockX() + 0.5f;
            // Rest the box on the block the marker sits on (feet at blockY).
            float y = pos.blockY() + yOffset;
            float z = pos.blockZ() + 0.5f;
            float[] matrix = new float[16];
            matrix[0] = scaleXZ;
            matrix[5] = scaleY;
            matrix[10] = scaleXZ;
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

    /** Clears all debug shapes for the admin (e.g. after a marker is removed). */
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
