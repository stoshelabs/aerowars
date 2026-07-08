package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/** {@code /aerowars help} — shows the command list. */
public class HelpCommand extends AbstractPlayerCommand {

    public HelpCommand(@Nonnull AeroWars plugin) {
        super("help", "Mostrar ajuda");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        playerRef.sendMessage(ChatUtil.plain(Tr.t("command.usage_header")));
        playerRef.sendMessage(ChatUtil.plain("{#ffffff}/aerowars join {#aaaaaa}- " + Tr.t("command.join_desc")));
        playerRef.sendMessage(ChatUtil.plain("{#ffffff}/aerowars leave {#aaaaaa}- " + Tr.t("command.leave_desc")));
        playerRef.sendMessage(ChatUtil.plain("{#ffffff}/aerowars kit <id> {#aaaaaa}- " + Tr.t("command.kit_desc")));
        playerRef.sendMessage(ChatUtil.plain("{#ffffff}/aerowars list {#aaaaaa}- " + Tr.t("command.list_desc")));
        playerRef.sendMessage(ChatUtil.plain("{#ffffff}/aerowars party {#aaaaaa}- " + Tr.t("command.party_desc")));
        if (PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.plain("{#ffaa00}/aerowars start {#aaaaaa}- force-start"));
            playerRef.sendMessage(ChatUtil.plain("{#ffaa00}/aerowars setup start <arena> <template> {#aaaaaa}- criar arena"));
        }
    }
}
