package dev.stoshe.aerowars.command.setup;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/** {@code /aerowars setup ...} — arena setup wizard subcommands. */
public class SetupCommand extends AbstractCommandCollection {

    public SetupCommand(@Nonnull AeroWars plugin) {
        super("setup", "Assistente de criação de arenas");
        addSubCommand(new SetupStartCommand(plugin));
        addSubCommand(new SetupEditCommand(plugin));
        addSubCommand(new SetupSetCommand(plugin));
        addSubCommand(new SetupActionCommand(plugin, "done", "Concluir passo atual"));
        addSubCommand(new SetupUndoCommand(plugin));
        addSubCommand(new SetupActionCommand(plugin, "skip", "Pular passo opcional"));
        addSubCommand(new SetupActionCommand(plugin, "save", "Salvar o mapa"));
        addSubCommand(new SetupActionCommand(plugin, "cancel", "Cancelar o setup"));
        addSubCommand(new SetupActionCommand(plugin, "exit", "Cancelar tudo e sair do setup"));
    }
}
