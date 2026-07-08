package dev.stoshe.aerowars.command.setup;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars setup mode <solo|coop> [size]} — sets whether the arena being built is Solo (FFA) or a
 * co-op Teams arena, and for co-op how many players share each team/island.
 */
public class SetupModeCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final RequiredArg<String> modeArg;
    private final OptionalArg<String> sizeArg;

    public SetupModeCommand(@Nonnull AeroWars plugin) {
        super("mode", "Definir modo da arena (solo|coop [tamanho])");
        this.plugin = plugin;
        this.modeArg = withRequiredArg("modo", "solo|coop", ArgTypes.STRING);
        this.sizeArg = withOptionalArg("tamanho", "jogadores por time (coop)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }
        String mode = modeArg.get(context);
        int size = 2;
        String raw = sizeArg.get(context);
        if (raw != null && !raw.isBlank()) {
            try {
                size = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        plugin.getSetupSessionManager().setMode(playerRef, mode, size);
    }
}
