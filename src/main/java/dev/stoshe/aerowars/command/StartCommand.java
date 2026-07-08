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
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars start} — admin force-start of the caller's match. */
public class StartCommand extends AbstractPlayerCommand {
    private final MatchManager matchManager;

    public StartCommand(@Nonnull AeroWars plugin) {
        super("start", "Forçar início da partida (admin)");
        this.matchManager = plugin.getMatchManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        if (!matchManager.forceStart(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.not_in_match")));
        }
    }
}
