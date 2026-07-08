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
import dev.stoshe.aerowars.model.PlayerStats;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboard modal ({@code /aerowars top|rank}). Highlights the top 3 as a podium, then a paginated,
 * searchable list of the rest. Metric switches between kills and wins.
 */
public class LeaderboardPage extends InteractiveCustomUIPage<LeaderboardPage.PageData> {

    private static final int PAGE_SIZE = 8;

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private String metric;
    private int page;
    private String searchInput;
    private String searchQuery;

    private LeaderboardPage(PlayerRef p, World w, AeroWars plugin, String metric, int page, String searchInput,
            String searchQuery) {
        super(p, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = p;
        this.sourceWorld = w;
        this.plugin = plugin;
        this.metric = "wins".equalsIgnoreCase(metric) ? "wins" : "kills";
        this.page = Math.max(0, page);
        this.searchInput = safe(searchInput);
        this.searchQuery = safe(searchQuery);
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World world, AeroWars plugin, String metric) {
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new LeaderboardPage(playerRef, world, plugin, metric, 0, "", ""));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cb,
            @Nonnull UIEventBuilder eb, @Nonnull Store<EntityStore> store) {
        cb.append("Pages/AeroWarsLeaderboard.ui");
        cb.set("#LbTitle.Text", Tr.t("leaderboard.title"));
        cb.set("#LbKills.Text", Tr.t("leaderboard.kills"));
        cb.set("#LbWins.Text", Tr.t("leaderboard.wins"));
        cb.set("#LbMetricLabel.Text", "kills".equals(this.metric) ? Tr.t("leaderboard.by_kills") : Tr.t("leaderboard.by_wins"));
        cb.set("#LbSearchField.Value", this.searchInput);
        cb.set("#LbSearchField.PlaceholderText", Tr.t("leaderboard.search_placeholder"));
        cb.set("#LbSearchBtn.Text", Tr.t("party.ui_btn_search"));
        cb.set("#LbClose.Text", Tr.t("party.ui_btn_close"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#LbKills", EventData.of("Action", "Kills"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#LbWins", EventData.of("Action", "Wins"), false);
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LbSearchField",
                EventData.of("@Search", "#LbSearchField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#LbSearchBtn",
                EventData.of("Action", "Search").append("@Search", "#LbSearchField.Value"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#LbPrev", EventData.of("Action", "Prev"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#LbNext", EventData.of("Action", "Next"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#LbClose", EventData.of("Action", "Close"), false);

        List<Map.Entry<UUID, PlayerStats>> all = this.plugin.getStatsManager().top(this.metric, Integer.MAX_VALUE);

        // Podium: global top 3 (unaffected by search).
        for (int i = 0; i < 3; i++) {
            if (i < all.size()) {
                PlayerStats s = all.get(i).getValue();
                cb.set("#LbPod" + (i + 1) + "Name.Text", nameOf(s));
                cb.set("#LbPod" + (i + 1) + "Val.Text", String.valueOf(value(s)));
            } else {
                cb.set("#LbPod" + (i + 1) + "Name.Text", "-");
                cb.set("#LbPod" + (i + 1) + "Val.Text", "0");
            }
        }

        // Filtered + paginated list (remembers each entry's GLOBAL rank).
        String filter = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        List<int[]> idx = new ArrayList<>(); // [globalRank]
        List<PlayerStats> rows = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            PlayerStats s = all.get(i).getValue();
            if (!filter.isEmpty() && !nameOf(s).toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            idx.add(new int[]{i + 1});
            rows.add(s);
        }
        int totalPages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (this.page >= totalPages) {
            this.page = totalPages - 1;
        }
        cb.set("#LbEmpty.Visible", rows.isEmpty());
        cb.set("#LbEmpty.Text", Tr.t("leaderboard.empty"));
        cb.set("#LbPageLabel.Text", Tr.t("leaderboard.page", "page", this.page + 1, "pages", totalPages));

        int start = this.page * PAGE_SIZE;
        for (int r = 0; r < PAGE_SIZE; r++) {
            int gi = start + r;
            if (gi < rows.size()) {
                PlayerStats s = rows.get(gi);
                cb.set("#LbRow" + r + ".Visible", true);
                cb.set("#LbRank" + r + ".Text", "#" + idx.get(gi)[0]);
                cb.set("#LbName" + r + ".Text", nameOf(s));
                cb.set("#LbVal" + r + ".Text", Tr.t("leaderboard.row_value",
                        "value", value(s), "wins", s.wins, "kills", s.kills, "games", s.gamesPlayed));
            } else {
                cb.set("#LbRow" + r + ".Visible", false);
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
        if (data.search != null) {
            this.searchInput = data.search;
        }
        switch (safe(data.action).trim()) {
            case "Close" -> player.getPageManager().setPage(ref, store, Page.None);
            case "Kills" -> reopen(player, ref, store, "kills", 0, this.searchQuery);
            case "Wins" -> reopen(player, ref, store, "wins", 0, this.searchQuery);
            case "Search" -> reopen(player, ref, store, this.metric, 0, safe(this.searchInput).trim());
            case "Prev" -> reopen(player, ref, store, this.metric, Math.max(0, this.page - 1), this.searchQuery);
            case "Next" -> reopen(player, ref, store, this.metric, this.page + 1, this.searchQuery);
            default -> {
            }
        }
    }

    private void reopen(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String metric, int page,
            String query) {
        player.getPageManager().openCustomPage(ref, store, (CustomUIPage)
                new LeaderboardPage(this.playerRef, resolveWorld(player), this.plugin, metric, page,
                        this.searchInput, query));
    }

    private int value(PlayerStats s) {
        return "wins".equals(this.metric) ? s.wins : s.kills;
    }

    private static String nameOf(PlayerStats s) {
        return s.name == null || s.name.isBlank() ? "?" : s.name;
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
                .append(new KeyedCodec<>("@Search", Codec.STRING), (o, v) -> o.search = v, o -> o.search).add()
                .build();
        public String action;
        public String search;
    }
}
