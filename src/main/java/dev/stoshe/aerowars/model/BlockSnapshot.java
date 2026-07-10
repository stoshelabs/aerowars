package dev.stoshe.aerowars.model;

/** A captured block (id + rotation index) so a cage can restore exactly what it overwrote when it opens. */
public record BlockSnapshot(String id, int rotation) {
}
