package dev.stoshe.aerowars.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.command.arena.ArenaCommand;
import dev.stoshe.aerowars.command.maps.MapsCommand;
import dev.stoshe.aerowars.command.party.PartyCommand;
import dev.stoshe.aerowars.command.setup.SetupCommand;
import dev.stoshe.aerowars.command.world.WorldCommand;

import javax.annotation.Nonnull;

/** Root {@code /aerowars} command. */
public class AeroWarsCommand extends AbstractCommandCollection {

    public AeroWarsCommand(@Nonnull AeroWars plugin) {
        super("aerowars", "Comandos do AeroWars");
        addSubCommand(new JoinCommand(plugin));
        addSubCommand(new QueueCommand(plugin));
        addSubCommand(new LeaveCommand(plugin));
        addSubCommand(new ListCommand(plugin));
        addSubCommand(new KitCommand(plugin));
        addSubCommand(new StartCommand(plugin));
        addSubCommand(new StopCommand(plugin));
        addSubCommand(new SetLobbyCommand(plugin));
        addSubCommand(new ReloadCommand(plugin));
        addSubCommand(new ChangelogCommand(plugin));
        addSubCommand(new AdminCommand(plugin));
        addSubCommand(new HelpCommand(plugin));
        addSubCommand(new StatsCommand(plugin));
        addSubCommand(new TopCommand(plugin));
        addSubCommand(new CosmeticsCommand(plugin));
        addSubCommand(new TopCommand(plugin, "rank"));
        addSubCommand(new SaveKitCommand(plugin));
        addSubCommand(new FireworkCommand(plugin));
        addSubCommand(new PartyCommand(plugin));
        addSubCommand(new SetupCommand(plugin));
        addSubCommand(new WandCommand(plugin));
        addSubCommand(new WorldCommand(plugin));
        addSubCommand(new MapsCommand(plugin));
        addSubCommand(new ArenaCommand(plugin));
    }
}
