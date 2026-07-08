package dev.stoshe.aerowars.command.world;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.WorldManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/** {@code /aerowars world list} — lists the available world templates. */
public class WorldListCommand extends AbstractPlayerCommand {
    private final WorldManager worldManager;

    public WorldListCommand(@Nonnull AeroWars plugin) {
        super("list", "Listar mundos/templates disponíveis");
        this.worldManager = plugin.getWorldManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        List<String> templates = worldManager.listWorldTemplates();
        if (templates.isEmpty()) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("world.list_empty")));
        } else {
            playerRef.sendMessage(ChatUtil.info(Tr.t("world.list", "list", String.join(", ", templates))));
        }
    }
}
