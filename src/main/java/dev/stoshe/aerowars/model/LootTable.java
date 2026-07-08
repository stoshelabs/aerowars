package dev.stoshe.aerowars.model;

import java.util.ArrayList;
import java.util.List;

/** A named pool of weighted items rolled to fill a chest. */
public final class LootTable {
    public String name;
    public int minItems = 3;
    public int maxItems = 7;
    public List<LootItem> items = new ArrayList<>();

    public LootTable() {
    }

    public LootTable(String name, int minItems, int maxItems) {
        this.name = name;
        this.minItems = Math.max(0, minItems);
        this.maxItems = Math.max(this.minItems, maxItems);
    }

    public int totalWeight() {
        int total = 0;
        if (items != null) {
            for (LootItem item : items) {
                total += Math.max(1, item.weight);
            }
        }
        return total;
    }
}
