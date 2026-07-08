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
        addSubCommand(new SetupModeCommand(plugin));
        addSubCommand(new SetupSetCommand(plugin));
        addSubCommand(new SetupActionCommand(plugin, "done", "Concluir passo atual"));
        addSubCommand(new SetupActionCommand(plugin, "undo", "Desfazer o último spawn/baú do passo"));
        addSubCommand(new SetupActionCommand(plugin, "skip", "Pular passo opcional"));
        addSubCommand(new SetupActionCommand(plugin, "save", "Salvar a arena"));
        addSubCommand(new SetupActionCommand(plugin, "cancel", "Cancelar o setup"));
    }
}
