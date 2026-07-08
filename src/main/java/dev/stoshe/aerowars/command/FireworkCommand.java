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
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars firework [show]} — debug the fireworks (single random burst, or full show). */
public class FireworkCommand extends AbstractPlayerCommand {
    private final MatchManager matchManager;
    private final OptionalArg<String> modeArg;

    public FireworkCommand(@Nonnull AeroWars plugin) {
        super("firework", "Debug de firework (admin)");
        addAliases("fw");
        this.matchManager = plugin.getMatchManager();
        this.modeArg = withOptionalArg("modo", "'show' para o show completo", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        String mode = modeArg.get(context);
        if (mode != null && mode.equalsIgnoreCase("show")) {
            matchManager.debugFireworkShow(playerRef);
            playerRef.sendMessage(ChatUtil.info(Tr.t("firework.show")));
        } else {
            matchManager.debugFireworkBurst(playerRef);
            playerRef.sendMessage(ChatUtil.info(Tr.t("firework.burst")));
        }
    }
}
