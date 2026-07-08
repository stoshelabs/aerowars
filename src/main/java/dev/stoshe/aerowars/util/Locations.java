package dev.stoshe.aerowars.util;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import dev.stoshe.aerowars.model.WorldPos;
import org.joml.Vector3d;

/** Converts between the engine-agnostic {@link WorldPos} and Hytale math types. */
public final class Locations {

    private Locations() {
    }

    public static Transform toTransform(WorldPos p) {
        return new Transform(p.x, p.y, p.z, p.rx, p.ry, p.rz);
    }

    public static Vector3d toJomlPos(WorldPos p) {
        return new Vector3d(p.x, p.y, p.z);
    }

    public static Rotation3f toRotation(WorldPos p) {
        return new Rotation3f(p.rx, p.ry, p.rz);
    }

    public static WorldPos fromTransform(Transform t) {
        Vector3d pos = t.getPosition();
        Rotation3f rot = t.getRotation();
        WorldPos p = new WorldPos(pos.x, pos.y, pos.z);
        if (rot != null) {
            p.rx = rot.x;
            p.ry = rot.y;
            p.rz = rot.z;
        }
        return p;
    }

    /** Centers a WorldPos on the block it sits in (x.5, y, z.5) keeping rotation. */
    public static WorldPos centerOfBlock(WorldPos p) {
        return new WorldPos(Math.floor(p.x) + 0.5, Math.floor(p.y), Math.floor(p.z) + 0.5, p.rx, p.ry, p.rz);
    }
}
