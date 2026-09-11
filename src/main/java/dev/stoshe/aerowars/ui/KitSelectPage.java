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
import dev.stoshe.aerowars.manager.KitManager;
import dev.stoshe.aerowars.model.Kit;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kit picker modal ({@code /aerowars kit} with no id), styled like the cosmetics shop: an ownership filter
 * (all / owned / to-buy), a per-kit icon, and the kit currently in effect flagged as "in use". You can View
 * a kit's contents (all items) and Select / Buy one to apply.
 */
public class KitSelectPage extends InteractiveCustomUIPage<KitSelectPage.PageData> {

    private static final int KIT_ROWS = 10;
    private static final int ITEM_ROWS = 12;

    /** Ownership filter applied to the kit list. */
    private enum Filter { ALL, OWNED, TOBUY }

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private final String detailKitId;
    private final Filter filter;
    private List<Kit> kitView = new ArrayList<>();

    private KitSelectPage(PlayerRef p, World w, AeroWars plugin, String detailKitId, Filter filter) {
        super(p, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = p;
        this.sourceWorld = w;
        this.plugin = plugin;
        this.detailKitId = detailKitId;
        this.filter = filter == null ? Filter.ALL : filter;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World world, AeroWars plugin) {
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new KitSelectPage(playerRef, world, plugin, null, Filter.ALL));
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

    /** The kit id currently in effect for this player (their match pick, else remembered, else default). */
    private String currentKitId() {
        String id = this.plugin.getMatchManager().selectedKitId(this.playerRef.getUuid());
        return id == null ? "" : id;
    }

    private static boolean sameKit(Kit kit, String id) {
        return kit != null && kit.id != null && kit.id.equalsIgnoreCase(id);
    }

    private void buildList(UICommandBuilder cb, UIEventBuilder eb) {
        cb.set("#KitSubtitle.Text", Tr.t("kitui.subtitle"));

        // Coin balance (only when a balance-capable economy is present), for the priced kits.
        double bal = this.plugin.getEconomyService().balance(this.playerRef.getUuid());
        cb.set("#KitBalance.Text", bal >= 0 ? Tr.t("cosmetics.balance", "n", String.valueOf((long) bal)) : "");

        filterButton(cb, eb, "#KitFilterAll", Tr.t("kitui.filter_all"), this.filter == Filter.ALL, "FilterAll");
        filterButton(cb, eb, "#KitFilterOwned", Tr.t("kitui.filter_owned"), this.filter == Filter.OWNED, "FilterOwned");
        filterButton(cb, eb, "#KitFilterBuy", Tr.t("kitui.filter_buy"), this.filter == Filter.TOBUY, "FilterBuy");

        KitManager km = this.plugin.getKitManager();
        UUID uuid = this.playerRef.getUuid();
        String currentId = currentKitId();
        List<Kit> all = km.getAllKits();
        this.kitView = applyFilter(km, uuid, all);
        boolean empty = this.kitView.isEmpty();
        cb.set("#KitEmpty.Visible", empty);
        if (empty) {
            // "No kits configured" only when there genuinely are none; otherwise the current filter just
            // has nothing to show (e.g. you already own everything, or nothing left to buy).
            cb.set("#KitEmpty.Text", Tr.t(all.isEmpty() ? "kitui.empty" : "kitui.empty_filter"));
        }

        String viewLabel = Tr.t("kitui.view");
        for (int i = 0; i < KIT_ROWS; i++) {
            if (i < this.kitView.size()) {
                Kit kit = this.kitView.get(i);
                boolean selected = sameKit(kit, currentId);
                int items = kit.items == null ? 0 : kit.items.size();
                cb.set("#KitRow" + i + ".Visible", true);
                if (kit.iconItem != null && !kit.iconItem.isBlank()) {
                    cb.set("#KitIcon" + i + ".ItemId", kit.iconItem);
                } else {
                    cb.clear("#KitIcon" + i + ".ItemId");
                }
                cb.set("#KitName" + i + ".Text", kit.displayName());
                cb.set("#KitInfo" + i + ".Text", Tr.t("kitui.items", "n", items) + "  ·  " + statusLabel(km, uuid, kit));
                cb.set("#KitTag" + i + ".Text", selected ? Tr.t("kitui.tag_selected") : "");
                cb.set("#BtnKitView" + i + ".Text", viewLabel);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitView" + i,
                        EventData.of("Action", "View").append("Param", kit.id), false);
                applyActionButton(cb, eb, "#BtnKitSelect" + i, kit, uuid, selected);
            } else {
                cb.set("#KitRow" + i + ".Visible", false);
            }
        }
    }

    /** A short ownership/price word for the info line: In use is shown separately via the tag. */
    private String statusLabel(KitManager km, UUID uuid, Kit kit) {
        boolean unlocked = km.isUnlocked(uuid, kit);
        if (unlocked) {
            return kit.isFree() ? Tr.t("kitui.status_free") : Tr.t("kitui.status_owned");
        }
        if (kit.isPurchase()) {
            return Tr.t("kitui.status_cost", "n", String.valueOf(kit.cost));
        }
        return Tr.t("kitui.status_perm");
    }

    /**
     * Sets a kit's action button: In use (current kit, not clickable) / Select / Buy(cost) / Locked, based
     * on the player's unlock state.
     */
    private void applyActionButton(UICommandBuilder cb, UIEventBuilder eb, String selector, Kit kit,
            UUID uuid, boolean selected) {
        if (selected) {
            // Currently equipped: show it as selected and leave it non-clickable (nothing to do).
            cb.set(selector + ".Text", Tr.t("kitui.selected"));
            return;
        }
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

    /** Narrows the kit list to the selected ownership filter (owned = unlocked; to-buy = locked purchasable). */
    private List<Kit> applyFilter(KitManager km, UUID uuid, List<Kit> all) {
        if (this.filter == Filter.ALL) {
            return all;
        }
        List<Kit> out = new ArrayList<>();
        for (Kit kit : all) {
            boolean unlocked = km.isUnlocked(uuid, kit);
            if (this.filter == Filter.OWNED ? unlocked : (!unlocked && kit.isPurchase())) {
                out.add(kit);
            }
        }
        return out;
    }

    private void filterButton(UICommandBuilder cb, UIEventBuilder eb, String selector, String text, boolean active,
            String action) {
        // Active filter shown with ASCII brackets (the game font renders no ●/○ glyph — it drew as "?").
        cb.set(selector + ".Text", active ? "[ " + text + " ]" : text);
        eb.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    private void buildDetail(UICommandBuilder cb, UIEventBuilder eb, Kit kit) {
        cb.set("#KitDetailTitle.Text", kit.displayName());
        boolean selected = sameKit(kit, currentKitId());
        cb.set("#KitDetailTag.Text", selected ? Tr.t("kitui.tag_selected") : "");
        cb.set("#BtnKitBack.Text", Tr.t("party.ui_btn_back"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitBack", EventData.of("Action", "Back"), false);
        applyActionButton(cb, eb, "#BtnKitDetailSelect", kit, this.playerRef.getUuid(), selected);

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
                cb.set("#KitItemName" + i + ".TextSpans", KitLabels.itemName(it.itemId));
                cb.set("#KitItemQty" + i + ".Text", "x" + Math.max(1, it.count));
                cb.set("#KitItemSlot" + i + ".Text", KitLabels.section(it.container));
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
            case "View" -> openPage(player, ref, store, param, this.filter);
            case "Back" -> openPage(player, ref, store, null, this.filter);
            case "FilterAll" -> openPage(player, ref, store, null, Filter.ALL);
            case "FilterOwned" -> openPage(player, ref, store, null, Filter.OWNED);
            case "FilterBuy" -> openPage(player, ref, store, null, Filter.TOBUY);
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
                // Reopen the list so the rows reflect the new (unlocked / in-use) state.
                openPage(player, ref, store, null, this.filter);
            }
            default -> {
            }
        }
    }

    private void openPage(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String detailKitId,
            Filter filter) {
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new KitSelectPage(this.playerRef, resolveWorld(player), this.plugin, detailKitId, filter));
    }

    /** "Weapon_Sword_Iron" -> "Weapon Sword Iron" for readable display. */

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
