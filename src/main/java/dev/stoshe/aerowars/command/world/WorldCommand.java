package dev.stoshe.aerowars.command.world;

import dev.stoshe.aerowars.util.Tr;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/** {@code /aerowars world ...} — create and list AeroWars build/template worlds. */
public class WorldCommand extends AbstractCommandCollection {

    public WorldCommand(@Nonnull AeroWars plugin) {
        super("world", Tr.t("command.world"));
        addSubCommand(new WorldCreateCommand(plugin));
        addSubCommand(new WorldListCommand(plugin));
    }
}
