package dev.stoshe.aerowars.command.setup;

import dev.stoshe.aerowars.util.Tr;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/** {@code /aerowars setup ...} — arena setup wizard subcommands. */
public class SetupCommand extends AbstractCommandCollection {

    public SetupCommand(@Nonnull AeroWars plugin) {
        super("setup", Tr.t("command.setup"));
        addSubCommand(new SetupStartCommand(plugin));
        addSubCommand(new SetupEditCommand(plugin));
        addSubCommand(new SetupSetCommand(plugin));
        addSubCommand(new SetupActionCommand(plugin, "done", Tr.t("command.setup.done")));
        addSubCommand(new SetupUndoCommand(plugin));
        addSubCommand(new SetupActionCommand(plugin, "skip", Tr.t("command.setup.skip")));
        addSubCommand(new SetupActionCommand(plugin, "save", Tr.t("command.setup.save")));
        addSubCommand(new SetupActionCommand(plugin, "cancel", Tr.t("command.setup.cancel")));
        addSubCommand(new SetupActionCommand(plugin, "exit", Tr.t("command.setup.exit")));
    }
}
