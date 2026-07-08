package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.KitManager;
import dev.stoshe.aerowars.model.Kit;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars savekit <name>} — saves the caller's hotbar as a kit. */
public class SaveKitCommand extends AbstractPlayerCommand {
    private final KitManager kitManager;
    private final RequiredArg<String> nameArg;

    public SaveKitCommand(@Nonnull AeroWars plugin) {
        super("savekit", "Salvar seu inventário como um kit (admin)");
        this.kitManager = plugin.getKitManager();
        this.nameArg = withRequiredArg("nome", "Nome do kit", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        String name = nameArg.get(context);
        if (name == null || name.length() < 2 || name.length() > 16 || !name.matches("^[a-zA-Z0-9_]+$")) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("kit.save_invalid_name")));
            return;
        }
        world.execute(() -> {
            try {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                int count = kitManager.saveFromInventory(name, player.getInventory());
                if (count > 0) {
                    playerRef.sendMessage(ChatUtil.success(Tr.t("kit.saved", "kit", name, "n", count)));
                } else {
                    playerRef.sendMessage(ChatUtil.error(Tr.t("kit.save_empty")));
                }
            } catch (Exception e) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("kit.save_failed")));
            }
        });
    }
}
