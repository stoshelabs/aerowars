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
