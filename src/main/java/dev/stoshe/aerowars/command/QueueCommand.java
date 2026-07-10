package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.QueueManager;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars queue [solo|teams]} — join the matchmaking queue for a mode (defaults to solo). The
 * queue auto-places the player into a fitting match as soon as an arena of that mode has room.
 */
public class QueueCommand extends AbstractPlayerCommand {
    private final QueueManager queueManager;
    private final OptionalArg<String> modeArg;

    public QueueCommand(@Nonnull AeroWars plugin) {
        super("queue", "Entrar na fila de matchmaking (solo|teams)");
        this.queueManager = plugin.getQueueManager();
        this.modeArg = withOptionalArg("modo", "solo|teams", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.canPlay(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        String modeStr = modeArg.get(context);
        GameMode mode = GameMode.SOLO;
        if (modeStr != null && !modeStr.isBlank()) {
            String m = modeStr.trim().toLowerCase();
            if (m.startsWith("team") || m.equals("t") || m.equals("duo") || m.equals("squad")) {
                mode = GameMode.TEAMS;
            } else if (m.startsWith("solo") || m.equals("s") || m.equals("ffa")) {
                mode = GameMode.SOLO;
            } else {
                playerRef.sendMessage(ChatUtil.error(Tr.t("queue.usage")));
                return;
            }
        }
        queueManager.enqueue(playerRef, mode);
    }
}
