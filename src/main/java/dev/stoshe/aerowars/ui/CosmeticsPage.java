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
import dev.stoshe.aerowars.manager.CosmeticsManager;
import dev.stoshe.aerowars.model.Cosmetic;
import dev.stoshe.aerowars.model.CosmeticCategory;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The player cosmetics shop ({@code /aerowars cosmetics}). A left sidebar of tabs — Cages / Kill
 * Effects / Victory — with a list of cosmetics per tab. Each row selects (if unlocked), buys (if a
 * priced cosmetic) or shows locked (permission-gated). Purchases go through the economy.
 */
public class CosmeticsPage extends InteractiveCustomUIPage<CosmeticsPage.PageData> {

    private static final int ROWS = 10;

    /** Ownership filter applied within a category tab. */
    private enum Filter { ALL, OWNED, TOBUY }

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private CosmeticCategory tab;
    private Filter filter;
    private List<Cosmetic> view = new ArrayList<>();

    private CosmeticsPage(PlayerRef playerRef, World sourceWorld, AeroWars plugin, CosmeticCategory tab,
            Filter filter) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = sourceWorld;
        this.plugin = plugin;
        this.tab = tab == null ? CosmeticCategory.CAGE : tab;
        this.filter = filter == null ? Filter.ALL : filter;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World sourceWorld, AeroWars plugin) {
        if (player == null || playerRef == null || plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new CosmeticsPage(playerRef, sourceWorld, plugin, CosmeticCategory.CAGE, Filter.ALL));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cb, @Nonnull UIEventBuilder eb,
            @Nonnull Store<EntityStore> store) {
        cb.append("Pages/AeroWarsCosmetics.ui");
        cb.set("#CosmTitle.Text", Tr.t("cosmetics.title"));
        cb.set("#BtnCosmClose.Text", Tr.t("party.ui_btn_close"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCosmClose",
                EventData.of("Action", "Close"), false);

        navButton(cb, eb, "Cage", Tr.t("cosmetics.tab_cage"), this.tab == CosmeticCategory.CAGE, "TabCage");
        navButton(cb, eb, "Kill", Tr.t("cosmetics.tab_kill"), this.tab == CosmeticCategory.KILL, "TabKill");
        navButton(cb, eb, "Victory", Tr.t("cosmetics.tab_victory"), this.tab == CosmeticCategory.VICTORY, "TabVictory");
        navButton(cb, eb, "Trail", Tr.t("cosmetics.tab_trail"), this.tab == CosmeticCategory.TRAIL, "TabTrail");

        // Balance (only if a balance-capable economy is present).
        double bal = plugin.getEconomyService().balance(this.playerRef.getUuid());
        cb.set("#CosmBalance.Text", bal >= 0 ? Tr.t("cosmetics.balance", "n", String.valueOf((long) bal)) : "");

        cb.set("#CosmPanelTitle.Text", tabTitle());
        cb.set("#CosmSubtitle.Text", Tr.t("cosmetics.subtitle"));

        filterButton(cb, eb, "#CosmFilterAll", Tr.t("cosmetics.filter_all"), this.filter == Filter.ALL, "FilterAll");
        filterButton(cb, eb, "#CosmFilterOwned", Tr.t("cosmetics.filter_owned"), this.filter == Filter.OWNED, "FilterOwned");
        filterButton(cb, eb, "#CosmFilterBuy", Tr.t("cosmetics.filter_buy"), this.filter == Filter.TOBUY, "FilterBuy");

        CosmeticsManager cm = plugin.getCosmeticsManager();
        UUID uuid = this.playerRef.getUuid();
        this.view = applyFilter(cm, uuid, cm.byCategory(this.tab));
        cb.set("#CosmEmpty.Visible", this.view.isEmpty());
        cb.set("#CosmEmpty.Text", Tr.t("cosmetics.empty"));

        for (int i = 0; i < ROWS; i++) {
            if (i < this.view.size()) {
                Cosmetic c = this.view.get(i);
                cb.set("#CosmRow" + i + ".Visible", true);
                cb.set("#CosmName" + i + ".Text", ChatUtil.stripColor(cm.displayName(c)));
                cb.set("#CosmDesc" + i + ".Text", ChatUtil.stripColor(cm.displayDescription(c)));

                boolean selected = cm.isSelected(uuid, c);
                boolean unlocked = cm.isUnlocked(uuid, c);
                String label;
                String action = "Select";
                boolean clickable = true;
                if (selected) {
                    label = Tr.t("cosmetics.btn_selected");
                } else if (unlocked) {
                    label = Tr.t("cosmetics.btn_select");
                } else if (cm.isClaimable(c)) {
                    // free (or a paid one when no economy is installed) → claim it for free ("resgatar")
                    label = Tr.t("cosmetics.btn_claim");
                    action = "Claim";
                } else if (c.isPurchase()) {
                    label = Tr.t("cosmetics.btn_buy", "n", String.valueOf(c.price));
                    action = "Buy";
                } else {
                    // permission-gated and not held
                    label = Tr.t("cosmetics.btn_locked");
                    clickable = false;
                }
                cb.set("#CosmAction" + i + ".Text", label);
                cb.set("#CosmAction" + i + ".Visible", true);
                if (clickable) {
                    eb.addEventBinding(CustomUIEventBindingType.Activating, "#CosmAction" + i,
                            EventData.of("Action", action).append("Param", c.id), false);
                }
            } else {
                cb.set("#CosmRow" + i + ".Visible", false);
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
        switch (action) {
            case "Close" -> player.getPageManager().setPage(ref, store, Page.None);
            case "TabCage" -> switchTab(player, ref, store, CosmeticCategory.CAGE);
            case "TabKill" -> switchTab(player, ref, store, CosmeticCategory.KILL);
            case "TabVictory" -> switchTab(player, ref, store, CosmeticCategory.VICTORY);
            case "TabTrail" -> switchTab(player, ref, store, CosmeticCategory.TRAIL);
            case "FilterAll" -> setFilter(player, ref, store, Filter.ALL);
            case "FilterOwned" -> setFilter(player, ref, store, Filter.OWNED);
            case "FilterBuy" -> setFilter(player, ref, store, Filter.TOBUY);
            case "Select" -> {
                report(plugin.getCosmeticsManager().select(this.playerRef.getUuid(), safe(data.param)), data.param);
                reopen(player, ref, store);
            }
            case "Claim" -> {
                report(plugin.getCosmeticsManager().claim(this.playerRef.getUuid(), safe(data.param)), data.param);
                reopen(player, ref, store);
            }
            case "Buy" -> {
                report(plugin.getCosmeticsManager().buy(this.playerRef.getUuid(), safe(data.param)), data.param);
                reopen(player, ref, store);
            }
            default -> {
            }
        }
    }

    private void report(CosmeticsManager.Result result, String cosmeticId) {
        Cosmetic c = plugin.getCosmeticsManager().get(cosmeticId);
        String name = c != null ? ChatUtil.stripColor(plugin.getCosmeticsManager().displayName(c)) : "";
        switch (result) {
            case OK -> this.playerRef.sendMessage(ChatUtil.success(Tr.t("cosmetics.msg_equipped", "name", name)));
            case NO_ECONOMY -> this.playerRef.sendMessage(ChatUtil.error(Tr.t("cosmetics.msg_no_economy")));
            case INSUFFICIENT_FUNDS ->
                this.playerRef.sendMessage(ChatUtil.error(Tr.t("cosmetics.msg_insufficient")));
            case LOCKED_PERMISSION ->
                this.playerRef.sendMessage(ChatUtil.error(Tr.t("cosmetics.msg_locked")));
            default -> {
            }
        }
    }

    private void switchTab(Player player, Ref<EntityStore> ref, Store<EntityStore> store, CosmeticCategory to) {
        this.tab = to;
        reopen(player, ref, store);
    }

    private void setFilter(Player player, Ref<EntityStore> ref, Store<EntityStore> store, Filter to) {
        this.filter = to;
        reopen(player, ref, store);
    }

    /** Narrows a category's cosmetics to the selected ownership filter. */
    private List<Cosmetic> applyFilter(CosmeticsManager cm, UUID uuid, List<Cosmetic> all) {
        if (this.filter == Filter.ALL) {
            return all;
        }
        List<Cosmetic> out = new ArrayList<>();
        for (Cosmetic c : all) {
            boolean unlocked = cm.isUnlocked(uuid, c);
            if (this.filter == Filter.OWNED ? unlocked : (!unlocked && c.isPurchase())) {
                out.add(c);
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

    private void reopen(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new CosmeticsPage(this.playerRef, resolveWorld(player), this.plugin, this.tab, this.filter));
    }

    private String tabTitle() {
        return switch (this.tab) {
            case CAGE -> Tr.t("cosmetics.tab_cage");
            case KILL -> Tr.t("cosmetics.tab_kill");
            case VICTORY -> Tr.t("cosmetics.tab_victory");
            case TRAIL -> Tr.t("cosmetics.tab_trail");
        };
    }

    private void navButton(UICommandBuilder cb, UIEventBuilder eb, String id, String text, boolean active,
            String action) {
        cb.set("#Nav" + id + ".Text", text);
        cb.set("#Nav" + id + "Active.Text", text);
        cb.set("#Nav" + id + ".Visible", !active);
        cb.set("#Nav" + id + "Active.Visible", active);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#Nav" + id, EventData.of("Action", action), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#Nav" + id + "Active",
                EventData.of("Action", action), false);
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

    private static String safe(String value) {
        return value == null ? "" : value;
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
