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
import dev.stoshe.aerowars.command.CageArgType;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.manager.CageManager;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars arena cage <arena> <cage>} — sets an arena's default cage prefab, applied to any
 * player who hasn't picked their own cage cosmetic. {@code <cage>} is a cage prefab name (see
 * {@code /aerowars cage list}); {@code reset} / {@code clear} / {@code default} fall back to the global
 * default cage. Precedence: player's cosmetic &gt; arena default &gt; default cage prefab.
 */
public class ArenaCageCommand extends AbstractPlayerCommand {
    private final ArenaManager arenaManager;
    private final CageManager cageManager;
    private final RequiredArg<String> arenaArg;
    private final RequiredArg<String> valueArg;

    public ArenaCageCommand(@Nonnull AeroWars plugin) {
        super("cage", Tr.t("command.arena.cage"));
        this.arenaManager = plugin.getArenaManager();
        this.cageManager = plugin.getCageManager();
        this.arenaArg = withRequiredArg("arena", Tr.t("command.arg.arena"), ArgTypes.STRING)
                .withSuggestionOverride(new ArenaArgType(arenaManager));
        this.valueArg = withRequiredArg("cage",
                Tr.t("command.arg.cage_or_reset"), ArgTypes.STRING)
                .withSuggestionOverride(new CageArgType(cageManager));
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
            playerRef.sendMessage(ChatUtil.error(Tr.t("maps.arena_not_found", "arena",
                    String.valueOf(arenaArg.get(context)))));
            return;
        }

        String value = valueArg.get(context);
        value = value == null ? "" : value.trim();

        // Reset keywords clear the override so the arena falls back to the global default cage.
        if (value.isEmpty() || value.equalsIgnoreCase("reset")
                || value.equalsIgnoreCase("clear") || value.equalsIgnoreCase("default")) {
            arena.defaultCage = "";
            arenaManager.saveArena(arena);
            playerRef.sendMessage(ChatUtil.success(Tr.t("arena.cage_reset", "arena", arena.name)));
            return;
        }

        // Must reference an existing cage prefab — reject typos rather than store a broken default.
        if (!cageManager.exists(value)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cage.not_found", "cage", value)));
            return;
        }

        String name = CageManager.normalize(value);
        arena.defaultCage = "prefab:" + name;
        arenaManager.saveArena(arena);
        playerRef.sendMessage(ChatUtil.success(Tr.t("arena.cage_set", "arena", arena.name, "cage", name)));
    }
}
