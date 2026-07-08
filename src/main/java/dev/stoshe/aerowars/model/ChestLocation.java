package dev.stoshe.aerowars.model;

/** A chest placed in an arena, with its loot tier, block id, and placed rotation. */
public final class ChestLocation {
    public WorldPos pos;
    public String blockId;
    public ChestType type;
    /** RotationTuple index captured at placement (0 = none); reapplied when the chest is spawned. */
    public int rotationIndex;

    public ChestLocation() {
    }

    public ChestLocation(WorldPos pos, String blockId, ChestType type) {
        this(pos, blockId, type, 0);
    }

    public ChestLocation(WorldPos pos, String blockId, ChestType type, int rotationIndex) {
        this.pos = pos;
        this.blockId = blockId;
        this.type = type == null ? ChestType.NORMAL : type;
        this.rotationIndex = rotationIndex;
    }

    public ChestType type() {
        return type == null ? ChestType.NORMAL : type;
    }
}
