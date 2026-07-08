package dev.stoshe.aerowars.game;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.ChestType;
import dev.stoshe.aerowars.model.GameMode;
import dev.stoshe.aerowars.model.SetupStep;
import dev.stoshe.aerowars.model.WorldPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Transient state of one admin building an arena in-game. */
public final class SetupSession {
    public final UUID playerId;
    public final String arenaName;
    public final String worldTemplate;
    public final World setupWorld;
    public final long startTimeMillis;

    public GameMode mode = GameMode.SOLO;
    public int teamSize = 1;
    public SetupStep step = SetupStep.SPAWN_POINTS;
    /** True when re-editing an existing arena (saving overwrites it) rather than creating a new one. */
    public boolean editing;

    public final List<WorldPos> spawnPoints = new ArrayList<>();
    public final List<ChestLocation> normalChests = new ArrayList<>();
    public final List<ChestLocation> middleChests = new ArrayList<>();
    public WorldPos spectatorSpawn;

    public SetupSession(UUID playerId, String arenaName, String worldTemplate, World setupWorld, long startTimeMillis) {
        this.playerId = playerId;
        this.arenaName = arenaName;
        this.worldTemplate = worldTemplate;
        this.setupWorld = setupWorld;
        this.startTimeMillis = startTimeMillis;
    }

    /** Pre-fills this session from an existing arena so an admin can adjust and re-save it. */
    public void seedFrom(Arena arena) {
        this.editing = true;
        this.mode = arena.mode();
        this.teamSize = arena.effectiveTeamSize();
        if (arena.spawnPoints != null) {
            this.spawnPoints.addAll(arena.spawnPoints);
        }
        if (arena.normalChests != null) {
            this.normalChests.addAll(arena.normalChests);
        }
        if (arena.middleChests != null) {
            this.middleChests.addAll(arena.middleChests);
        }
        this.spectatorSpawn = arena.spectatorSpawn;
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

    public Arena toArena() {
        Arena arena = new Arena(arenaName, worldTemplate);
        arena.mode = mode;
        arena.teamSize = teamSize;
        arena.spawnPoints.addAll(spawnPoints);
        arena.normalChests.addAll(normalChests);
        arena.middleChests.addAll(middleChests);
        arena.spectatorSpawn = spectatorSpawn;
        // A SkyWars map with no chests isn't meant to be played — save it as a draft so matchmaking
        // skips it until chests are added. With chests it's a normal, playable arena.
        arena.draft = arena.allChests().isEmpty();
        return arena;
    }
}
