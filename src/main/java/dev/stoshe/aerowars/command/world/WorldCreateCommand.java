package dev.stoshe.aerowars.command.world;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.WorldManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Teleports;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars world create <nome>} — creates a void build world with a spawn platform. */
public class WorldCreateCommand extends AbstractPlayerCommand {
    private final WorldManager worldManager;
    private final RequiredArg<String> nameArg;

    public WorldCreateCommand(@Nonnull AeroWars plugin) {
        super("create", "Criar um mundo void com plataforma de spawn");
        this.worldManager = plugin.getWorldManager();
        this.nameArg = withRequiredArg("nome", "Nome do mundo/template", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        String name = nameArg.get(context);
        if (name == null) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("world.usage")));
            return;
        }
        if (!isValidName(name)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("world.invalid_name")));
            return;
        }
        if (worldManager.worldTemplateExists(name)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("world.exists", "name", name)));
            return;
        }
        World created = worldManager.createTemplateWorld(name);
        if (created == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("world.create_failed", "name", name)));
            return;
        }
        Teleports.to(playerRef, created, worldManager.templateSpawnPos());
        playerRef.sendMessage(ChatUtil.success(Tr.t("world.created", "name", name)));
    }

    private boolean isValidName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$");
    }
}
