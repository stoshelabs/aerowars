package dev.stoshe.aerowars.command.maps;

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
import java.util.List;

/** {@code /aerowars maps list <arena>} — shows an arena's map pool and whether rotation is on (admin). */
public class MapsListCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final RequiredArg<String> arenaArg;

    public MapsListCommand(@Nonnull AeroWars plugin) {
        super("list", "Listar o pool de mapas da arena (admin)");
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

        List<String> pool = arena.templatePool();

        if (pool.isEmpty()) {
            playerRef.sendMessage(ChatUtil.info(Tr.t("maps.list_empty", "arena", arena.name)));
            return;
        }

        String rotation = Tr.t(arena.randomMaps ? "maps.rotation_on" : "maps.rotation_off");
        playerRef.sendMessage(ChatUtil.info(Tr.t("maps.list", "arena", arena.name,
                "maps", String.join(", ", pool), "rotation", rotation)));
    }
}
