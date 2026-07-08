package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.model.WorldPos;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Locations;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars setlobby} (admin) — sets the AeroWars lobby to the caller's current world and
 * position; players return here when they leave/finish a match. Persisted to config.json.
 */
public class SetLobbyCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;

    public SetLobbyCommand(@Nonnull AeroWars plugin) {
        super("setlobby", "Definir o lobby do AeroWars na sua posição (admin)");
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        WorldPos pos = Locations.fromTransform(playerRef.getTransform());
        if (pos == null || world.getName() == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("lobby.set_failed")));
            return;
        }
        plugin.getConfig().General.LobbyWorld = world.getName();
        plugin.getConfig().General.LobbySpawn = pos.serialize();
        plugin.saveConfig();
        playerRef.sendMessage(ChatUtil.success(Tr.t("lobby.set", "world", world.getName())));
    }
}
