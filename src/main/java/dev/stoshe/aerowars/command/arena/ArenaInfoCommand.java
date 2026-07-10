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
import dev.stoshe.aerowars.manager.MapManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars arena info <arena>} — shows an arena's status, mode, capacity and map pool. */
public class ArenaInfoCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final MapManager mapManager;
    private final RequiredArg<String> arenaArg;

    public ArenaInfoCommand(@Nonnull AeroWars plugin) {
        super("info", "Ver detalhes de uma arena (admin)");
        this.arenaManager = plugin.getArenaManager();
        this.mapManager = plugin.getMapManager();
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

        String status = mapManager.isArenaPlayable(arena)
                ? Tr.t("arena.status_active") : Tr.t("arena.status_disabled");
        playerRef.sendMessage(ChatUtil.info(Tr.t("arena.info",
                "arena", arena.name,
                "status", status,
                "mode", arena.mode().name().toLowerCase(),
                "max", String.valueOf(mapManager.maxPlayersFor(arena)),
                "maps", String.join(", ", arena.templatePool()))));
    }
}
