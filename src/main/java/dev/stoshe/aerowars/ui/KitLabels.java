package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.util.Tr;

/**
 * Display text for kit contents, shared by the kit picker and the admin panel.
 */
public final class KitLabels {

    private KitLabels() {
    }

    /**
     * An item's name as the client shows it: the item's own translation, so it reads in each player's language
     * and matches the item tooltip. Set it on a label's {@code TextSpans}. Falls back to the id (underscores as
     * spaces) for an id the asset store doesn't know — a removed or misspelled item in kits.json.
     */
    public static Message itemName(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Message.raw("?");
        }
        try {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item != null && item.getTranslationKey() != null) {
                return item.getTranslationMessage();
            }
        } catch (Throwable ignored) {
        }
        return Message.raw(itemId.replace('_', ' '));
    }

    /** The inventory section a kit item goes into (hotbar, armor, …), in the server's language. */
    public static String section(int container) {
        return switch (container) {
            case KitItem.STORAGE -> Tr.t("admin.section_inventory");
            case KitItem.BACKPACK -> Tr.t("admin.section_backpack");
            case KitItem.ARMOR -> Tr.t("admin.section_armor");
            case KitItem.UTILITY -> Tr.t("admin.section_utility");
            case KitItem.TOOLS -> Tr.t("admin.section_tools");
            default -> Tr.t("admin.section_hotbar");
        };
    }
}
