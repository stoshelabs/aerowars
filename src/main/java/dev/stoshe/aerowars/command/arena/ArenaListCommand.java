package dev.stoshe.aerowars.command.arena;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.manager.MapManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/** {@code /aerowars arena list} — admin listing of EVERY arena with its status/mode/capacity/map pool. */
public class ArenaListCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final MapManager mapManager;

    public ArenaListCommand(@Nonnull AeroWars plugin) {
        super("list", "Listar todas as arenas (admin)");
        this.arenaManager = plugin.getArenaManager();
        this.mapManager = plugin.getMapManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        List<Arena> arenas = arenaManager.getAllArenas();
        if (arenas.isEmpty()) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("command.arena_list_empty")));
            return;
        }

        playerRef.sendMessage(ChatUtil.plain(Tr.t("command.arena_list_header")));
        for (Arena arena : arenas) {
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
}
