package dev.stoshe.aerowars.model;

/**
 * The kinds of in-match "events" that can be scheduled during a game. Everything that used to be a
 * separate loot mechanic (periodic refill, gradual common-chest upgrade) is now just an event type,
 * so admins configure ONE unified, ordered/randomizable list instead of several disjoint toggles.
 *
 * <p>Add a new mechanic by adding a constant here and handling it in
 * {@code MatchManager.fireEvent(...)}. Each type carries the lang keys used for its scoreboard label,
 * chat broadcast and on-screen title so the UI only ever shows the event itself (never a "1/3" count).
 */
public enum LootEventType {
    /** Refills every chest with a fresh roll of its own tier. */
    REFILL("refill", "event.refill_name", "event.refill_broadcast", "event.refill_title"),
    /** Upgrades a growing share of the common (island) chests to the MIDDLE table, then refills. */
    LOOT_UPGRADE("loot_upgrade", "event.upgrade_name", "event.upgrade_broadcast", "event.upgrade_title");

    private final String id;
    private final String nameKey;
    private final String broadcastKey;
    private final String titleKey;

    LootEventType(String id, String nameKey, String broadcastKey, String titleKey) {
        this.id = id;
        this.nameKey = nameKey;
        this.broadcastKey = broadcastKey;
        this.titleKey = titleKey;
    }

    public String id() {
        return id;
    }

    public String nameKey() {
        return nameKey;
    }

    public String broadcastKey() {
        return broadcastKey;
    }

    public String titleKey() {
        return titleKey;
    }

    /** Parses a config id ("refill", "loot_upgrade", ...); defaults to {@link #REFILL} when unknown. */
    public static LootEventType fromId(String id) {
        if (id != null) {
            for (LootEventType t : values()) {
                if (t.id.equalsIgnoreCase(id.trim())) {
                    return t;
                }
            }
        }
        return REFILL;
    }
}
