package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.MatchManager;

import javax.annotation.Nonnull;

/** {@code /aerowars leave} — leaves the current match and returns to the lobby. */
public class LeaveCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final MatchManager matchManager;

    public LeaveCommand(@Nonnull AeroWars plugin) {
        super("leave", "Sair da partida atual");
        this.plugin = plugin;
        this.matchManager = plugin.getMatchManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // Leaving the matchmaking queue counts as "leave"; otherwise leave the current match.
        if (plugin.getQueueManager() != null && plugin.getQueueManager().leave(playerRef)) {
            return;
        }
        matchManager.removePlayer(playerRef);
    }
}
