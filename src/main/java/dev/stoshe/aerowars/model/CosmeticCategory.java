package dev.stoshe.aerowars.model;

/** Cosmetic tabs. Each maps to an in-game hook (cage block, kill particle, victory particle). */
public enum CosmeticCategory {
    CAGE("cage"),
    KILL("kill"),
    VICTORY("victory"),
    TRAIL("trail");

    private final String id;

    CosmeticCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CosmeticCategory fromId(String id) {
        if (id == null) {
            return CAGE;
        }
        for (CosmeticCategory c : values()) {
            if (c.id.equalsIgnoreCase(id.trim())) {
                return c;
            }
        }
        return CAGE;
    }
}
