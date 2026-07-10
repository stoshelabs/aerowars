package dev.stoshe.aerowars.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.game.Party;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.manager.PartyManager;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import java.util.UUID;

import javax.annotation.Nonnull;

/** {@code /aerowars join} — joins (or creates) a random available match. Pulls the whole party in. */
public class JoinCommand extends AbstractPlayerCommand {
    private final MatchManager matchManager;
    private final PartyManager partyManager;
    private final dev.stoshe.aerowars.manager.QueueManager queueManager;

    public JoinCommand(@Nonnull AeroWars plugin) {
        super("join", "Entrar em uma partida");
        this.matchManager = plugin.getMatchManager();
        this.partyManager = plugin.getPartyManager();
        this.queueManager = plugin.getQueueManager();
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID uuid = playerRef.getUuid();

        if (!PermissionUtil.canPlay(uuid)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        if (matchManager.getPlayerMatch(uuid) != null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("match.already_in_match")));
            return;
        }

        Party party = partyManager.getParty(uuid);
        if (party != null && party.size() > 1) {
            if (!party.isLeader(uuid)) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("party.only_leader_queues")));
                return;
            }
            int result = matchManager.joinParty(partyManager.onlineMembers(party), party.keepTogether());
            if (result == -2) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("party.keep_together_no_arena", "n", party.size())));
            } else if (result == -1) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("party.no_arena_fits", "n", party.size())));
            } else if (result == 0) {
                playerRef.sendMessage(ChatUtil.warning(Tr.t("party.all_in_match")));
            } else {
                playerRef.sendMessage(ChatUtil.success(Tr.t("party.queued", "n", result)));
            }
            return;
        }

        Match match = matchManager.findOrCreateRandomMatch();
        if (match == null || !matchManager.addPlayer(match, store, ref, playerRef)) {
            // Can't get into a game right now (no arena free / it filled) → wait in the matchmaking queue.
            queueManager.enqueueAny(playerRef);
        }
    }
}
