package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Online-player picker for party invites. Follows the aero/plots picker pattern: type a name and press
 * Search to filter (a distinct {@code searchQuery} applied on the button, not live), or click a row's
 * Invite to open a confirmation that returns to the party menu. Live {@code ValueChanged} filtering is
 * unreliable, so an explicit Search button drives the filter.
 */
public class PartyPlayerPickerPage extends InteractiveCustomUIPage<PartyPlayerPickerPage.PageData> {

    private static final int MAX_ROWS = 10;

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private String searchInput;
    private String searchQuery;
    private List<PlayerEntry> displayed = new ArrayList<>();

    private PartyPlayerPickerPage(@Nonnull PlayerRef playerRef, @Nonnull World sourceWorld, @Nonnull AeroWars plugin,
            @Nonnull String searchInput, @Nonnull String searchQuery) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = sourceWorld;
        this.plugin = plugin;
        this.searchInput = safe(searchInput);
        this.searchQuery = safe(searchQuery);
    }

    public static PartyPlayerPickerPage create(@Nonnull PlayerRef playerRef, @Nonnull World sourceWorld,
            @Nonnull AeroWars plugin) {
        return new PartyPlayerPickerPage(playerRef, sourceWorld, plugin, "", "");
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/AeroWarsPartyPicker.ui");
        commandBuilder.set("#PickerTitle.Text", Tr.t("party.ui_picker_title"));
        commandBuilder.set("#PickerSubtitle.Text", Tr.t("party.ui_picker_subtitle"));
        commandBuilder.set("#PickerSearchField.PlaceholderText", Tr.t("party.ui_picker_search_placeholder"));
        commandBuilder.set("#PickerSearchField.Value", this.searchInput);
        commandBuilder.set("#PickerSubmitButton.Text", Tr.t("party.ui_btn_search"));
        commandBuilder.set("#PickerBackButton.Text", Tr.t("party.ui_btn_back"));

        this.displayed = listCandidates();
        commandBuilder.set("#PickerCountLabel.Text", Tr.t("party.ui_picker_count", "n", this.displayed.size()));
        commandBuilder.set("#PickerEmptyLabel.Text", Tr.t("party.ui_picker_empty"));
        commandBuilder.set("#PickerEmptyLabel.Visible", this.displayed.isEmpty());

        String inviteLabel = Tr.t("party.ui_btn_invite");
        UUID selfUuid = this.playerRef.getUuid();
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < this.displayed.size()) {
                PlayerEntry entry = this.displayed.get(i);
                boolean isSelf = entry.uuid().equals(selfUuid);
                commandBuilder.set("#PickerRow" + i + ".Visible", true);
                commandBuilder.set("#PickerName" + i + ".Text", entry.name() + (isSelf ? "  (you)" : ""));
                // Own row: no invite button (you can't invite yourself).
                commandBuilder.set("#PickerAction" + i + ".Visible", !isSelf);
                commandBuilder.set("#PickerAction" + i + ".Text", inviteLabel);
                if (!isSelf) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PickerAction" + i,
                            EventData.of("Action", "Select").append("Param", String.valueOf(i)), false);
                }
            } else {
                commandBuilder.set("#PickerRow" + i + ".Visible", false);
            }
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PickerSearchField",
                EventData.of("@SearchInput", "#PickerSearchField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PickerSubmitButton",
                EventData.of("Action", "Search").append("@SearchInput", "#PickerSearchField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PickerBackButton",
                EventData.of("Action", "Back"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (data.searchInput != null) {
            this.searchInput = data.searchInput;
        }
        String action = safe(data.action).trim();
        World world = resolveWorld(player);
        switch (action) {
            case "Back" -> PartyMenuPage.open(player, ref, store, this.playerRef, world, this.plugin);
            case "Search" -> {
                this.searchQuery = safe(this.searchInput).trim();
                reopen(player, ref, store);
            }
            case "Select" -> {
                int index;
                try {
                    index = Integer.parseInt(safe(data.param).trim());
                } catch (NumberFormatException ex) {
                    reopen(player, ref, store);
                    return;
                }
                if (index < 0 || index >= this.displayed.size()) {
                    reopen(player, ref, store);
                    return;
                }
                player.getPageManager().openCustomPage(ref, store, (CustomUIPage) PartyConfirmPopupPage.forInvite(
                        this.playerRef, world, this.plugin, this.displayed.get(index).name()));
            }
            // A bare ValueChanged (typing) carries no Action — just record the text and DO NOT reopen,
            // otherwise every keystroke rebuilds the page and the search field loses focus.
            default -> {
            }
        }
    }

    /** ALL online players (including the viewer), filtered by the search query. Invite validation
     *  (self / already-in-a-party) happens in {@link PartyManager#invite}, so everyone is listed. */
    private List<PlayerEntry> listCandidates() {
        List<PlayerEntry> result = new ArrayList<>();
        try {
            String filter = safe(this.searchQuery).trim().toLowerCase(Locale.ROOT);
            for (PlayerRef p : Universe.get().getPlayers()) {
                if (p == null || p.getUuid() == null) {
                    continue;
                }
                String name = p.getUsername();
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (!filter.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                result.add(new PlayerEntry(p.getUuid(), name));
                if (result.size() >= MAX_ROWS) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void reopen(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, (CustomUIPage) new PartyPlayerPickerPage(this.playerRef,
                resolveWorld(player), this.plugin, safe(this.searchInput), safe(this.searchQuery)));
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
                .append(new KeyedCodec<>("@SearchInput", Codec.STRING), (o, v) -> o.searchInput = v, o -> o.searchInput)
                .add()
                .build();
        public String action;
        public String param;
        public String searchInput;
    }

    private record PlayerEntry(UUID uuid, String name) {
    }
}
