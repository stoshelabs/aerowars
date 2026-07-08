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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.Party;
import dev.stoshe.aerowars.manager.PartyManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The party management modal ({@code /aerowars party}). Shows the current members,
 * lets the leader invite / kick / transfer leadership / disband and any member leave
 * or queue the party — each destructive action goes through a confirmation popup that
 * returns here, mirroring the Plots menu flow.
 */
public class PartyMenuPage extends InteractiveCustomUIPage<PartyMenuPage.PageData> {

    private static final int MAX_ROWS = 8;

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;

    /** Member UUIDs parallel to the rendered rows (row index -> uuid). */
    private List<UUID> memberView = new ArrayList<>();

    private PartyMenuPage(@Nonnull PlayerRef playerRef, @Nonnull World sourceWorld, @Nonnull AeroWars plugin) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = sourceWorld;
        this.plugin = plugin;
    }

    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World sourceWorld, AeroWars plugin) {
        if (player == null || ref == null || store == null || playerRef == null || sourceWorld == null
                || plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new PartyMenuPage(playerRef, sourceWorld, plugin));
    }

    // ===================================================================== build

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/AeroWarsPartyMenu.ui");

        PartyManager pm = this.plugin.getPartyManager();
        UUID self = this.playerRef.getUuid();
        Party party = pm.getParty(self);
        boolean inParty = party != null;
        boolean isLeader = inParty && party.isLeader(self);
        List<UUID> members = inParty ? party.members() : new ArrayList<>();
        this.memberView = members;

        // Static localized labels.
        commandBuilder.set("#PartyTitle.Text", Tr.t("party.ui_title"));
        commandBuilder.set("#PartyMembersLabel.Text", Tr.t("party.ui_members"));
        commandBuilder.set("#PartyEmpty.Text", Tr.t("party.ui_empty"));
        commandBuilder.set("#BtnInvite.Text", Tr.t("party.ui_btn_invite"));
        commandBuilder.set("#BtnQueue.Text", Tr.t("party.ui_btn_queue"));
        commandBuilder.set("#BtnLeave.Text", Tr.t("party.ui_btn_leave"));
        commandBuilder.set("#BtnDisband.Text", Tr.t("party.ui_btn_disband"));
        commandBuilder.set("#BtnClose.Text", Tr.t("party.ui_btn_close"));
        commandBuilder.set("#BtnAcceptInvite.Text", Tr.t("party.ui_btn_accept"));
        commandBuilder.set("#BtnDeclineInvite.Text", Tr.t("party.ui_btn_decline"));

        // Status line: only show the member count when in a party (the empty-state message below the
        // MEMBERS header already covers the "not in a party" case, so don't duplicate it up here).
        commandBuilder.set("#PartyStatus.Visible", inParty);
        commandBuilder.set("#PartyStatus.Text", inParty
                ? Tr.t("party.ui_status", "n", party.size(), "max", pm.maxSize())
                : "");

        // Incoming-invite banner (only when the viewer is party-less with a pending invite).
        boolean pending = !inParty && pm.hasPendingInvite(self);
        commandBuilder.set("#PartyInviteBanner.Visible", pending);
        if (pending) {
            commandBuilder.set("#PartyInviteText.Text",
                    Tr.t("party.ui_invite_banner", "player", safe(pm.pendingInviteLeaderName(self))));
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnAcceptInvite",
                EventData.of("Action", "Accept"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDeclineInvite",
                EventData.of("Action", "Decline"), false);

        commandBuilder.set("#PartyEmpty.Visible", members.isEmpty());

        // Member rows.
        String leaderTag = "  " + Tr.t("party.ui_leader_tag");
        String promoteLabel = Tr.t("party.ui_btn_promote");
        String kickLabel = Tr.t("party.ui_btn_kick");
        UUID leaderUuid = inParty ? party.leader() : null;
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < members.size()) {
                UUID member = members.get(i);
                boolean memberIsLeader = member.equals(leaderUuid);
                String name = resolveName(member) + (memberIsLeader ? leaderTag : "");
                commandBuilder.set("#PartyRow" + i + ".Visible", true);
                commandBuilder.set("#PartyName" + i + ".Text", name);
                commandBuilder.set("#BtnPromote" + i + ".Text", promoteLabel);
                commandBuilder.set("#BtnKick" + i + ".Text", kickLabel);

                // Only the leader can manage other members (not themselves, not another leader row).
                boolean canManage = isLeader && !member.equals(self);
                commandBuilder.set("#BtnPromote" + i + ".Visible", canManage);
                commandBuilder.set("#BtnKick" + i + ".Visible", canManage);
                if (canManage) {
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPromote" + i,
                            EventData.of("Action", "Promote").append("Param", String.valueOf(i)), false);
                    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKick" + i,
                            EventData.of("Action", "Kick").append("Param", String.valueOf(i)), false);
                }
            } else {
                commandBuilder.set("#PartyRow" + i + ".Visible", false);
            }
        }

        // Keep-party-together toggle (leader only): ON = never split across teams (informs the leader if
        // no arena fits the whole party on one team); OFF = an oversized party is split, overflow randomized.
        commandBuilder.set("#PartyKeepRow.Visible", isLeader);
        if (isLeader) {
            commandBuilder.set("#PartyKeepLabel.Text", Tr.t("party.ui_keep_label"));
            commandBuilder.set("#BtnKeepTogether.Text",
                    party.keepTogether() ? Tr.t("party.ui_keep_on") : Tr.t("party.ui_keep_off"));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKeepTogether",
                    EventData.of("Action", "ToggleKeep"), false);
        }

        // Footer buttons.
        boolean canInvite = pm.isEnabled() && (!inParty || isLeader);
        commandBuilder.set("#BtnInvite.Visible", canInvite);
        commandBuilder.set("#BtnQueue.Visible", isLeader);
        commandBuilder.set("#BtnLeave.Visible", inParty && !isLeader);
        commandBuilder.set("#BtnDisband.Visible", isLeader);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnInvite",
                EventData.of("Action", "Invite"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnQueue",
                EventData.of("Action", "Queue"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnLeave",
                EventData.of("Action", "Leave"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDisband",
                EventData.of("Action", "Disband"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnClose",
                EventData.of("Action", "Close"), false);
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
        PartyManager pm = this.plugin.getPartyManager();

        switch (action) {
            case "Close" -> player.getPageManager().setPage(ref, store, Page.None);
            case "Accept" -> {
                pm.accept(this.playerRef);
                reopen(player, ref, store);
            }
            case "Decline" -> {
                pm.decline(this.playerRef);
                reopen(player, ref, store);
            }
            case "Invite" -> player.getPageManager().openCustomPage(ref, store,
                    (CustomUIPage) PartyPlayerPickerPage.create(this.playerRef, world, this.plugin));
            case "ToggleKeep" -> {
                pm.toggleKeepTogether(this.playerRef.getUuid());
                reopen(player, ref, store);
            }
            case "Queue" -> {
                Party party = pm.getParty(this.playerRef.getUuid());
                if (party == null || !party.isLeader(this.playerRef.getUuid())) {
                    reopen(player, ref, store);
                    return;
                }
                int result = this.plugin.getMatchManager().joinParty(pm.onlineMembers(party), party.keepTogether());
                if (result == -2) {
                    this.playerRef.sendMessage(ChatUtil.error(Tr.t("party.keep_together_no_arena", "n", party.size())));
                    reopen(player, ref, store);
                } else if (result == -1) {
                    this.playerRef.sendMessage(ChatUtil.error(Tr.t("party.no_arena_fits", "n", party.size())));
                    reopen(player, ref, store);
                } else if (result == 0) {
                    this.playerRef.sendMessage(ChatUtil.warning(Tr.t("party.all_in_match")));
                    reopen(player, ref, store);
                } else {
                    this.playerRef.sendMessage(ChatUtil.success(Tr.t("party.queued", "n", result)));
                    player.getPageManager().setPage(ref, store, Page.None);
                }
            }
            case "Leave" -> player.getPageManager().openCustomPage(ref, store,
                    (CustomUIPage) PartyConfirmPopupPage.forLeave(this.playerRef, world, this.plugin));
            case "Disband" -> player.getPageManager().openCustomPage(ref, store,
                    (CustomUIPage) PartyConfirmPopupPage.forDisband(this.playerRef, world, this.plugin));
            case "Kick" -> {
                UUID target = memberAt(data.param);
                if (target == null) {
                    reopen(player, ref, store);
                    return;
                }
                player.getPageManager().openCustomPage(ref, store, (CustomUIPage) PartyConfirmPopupPage.forKick(
                        this.playerRef, world, this.plugin, target, resolveName(target)));
            }
            case "Promote" -> {
                UUID target = memberAt(data.param);
                if (target == null) {
                    reopen(player, ref, store);
                    return;
                }
                player.getPageManager().openCustomPage(ref, store, (CustomUIPage) PartyConfirmPopupPage.forPromote(
                        this.playerRef, world, this.plugin, target, resolveName(target)));
            }
            default -> reopen(player, ref, store);
        }
    }

    private UUID memberAt(String param) {
        int index;
        try {
            index = Integer.parseInt(safe(param).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (index < 0 || index >= this.memberView.size()) {
            return null;
        }
        return this.memberView.get(index);
    }

    // ======================================================================== helpers

    private void reopen(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new PartyMenuPage(this.playerRef, resolveWorld(player), this.plugin));
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

    private static String resolveName(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        try {
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.getUsername() != null && !ref.getUsername().isBlank()) {
                return ref.getUsername();
            }
        } catch (Exception ignored) {
        }
        return uuid.toString().substring(0, 8);
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
