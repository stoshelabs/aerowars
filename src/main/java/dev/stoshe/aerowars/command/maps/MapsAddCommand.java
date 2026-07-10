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
import dev.stoshe.aerowars.command.MapArgType;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.manager.MapManager;
import dev.stoshe.aerowars.manager.WorldManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars maps add <arena> <map>} — adds an alternate map to an arena's random pool (admin). */
public class MapsAddCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final MapManager mapManager;
    private final WorldManager worldManager;
    private final RequiredArg<String> arenaArg;
    private final RequiredArg<String> mapArg;

    public MapsAddCommand(@Nonnull AeroWars plugin) {
        super("add", "Adicionar um mapa ao pool da arena (admin)");
        this.arenaManager = plugin.getArenaManager();
        this.mapManager = plugin.getMapManager();
        this.worldManager = plugin.getWorldManager();
        this.arenaArg = withRequiredArg("arena", "Nome da arena", ArgTypes.STRING)
                .withSuggestionOverride(new ArenaArgType(arenaManager));
        this.mapArg = withRequiredArg("mapa", "Mapa (template) configurado", ArgTypes.STRING)
                .withSuggestionOverride(new MapArgType(mapManager));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        Arena arena = arenaManager.getArena(arenaArg.get(context));
        String template = mapArg.get(context);

        if (arena == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("maps.arena_not_found", "arena", String.valueOf(arenaArg.get(context)))));
            return;
        }

        if (template == null) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("maps.usage")));
            return;
        }

        if (!mapManager.hasLayout(template) && !worldManager.worldTemplateExists(template)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.map_not_found", "template", template)));
            return;
        }

        if (arena.templatePool().stream().anyMatch(t -> t.equalsIgnoreCase(template))) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("maps.already_in_pool", "template", template)));
            return;
        }

        arena.extraTemplates.add(template);
        arenaManager.saveArena(arena);
        playerRef.sendMessage(ChatUtil.success(Tr.t("maps.added", "template", template, "arena", arena.name)));
    }
}
