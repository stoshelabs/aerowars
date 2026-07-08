package dev.stoshe.aerowars.model;

/** Persisted per-player AeroWars stats. */
public final class PlayerStats {
    public String name = "";
    public int kills;
    public int deaths;
    public int wins;
    public int gamesPlayed;

    public PlayerStats() {
    }

    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}
