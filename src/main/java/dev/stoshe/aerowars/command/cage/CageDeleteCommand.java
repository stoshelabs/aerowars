package dev.stoshe.aerowars.command.cage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.command.CageArgType;
import dev.stoshe.aerowars.manager.CageManager;
import dev.stoshe.aerowars.ui.AeroAdminConfirmPopupPage;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars cage delete <name>} — deletes a cage prefab, behind a confirmation modal (reuses
 * {@link AeroAdminConfirmPopupPage}). Any cosmetic / arena default still pointing at it falls back to
 * the default cage at match time.
 */
public class CageDeleteCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final CageManager cageManager;
    private final RequiredArg<String> cageArg;

    public CageDeleteCommand(@Nonnull AeroWars plugin) {
        super("delete", Tr.t("command.cage.delete"));
        this.plugin = plugin;
        this.cageManager = plugin.getCageManager();
        this.cageArg = withRequiredArg("cage", Tr.t("command.arg.cage"), ArgTypes.STRING)
                .withSuggestionOverride(new CageArgType(cageManager));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        String input = cageArg.get(context);
        if (!cageManager.exists(input)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cage.not_found", "cage", String.valueOf(input))));
            return;
        }

        String name = CageManager.normalize(input);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) AeroAdminConfirmPopupPage.forDeleteCage(playerRef, world, plugin, name));
    }
}
