package dev.stoshe.aerowars.util;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Fires a firework-style particle burst to an audience via {@link SpawnParticleSystem}. */
public final class Fireworks {
    private Fireworks() {
    }

    /** Spawns one particle-system burst at (x,y,z) with the given colour for every viewer. */
    public static void burst(Iterable<PlayerRef> audience, double x, double y, double z,
            String particleId, int red, int green, int blue, float scale, float duration) {
        if (audience == null || particleId == null || particleId.isBlank()) {
            return;
        }
        Position pos = new Position(x, y, z);
        Direction dir = new Direction(0f, 0f, 0f);
        Color color = new Color((byte) clamp(red), (byte) clamp(green), (byte) clamp(blue));
        for (PlayerRef pr : audience) {
            if (pr == null) {
                continue;
            }
            try {
                pr.getPacketHandler().write(new SpawnParticleSystem(particleId, pos, dir, scale, color, duration));
            } catch (Exception ignored) {
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
