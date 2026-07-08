package dev.stoshe.aerowars.model;

/** Loot tier of a chest. Island (NORMAL) chests are weaker than MIDDLE chests. */
public enum ChestType {
    NORMAL,
    MIDDLE;

    /** Default loot-table name backing this chest tier. */
    public String defaultLootTable() {
        return this == MIDDLE ? "middle" : "normal";
    }
}
