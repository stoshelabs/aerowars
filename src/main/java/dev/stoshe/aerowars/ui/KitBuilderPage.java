package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.model.Kit;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual kit editor: shows the kit's current items and the admin's whole inventory (all sections) as real
 * item icons ({@code ItemSlot}). Click an inventory item to COPY it into the kit; click a kit item to
 * remove it. This is the "copy from inventory with previews" flow (true OS drag-drop isn't available in
 * Hytale custom UI — the economy market uses the same click-to-move pattern).
 */
public class KitBuilderPage extends InteractiveCustomUIPage<KitBuilderPage.PageData> {

    private static final int KIT_SLOTS = 24;
    private static final int INV_SLOTS = 54;
    /** Inventory sections shown, in order; index = KitItem container constant. */
    private static final int[] SECTIONS = {
            KitItem.HOTBAR, KitItem.STORAGE, KitItem.BACKPACK, KitItem.ARMOR, KitItem.UTILITY, KitItem.TOOLS
    };

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private final String kitId;

    /** Grid-cell -> live inventory item (container + id + count), parallel to the rendered #InvSlot cells. */
    private final List<InvItem> invView = new ArrayList<>();

    private record InvItem(int container, String itemId, int count) {
    }

    private KitBuilderPage(PlayerRef playerRef, World world, AeroWars plugin, String kitId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = world;
        this.plugin = plugin;
        this.kitId = kitId;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World world, AeroWars plugin, String kitId) {
        if (player == null || playerRef == null || plugin == null || kitId == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new KitBuilderPage(playerRef, world, plugin, kitId));
    }

    // ===================================================================== build

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cb,
            @Nonnull UIEventBuilder eb, @Nonnull Store<EntityStore> store) {
        cb.append("Pages/AeroWarsKitBuilder.ui");
        Kit kit = this.plugin.getKitManager().getKit(this.kitId);

        cb.set("#KitBuilderTitle.Text", Tr.t("kitbuild.title"));
        cb.set("#KbNameLabel.Text", Tr.t("kitbuild.name"));
        cb.set("#KbNameField.PlaceholderText", Tr.t("kitbuild.name_ph"));
        cb.set("#KbKitLabel.Text", Tr.t("kitbuild.kit_label", "kit", kit == null ? this.kitId : kit.displayName()));
        cb.set("#KbKitHint.Text", Tr.t("kitbuild.kit_hint"));
        cb.set("#KbInvLabel.Text", Tr.t("kitbuild.inv_label"));
        cb.set("#KbInvHint.Text", Tr.t("kitbuild.inv_hint"));
        cb.set("#BtnKbBack.Text", Tr.t("party.ui_btn_back"));
        cb.set("#BtnKbSave.Text", Tr.t("kitbuild.save"));
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KbNameField",
                EventData.of("@Name", "#KbNameField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKbBack",
                EventData.of("Action", "Back"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKbSave",
                EventData.of("Action", "Save").append("@Name", "#KbNameField.Value"), false);

        buildKitGrid(cb, eb, kit);
        buildInventoryGrid(cb, eb, store, ref);
    }

    /** Fills the kit-contents grid: each item is a clickable icon that removes it on click. */
    private void buildKitGrid(UICommandBuilder cb, UIEventBuilder eb, Kit kit) {
        List<KitItem> items = kit == null || kit.items == null ? new ArrayList<>() : kit.items;
        for (int i = 0; i < KIT_SLOTS; i++) {
            String path = "#KitSlot" + i;
            cb.append(path, "Pages/AeroWarsItemSlot.ui");
            if (i < items.size() && items.get(i) != null && items.get(i).itemId != null) {
                KitItem it = items.get(i);
                cb.set(path + " #SlotItem.ItemId", it.itemId);
                cb.set(path + " #SlotQuantity.Text", it.count > 1 ? String.valueOf(it.count) : "");
                eb.addEventBinding(CustomUIEventBindingType.Activating, path + " #SlotButton",
                        EventData.of("Action", "Remove").append("Param", String.valueOf(i)), false);
            } else {
                cb.clear(path + " #SlotItem.ItemId");
                cb.set(path + " #SlotQuantity.Text", "");
            }
        }
    }

    /** Fills the inventory grid from the admin's live inventory (all sections); each cell copies into the kit. */
    private void buildInventoryGrid(UICommandBuilder cb, UIEventBuilder eb, Store<EntityStore> store,
            Ref<EntityStore> ref) {
        this.invView.clear();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            Inventory inv = player.getInventory();
            for (int container : SECTIONS) {
                ItemContainer c = section(inv, container);
                if (c == null) {
                    continue;
                }
                c.forEach((slot, stack) -> {
                    if (stack != null && !stack.isEmpty() && stack.getItemId() != null
                            && this.invView.size() < INV_SLOTS) {
                        this.invView.add(new InvItem(container, stack.getItemId(), Math.max(1, stack.getQuantity())));
                    }
                });
            }
        }
        for (int i = 0; i < INV_SLOTS; i++) {
            String path = "#InvSlot" + i;
            cb.append(path, "Pages/AeroWarsItemSlot.ui");
            if (i < this.invView.size()) {
                InvItem it = this.invView.get(i);
                cb.set(path + " #SlotItem.ItemId", it.itemId());
                cb.set(path + " #SlotQuantity.Text", it.count() > 1 ? String.valueOf(it.count()) : "");
                eb.addEventBinding(CustomUIEventBindingType.Activating, path + " #SlotButton",
                        EventData.of("Action", "Add").append("Param", String.valueOf(i)), false);
            } else {
                cb.clear(path + " #SlotItem.ItemId");
                cb.set(path + " #SlotQuantity.Text", "");
            }
        }
    }

    private static ItemContainer section(Inventory inv, int container) {
        return switch (container) {
            case KitItem.STORAGE -> inv.getStorage();
            case KitItem.BACKPACK -> inv.getBackpack();
            case KitItem.ARMOR -> inv.getArmor();
            case KitItem.UTILITY -> inv.getUtility();
            case KitItem.TOOLS -> inv.getTools();
            default -> inv.getHotbar();
        };
    }

    // =============================================================== event handling

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        String action = safe(data.action).trim();
        Kit kit = this.plugin.getKitManager().getKit(this.kitId);
        switch (action) {
            case "Add" -> {
                int idx = parseInt(data.param);
                if (kit != null && idx >= 0 && idx < this.invView.size()) {
                    InvItem it = this.invView.get(idx);
                    kit.items.add(new KitItem(it.itemId(), it.count(), -1, it.container()));
                    this.plugin.getKitManager().saveKit(kit);
                }
                reopen(player, ref, store);
            }
            case "Remove" -> {
                int idx = parseInt(data.param);
                if (kit != null && kit.items != null && idx >= 0 && idx < kit.items.size()) {
                    kit.items.remove(idx);
                    this.plugin.getKitManager().saveKit(kit);
                }
                reopen(player, ref, store);
            }
            case "Save" -> {
                if (kit != null) {
                    String name = safe(data.name).trim();
                    if (!name.isEmpty()) {
                        kit.displayName = name;
                    }
                    this.plugin.getKitManager().saveKit(kit);
                    this.playerRef.sendMessage(ChatUtil.success(Tr.t("kitbuild.saved", "kit", kit.displayName())));
                }
                backToAdmin(player, ref, store);
            }
            case "Back" -> backToAdmin(player, ref, store);
            // Bare ValueChanged (typing the name) carries no Action — do NOT reopen (would drop focus).
            default -> {
            }
        }
    }

    private void reopen(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new KitBuilderPage(this.playerRef, resolveWorld(player), this.plugin, this.kitId));
    }

    private void backToAdmin(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        AeroAdminPage.open(player, ref, store, this.playerRef, resolveWorld(player), this.plugin, "kits");
    }

    private World resolveWorld(Player player) {
        try {
            if (player != null && player.getWorld() != null) {
                return player.getWorld();
            }
        } catch (Exception ignored) {
        }
        return this.sourceWorld;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(safe(s).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action).add()
                .append(new KeyedCodec<>("Param", Codec.STRING), (o, v) -> o.param = v, o -> o.param).add()
                .append(new KeyedCodec<>("@Name", Codec.STRING), (o, v) -> o.name = v, o -> o.name).add()
                .build();
        public String action;
        public String param;
        public String name;
    }
}
