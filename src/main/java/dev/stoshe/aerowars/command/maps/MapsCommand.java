package dev.stoshe.aerowars.command.maps;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/** {@code /aerowars maps ...} — manages an arena's random map pool (add/remove/list/random). */
public class MapsCommand extends AbstractCommandCollection {

    public MapsCommand(@Nonnull AeroWars plugin) {
        super("maps", "Pool de mapas aleatórios de uma arena");
        addSubCommand(new MapsAddCommand(plugin));
        addSubCommand(new MapsRemoveCommand(plugin));
        addSubCommand(new MapsListCommand(plugin));
        addSubCommand(new MapsRandomCommand(plugin));
    }
}
