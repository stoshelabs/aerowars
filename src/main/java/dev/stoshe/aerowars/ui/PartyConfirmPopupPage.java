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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.PartyManager;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Generic confirmation popup for party actions (invite / kick / promote / leave /
 * disband). Both Confirm and Cancel return to the party menu, mirroring the Plots
 * confirm-popup flow.
 */
public class PartyConfirmPopupPage extends InteractiveCustomUIPage<PartyConfirmPopupPage.PageData> {

    public enum ActionType {
        INVITE, KICK, PROMOTE, LEAVE, DISBAND
    }

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private final ActionType actionType;
    private final UUID targetUuid;
    private final String targetName;
    /** One-shot guard: the client can emit Activating on both press and release, which would run the
     *  action (and its chat messages) twice. Only the first Confirm/Cancel on this instance is honored. */
    private boolean handled;

    private PartyConfirmPopupPage(@Nonnull PlayerRef playerRef, @Nonnull World sourceWorld, @Nonnull AeroWars plugin,
            @Nonnull ActionType actionType, @Nullable UUID targetUuid, @Nullable String targetName) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = sourceWorld;
        this.plugin = plugin;
        this.actionType = actionType;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public static PartyConfirmPopupPage forInvite(PlayerRef playerRef, World world, AeroWars plugin, String targetName) {
        return new PartyConfirmPopupPage(playerRef, world, plugin, ActionType.INVITE, null, targetName);
    }

    public static PartyConfirmPopupPage forKick(PlayerRef playerRef, World world, AeroWars plugin, UUID targetUuid,
            String targetName) {
        return new PartyConfirmPopupPage(playerRef, world, plugin, ActionType.KICK, targetUuid, targetName);
    }

    public static PartyConfirmPopupPage forPromote(PlayerRef playerRef, World world, AeroWars plugin, UUID targetUuid,
            String targetName) {
        return new PartyConfirmPopupPage(playerRef, world, plugin, ActionType.PROMOTE, targetUuid, targetName);
    }

    public static PartyConfirmPopupPage forLeave(PlayerRef playerRef, World world, AeroWars plugin) {
        return new PartyConfirmPopupPage(playerRef, world, plugin, ActionType.LEAVE, null, null);
    }

    public static PartyConfirmPopupPage forDisband(PlayerRef playerRef, World world, AeroWars plugin) {
        return new PartyConfirmPopupPage(playerRef, world, plugin, ActionType.DISBAND, null, null);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/AeroWarsPartyConfirm.ui");
        String title;
        String message;
        String confirm;
        String who = safe(this.targetName);
        switch (this.actionType) {
            case INVITE -> {
                title = Tr.t("party.ui_confirm_invite_title");
                message = Tr.t("party.ui_confirm_invite_msg", "player", who);
                confirm = Tr.t("party.ui_confirm_invite_btn");
            }
            case KICK -> {
                title = Tr.t("party.ui_confirm_kick_title");
                message = Tr.t("party.ui_confirm_kick_msg", "player", who);
                confirm = Tr.t("party.ui_confirm_kick_btn");
            }
            case PROMOTE -> {
                title = Tr.t("party.ui_confirm_promote_title");
                message = Tr.t("party.ui_confirm_promote_msg", "player", who);
                confirm = Tr.t("party.ui_confirm_promote_btn");
            }
            case LEAVE -> {
                title = Tr.t("party.ui_confirm_leave_title");
                message = Tr.t("party.ui_confirm_leave_msg");
                confirm = Tr.t("party.ui_confirm_leave_btn");
            }
            default -> {
                title = Tr.t("party.ui_confirm_disband_title");
                message = Tr.t("party.ui_confirm_disband_msg");
                confirm = Tr.t("party.ui_confirm_disband_btn");
            }
        }
        commandBuilder.set("#ConfirmPopupTitle.Text", title);
        commandBuilder.set("#ConfirmPopupMessage.Text", message);
        commandBuilder.set("#BtnConfirmCancel.Text", Tr.t("party.ui_btn_cancel"));
        commandBuilder.set("#BtnConfirmConfirm.Text", confirm);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnConfirmCancel",
                EventData.of("Action", "Cancel"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnConfirmConfirm",
                EventData.of("Action", "Confirm"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (this.handled) {
            return; // ignore a duplicate press/release event for the same button
        }
        this.handled = true;
        String action = safe(data.action).trim();
        World world = resolveWorld(player);
        if ("Confirm".equals(action)) {
            perform();
        }
        // Both Confirm and Cancel return to the party menu.
        PartyMenuPage.open(player, ref, store, this.playerRef, world, this.plugin);
    }

    private void perform() {
        PartyManager pm = this.plugin.getPartyManager();
        UUID self = this.playerRef.getUuid();
        switch (this.actionType) {
            case INVITE -> pm.invite(this.playerRef, safe(this.targetName));
            case KICK -> pm.kick(self, this.targetUuid);
            case PROMOTE -> pm.promote(self, this.targetUuid);
            case LEAVE -> pm.leave(this.playerRef);
            case DISBAND -> pm.disband(this.playerRef);
        }
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
                .build();
        public String action;
    }
}
