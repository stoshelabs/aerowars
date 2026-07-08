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
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The spectator's living-player tracker modal (opened from the spectator hotbar). Lists every
 * living player of the match and teleports the spectator to whichever one they pick, then closes.
 */
public class SpectatorTrackerPage extends InteractiveCustomUIPage<SpectatorTrackerPage.PageData> {

    private static final int MAX_ROWS = 10;

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private List<MatchManager.LivePlayer> displayed = new ArrayList<>();

    private SpectatorTrackerPage(@Nonnull PlayerRef playerRef, @Nonnull World sourceWorld, @Nonnull AeroWars plugin) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = sourceWorld;
        this.plugin = plugin;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World sourceWorld, AeroWars plugin) {
        if (player == null || ref == null || store == null || playerRef == null || plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new SpectatorTrackerPage(playerRef, sourceWorld, plugin));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/AeroWarsSpecTracker.ui");
        commandBuilder.set("#SpecTitle.Text", Tr.t("spectator.ui_title"));
        commandBuilder.set("#SpecSubtitle.Text", Tr.t("spectator.ui_subtitle"));
        commandBuilder.set("#SpecCloseButton.Text", Tr.t("spectator.ui_btn_close"));

        this.displayed = plugin.getMatchManager().livePlayersFor(this.playerRef.getUuid());
        commandBuilder.set("#SpecCountLabel.Text", Tr.t("spectator.ui_count", "n", this.displayed.size()));
        commandBuilder.set("#SpecEmptyLabel.Text", Tr.t("spectator.ui_empty"));
        commandBuilder.set("#SpecEmptyLabel.Visible", this.displayed.isEmpty());

        String teleportLabel = Tr.t("spectator.ui_btn_teleport");
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < this.displayed.size()) {
                commandBuilder.set("#SpecRow" + i + ".Visible", true);
                commandBuilder.set("#SpecName" + i + ".Text", this.displayed.get(i).name());
                commandBuilder.set("#SpecAction" + i + ".Visible", true);
                commandBuilder.set("#SpecAction" + i + ".Text", teleportLabel);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SpecAction" + i,
                        EventData.of("Action", "Teleport").append("Param", String.valueOf(i)), false);
            } else {
                commandBuilder.set("#SpecRow" + i + ".Visible", false);
            }
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SpecCloseButton",
                EventData.of("Action", "Close"), false);
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
            case "Teleport" -> {
                int idx;
                try {
                    idx = Integer.parseInt(safe(data.param).trim());
                } catch (NumberFormatException ex) {
                    player.getPageManager().setPage(ref, store, Page.None);
                    return;
                }
                if (idx >= 0 && idx < this.displayed.size()) {
                    plugin.getMatchManager().teleportSpectatorTo(this.playerRef, this.displayed.get(idx).uuid());
                }
                // Close the modal so the spectator can actually see where they landed.
                player.getPageManager().setPage(ref, store, Page.None);
            }
            default -> {
            }
        }
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
