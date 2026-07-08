package dev.stoshe.aerowars.model;

import java.util.ArrayList;
import java.util.List;

/** A selectable loadout applied to a player when the match starts. */
public final class Kit {
    public String id;
    public String displayName;
    public String iconItem;
    public String permission;
    public int cost = 0;
    public List<KitItem> items = new ArrayList<>();

    public Kit() {
    }

    public Kit(String id, String displayName, String iconItem) {
        this.id = id;
        this.displayName = displayName;
        this.iconItem = iconItem;
    }

    public String displayName() {
        return (displayName == null || displayName.isBlank()) ? id : displayName;
    }

    /** Free = no permission node and no price. */
    public boolean isFree() {
        return (permission == null || permission.isBlank()) && cost <= 0;
    }

    /** Gated behind a permission node (may also be purchasable). */
    public boolean isPermission() {
        return permission != null && !permission.isBlank();
    }

    /** Purchasable with coins. */
    public boolean isPurchase() {
        return cost > 0;
    }
}
