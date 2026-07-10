package dev.stoshe.aerowars.command.arena;

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
import dev.stoshe.aerowars.command.ArenaArgType;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars arena disable <arena>} — removes an arena from matchmaking without deleting it. */
public class ArenaDisableCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final RequiredArg<String> arenaArg;

    public ArenaDisableCommand(@Nonnull AeroWars plugin) {
        super("disable", "Desabilitar uma arena na rotação (admin)");
        this.arenaManager = plugin.getArenaManager();
        this.arenaArg = withRequiredArg("arena", "Nome da arena", ArgTypes.STRING)
                .withSuggestionOverride(new ArenaArgType(arenaManager));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        Arena arena = arenaManager.getArena(arenaArg.get(context));

        if (arena == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("maps.arena_not_found", "arena", String.valueOf(arenaArg.get(context)))));
            return;
        }

        arena.draft = true;
        arenaManager.saveArena(arena);
        playerRef.sendMessage(ChatUtil.success(Tr.t("arena.disabled", "arena", arena.name)));
    }
}
