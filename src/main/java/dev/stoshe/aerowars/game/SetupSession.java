package dev.stoshe.aerowars.game;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.ChestType;
import dev.stoshe.aerowars.model.MapLayout;
import dev.stoshe.aerowars.model.SetupStep;
import dev.stoshe.aerowars.model.WorldPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transient state of one admin building a MAP layout in-game. Setup is map-centric: it produces a
 * {@link MapLayout} (spawns/chests/spectator) for a world {@link #template}, independent of any arena.
 * Game rules (mode/team size) are chosen later when an arena is created for the map.
 */
public final class SetupSession {
    public final UUID playerId;
    public final String template;
    public final World setupWorld;
    public final long startTimeMillis;

    public SetupStep step = SetupStep.SPAWN_POINTS;
    /** True when re-editing an existing map (saving overwrites it) rather than creating a new one. */
    public boolean editing;

    public final List<WorldPos> spawnPoints = new ArrayList<>();
    public final List<ChestLocation> normalChests = new ArrayList<>();
    public final List<ChestLocation> middleChests = new ArrayList<>();
    public WorldPos spectatorSpawn;

    public SetupSession(UUID playerId, String template, World setupWorld, long startTimeMillis) {
        this.playerId = playerId;
        this.template = template;
        this.setupWorld = setupWorld;
        this.startTimeMillis = startTimeMillis;
    }

    /** Pre-fills this session from an existing map layout so an admin can adjust and re-save it. */
    public void seedFrom(MapLayout layout) {
        this.editing = true;

        if (layout.spawnPoints != null) {
            this.spawnPoints.addAll(layout.spawnPoints);
        }

        if (layout.normalChests != null) {
            this.normalChests.addAll(layout.normalChests);
        }

        if (layout.middleChests != null) {
            this.middleChests.addAll(layout.middleChests);
        }

        this.spectatorSpawn = layout.spectatorSpawn;
    }

    public void addChest(ChestLocation chest) {
        if (chest.type() == ChestType.MIDDLE) {
            middleChests.add(chest);
        } else {
            normalChests.add(chest);
        }
    }

    public boolean isValid() {
        return spawnPoints.size() >= 2 && spectatorSpawn != null;
    }

    public long elapsedMinutes() {
        return (System.currentTimeMillis() - startTimeMillis) / 60000L;
    }

    public MapLayout toLayout() {
        MapLayout layout = new MapLayout(template);
        layout.spawnPoints.addAll(spawnPoints);
        layout.normalChests.addAll(normalChests);
        layout.middleChests.addAll(middleChests);
        layout.spectatorSpawn = spectatorSpawn;
        return layout;
    }
}
