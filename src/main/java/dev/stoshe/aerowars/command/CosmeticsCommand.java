package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.ui.CosmeticsPage;

import javax.annotation.Nonnull;

/** {@code /aerowars cosmetics} — opens the cosmetics shop (cages / kill effects / victory). */
public class CosmeticsCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;

    public CosmeticsCommand(@Nonnull AeroWars plugin) {
        super("cosmetics", "Loja de cosméticos");
        addAliases("cosmetic", "cosmeticos");
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                CosmeticsPage.open(player, ref, store, playerRef, world, plugin);
            }
        });
    }
}
