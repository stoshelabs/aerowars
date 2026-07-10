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
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.LootItem;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Full admin panel ({@code /aerowars admin}) with a left sidebar of tabs (Arenas / Loot / Settings),
 * modelled on the Plots menu. Lets an admin delete arenas, clear their chests, edit the loot tables
 * (add/remove items) and toggle the main config flags — persisted immediately.
 */
public class AeroAdminPage extends InteractiveCustomUIPage<AeroAdminPage.PageData> {

    private static final int ARENA_ROWS = 8;
    private static final int LOOT_ROWS = 10;
    private static final int MATCH_ROWS = 8;
    private static final int MATCH_PLAYER_ROWS = 12;
    private static final int KIT_ROWS = 10;

    private enum Tab {
        ARENAS, LOOT, MATCHES, KITS, SETTINGS
    }

    /** Toggle rows on the Settings tab: {config flag key, lang key for the label}. */
    private static final String[][] SETTINGS = {
            {"events", "admin.set_events"},
            {"randomize", "admin.set_randomize"},
            {"fillstart", "admin.set_fillstart"},
            {"friendlyfire", "admin.set_friendlyfire"},
            {"spectate", "admin.set_spectate"},
            {"saveinv", "admin.set_saveinv"},
            {"fireworks", "admin.set_fireworks"},
            {"scoreboard", "admin.set_scoreboard"},
            {"party", "admin.set_party"},
    };

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private Tab currentTab;
    private String lootTable;
    /** When set (MATCHES tab), shows that match's player list instead of the match list. */
    private String detailMatchId;
    /** When set (KITS tab), shows that kit's item list instead of the kit list. */
    private String detailKitId;

    private List<Arena> arenaView = new ArrayList<>();
    private List<LootItem> lootView = new ArrayList<>();
    private List<Match> matchView = new ArrayList<>();
    private List<java.util.UUID> detailPlayers = new ArrayList<>();

    private AeroAdminPage(PlayerRef playerRef, World world, AeroWars plugin, Tab tab, String lootTable,
            String detailMatchId, String detailKitId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = world;
        this.plugin = plugin;
        this.currentTab = tab;
        this.lootTable = lootTable == null || lootTable.isBlank() ? "normal" : lootTable;
        this.detailMatchId = detailMatchId;
        this.detailKitId = detailKitId;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World world, AeroWars plugin, String tab) {
        if (player == null || playerRef == null || plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new AeroAdminPage(playerRef, world, plugin, tabFromId(tab), "normal", null, null));
    }

    private static Tab tabFromId(String id) {
        if (id == null) {
            return Tab.ARENAS;
        }
        return switch (id.trim().toLowerCase()) {
            case "loot" -> Tab.LOOT;
            case "matches" -> Tab.MATCHES;
            case "kits" -> Tab.KITS;
            case "settings" -> Tab.SETTINGS;
            default -> Tab.ARENAS;
        };
    }

    // ===================================================================== build

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cb,
            @Nonnull UIEventBuilder eb, @Nonnull Store<EntityStore> store) {
        cb.append("Pages/AeroWarsAdminMenu.ui");
        // Title carries the running version, plus an update note when a newer release is out.
        String title = Tr.t("admin.title") + "  v" + this.plugin.getVersion();
        if (this.plugin.isUpdateAvailable()) {
            title = title + "  — " + Tr.t("admin.update_available", "version", this.plugin.getLatestVersion());
        }
        cb.set("#AdminTitle.Text", ChatUtil.stripColor(title));

        // Sidebar nav — the current tab shows its highlighted (Active) variant.
        navButton(cb, eb, "Arenas", Tr.t("admin.tab_arenas"), this.currentTab == Tab.ARENAS, "TabArenas");
        navButton(cb, eb, "Loot", Tr.t("admin.tab_loot"), this.currentTab == Tab.LOOT, "TabLoot");
        navButton(cb, eb, "Matches", Tr.t("admin.tab_matches"), this.currentTab == Tab.MATCHES, "TabMatches");
        navButton(cb, eb, "Kits", Tr.t("admin.tab_kits"), this.currentTab == Tab.KITS, "TabKits");
        navButton(cb, eb, "Settings", Tr.t("admin.tab_settings"), this.currentTab == Tab.SETTINGS, "TabSettings");
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnAdminClose", EventData.of("Action", "Close"), false);
        cb.set("#BtnAdminClose.Text", Tr.t("party.ui_btn_close"));

        cb.set("#PanelArenas.Visible", this.currentTab == Tab.ARENAS);
        cb.set("#PanelLoot.Visible", this.currentTab == Tab.LOOT);
        cb.set("#PanelMatches.Visible", this.currentTab == Tab.MATCHES);
        cb.set("#PanelKits.Visible", this.currentTab == Tab.KITS);
        cb.set("#PanelSettings.Visible", this.currentTab == Tab.SETTINGS);
        cb.set("#AdminPanelTitle.Text", switch (this.currentTab) {
            case ARENAS -> Tr.t("admin.tab_arenas");
            case LOOT -> Tr.t("admin.tab_loot");
            case MATCHES -> Tr.t("admin.tab_matches");
            case KITS -> Tr.t("admin.tab_kits");
            case SETTINGS -> Tr.t("admin.tab_settings");
        });

        buildArenas(cb, eb);
        buildLoot(cb, eb);
        buildMatches(cb, eb);
        buildKits(cb, eb);
        buildSettings(cb, eb);
    }

    private void buildKits(UICommandBuilder cb, UIEventBuilder eb) {
        dev.stoshe.aerowars.model.Kit detail = this.detailKitId == null ? null
                : this.plugin.getKitManager().getKit(this.detailKitId);
        boolean detailMode = detail != null;
        cb.set("#KitAdminListView.Visible", !detailMode);
        cb.set("#KitAdminDetailView.Visible", detailMode);
        if (detailMode) {
            buildKitDetail(cb, eb, detail);
        } else {
            buildKitList(cb, eb);
        }
    }

    private void buildKitList(UICommandBuilder cb, UIEventBuilder eb) {
        cb.set("#KitAdminHint.Text", Tr.t("admin.kit_save_hint"));
        cb.set("#BtnKitSave.Text", Tr.t("admin.kit_save_btn"));
        cb.set("#KitNameField.PlaceholderText", Tr.t("admin.kit_name_placeholder"));
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KitNameField",
                EventData.of("@KitName", "#KitNameField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitSave",
                EventData.of("Action", "KitSave").append("@KitName", "#KitNameField.Value"), false);
        cb.set("#BtnKitBuild.Text", Tr.t("admin.kit_build_btn"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitBuild",
                EventData.of("Action", "KitBuild").append("@KitName", "#KitNameField.Value"), false);

        List<dev.stoshe.aerowars.model.Kit> kits = this.plugin.getKitManager().getAllKits();
        cb.set("#KitAdminCount.Text", Tr.t("admin.kit_count", "n", kits.size()));
        cb.set("#KitAdminEmpty.Visible", kits.isEmpty());
        cb.set("#KitAdminEmpty.Text", Tr.t("admin.kit_empty"));
        String viewBtn = Tr.t("admin.btn_view");
        String delBtn = Tr.t("admin.btn_delete");
        for (int i = 0; i < KIT_ROWS; i++) {
            if (i < kits.size()) {
                dev.stoshe.aerowars.model.Kit kit = kits.get(i);
                int items = kit.items == null ? 0 : kit.items.size();
                cb.set("#KitAdminRow" + i + ".Visible", true);
                cb.set("#KitAdminName" + i + ".Text", kit.displayName() + "  {" + items + "}");
                cb.set("#BtnKitAdminView" + i + ".Text", viewBtn);
                cb.set("#BtnKitAdminDel" + i + ".Text", delBtn);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitAdminView" + i,
                        EventData.of("Action", "KitView").append("Param", kit.id), false);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitAdminDel" + i,
                        EventData.of("Action", "KitDelete").append("Param", kit.id), false);
            } else {
                cb.set("#KitAdminRow" + i + ".Visible", false);
            }
        }
    }

    private void buildKitDetail(UICommandBuilder cb, UIEventBuilder eb, dev.stoshe.aerowars.model.Kit kit) {
        cb.set("#KitAdminDetailTitle.Text", kit.displayName());
        cb.set("#BtnKitAdminBack.Text", Tr.t("party.ui_btn_back"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitAdminBack",
                EventData.of("Action", "KitBack"), false);
        cb.set("#BtnKitAdminEdit.Text", Tr.t("admin.kit_edit_items"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitAdminEdit",
                EventData.of("Action", "KitBuildEdit").append("Param", kit.id), false);

        // Price / permission editor.
        String meta;
        if (kit.permission != null && !kit.permission.isBlank()) {
            meta = Tr.t("admin.kit_meta_perm", "perm", kit.permission);
        } else if (kit.cost > 0) {
            meta = Tr.t("admin.kit_meta_cost", "n", String.valueOf(kit.cost));
        } else {
            meta = Tr.t("admin.kit_meta_free");
        }
        cb.set("#KitAdminMeta.Text", meta);
        cb.set("#KitCostField.PlaceholderText", Tr.t("admin.kit_cost_ph"));
        cb.set("#KitPermField.PlaceholderText", Tr.t("admin.kit_perm_ph"));
        cb.set("#BtnKitSetCost.Text", Tr.t("admin.kit_set_cost"));
        cb.set("#BtnKitSetPerm.Text", Tr.t("admin.kit_set_perm"));
        cb.set("#BtnKitFree.Text", Tr.t("admin.kit_make_free"));
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KitCostField",
                EventData.of("@KitCost", "#KitCostField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KitPermField",
                EventData.of("@KitPerm", "#KitPermField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitSetCost",
                EventData.of("Action", "KitSetCost").append("Param", kit.id)
                        .append("@KitCost", "#KitCostField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitSetPerm",
                EventData.of("Action", "KitSetPerm").append("Param", kit.id)
                        .append("@KitPerm", "#KitPermField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKitFree",
                EventData.of("Action", "KitFree").append("Param", kit.id), false);
        List<dev.stoshe.aerowars.model.KitItem> items = kit.items == null ? new ArrayList<>() : kit.items;
        cb.set("#KitAdminItemEmpty.Visible", items.isEmpty());
        cb.set("#KitAdminItemEmpty.Text", Tr.t("kitui.no_items"));
        for (int i = 0; i < 12; i++) {
            if (i < items.size()) {
                dev.stoshe.aerowars.model.KitItem it = items.get(i);
                cb.set("#KitAdminItemRow" + i + ".Visible", true);
                // Real item icon preview (native ItemSlot widget rendered from the item id).
                if (it.itemId != null && !it.itemId.isBlank()) {
                    cb.set("#KitAdminItemIcon" + i + ".ItemId", it.itemId);
                } else {
                    cb.clear("#KitAdminItemIcon" + i + ".ItemId");
                }
                cb.set("#KitAdminItemName" + i + ".Text", it.itemId == null ? "?" : it.itemId.replace('_', ' '));
                cb.set("#KitAdminItemQty" + i + ".Text", "x" + Math.max(1, it.count));
                cb.set("#KitAdminItemSlot" + i + ".Text", kitSection(it.container));
            } else {
                cb.set("#KitAdminItemRow" + i + ".Visible", false);
            }
        }
    }

    private static String kitSection(int container) {
        return switch (container) {
            case 1 -> Tr.t("admin.section_inventory");
            case 2 -> Tr.t("admin.section_backpack");
            case 3 -> Tr.t("admin.section_armor");
            case 4 -> Tr.t("admin.section_utility");
            case 5 -> Tr.t("admin.section_tools");
            default -> Tr.t("admin.section_hotbar");
        };
    }

    private void buildMatches(UICommandBuilder cb, UIEventBuilder eb) {
        Match detail = this.detailMatchId == null ? null : findMatch(this.detailMatchId);
        boolean detailMode = detail != null;
        cb.set("#MatchListView.Visible", !detailMode);
        cb.set("#MatchDetailView.Visible", detailMode);
        if (detailMode) {
            buildMatchDetail(cb, eb, detail);
        } else {
            buildMatchList(cb, eb);
        }
    }

    private void buildMatchList(UICommandBuilder cb, UIEventBuilder eb) {
        this.matchView = new ArrayList<>(this.plugin.getMatchManager().liveMatches());
        cb.set("#MatchCount.Text", Tr.t("admin.match_count", "n", this.matchView.size()));
        cb.set("#MatchEmpty.Visible", this.matchView.isEmpty());
        cb.set("#MatchEmpty.Text", Tr.t("admin.match_empty"));
        String viewBtn = Tr.t("admin.btn_view");
        String specBtn = Tr.t("admin.btn_spectate");
        String endBtn = Tr.t("admin.btn_end");
        for (int i = 0; i < MATCH_ROWS; i++) {
            if (i < this.matchView.size()) {
                Match m = this.matchView.get(i);
                String info = Tr.t("admin.match_row", "arena", m.arena.name, "state", m.state.name(),
                        "alive", m.alive.size(), "players", m.totalPlayers());
                cb.set("#MatchRow" + i + ".Visible", true);
                cb.set("#MatchName" + i + ".Text", info);
                cb.set("#BtnMatchView" + i + ".Text", viewBtn);
                cb.set("#BtnMatchSpectate" + i + ".Text", specBtn);
                cb.set("#BtnMatchEnd" + i + ".Text", endBtn);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnMatchView" + i,
                        EventData.of("Action", "MatchPlayers").append("Param", m.id), false);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnMatchSpectate" + i,
                        EventData.of("Action", "Spectate").append("Param", m.id), false);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnMatchEnd" + i,
                        EventData.of("Action", "EndMatch").append("Param", m.id), false);
            } else {
                cb.set("#MatchRow" + i + ".Visible", false);
            }
        }
    }

    private void buildMatchDetail(UICommandBuilder cb, UIEventBuilder eb, Match m) {
        cb.set("#BtnMatchBack.Text", Tr.t("party.ui_btn_back"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnMatchBack",
                EventData.of("Action", "MatchBack"), false);
        cb.set("#MatchDetailTitle.Text", Tr.t("admin.match_detail_title",
                "arena", m.arena.name, "state", m.state.name()));

        // Alive players first, then spectators.
        this.detailPlayers = new ArrayList<>();
        this.detailPlayers.addAll(m.alive);
        for (java.util.UUID s : m.spectators) {
            if (!this.detailPlayers.contains(s)) {
                this.detailPlayers.add(s);
            }
        }
        cb.set("#MatchDetailEmpty.Visible", this.detailPlayers.isEmpty());
        cb.set("#MatchDetailEmpty.Text", Tr.t("admin.match_detail_empty"));

        boolean teams = m.mode() == dev.stoshe.aerowars.model.GameMode.TEAMS;
        for (int i = 0; i < MATCH_PLAYER_ROWS; i++) {
            if (i < this.detailPlayers.size()) {
                java.util.UUID u = this.detailPlayers.get(i);
                boolean spec = m.spectators.contains(u);
                String name = m.names.getOrDefault(u, u.toString().substring(0, 8));
                dev.stoshe.aerowars.game.Team team = m.teamOf(u);
                if (teams && team != null) {
                    name = name + "  [" + team.name + "]";
                }
                int kills = m.kills.getOrDefault(u, 0);
                cb.set("#MatchPlayerRow" + i + ".Visible", true);
                cb.set("#MatchPlayerName" + i + ".Text", name);
                // Plain String only — a Message on a Label .Text crashes the client.
                cb.set("#MatchPlayerStatus" + i + ".Text",
                        ChatUtil.stripColor(Tr.t(spec ? "admin.status_spec" : "admin.status_alive")));
                cb.set("#MatchPlayerKills" + i + ".Text", Tr.t("admin.kills", "n", kills));
            } else {
                cb.set("#MatchPlayerRow" + i + ".Visible", false);
            }
        }
    }

    /** Snapshots the admin's whole inventory (hotbar/armor/etc.) into a named kit. */
    private void saveKitFromHotbar(Player player, String name) {
        String n = safe(name).trim();
        if (!dev.stoshe.aerowars.manager.KitManager.isValidName(n)) {
            this.playerRef.sendMessage(dev.stoshe.aerowars.util.ChatUtil.error(Tr.t("kit.save_invalid_name")));
            return;
        }
        try {
            int count = this.plugin.getKitManager().saveFromInventory(n, player.getInventory());
            if (count > 0) {
                this.playerRef.sendMessage(dev.stoshe.aerowars.util.ChatUtil.success(
                        Tr.t("kit.saved", "kit", n, "n", count)));
            } else {
                this.playerRef.sendMessage(dev.stoshe.aerowars.util.ChatUtil.error(Tr.t("kit.save_empty")));
            }
        } catch (Exception e) {
            this.playerRef.sendMessage(dev.stoshe.aerowars.util.ChatUtil.error(Tr.t("kit.save_failed")));
        }
    }

    /** Renders a sidebar nav item: shows the highlighted Active variant for the current tab. */
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

    private Match findMatch(String id) {
        for (Match m : this.plugin.getMatchManager().liveMatches()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    private void buildArenas(UICommandBuilder cb, UIEventBuilder eb) {
        this.arenaView = this.plugin.getArenaManager().getAllArenas();
        cb.set("#ArenaCount.Text", Tr.t("admin.arena_count", "n", this.arenaView.size()));
        cb.set("#ArenaEmpty.Visible", this.arenaView.isEmpty());
        cb.set("#ArenaEmpty.Text", Tr.t("admin.arena_empty"));
        String editBtn = Tr.t("admin.btn_edit");
        String chestsBtn = Tr.t("admin.btn_clear_chests");
        String delBtn = Tr.t("admin.btn_delete");
        for (int i = 0; i < ARENA_ROWS; i++) {
            if (i < this.arenaView.size()) {
                Arena a = this.arenaView.get(i);
                String mode = a.mode() == dev.stoshe.aerowars.model.GameMode.TEAMS
                        ? Tr.t("scoreboard.mode_teams") + " x" + a.effectiveTeamSize()
                        : Tr.t("scoreboard.mode_solo");
                // Flag draft (chest-less / unplayable) arenas so admins see why one isn't in rotation.
                String draftTag = a.draft ? "  " + Tr.t("admin.arena_draft_tag") : "";
                dev.stoshe.aerowars.model.MapLayout aLayout = this.plugin.getMapManager().layoutFor(a);
                int aMax = this.plugin.getMapManager().maxPlayersFor(a);
                int aChests = aLayout == null ? 0 : aLayout.allChests().size();
                String info = a.name + draftTag + "  {" + mode + ", " + aMax + "p, "
                        + aChests + " chests}";
                cb.set("#ArenaRow" + i + ".Visible", true);
                cb.set("#ArenaName" + i + ".Text", info);
                cb.set("#BtnArenaEdit" + i + ".Text", editBtn);
                cb.set("#BtnArenaChests" + i + ".Text", chestsBtn);
                cb.set("#BtnArenaDelete" + i + ".Text", delBtn);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnArenaEdit" + i,
                        EventData.of("Action", "ArenaEdit").append("Param", String.valueOf(i)), false);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnArenaChests" + i,
                        EventData.of("Action", "ArenaChests").append("Param", String.valueOf(i)), false);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnArenaDelete" + i,
                        EventData.of("Action", "ArenaDelete").append("Param", String.valueOf(i)), false);
            } else {
                cb.set("#ArenaRow" + i + ".Visible", false);
            }
        }
    }

    private void buildLoot(UICommandBuilder cb, UIEventBuilder eb) {
        cb.set("#LootTableLabel.Text", Tr.t("admin.loot_table", "table", this.lootTable));
        cb.set("#BtnLootNormal.Text", "normal");
        cb.set("#BtnLootMiddle.Text", "middle");
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnLootNormal",
                EventData.of("Action", "LootTable").append("Param", "normal"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnLootMiddle",
                EventData.of("Action", "LootTable").append("Param", "middle"), false);

        cb.set("#LootAddHint.Text", Tr.t("admin.loot_add_hint"));
        cb.set("#BtnLootAdd.Text", Tr.t("admin.btn_add"));
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LootAddField",
                EventData.of("@LootAdd", "#LootAddField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnLootAdd",
                EventData.of("Action", "LootAdd").append("@LootAdd", "#LootAddField.Value"), false);

        this.lootView = this.plugin.getLootManager().getItems(this.lootTable);
        cb.set("#LootCount.Text", Tr.t("admin.loot_count", "n", this.lootView.size()));
        cb.set("#LootEmpty.Visible", this.lootView.isEmpty());
        cb.set("#LootEmpty.Text", Tr.t("admin.loot_empty"));
        String remBtn = Tr.t("admin.btn_remove");
        for (int i = 0; i < LOOT_ROWS; i++) {
            if (i < this.lootView.size()) {
                LootItem it = this.lootView.get(i);
                String info = it.itemId + "  x" + it.minAmount + "-" + it.maxAmount + "  w" + it.weight;
                cb.set("#LootRow" + i + ".Visible", true);
                cb.set("#LootName" + i + ".Text", info);
                cb.set("#BtnLootRemove" + i + ".Text", remBtn);
                eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnLootRemove" + i,
                        EventData.of("Action", "LootRemove").append("Param", String.valueOf(i)), false);
            } else {
                cb.set("#LootRow" + i + ".Visible", false);
            }
        }
    }

    private void buildSettings(UICommandBuilder cb, UIEventBuilder eb) {
        for (int i = 0; i < SETTINGS.length; i++) {
            String key = SETTINGS[i][0];
            boolean on = getFlag(key);
            cb.set("#SetLabel" + i + ".Text", Tr.t(SETTINGS[i][1]));
            cb.set("#SetToggle" + i + ".Text", on ? Tr.t("admin.on") : Tr.t("admin.off"));
            eb.addEventBinding(CustomUIEventBindingType.Activating, "#SetToggle" + i,
                    EventData.of("Action", "Toggle").append("Param", key), false);
        }
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
        World world = resolveWorld(player);
        switch (action) {
            case "Close" -> player.getPageManager().setPage(ref, store, Page.None);
            case "TabArenas" -> reopen(player, ref, store, Tab.ARENAS);
            case "TabLoot" -> reopen(player, ref, store, Tab.LOOT);
            case "TabMatches" -> reopen(player, ref, store, Tab.MATCHES);
            case "TabKits" -> reopen(player, ref, store, Tab.KITS);
            case "TabSettings" -> reopen(player, ref, store, Tab.SETTINGS);
            case "KitDelete" -> {
                String kitId = safe(data.param);
                if (!kitId.isEmpty() && this.plugin.getKitManager().hasKit(kitId)) {
                    player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                            AeroAdminConfirmPopupPage.forDeleteKit(this.playerRef, world, this.plugin, kitId));
                } else {
                    reopen(player, ref, store, Tab.KITS);
                }
            }
            case "KitView" -> reopenKitDetail(player, ref, store, safe(data.param));
            case "KitBuild" -> {
                // Create (or reuse) a kit from the name field and open the visual builder.
                String name = safe(data.kitName).trim();
                if (!dev.stoshe.aerowars.manager.KitManager.isValidName(name)) {
                    this.playerRef.sendMessage(ChatUtil.error(Tr.t("kit.save_invalid_name")));
                    reopen(player, ref, store, Tab.KITS);
                } else {
                    String id = name.toLowerCase();
                    dev.stoshe.aerowars.model.Kit kit = this.plugin.getKitManager().getKit(id);
                    if (kit == null) {
                        kit = new dev.stoshe.aerowars.model.Kit(id, name, null);
                        this.plugin.getKitManager().saveKit(kit);
                    }
                    KitBuilderPage.open(player, ref, store, this.playerRef, world, this.plugin, kit.id);
                }
            }
            case "KitBuildEdit" -> {
                String id = safe(data.param);
                if (this.plugin.getKitManager().hasKit(id)) {
                    KitBuilderPage.open(player, ref, store, this.playerRef, world, this.plugin, id);
                } else {
                    reopen(player, ref, store, Tab.KITS);
                }
            }
            case "KitBack" -> reopen(player, ref, store, Tab.KITS);
            case "KitSave" -> {
                saveKitFromHotbar(player, safe(data.kitName));
                reopen(player, ref, store, Tab.KITS);
            }
            case "KitSetCost" -> {
                setKitCost(safe(data.param), safe(data.kitCost));
                reopenKitDetail(player, ref, store, safe(data.param));
            }
            case "KitSetPerm" -> {
                setKitPermission(safe(data.param), safe(data.kitPerm));
                reopenKitDetail(player, ref, store, safe(data.param));
            }
            case "KitFree" -> {
                dev.stoshe.aerowars.model.Kit k = this.plugin.getKitManager().getKit(safe(data.param));
                if (k != null) {
                    k.cost = 0;
                    k.permission = "";
                    this.plugin.getKitManager().saveKit(k);
                    this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.kit_now_free")));
                }
                reopenKitDetail(player, ref, store, safe(data.param));
            }
            case "MatchPlayers" -> reopenMatchDetail(player, ref, store, safe(data.param));
            case "MatchBack" -> reopen(player, ref, store, Tab.MATCHES);
            case "Spectate" -> {
                // Close the panel and drop into the match as a hidden spectator.
                player.getPageManager().setPage(ref, store, Page.None);
                this.plugin.getMatchManager().adminSpectate(this.playerRef, safe(data.param));
            }
            case "EndMatch" -> {
                this.plugin.getMatchManager().adminEndMatch(safe(data.param));
                reopen(player, ref, store, Tab.MATCHES);
            }
            case "LootTable" -> {
                this.lootTable = "middle".equalsIgnoreCase(safe(data.param)) ? "middle" : "normal";
                reopen(player, ref, store, Tab.LOOT);
            }
            case "ArenaEdit" -> {
                Arena a = arenaAt(data.param);
                if (a == null) {
                    reopen(player, ref, store, Tab.ARENAS);
                } else if (this.plugin.getMatchManager().isArenaInUse(a.name)) {
                    this.playerRef.sendMessage(ChatUtil.error(Tr.t("setup.arena_in_use", "arena", a.name)));
                    reopen(player, ref, store, Tab.ARENAS);
                } else {
                    // Close the panel and drop the admin into an edit session for the arena's PRIMARY
                    // map (layout is per-map now; teleports to a setup world cloned from that template).
                    player.getPageManager().setPage(ref, store, Page.None);
                    this.plugin.getSetupSessionManager().editSession(this.playerRef, a.worldTemplate);
                }
            }
            case "ArenaDelete" -> {
                Arena a = arenaAt(data.param);
                if (a == null) {
                    reopen(player, ref, store, Tab.ARENAS);
                } else if (this.plugin.getMatchManager().isArenaInUse(a.name)) {
                    this.playerRef.sendMessage(ChatUtil.error(Tr.t("setup.arena_in_use", "arena", a.name)));
                    reopen(player, ref, store, Tab.ARENAS);
                } else {
                    player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                            AeroAdminConfirmPopupPage.forDeleteArena(this.playerRef, world, this.plugin, a.name));
                }
            }
            case "ArenaChests" -> {
                Arena a = arenaAt(data.param);
                if (a == null) {
                    reopen(player, ref, store, Tab.ARENAS);
                } else if (this.plugin.getMatchManager().isArenaInUse(a.name)) {
                    this.playerRef.sendMessage(ChatUtil.error(Tr.t("setup.arena_in_use", "arena", a.name)));
                    reopen(player, ref, store, Tab.ARENAS);
                } else {
                    player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                            AeroAdminConfirmPopupPage.forClearChests(this.playerRef, world, this.plugin, a.name));
                }
            }
            case "LootRemove" -> {
                int idx = parseInt(data.param);
                if (idx >= 0 && this.plugin.getLootManager().removeItem(this.lootTable, idx)) {
                    this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.loot_removed", "table", this.lootTable)));
                }
                reopen(player, ref, store, Tab.LOOT);
            }
            case "LootAdd" -> {
                addLoot(safe(data.lootAdd));
                reopen(player, ref, store, Tab.LOOT);
            }
            case "Toggle" -> {
                toggleFlag(safe(data.param));
                reopen(player, ref, store, Tab.SETTINGS);
            }
            // A bare ValueChanged (typing in a field) carries no Action — do NOT reopen, or every
            // keystroke rebuilds the page and the text field loses focus.
            default -> {
            }
        }
    }

    /** Parses "itemId [min] [max] [weight]" and adds it to the current table. */
    private void addLoot(String raw) {
        String s = safe(raw).trim();
        if (s.isEmpty()) {
            return;
        }
        String[] parts = s.split("\\s+");
        String id = parts[0];
        int min = parts.length > 1 ? parseIntOr(parts[1], 1) : 1;
        int max = parts.length > 2 ? parseIntOr(parts[2], min) : min;
        int weight = parts.length > 3 ? parseIntOr(parts[3], 10) : 10;
        if (this.plugin.getLootManager().addItem(this.lootTable, id, min, max, weight)) {
            this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.loot_added", "item", id, "table", this.lootTable)));
        }
    }

    // ---- config flags ----
    private boolean getFlag(String key) {
        AeroWarsConfig c = this.plugin.getConfig();
        return switch (key) {
            case "events" -> c.Loot.Events.Enabled;
            case "randomize" -> c.Loot.Events.Randomize;
            case "fillstart" -> c.Loot.FillOnStart;
            case "friendlyfire" -> c.Match.FriendlyFire;
            case "spectate" -> c.Match.SpectateOnDeath;
            case "saveinv" -> c.Match.SaveInventory;
            case "fireworks" -> c.Effects.VictoryFireworks;
            case "scoreboard" -> c.Scoreboard.Enabled;
            case "party" -> c.Party.Enabled;
            default -> false;
        };
    }

    private void toggleFlag(String key) {
        AeroWarsConfig c = this.plugin.getConfig();
        boolean v = !getFlag(key);
        switch (key) {
            case "events" -> c.Loot.Events.Enabled = v;
            case "randomize" -> c.Loot.Events.Randomize = v;
            case "fillstart" -> c.Loot.FillOnStart = v;
            case "friendlyfire" -> c.Match.FriendlyFire = v;
            case "spectate" -> c.Match.SpectateOnDeath = v;
            case "saveinv" -> c.Match.SaveInventory = v;
            case "fireworks" -> c.Effects.VictoryFireworks = v;
            case "scoreboard" -> c.Scoreboard.Enabled = v;
            case "party" -> c.Party.Enabled = v;
            default -> {
                return;
            }
        }
        this.plugin.saveConfig();
    }

    // ======================================================================== helpers

    private Arena arenaAt(String param) {
        int idx = parseInt(param);
        return idx >= 0 && idx < this.arenaView.size() ? this.arenaView.get(idx) : null;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(safe(s).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void reopen(Player player, Ref<EntityStore> ref, Store<EntityStore> store, Tab tab) {
        player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                new AeroAdminPage(this.playerRef, resolveWorld(player), this.plugin, tab, this.lootTable, null, null));
    }

    private void reopenMatchDetail(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String matchId) {
        player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                new AeroAdminPage(this.playerRef, resolveWorld(player), this.plugin, Tab.MATCHES, this.lootTable, matchId, null));
    }

    private void reopenKitDetail(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String kitId) {
        player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                new AeroAdminPage(this.playerRef, resolveWorld(player), this.plugin, Tab.KITS, this.lootTable, null, kitId));
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

    private void setKitCost(String kitId, String costStr) {
        dev.stoshe.aerowars.model.Kit k = this.plugin.getKitManager().getKit(kitId);
        if (k == null) {
            return;
        }
        try {
            int c = Math.max(0, Integer.parseInt(costStr.trim()));
            k.cost = c;
            this.plugin.getKitManager().saveKit(k);
            this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.kit_cost_set", "n", String.valueOf(c))));
        } catch (NumberFormatException e) {
            this.playerRef.sendMessage(ChatUtil.error(Tr.t("admin.kit_cost_invalid")));
        }
    }

    private void setKitPermission(String kitId, String perm) {
        dev.stoshe.aerowars.model.Kit k = this.plugin.getKitManager().getKit(kitId);
        if (k == null) {
            return;
        }
        k.permission = perm == null ? "" : perm.trim();
        this.plugin.getKitManager().saveKit(k);
        this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.kit_perm_set")));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action).add()
                .append(new KeyedCodec<>("Param", Codec.STRING), (o, v) -> o.param = v, o -> o.param).add()
                .append(new KeyedCodec<>("@LootAdd", Codec.STRING), (o, v) -> o.lootAdd = v, o -> o.lootAdd).add()
                .append(new KeyedCodec<>("@KitName", Codec.STRING), (o, v) -> o.kitName = v, o -> o.kitName).add()
                .append(new KeyedCodec<>("@KitCost", Codec.STRING), (o, v) -> o.kitCost = v, o -> o.kitCost).add()
                .append(new KeyedCodec<>("@KitPerm", Codec.STRING), (o, v) -> o.kitPerm = v, o -> o.kitPerm).add()
                .build();
        public String action;
        public String param;
        public String lootAdd;
        public String kitName;
        public String kitCost;
        public String kitPerm;
    }
}
