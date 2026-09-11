package dev.stoshe.aerowars.command;

import dev.stoshe.aerowars.util.Tr;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import dev.stoshe.aerowars.manager.CageManager;

import javax.annotation.Nonnull;

/** A string argument that tab-completes with the existing cage prefab names. */
public class CageArgType extends SingleArgumentType<String> {
    private final CageManager cageManager;

    public CageArgType(@Nonnull CageManager cageManager) {
        super("cage", Tr.t("command.arg.cage"));
        this.cageManager = cageManager;
    }

    @Override
    public String parse(String input, ParseResult result) {
        return input;
    }

    @Override
    public void suggest(CommandSender sender, String current, int index, SuggestionResult result) {
        String prefix = CageManager.normalize(current == null ? "" : current);

        for (String name : cageManager.names()) {
            if (name.startsWith(prefix)) {
                result.suggest(name);
            }
        }
    }
}
