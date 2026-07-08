package dev.stoshe.aerowars.command.party;

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
import dev.stoshe.aerowars.manager.PartyManager;
import dev.stoshe.aerowars.ui.PartyMenuPage;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars party [action] [player]} — with no arguments opens the party
 * management modal; the text sub-actions (invite/accept/decline/leave/disband/kick)
 * remain available as a chat fallback.
 */
public class PartyCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final PartyManager partyManager;
    private final OptionalArg<String> actionArg;
    private final OptionalArg<String> targetArg;

    public PartyCommand(@Nonnull AeroWars plugin) {
        super("party", "Grupo — jogar em conjunto (abre o menu)");
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
        this.actionArg = withOptionalArg("ação", "invite|accept|decline|leave|disband|kick|menu", ArgTypes.STRING);
        this.targetArg = withOptionalArg("jogador", "alvo (invite/kick)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String action = actionArg.get(context);
        if (action == null || action.isBlank()) {
            openMenu(store, ref, playerRef, world);
            return;
        }
        String target = targetArg.get(context);
        switch (action.toLowerCase()) {
            case "invite" -> partyManager.invite(playerRef, target);
            case "accept" -> partyManager.accept(playerRef);
            case "decline", "deny" -> partyManager.decline(playerRef);
            case "leave" -> partyManager.leave(playerRef);
            case "disband" -> partyManager.disband(playerRef);
            case "kick" -> partyManager.kick(playerRef, target);
            case "list", "info", "menu" -> openMenu(store, ref, playerRef, world);
            default -> openMenu(store, ref, playerRef, world);
        }
    }

    private void openMenu(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                PartyMenuPage.open(player, ref, store, playerRef, world, plugin);
            }
        });
    }
}
