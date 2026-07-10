package dev.stoshe.aerowars.command;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import dev.stoshe.aerowars.manager.MapManager;

import javax.annotation.Nonnull;

/** A string argument that tab-completes with the templates that already have a saved map layout. */
public class MapArgType extends SingleArgumentType<String> {
    private final MapManager mapManager;

    public MapArgType(@Nonnull MapManager mapManager) {
        super("map", "Mapa (template) já configurado");
        this.mapManager = mapManager;
    }

    @Override
    public String parse(String input, ParseResult result) {
        return input;
    }

    @Override
    public void suggest(CommandSender sender, String current, int index, SuggestionResult result) {
        String prefix = current == null ? "" : current.toLowerCase();

        for (String template : mapManager.layoutTemplates()) {
            if (template != null && template.toLowerCase().startsWith(prefix)) {
                result.suggest(template);
            }
        }
    }
}
