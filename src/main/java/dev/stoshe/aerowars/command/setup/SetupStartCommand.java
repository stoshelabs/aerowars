package dev.stoshe.aerowars.command.setup;

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
import dev.stoshe.aerowars.manager.ArenaManager;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.manager.WorldManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/** {@code /aerowars setup start <arena> <template>} — begins an arena setup session. */
public class SetupStartCommand extends AbstractPlayerCommand {
    private final SetupSessionManager setupManager;
    private final WorldManager worldManager;
    private final ArenaManager arenaManager;
    private final RequiredArg<String> arenaArg;
    private final RequiredArg<String> templateArg;

    public SetupStartCommand(@Nonnull AeroWars plugin) {
        super("start", "Iniciar setup de arena");
        this.setupManager = plugin.getSetupSessionManager();
        this.worldManager = plugin.getWorldManager();
        this.arenaManager = plugin.getArenaManager();
        this.arenaArg = withRequiredArg("arena", "Nome da arena", ArgTypes.STRING);
        this.templateArg = withRequiredArg("template", "Template de mundo", ArgTypes.STRING)
                .withSuggestionOverride(new dev.stoshe.aerowars.command.TemplateArgType(worldManager));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        String arena = arenaArg.get(context);
        String template = templateArg.get(context);
        if (arena == null || template == null) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.start_usage")));
            showTemplates(playerRef);
            return;
        }
        if (!isValidName(arena)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.invalid_name")));
            return;
        }
        if (arenaManager.hasArena(arena)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.already_exists")));
            return;
        }
        if (!worldManager.worldTemplateExists(template)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.template_not_found", "template", template)));
            showTemplates(playerRef);
            return;
        }
        setupManager.startSession(store, ref, playerRef, arena, template);
    }

    private void showTemplates(PlayerRef playerRef) {
        List<String> templates = worldManager.listWorldTemplates();
        if (!templates.isEmpty()) {
            playerRef.sendMessage(ChatUtil.info("Templates: " + String.join(", ", templates)));
        }
    }

    private boolean isValidName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$");
    }
}
