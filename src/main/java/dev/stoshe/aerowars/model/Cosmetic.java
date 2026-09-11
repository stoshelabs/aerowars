package dev.stoshe.aerowars.model;

/**
 * A single unlockable cosmetic (a themed cage block, a kill particle, a victory particle…).
 * Loaded from {@code cosmetics.json}. Its {@link #value} is the in-game asset id the category hook
 * consumes (block id for CAGE, particle-system id for KILL/VICTORY).
 */
public class Cosmetic {
    /** Unique id (also the persistence key in a player's owned/selected sets). */
    public String id;
    /** Category id: {@code cage} / {@code kill} / {@code victory}. */
    public String category = "cage";
    /** Display name (plain text; may carry {#hex}/&-codes for chat, stripped for UI labels). */
    public String name = "";
    /** Short tooltip/description line. */
    public String description = "";
    /**
     * Unlock mode:
     * {@code default} = comes with the account (auto-owned, no claim);
     * {@code free} = free but must be CLAIMED ("resgatar") before it counts as owned;
     * {@code purchase} = bought with coins; {@code permission} = unlocked by a permission node.
     */
    public String unlock = "free";
    /** Price in coins for {@code purchase} cosmetics. */
    public int price = 0;
    /** Permission node for {@code permission} cosmetics. */
    public String permission = "";
    /** In-game asset id applied when selected (cage block id, or particle-system id). */
    public String value = "";

    /**
     * CAGE cosmetics only: rotation in degrees (0/90/180/270) applied when the cage is pasted — points a
     * sideways prefab the right way, or (with {@link #faceMiddle}) calibrates which way this cage's front
     * faces at 0°. Ignored for non-cage cosmetics.
     */
    public int rotation = 0;

    /**
     * CAGE cosmetics only: when true, the cage is additionally rotated so its FRONT faces the arena centre
     * (the middle-chests, else the average spawn) — so cages arranged around the island face inward. Uses
     * {@link #rotation} as the front calibration. Ignored for non-cage cosmetics.
     */
    public boolean faceMiddle = false;

    public Cosmetic() {
    }

    public Cosmetic(String id, String category, String name, String description, String unlock, int price,
            String permission, String value) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.description = description;
        this.unlock = unlock;
        this.price = price;
        this.permission = permission;
        this.value = value;
    }

    public CosmeticCategory categoryEnum() {
        return CosmeticCategory.fromId(category);
    }

    /** Comes with the account — always owned, no claim/purchase needed. */
    public boolean isDefault() {
        return "default".equalsIgnoreCase(unlock);
    }

    /** Free but must be explicitly claimed ("resgatar") before it is owned. Null unlock defaults to free. */
    public boolean isFree() {
        return unlock == null || unlock.equalsIgnoreCase("free");
    }

    public boolean isPurchase() {
        return "purchase".equalsIgnoreCase(unlock);
    }

    public boolean isPermission() {
        return "permission".equalsIgnoreCase(unlock);
    }
}
