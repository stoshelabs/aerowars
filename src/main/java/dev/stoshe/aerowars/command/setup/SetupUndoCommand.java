package dev.stoshe.aerowars.command.setup;

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
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars setup undo [n]} — undoes the last spawn/chest of the active step. The optional count
 * undoes up to n at once; if it exceeds what's available, everything on the step is undone. Defaults to 1.
 */
public class SetupUndoCommand extends AbstractPlayerCommand {
    private final SetupSessionManager setupManager;
    private final OptionalArg<Integer> countArg;

    public SetupUndoCommand(@Nonnull AeroWars plugin) {
        super("undo", "Desfazer o(s) último(s) spawn/baú do passo");
        this.setupManager = plugin.getSetupSessionManager();
        this.countArg = withOptionalArg("quantidade", "Quantos desfazer (padrão 1)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        Integer count = countArg.get(context);
        setupManager.handleUndo(playerRef, count == null ? 1 : count);
    }
}
