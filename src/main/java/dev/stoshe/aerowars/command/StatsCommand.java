package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.StatsManager;
import dev.stoshe.aerowars.model.PlayerStats;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars stats} — shows the caller's AeroWars stats. */
public class StatsCommand extends AbstractPlayerCommand {
    private final StatsManager statsManager;

    public StatsCommand(@Nonnull AeroWars plugin) {
        super("stats", "Ver suas estatísticas");
        this.statsManager = plugin.getStatsManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        PlayerStats s = statsManager.get(playerRef.getUuid());
        if (s == null) {
            playerRef.sendMessage(ChatUtil.info(Tr.t("stats.none")));
            return;
        }
        playerRef.sendMessage(ChatUtil.info(Tr.t("stats.header", "player", playerRef.getUsername())));
        playerRef.sendMessage(ChatUtil.plain(Tr.t("stats.line",
                "kills", s.kills, "deaths", s.deaths, "wins", s.wins, "games", s.gamesPlayed,
                "kdr", String.format("%.2f", s.kdr()))));
    }
}
