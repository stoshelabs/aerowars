package dev.stoshe.aerowars.model;

import java.util.List;

/**
 * A cage loaded from a Hytale-native {@code .prefab.json} (the format exported by the in-game prefab
 * capture tool). Only the fields AeroWars needs to rebuild the cage are mapped; anything else in the
 * file (per-block {@code filler}/{@code components}, {@code fluids}, …) is ignored by Gson.
 *
 * <p>Each block carries a block-NAME string ({@code "Glass_Block_Red"}), which
 * {@code World.setBlock(x, y, z, name, rotation)} accepts directly — so pasting needs no int id
 * resolution. A block placed at prefab-local {@code (x,y,z)} lands at
 * {@code base + ((x,y,z) - anchor)} in the world, where {@code base} is the player's spawn block and
 * {@code anchor} is the prefab's anchor (its logical spawn point). When the prefab has no anchor
 * (all zero), the min corner of the bounding box is used instead — mirrors the plots plugin.
 */
public final class CagePrefab {
    // ---- fields as they appear in the .prefab.json (populated by Gson) ----
    public int version;
    public int blockIdVersion;
    public int anchorX;
    public int anchorY;
    public int anchorZ;
    public List<PrefabBlock> blocks;

    /** Cage name (filename without {@code .prefab.json}); set by {@code CageManager}, not from JSON. */
    public transient String name;

    // ---- bounding box, computed once from the blocks ----
    private transient boolean boundsComputed;
    private transient int minX, minY, minZ, maxX, maxY, maxZ;

    public static final class PrefabBlock {
        public int x;
        public int y;
        public int z;
        public String name;
        /** Block rotation/orientation index as captured (0 when absent) — must be reproduced on paste. */
        public int rotation;
    }

    /** True if an explicit anchor was authored (any component non-zero). Else paste is min-relative. */
    public boolean hasAnchor() {
        return anchorX != 0 || anchorY != 0 || anchorZ != 0;
    }

    private void computeBounds() {
        if (boundsComputed) {
            return;
        }
        int lminX = Integer.MAX_VALUE, lminY = Integer.MAX_VALUE, lminZ = Integer.MAX_VALUE;
        int lmaxX = Integer.MIN_VALUE, lmaxY = Integer.MIN_VALUE, lmaxZ = Integer.MIN_VALUE;
        if (blocks != null) {
            for (PrefabBlock b : blocks) {
                lminX = Math.min(lminX, b.x);
                lminY = Math.min(lminY, b.y);
                lminZ = Math.min(lminZ, b.z);
                lmaxX = Math.max(lmaxX, b.x);
                lmaxY = Math.max(lmaxY, b.y);
                lmaxZ = Math.max(lmaxZ, b.z);
            }
        }
        minX = lminX;
        minY = lminY;
        minZ = lminZ;
        maxX = lmaxX;
        maxY = lmaxY;
        maxZ = lmaxZ;
        boundsComputed = true;
    }

    /** The x/y/z the paste is measured FROM: the anchor when set, else the bounding-box min corner. */
    public int originX() {
        if (hasAnchor()) {
            return anchorX;
        }
        computeBounds();
        return minX;
    }

    public int originY() {
        if (hasAnchor()) {
            return anchorY;
        }
        computeBounds();
        return minY;
    }

    public int originZ() {
        if (hasAnchor()) {
            return anchorZ;
        }
        computeBounds();
        return minZ;
    }

    public boolean isEmpty() {
        return blocks == null || blocks.isEmpty();
    }

    public int blockCount() {
        return blocks == null ? 0 : blocks.size();
    }
}
