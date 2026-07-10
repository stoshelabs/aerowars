package dev.stoshe.aerowars.command.arena;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.command.ChoiceArgType;
import dev.stoshe.aerowars.command.MapArgType;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.manager.MapManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.MapLayout;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars arena create <name> <map> <solo|teams> [teamSize]} — creates an arena over an
 * already-configured MAP. Several arenas can point at the same map (its layout is shared).
 */
public class ArenaCreateCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final MapManager mapManager;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<String> mapArg;
    private final RequiredArg<String> modeArg;
    private final OptionalArg<Integer> teamSizeArg;

    public ArenaCreateCommand(@Nonnull AeroWars plugin) {
        super("create", "Criar uma arena sobre um mapa configurado (admin)");
        this.arenaManager = plugin.getArenaManager();
        this.mapManager = plugin.getMapManager();
        this.nameArg = withRequiredArg("nome", "Nome da arena", ArgTypes.STRING);
        this.mapArg = withRequiredArg("mapa", "Mapa (template) já configurado", ArgTypes.STRING)
                .withSuggestionOverride(new MapArgType(mapManager));
        this.modeArg = withRequiredArg("modo", "solo | teams", ArgTypes.STRING)
                .withSuggestionOverride(new ChoiceArgType("modo", "solo", "teams"));
        // Arg label + help come from the configured language (loaded before commands are registered).
        this.teamSizeArg = withOptionalArg(Tr.t("arena.arg_size_name"), Tr.t("arena.arg_size_desc"), ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        String name = nameArg.get(context);
        String map = mapArg.get(context);
        String modeStr = modeArg.get(context);

        if (name == null || map == null || modeStr == null) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("arena.create_usage")));
            return;
        }

        if (!isValidName(name)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.invalid_name")));
            return;
        }

        if (arenaManager.hasArena(name)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.already_exists")));
            return;
        }

        MapLayout layout = mapManager.getLayout(map);
        if (layout == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.map_not_found", "template", map)));
            return;
        }

        boolean teams = modeStr.trim().toLowerCase().startsWith("team");
        Arena arena = new Arena(name, map);
        arena.mode = teams ? GameMode.TEAMS : GameMode.SOLO;
        arena.teamSize = teams ? Math.max(2, teamSizeArg.get(context) == null ? 2 : teamSizeArg.get(context)) : 1;

        if (!layout.isComplete(arena.mode(), arena.effectiveTeamSize())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("arena.map_incomplete", "map", map)));
            return;
        }

        // A chest-less map isn't meant to be played — start the arena as a draft until chests exist.
        arena.draft = layout.allChests().isEmpty();
        arenaManager.saveArena(arena);
        playerRef.sendMessage(ChatUtil.success(Tr.t("arena.created",
                "arena", name, "map", map, "mode", arena.mode().name().toLowerCase(),
                "max", String.valueOf(mapManager.maxPlayersFor(arena)))));

        if (arena.draft) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("arena.created_draft", "arena", name)));
        }
    }

    private boolean isValidName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$");
    }
}
