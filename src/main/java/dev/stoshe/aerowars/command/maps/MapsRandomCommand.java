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
import dev.stoshe.aerowars.command.ChoiceArgType;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars maps random <arena> <on|off>} — toggles random map rotation for an arena (admin). */
public class MapsRandomCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final RequiredArg<String> arenaArg;
    private final RequiredArg<String> valueArg;

    public MapsRandomCommand(@Nonnull AeroWars plugin) {
        super("random", "Ligar/desligar rotação aleatória de mapas (admin)");
        this.arenaManager = plugin.getArenaManager();
        this.arenaArg = withRequiredArg("arena", "Nome da arena", ArgTypes.STRING)
                .withSuggestionOverride(new ArenaArgType(arenaManager));
        this.valueArg = withRequiredArg("valor", "on | off", ArgTypes.STRING)
                .withSuggestionOverride(new ChoiceArgType("valor", "on", "off"));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        Arena arena = arenaManager.getArena(arenaArg.get(context));
        String v = valueArg.get(context) == null ? "" : valueArg.get(context).trim().toLowerCase();

        if (arena == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("maps.arena_not_found", "arena", String.valueOf(arenaArg.get(context)))));
            return;
        }

        boolean on;

        if (v.equals("on") || v.equals("true") || v.equals("yes")) {
            on = true;
        } else if (v.equals("off") || v.equals("false") || v.equals("no")) {
            on = false;
        } else {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("maps.usage")));
            return;
        }

        arena.randomMaps = on;
        arenaManager.saveArena(arena);
        playerRef.sendMessage(ChatUtil.success(Tr.t(on ? "maps.random_on" : "maps.random_off",
                "arena", arena.name)));
    }
}
