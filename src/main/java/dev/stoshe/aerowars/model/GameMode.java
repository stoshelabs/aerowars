package dev.stoshe.aerowars.model;

/** Whether islands hold a single player or a team. */
public enum GameMode {
    SOLO,
    TEAMS;

    public static GameMode fromString(String s) {
        if (s == null) {
            return SOLO;
        }
        try {
            return GameMode.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SOLO;
        }
    }
}
