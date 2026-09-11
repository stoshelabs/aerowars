package dev.stoshe.aerowars.command.cage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.CageManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/** {@code /aerowars cage list} — lists the cage prefabs available in the {@code cages/} folder (admin). */
public class CageListCommand extends AbstractPlayerCommand {
    private final CageManager cageManager;

    public CageListCommand(@Nonnull AeroWars plugin) {
        super("list", Tr.t("command.cage.list"));
        this.cageManager = plugin.getCageManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        List<String> names = cageManager.names();
        if (names.isEmpty()) {
            playerRef.sendMessage(ChatUtil.info(Tr.t("cage.list_empty")));
            return;
        }

        playerRef.sendMessage(ChatUtil.info(Tr.t("cage.list",
                "count", String.valueOf(names.size()), "cages", String.join(", ", names))));
    }
}
