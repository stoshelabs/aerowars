package dev.stoshe.aerowars.command.setup;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars setup edit <arena>} — re-opens an existing arena in the setup wizard to adjust it. */
public class SetupEditCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final RequiredArg<String> arenaArg;

    public SetupEditCommand(@Nonnull AeroWars plugin) {
        super("edit", "Editar uma arena existente");
        this.plugin = plugin;
        this.arenaArg = withRequiredArg("arena", "Nome da arena", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        String name = arenaArg.get(context);
        Arena arena = name == null ? null : plugin.getArenaManager().getArena(name);
        if (arena == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.arena_not_found", "arena", String.valueOf(name))));
            return;
        }
        plugin.getSetupSessionManager().editSession(playerRef, arena);
    }
}
