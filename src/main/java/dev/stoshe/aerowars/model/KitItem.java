package dev.stoshe.aerowars.model;

/**
 * One item granted by a kit. {@code slot < 0} means "first free slot". {@code container} is which
 * inventory section the item lives in: 0=hotbar, 1=storage, 2=backpack, 3=armor, 4=utility, 5=tools
 * (0 by default, so old kits — which only stored hotbar items — keep working).
 */
public final class KitItem {
    public static final int HOTBAR = 0;
    public static final int STORAGE = 1;
    public static final int BACKPACK = 2;
    public static final int ARMOR = 3;
    public static final int UTILITY = 4;
    public static final int TOOLS = 5;

    public String itemId;
    public int count = 1;
    public int slot = -1;
    public int container = HOTBAR;

    public KitItem() {
    }

    public KitItem(String itemId, int count) {
        this.itemId = itemId;
        this.count = Math.max(1, count);
    }

    public KitItem(String itemId, int count, int slot) {
        this(itemId, count);
        this.slot = slot;
    }

    public KitItem(String itemId, int count, int slot, int container) {
        this(itemId, count, slot);
        this.container = container;
    }
}
