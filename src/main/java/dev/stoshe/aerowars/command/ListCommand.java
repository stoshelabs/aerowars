package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/** {@code /aerowars list} — lists configured arenas. */
public class ListCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;

    public ListCommand(@Nonnull AeroWars plugin) {
        super("list", "Listar arenas");
        this.arenaManager = plugin.getArenaManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        List<Arena> arenas = arenaManager.getAllArenas();
        if (arenas.isEmpty()) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("command.arena_list_empty")));
            return;
        }
        playerRef.sendMessage(ChatUtil.plain(Tr.t("command.arena_list_header")));
        for (Arena arena : arenas) {
            playerRef.sendMessage(ChatUtil.plain(Tr.t("command.arena_list_entry",
                    "arena", arena.displayName(),
                    "mode", arena.mode().name().toLowerCase(),
                    "min", 2,
                    "max", arena.getMaxPlayers())));
        }
    }
}
