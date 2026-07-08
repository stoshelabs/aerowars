package dev.stoshe.aerowars.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A group of players who queue together. The insertion-ordered member set keeps
 * the leader first and gives a stable "next in line" when the leader leaves.
 */
public final class Party {
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    /**
     * "Keep together" preference (leader-controlled, seeded from config). When true the party only joins a
     * match where the WHOLE party fits on one team and is never split; when false an oversized party is
     * split across teams. See {@link dev.stoshe.aerowars.manager.MatchManager#joinParty}.
     */
    private boolean keepTogether;

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public boolean keepTogether() {
        return keepTogether;
    }

    public void setKeepTogether(boolean keepTogether) {
        this.keepTogether = keepTogether;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public void setLeader(UUID uuid) {
        this.leader = uuid;
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public void add(UUID uuid) {
        members.add(uuid);
    }

    public void remove(UUID uuid) {
        members.remove(uuid);
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** A snapshot copy, leader first, safe to iterate while the party mutates. */
    public List<UUID> members() {
        return new ArrayList<>(members);
    }

    /** The first remaining member (used to promote a new leader). */
    public UUID firstMember() {
        for (UUID uuid : members) {
            return uuid;
        }
        return null;
    }
}
