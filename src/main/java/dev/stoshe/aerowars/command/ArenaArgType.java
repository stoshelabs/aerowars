package dev.stoshe.aerowars.command;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.model.Arena;

import javax.annotation.Nonnull;

/** A string argument that tab-completes with the existing arena names. */
public class ArenaArgType extends SingleArgumentType<String> {
    private final ArenaManager arenaManager;

    public ArenaArgType(@Nonnull ArenaManager arenaManager) {
        super("arena", "Nome de uma arena existente");
        this.arenaManager = arenaManager;
    }

    @Override
    public String parse(String input, ParseResult result) {
        return input;
    }

    @Override
    public void suggest(CommandSender sender, String current, int index, SuggestionResult result) {
        String prefix = current == null ? "" : current.toLowerCase();

        for (Arena arena : arenaManager.getAllArenas()) {
            if (arena.name != null && arena.name.toLowerCase().startsWith(prefix)) {
                result.suggest(arena.name);
            }
        }
    }
}
