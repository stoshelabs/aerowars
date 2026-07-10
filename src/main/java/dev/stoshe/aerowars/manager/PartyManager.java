package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.game.Party;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.Tr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks parties (friends who queue together) and pending invites. Members join
 * the same match on {@code /aerowars join}; in TEAMS arenas they are clustered
 * onto the same team, in SOLO arenas they simply share the arena as rivals.
 *
 * <p>Each command method sends its own feedback to the caller, so the command
 * classes are thin. Invites expire lazily (checked on accept).
 */
public class PartyManager {
    private final AeroWars plugin;
    private final Map<UUID, Party> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public PartyManager(AeroWars plugin) {
        this.plugin = plugin;
    }

    private record Invite(UUID leader, String leaderName, long expiresAt) {
    }

    // ---------------------------------------------------------------- config
    private boolean enabled() {
        return plugin.getConfig().Party.Enabled;
    }

    /**
     * Effective party cap: the configured {@code Party.MaxSize}, but never more than the biggest arena can
     * hold (its player capacity = configured spawns). Otherwise a party could grow past every arena and
     * never be able to play together. Falls back to the config value when no arena exists yet.
     */
    public int maxSize() {
        int configMax = Math.max(2, plugin.getConfig().Party.MaxSize);
        int arenaCap = maxArenaCapacity();
        if (arenaCap <= 0) {
            return configMax;
        }
        return Math.max(2, Math.min(configMax, arenaCap));
    }

    /** Player capacity of the largest configured arena (0 if none). */
    private int maxArenaCapacity() {
        int max = 0;
        for (dev.stoshe.aerowars.model.Arena a : plugin.getArenaManager().getAllArenas()) {
            if (a != null) {
                max = Math.max(max, plugin.getMapManager().maxPlayersFor(a));
            }
        }
        return max;
    }

    public boolean isEnabled() {
        return enabled();
    }

    public boolean hasPendingInvite(UUID uuid) {
        Invite invite = invites.get(uuid);
        return invite != null && invite.expiresAt() >= now();
    }

    public String pendingInviteLeaderName(UUID uuid) {
        Invite invite = invites.get(uuid);
        return invite != null && invite.expiresAt() >= now() ? invite.leaderName() : null;
    }

    private long inviteMillis() {
        return Math.max(5, plugin.getConfig().Party.InviteExpirySeconds) * 1000L;
    }

    // ---------------------------------------------------------------- queries
    public Party getParty(UUID uuid) {
        return byMember.get(uuid);
    }

    public boolean isInParty(UUID uuid) {
        return byMember.containsKey(uuid);
    }

    /** Online {@link PlayerRef}s of a party, leader first, offline members skipped. */
    public List<PlayerRef> onlineMembers(Party party) {
        List<PlayerRef> refs = new ArrayList<>();
        if (party == null) {
            return refs;
        }
        for (UUID uuid : party.members()) {
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                refs.add(pr);
            }
        }
        return refs;
    }

    // ---------------------------------------------------------------- commands
    public void invite(PlayerRef inviter, String targetName) {
        if (!enabled()) {
            inviter.sendMessage(ChatUtil.error(Tr.t("party.disabled")));
            return;
        }
        if (targetName == null || targetName.isBlank()) {
            inviter.sendMessage(ChatUtil.warning(Tr.t("party.invite_usage")));
            return;
        }
        PlayerRef target = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        if (target == null) {
            inviter.sendMessage(ChatUtil.error(Tr.t("party.player_not_found", "player", targetName)));
            return;
        }
        if (target.getUuid().equals(inviter.getUuid())) {
            inviter.sendMessage(ChatUtil.error(Tr.t("party.cannot_invite_self")));
            return;
        }
        Party party = byMember.get(inviter.getUuid());
        if (party == null) {
            party = new Party(inviter.getUuid());
            party.setKeepTogether(plugin.getConfig().Party.KeepTogetherByDefault);
            byMember.put(inviter.getUuid(), party);
        } else if (!party.isLeader(inviter.getUuid())) {
            inviter.sendMessage(ChatUtil.error(Tr.t("party.not_leader")));
            return;
        }
        if (party.size() >= maxSize()) {
            inviter.sendMessage(ChatUtil.error(Tr.t("party.full", "max", maxSize())));
            return;
        }
        if (byMember.containsKey(target.getUuid())) {
            inviter.sendMessage(ChatUtil.error(Tr.t("party.target_in_party", "player", target.getUsername())));
            return;
        }
        invites.put(target.getUuid(),
                new Invite(inviter.getUuid(), inviter.getUsername(), now() + inviteMillis()));
        inviter.sendMessage(ChatUtil.success(Tr.t("party.invite_sent", "player", target.getUsername())));
        target.sendMessage(ChatUtil.info(Tr.t("party.invite_received",
                "player", inviter.getUsername(),
                "seconds", plugin.getConfig().Party.InviteExpirySeconds)));
    }

    public void accept(PlayerRef player) {
        UUID uuid = player.getUuid();
        Invite invite = invites.get(uuid);
        if (invite == null || invite.expiresAt() < now()) {
            invites.remove(uuid);
            player.sendMessage(ChatUtil.error(Tr.t("party.no_invite")));
            return;
        }
        invites.remove(uuid);
        if (byMember.containsKey(uuid)) {
            player.sendMessage(ChatUtil.error(Tr.t("party.already_in_party")));
            return;
        }
        Party party = byMember.get(invite.leader());
        if (party == null) {
            player.sendMessage(ChatUtil.error(Tr.t("party.party_gone")));
            return;
        }
        if (party.size() >= maxSize()) {
            player.sendMessage(ChatUtil.error(Tr.t("party.full", "max", maxSize())));
            return;
        }
        party.add(uuid);
        byMember.put(uuid, party);
        broadcast(party, ChatUtil.success(Tr.t("party.member_joined", "player", player.getUsername())));
    }

    public void decline(PlayerRef player) {
        Invite invite = invites.remove(player.getUuid());
        if (invite == null) {
            player.sendMessage(ChatUtil.error(Tr.t("party.no_invite")));
            return;
        }
        player.sendMessage(ChatUtil.warning(Tr.t("party.you_declined", "player", invite.leaderName())));
        PlayerRef leader = Universe.get().getPlayer(invite.leader());
        if (leader != null) {
            leader.sendMessage(ChatUtil.warning(Tr.t("party.invite_declined", "player", player.getUsername())));
        }
    }

    public void leave(PlayerRef player) {
        UUID uuid = player.getUuid();
        Party party = byMember.get(uuid);
        if (party == null) {
            player.sendMessage(ChatUtil.error(Tr.t("party.not_in_party")));
            return;
        }
        player.sendMessage(ChatUtil.warning(Tr.t("party.you_left")));
        removeMember(party, uuid, true);
    }

    public void disband(PlayerRef player) {
        UUID uuid = player.getUuid();
        Party party = byMember.get(uuid);
        if (party == null) {
            player.sendMessage(ChatUtil.error(Tr.t("party.not_in_party")));
            return;
        }
        if (!party.isLeader(uuid)) {
            player.sendMessage(ChatUtil.error(Tr.t("party.not_leader")));
            return;
        }
        broadcast(party, ChatUtil.warning(Tr.t("party.disbanded")));
        for (UUID member : party.members()) {
            byMember.remove(member);
        }
    }

    public void kick(PlayerRef leader, String targetName) {
        UUID leaderUuid = leader.getUuid();
        Party party = byMember.get(leaderUuid);
        if (party == null) {
            leader.sendMessage(ChatUtil.error(Tr.t("party.not_in_party")));
            return;
        }
        if (!party.isLeader(leaderUuid)) {
            leader.sendMessage(ChatUtil.error(Tr.t("party.not_leader")));
            return;
        }
        UUID targetUuid = null;
        String resolvedName = targetName;
        for (UUID member : party.members()) {
            if (member.equals(leaderUuid)) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(member);
            String name = pr != null ? pr.getUsername() : null;
            if (name != null && name.equalsIgnoreCase(targetName)) {
                targetUuid = member;
                resolvedName = name;
                break;
            }
        }
        if (targetUuid == null) {
            leader.sendMessage(ChatUtil.error(Tr.t("party.not_in_your_party", "player", targetName)));
            return;
        }
        PlayerRef target = Universe.get().getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(ChatUtil.warning(Tr.t("party.you_were_kicked")));
        }
        removeMember(party, targetUuid, false);
        broadcast(party, ChatUtil.warning(Tr.t("party.member_kicked", "player", resolvedName)));
    }

    /** Leader-only: hand leadership to another current member (used by the modal). */
    public boolean promote(UUID actorUuid, UUID targetUuid) {
        Party party = byMember.get(actorUuid);
        if (party == null || !party.isLeader(actorUuid)) {
            return false;
        }
        if (targetUuid == null || targetUuid.equals(actorUuid) || !party.contains(targetUuid)) {
            return false;
        }
        party.setLeader(targetUuid);
        PlayerRef target = Universe.get().getPlayer(targetUuid);
        String name = target != null ? target.getUsername() : "?";
        broadcast(party, ChatUtil.info(Tr.t("party.new_leader", "player", name)));
        return true;
    }

    /** Leader-only: flip the party's "keep together" preference. Returns the new value (null if not allowed). */
    public Boolean toggleKeepTogether(UUID actorUuid) {
        Party party = byMember.get(actorUuid);
        if (party == null || !party.isLeader(actorUuid)) {
            return null;
        }
        boolean v = !party.keepTogether();
        party.setKeepTogether(v);
        broadcast(party, ChatUtil.info(Tr.t(v ? "party.keep_together_on" : "party.keep_together_off")));
        return v;
    }

    /** Leader-only kick by UUID (used by the modal). */
    public boolean kick(UUID actorUuid, UUID targetUuid) {
        Party party = byMember.get(actorUuid);
        if (party == null || !party.isLeader(actorUuid)) {
            return false;
        }
        if (targetUuid == null || targetUuid.equals(actorUuid) || !party.contains(targetUuid)) {
            return false;
        }
        PlayerRef target = Universe.get().getPlayer(targetUuid);
        String name = target != null ? target.getUsername() : "?";
        if (target != null) {
            target.sendMessage(ChatUtil.warning(Tr.t("party.you_were_kicked")));
        }
        removeMember(party, targetUuid, false);
        broadcast(party, ChatUtil.warning(Tr.t("party.member_kicked", "player", name)));
        return true;
    }

    public void info(PlayerRef player) {
        Party party = byMember.get(player.getUuid());
        if (party == null) {
            player.sendMessage(ChatUtil.info(Tr.t("party.not_in_party")));
            return;
        }
        player.sendMessage(ChatUtil.info(Tr.t("party.list_header", "n", party.size(), "max", maxSize())));
        for (UUID member : party.members()) {
            PlayerRef pr = Universe.get().getPlayer(member);
            String name = pr != null ? pr.getUsername() : member.toString().substring(0, 8);
            String key = party.isLeader(member) ? "party.list_leader" : "party.list_member";
            player.sendMessage(ChatUtil.plain(Tr.t(key, "player", name)));
        }
    }

    // ---------------------------------------------------------------- lifecycle
    public void handleDisconnect(UUID uuid) {
        invites.remove(uuid);
        Party party = byMember.get(uuid);
        if (party != null) {
            removeMember(party, uuid, true);
        }
    }

    // ---------------------------------------------------------------- internals
    /**
     * Removes a member. If the leader leaves and members remain, the next member
     * is promoted; when the party drops to a single player it is dissolved.
     */
    private void removeMember(Party party, UUID uuid, boolean announce) {
        boolean wasLeader = party.isLeader(uuid);
        party.remove(uuid);
        byMember.remove(uuid);
        if (party.size() <= 1) {
            // A lone member is no longer a party — free them too.
            UUID last = party.firstMember();
            if (last != null) {
                byMember.remove(last);
                PlayerRef pr = Universe.get().getPlayer(last);
                if (pr != null) {
                    pr.sendMessage(ChatUtil.warning(Tr.t("party.disbanded_empty")));
                }
            }
            return;
        }
        if (wasLeader) {
            UUID promoted = party.firstMember();
            party.setLeader(promoted);
            PlayerRef pr = Universe.get().getPlayer(promoted);
            String name = pr != null ? pr.getUsername() : "?";
            broadcast(party, ChatUtil.info(Tr.t("party.new_leader", "player", name)));
        }
        if (announce) {
            PlayerRef left = Universe.get().getPlayer(uuid);
            String name = left != null ? left.getUsername() : "?";
            broadcast(party, ChatUtil.warning(Tr.t("party.member_left", "player", name)));
        }
    }

    private void broadcast(Party party, com.hypixel.hytale.server.core.Message message) {
        for (UUID member : party.members()) {
            PlayerRef pr = Universe.get().getPlayer(member);
            if (pr != null) {
                pr.sendMessage(message);
            }
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
