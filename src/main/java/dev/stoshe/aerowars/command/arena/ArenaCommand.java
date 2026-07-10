package dev.stoshe.aerowars.command.arena;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/** {@code /aerowars arena ...} — arena administration (create over a map, enable/disable, info). */
public class ArenaCommand extends AbstractCommandCollection {

    public ArenaCommand(@Nonnull AeroWars plugin) {
        super("arena", "Administração de arenas");
        addSubCommand(new ArenaCreateCommand(plugin));
        addSubCommand(new ArenaListCommand(plugin));
        addSubCommand(new ArenaEnableCommand(plugin));
        addSubCommand(new ArenaDisableCommand(plugin));
        addSubCommand(new ArenaInfoCommand(plugin));
    }
}
