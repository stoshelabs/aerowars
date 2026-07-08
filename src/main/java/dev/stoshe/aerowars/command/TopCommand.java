package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.ui.LeaderboardPage;

import javax.annotation.Nonnull;

/** {@code /aerowars top|rank [kills|wins]} — opens the leaderboard modal (podium + pagination + search). */
public class TopCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final OptionalArg<String> metricArg;

    public TopCommand(@Nonnull AeroWars plugin) {
        this(plugin, "top");
    }

    public TopCommand(@Nonnull AeroWars plugin, @Nonnull String name) {
        super(name, "Ranking de jogadores");
        this.plugin = plugin;
        this.metricArg = withOptionalArg("métrica", "kills ou wins", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String metric = metricArg.get(context);
        String m = metric != null && metric.equalsIgnoreCase("wins") ? "wins" : "kills";
        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                LeaderboardPage.open(player, ref, store, playerRef, world, plugin, m);
            }
        });
    }
}
