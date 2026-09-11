package dev.stoshe.aerowars.command.maps;

import dev.stoshe.aerowars.util.Tr;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/** {@code /aerowars maps ...} — manages an arena's random map pool (add/remove/list/random). */
public class MapsCommand extends AbstractCommandCollection {

    public MapsCommand(@Nonnull AeroWars plugin) {
        super("maps", Tr.t("command.maps"));
        addSubCommand(new MapsAddCommand(plugin));
        addSubCommand(new MapsRemoveCommand(plugin));
        addSubCommand(new MapsListCommand(plugin));
        addSubCommand(new MapsRandomCommand(plugin));
    }
}
