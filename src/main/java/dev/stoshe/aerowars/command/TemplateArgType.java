package dev.stoshe.aerowars.command;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import dev.stoshe.aerowars.manager.WorldManager;

import javax.annotation.Nonnull;

/** A string argument that tab-completes with the world templates present on the server. */
public class TemplateArgType extends SingleArgumentType<String> {
    private final WorldManager worldManager;

    public TemplateArgType(@Nonnull WorldManager worldManager) {
        super("template", "Template de mundo existente");
        this.worldManager = worldManager;
    }

    @Override
    public String parse(String input, ParseResult result) {
        return input;
    }

    @Override
    public void suggest(CommandSender sender, String current, int index, SuggestionResult result) {
        String prefix = current == null ? "" : current.toLowerCase();
        for (String template : worldManager.listWorldTemplates()) {
            if (template != null && template.toLowerCase().startsWith(prefix)) {
                result.suggest(template);
            }
        }
    }
}
