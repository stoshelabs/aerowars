package dev.stoshe.aerowars.model;

/** Ordered steps of the in-game arena setup wizard. */
public enum SetupStep {
    SPAWN_POINTS("setup.step_spawns", false),
    NORMAL_CHESTS("setup.step_chests_normal", true),
    MIDDLE_CHESTS("setup.step_chests_middle", true),
    SPECTATOR_SPAWN("setup.step_spectator", false),
    CONFIRMATION("setup.confirmation", false);

    private final String instructionKey;
    private final boolean optional;

    SetupStep(String instructionKey, boolean optional) {
        this.instructionKey = instructionKey;
        this.optional = optional;
    }

    public String instructionKey() {
        return instructionKey;
    }

    public boolean isOptional() {
        return optional;
    }

    public SetupStep next() {
        int i = ordinal() + 1;
        SetupStep[] values = values();
        return i < values.length ? values[i] : null;
    }
}
