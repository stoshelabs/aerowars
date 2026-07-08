package dev.stoshe.aerowars.model;

/** A weighted loot entry. Higher {@link #weight} = more likely to be rolled. */
public final class LootItem {
    public String itemId;
    public int minAmount = 1;
    public int maxAmount = 1;
    public int weight = 1;

    public LootItem() {
    }

    public LootItem(String itemId, int minAmount, int maxAmount, int weight) {
        this.itemId = itemId;
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.weight = Math.max(1, weight);
    }
}
