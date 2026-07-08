package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.ui.KitSelectPage;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars kit [id]} — with an id selects it (tab-completes); with no id opens the kit picker modal. */
public class KitCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final MatchManager matchManager;
    private final OptionalArg<String> kitArg;

    public KitCommand(@Nonnull AeroWars plugin) {
        super("kit", "Escolher um kit");
        this.plugin = plugin;
        this.matchManager = plugin.getMatchManager();
        this.kitArg = withOptionalArg("kit", "ID do kit (vazio abre o menu)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String kitId = kitArg.get(context);
        if (kitId == null || kitId.isBlank()) {
            // The kit picker is only meaningful inside a match (you pick a kit for the current game).
            if (matchManager.getPlayerMatch(playerRef.getUuid()) == null) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("match.not_in_match")));
                return;
            }
            world.execute(() -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    KitSelectPage.open(player, ref, store, playerRef, world, plugin);
                }
            });
            return;
        }
        matchManager.selectKit(playerRef, kitId);
    }
}
