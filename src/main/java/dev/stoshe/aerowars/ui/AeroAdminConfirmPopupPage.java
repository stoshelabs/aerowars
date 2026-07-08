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
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** Confirmation popup for destructive admin-panel actions; both buttons return to the admin menu. */
public class AeroAdminConfirmPopupPage extends InteractiveCustomUIPage<AeroAdminConfirmPopupPage.PageData> {

    public enum ActionType {
        DELETE_ARENA, CLEAR_CHESTS, DELETE_KIT
    }

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    private final ActionType actionType;
    /** Target id: arena name for arena actions, kit id for DELETE_KIT. */
    private final String arena;
    private boolean handled;

    private AeroAdminConfirmPopupPage(PlayerRef playerRef, World world, AeroWars plugin, ActionType type,
            String arena) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = world;
        this.plugin = plugin;
        this.actionType = type;
        this.arena = arena;
    }

    public static AeroAdminConfirmPopupPage forDeleteArena(PlayerRef p, World w, AeroWars plugin, String arena) {
        return new AeroAdminConfirmPopupPage(p, w, plugin, ActionType.DELETE_ARENA, arena);
    }

    public static AeroAdminConfirmPopupPage forClearChests(PlayerRef p, World w, AeroWars plugin, String arena) {
        return new AeroAdminConfirmPopupPage(p, w, plugin, ActionType.CLEAR_CHESTS, arena);
    }

    public static AeroAdminConfirmPopupPage forDeleteKit(PlayerRef p, World w, AeroWars plugin, String kitId) {
        return new AeroAdminConfirmPopupPage(p, w, plugin, ActionType.DELETE_KIT, kitId);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/AeroWarsPartyConfirm.ui");
        String title;
        String message;
        String confirm;
        switch (this.actionType) {
            case DELETE_ARENA -> {
                title = Tr.t("admin.confirm_delete_arena_title");
                message = Tr.t("admin.confirm_delete_arena_msg", "arena", safe(this.arena));
                confirm = Tr.t("admin.confirm_delete_arena_btn");
            }
            case DELETE_KIT -> {
                title = Tr.t("admin.confirm_delete_kit_title");
                message = Tr.t("admin.confirm_delete_kit_msg", "kit", safe(this.arena));
                confirm = Tr.t("admin.confirm_delete_kit_btn");
            }
            default -> {
                title = Tr.t("admin.confirm_clear_chests_title");
                message = Tr.t("admin.confirm_clear_chests_msg", "arena", safe(this.arena));
                confirm = Tr.t("admin.confirm_clear_chests_btn");
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
            return;
        }
        this.handled = true;
        World world = resolveWorld(player);
        if ("Confirm".equals(safe(data.action).trim())) {
            switch (this.actionType) {
                case DELETE_ARENA -> {
                    if (this.plugin.getArenaManager().deleteArena(this.arena)) {
                        this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.arena_deleted", "arena", safe(this.arena))));
                    }
                }
                case CLEAR_CHESTS -> {
                    if (this.plugin.getArenaManager().clearChests(this.arena)) {
                        this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.chests_cleared", "arena", safe(this.arena))));
                    }
                }
                case DELETE_KIT -> {
                    if (this.plugin.getKitManager().deleteKit(this.arena)) {
                        this.playerRef.sendMessage(ChatUtil.success(Tr.t("admin.kit_deleted", "kit", safe(this.arena))));
                    }
                }
            }
        }
        String returnTab = this.actionType == ActionType.DELETE_KIT ? "kits" : "arenas";
        AeroAdminPage.open(player, ref, store, this.playerRef, world, this.plugin, returnTab);
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
                .build();
        public String action;
    }
}
