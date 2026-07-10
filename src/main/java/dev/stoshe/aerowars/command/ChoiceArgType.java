package dev.stoshe.aerowars.command;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;

import javax.annotation.Nonnull;

/** A string argument that tab-completes from a fixed set of choices (e.g. {@code solo|teams}). */
public class ChoiceArgType extends SingleArgumentType<String> {
    private final String[] choices;

    public ChoiceArgType(@Nonnull String name, @Nonnull String... choices) {
        super(name, "Um de: " + String.join(", ", choices));
        this.choices = choices;
    }

    @Override
    public String parse(String input, ParseResult result) {
        return input;
    }

    @Override
    public void suggest(CommandSender sender, String current, int index, SuggestionResult result) {
        String prefix = current == null ? "" : current.toLowerCase();

        for (String choice : choices) {
            if (choice.toLowerCase().startsWith(prefix)) {
                result.suggest(choice);
            }
        }
    }
}
