package dev.stoshe.aerowars.command;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import dev.stoshe.aerowars.manager.KitManager;
import dev.stoshe.aerowars.model.Kit;

import javax.annotation.Nonnull;

/** A string argument that tab-completes with every configured kit id. */
public class KitArgType extends SingleArgumentType<String> {
    private final KitManager kitManager;

    public KitArgType(@Nonnull KitManager kitManager) {
        super("kit", "ID de um kit existente");
        this.kitManager = kitManager;
    }

    @Override
    public String parse(String input, ParseResult result) {
        return input;
    }

    @Override
    public void suggest(CommandSender sender, String current, int index, SuggestionResult result) {
        String prefix = current == null ? "" : current.toLowerCase();
        for (Kit kit : kitManager.getAllKits()) {
            if (kit != null && kit.id != null && kit.id.toLowerCase().startsWith(prefix)) {
                result.suggest(kit.id);
            }
        }
    }
}
