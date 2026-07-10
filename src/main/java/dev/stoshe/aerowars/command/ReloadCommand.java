package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars reload} — re-reads config, language and loot/kit definitions at runtime (admin). */
public class ReloadCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;

    public ReloadCommand(@Nonnull AeroWars plugin) {
        super("reload", "Recarregar config, idioma, loot e kits (admin)");
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        if (plugin.reload()) {
            playerRef.sendMessage(ChatUtil.success(Tr.t("general.reloaded")));
        } else {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.reload_failed")));
        }
    }
}
