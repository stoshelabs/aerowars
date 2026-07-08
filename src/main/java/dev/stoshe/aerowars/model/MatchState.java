package dev.stoshe.aerowars.model;

/**
 * Lifecycle of a match.
 *
 * <pre>
 * WAITING    -> lobby is filling; players stand in cages on their islands
 * COUNTDOWN  -> enough players; ticking down to start
 * ACTIVE     -> cages opened; combat and looting allowed
 * ENDING     -> a winner (or draw) was decided; short celebration
 * CLEANUP    -> world/players being torn down; match removed afterwards
 * </pre>
 */
public enum MatchState {
    WAITING,
    COUNTDOWN,
    ACTIVE,
    ENDING,
    CLEANUP;

    public boolean acceptsPlayers() {
        return this == WAITING || this == COUNTDOWN;
    }

    public boolean isRunning() {
        return this == ACTIVE;
    }
}
