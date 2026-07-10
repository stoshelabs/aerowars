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
import dev.stoshe.aerowars.command.TemplateArgType;
import dev.stoshe.aerowars.manager.SetupSessionManager;
import dev.stoshe.aerowars.manager.WorldManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * {@code /aerowars setup start <template>} — begins a MAP setup session on a world template. Builds the
 * map's spawns/chests/spectator; create an arena for it afterwards with {@code /aerowars arena create}.
 */
public class SetupStartCommand extends AbstractPlayerCommand {
    private final SetupSessionManager setupManager;
    private final WorldManager worldManager;
    private final RequiredArg<String> templateArg;

    public SetupStartCommand(@Nonnull AeroWars plugin) {
        super("start", "Iniciar setup de um mapa");
        this.setupManager = plugin.getSetupSessionManager();
        this.worldManager = plugin.getWorldManager();
        this.templateArg = withRequiredArg("template", "Template de mundo", ArgTypes.STRING)
                .withSuggestionOverride(new TemplateArgType(worldManager));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        String template = templateArg.get(context);

        if (template == null) {
            playerRef.sendMessage(ChatUtil.warning(Tr.t("setup.start_usage")));
            showTemplates(playerRef);
            return;
        }

        if (!worldManager.worldTemplateExists(template)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("setup.template_not_found", "template", template)));
            showTemplates(playerRef);
            return;
        }

        setupManager.startSession(store, ref, playerRef, template);
    }

    private void showTemplates(PlayerRef playerRef) {
        List<String> templates = worldManager.listWorldTemplates();

        if (!templates.isEmpty()) {
            playerRef.sendMessage(ChatUtil.info("Templates: " + String.join(", ", templates)));
        }
    }
}
