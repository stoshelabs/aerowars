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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.model.Kit;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Kit picker modal ({@code /aerowars kit} with no id): lists kits, lets you View a kit's contents
 * (all items) and Select one to apply.
 */
public class KitSelectPage extends InteractiveCustomUIPage<KitSelectPage.PageData> {

    private static final int KIT_ROWS = 10;
    private static final int ITEM_ROWS = 12;

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private final String detailKitId;
    private List<Kit> kitView = new ArrayList<>();

    private KitSelectPage(PlayerRef p, World w, AeroWars plugin, String detailKitId) {
        super(p, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = p;
        this.sourceWorld = w;
        this.plugin = plugin;
        this.detailKitId = detailKitId;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World world, AeroWars plugin) {
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new KitSelectPage(playerRef, world, plugin, null));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cb,
            @Nonnull UIEventBuilder eb, @Nonnull Store<EntityStore> store) {
        cb.append("Pages/AeroWarsKitSelect.ui");
        cb.set("#KitTitle.Text", Tr.t("kitui.title"));
        cb.set("#KitClose.Text", Tr.t("party.ui_btn_close"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#KitClose", EventData.of("Action", "Close"), false);

        Kit detail = this.detailKitId == null ? null : this.plugin.getKitManager().getKit(this.detailKitId);
        boolean detailMode = detail != null;
        cb.set("#KitListView.Visible", !detailMode);
        cb.set("#KitDetailView.Visible", detailMode);
        if (detailMode) {
            buildDetail(cb, eb, detail);
        } else {
            buildList(cb, eb);
        }
    }

    private void buildList(UICommandBuilder cb, UIEventBuilder eb) {
        cb.set("#KitSubtitle.Text", Tr.t("kitui.subtitle"));
        this.kitView = this.plugin.getKitManager().getAllKits();
        cb.set("#KitEmpty.Visible", this.kitView.isEmpty());
        cb.set("#KitEmpty.Text", Tr.t("kitui.empty"));
        String viewLabel = Tr.t("kitui.view");
        java.util.UUID uuid = this.playerRef.getUuid();
        for (int i = 0; i < KIT_ROWS; i++) {
            if (i < this.kitView.size()) {
                Kit kit = this.kitView.get(i);
                int items = kit.items == null ? 0 : kit.items.size();
                cb.set("#KitRow" + i + ".Visible", true);
                cb.set("#KitName" + i + ".Text", kit.displayName());
                cb.set("#KitInfo" + i + ".Text", Tr.t("kitui.items", "n", items));
                cb.set("#BtnKitView" + i + ".Text", viewLabel);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitView" + i,
                        EventData.of("Action", "View").append("Param", kit.id), false);
                applyActionButton(cb, eb, "#BtnKitSelect" + i, kit, uuid);
            } else {
                cb.set("#KitRow" + i + ".Visible", false);
            }
        }
    }

    /** Sets a kit's action button to Select / Buy(cost) / Locked based on the player's unlock state. */
    private void applyActionButton(UICommandBuilder cb, UIEventBuilder eb, String selector, Kit kit,
            java.util.UUID uuid) {
        boolean unlocked = this.plugin.getKitManager().isUnlocked(uuid, kit);
        if (unlocked) {
            cb.set(selector + ".Text", Tr.t("kitui.select"));
            eb.addEventBinding(CustomUIEventBindingType.Activating, selector,
                    EventData.of("Action", "Select").append("Param", kit.id), false);
        } else if (kit.isPurchase()) {
            cb.set(selector + ".Text", Tr.t("cosmetics.btn_buy", "n", String.valueOf(kit.cost)));
            eb.addEventBinding(CustomUIEventBindingType.Activating, selector,
                    EventData.of("Action", "Buy").append("Param", kit.id), false);
        } else {
            // permission-only, not held
            cb.set(selector + ".Text", Tr.t("cosmetics.btn_locked"));
        }
    }

    private void buildDetail(UICommandBuilder cb, UIEventBuilder eb, Kit kit) {
        cb.set("#KitDetailTitle.Text", kit.displayName());
        cb.set("#BtnKitBack.Text", Tr.t("party.ui_btn_back"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitBack", EventData.of("Action", "Back"), false);
        applyActionButton(cb, eb, "#BtnKitDetailSelect", kit, this.playerRef.getUuid());

        List<KitItem> items = kit.items == null ? new ArrayList<>() : kit.items;
        cb.set("#KitItemEmpty.Visible", items.isEmpty());
        cb.set("#KitItemEmpty.Text", Tr.t("kitui.no_items"));
        for (int i = 0; i < ITEM_ROWS; i++) {
            if (i < items.size()) {
                KitItem it = items.get(i);
                cb.set("#KitItemRow" + i + ".Visible", true);
                // Real item icon preview (native ItemSlot rendered from the item id).
                if (it.itemId != null && !it.itemId.isBlank()) {
                    cb.set("#KitItemIcon" + i + ".ItemId", it.itemId);
                } else {
                    cb.clear("#KitItemIcon" + i + ".ItemId");
                }
                cb.set("#KitItemName" + i + ".Text", prettyItem(it.itemId));
                cb.set("#KitItemQty" + i + ".Text", "x" + Math.max(1, it.count));
                cb.set("#KitItemSlot" + i + ".Text", containerName(it.container));
            } else {
                cb.set("#KitItemRow" + i + ".Visible", false);
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        String action = safe(data.action).trim();
        String param = safe(data.param).trim();
        switch (action) {
            case "Close" -> player.getPageManager().setPage(ref, store, Page.None);
            case "View" -> player.getPageManager().openCustomPage(ref, store,
                    (CustomUIPage) new KitSelectPage(this.playerRef, resolveWorld(player), this.plugin, param));
            case "Back" -> player.getPageManager().openCustomPage(ref, store,
                    (CustomUIPage) new KitSelectPage(this.playerRef, resolveWorld(player), this.plugin, null));
            case "Select" -> {
                if (!param.isEmpty()) {
                    this.plugin.getMatchManager().selectKit(this.playerRef, param);
                }
                player.getPageManager().setPage(ref, store, Page.None);
            }
            case "Buy" -> {
                if (!param.isEmpty()) {
                    this.plugin.getMatchManager().buyKit(this.playerRef, param);
                }
                // Reopen the list so the row reflects the new (unlocked/selected) state.
                player.getPageManager().openCustomPage(ref, store,
                        (CustomUIPage) new KitSelectPage(this.playerRef, resolveWorld(player), this.plugin, null));
            }
            default -> {
            }
        }
    }

    /** "Weapon_Sword_Iron" -> "Weapon Sword Iron" for readable display. */
    private static String prettyItem(String id) {
        return id == null ? "?" : id.replace('_', ' ');
    }

    private static String containerName(int container) {
        return switch (container) {
            case KitItem.STORAGE -> "inventory";
            case KitItem.BACKPACK -> "backpack";
            case KitItem.ARMOR -> "armor";
            case KitItem.UTILITY -> "utility";
            case KitItem.TOOLS -> "tools";
            default -> "hotbar";
        };
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

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action).add()
                .append(new KeyedCodec<>("Param", Codec.STRING), (o, v) -> o.param = v, o -> o.param).add()
                .build();
        public String action;
        public String param;
    }
}
