package dev.stoshe.aerowars.model;

/**
 * A position with orientation, intentionally decoupled from any engine vector
 * type so the domain model stays serialization-friendly (Gson) and independent
 * of the exact Hytale math classes. The rotation is stored as the raw
 * (rx, ry, rz) components of the engine rotation vector so it round-trips
 * exactly. Converted to/from engine transforms only at the manager boundary.
 */
public final class WorldPos {
    public double x;
    public double y;
    public double z;
    public float rx;
    public float ry;
    public float rz;

    public WorldPos() {
    }

    public WorldPos(double x, double y, double z) {
        this(x, y, z, 0f, 0f, 0f);
    }

    public WorldPos(double x, double y, double z, float rx, float ry, float rz) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    public static WorldPos of(double x, double y, double z) {
        return new WorldPos(x, y, z);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public WorldPos copy() {
        return new WorldPos(x, y, z, rx, ry, rz);
    }

    /** Encodes as "x,y,z,rx,ry,rz" for compact persistence. */
    public String serialize() {
        return x + "," + y + "," + z + "," + rx + "," + ry + "," + rz;
    }

    /** Parses "x,y,z" or "x,y,z,rx,ry,rz"; returns null on malformed input. */
    public static WorldPos parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length < 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(p[0].trim());
            double y = Double.parseDouble(p[1].trim());
            double z = Double.parseDouble(p[2].trim());
            float rx = p.length > 3 ? Float.parseFloat(p[3].trim()) : 0f;
            float ry = p.length > 4 ? Float.parseFloat(p[4].trim()) : 0f;
            float rz = p.length > 5 ? Float.parseFloat(p[5].trim()) : 0f;
            return new WorldPos(x, y, z, rx, ry, rz);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f, %.1f)", x, y, z);
    }
}
