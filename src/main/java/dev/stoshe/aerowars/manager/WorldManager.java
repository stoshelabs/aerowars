package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.model.WorldPos;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates and tears down arena worlds by cloning template directories from
 * {@code <dataDir>/worlds/<template>} into the universe's {@code worlds/} folder.
 * Match worlds are named {@code aerowars_match_<id>} and setup worlds
 * {@code aerowars_setup_<arena>}.
 */
public class WorldManager {
    private static final String MATCH_PREFIX = "aerowars_match_";
    private static final String SETUP_PREFIX = "aerowars_setup_";

    private final File worldsDirectory;
    private final AeroWarsConfig config;
    /**
     * Invoked with a world about to be removed, BEFORE it is torn down, so a live match on that world can
     * be cancelled and its players moved to the lobby first (see {@code MatchManager.evacuateWorld}). Set
     * by MatchManager; null before wiring. Guarantees no match world is ever deleted out from under players.
     */
    private java.util.function.Consumer<World> deleteGuard;

    public WorldManager(File pluginDataDirectory, AeroWarsConfig config) {
        this.worldsDirectory = new File(pluginDataDirectory, "worlds");
        this.config = config;
        if (!worldsDirectory.exists() && worldsDirectory.mkdirs()) {
            Console.info("Created worlds directory at: " + worldsDirectory.getAbsolutePath());
        }
    }

    /** Registers the pre-delete guard (MatchManager) run before any world removal. */
    public void setDeleteGuard(java.util.function.Consumer<World> guard) {
        this.deleteGuard = guard;
    }

    private void runDeleteGuard(World world) {
        if (deleteGuard == null || world == null) {
            return;
        }
        try {
            deleteGuard.accept(world);
        } catch (Exception e) {
            Console.warning("World delete guard failed for " + world.getName() + ": " + e.getMessage());
        }
    }

    public List<String> listWorldTemplates() {
        File[] files = worldsDirectory.listFiles(File::isDirectory);
        if (files == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(files).map(File::getName).collect(Collectors.toList());
    }

    public boolean worldTemplateExists(String templateName) {
        if (templateName == null) {
            return false;
        }
        File templateDir = new File(worldsDirectory, templateName);
        return templateDir.exists() && templateDir.isDirectory();
    }

    public boolean isAeroWarsWorld(String worldName) {
        return worldName != null && (worldName.startsWith(MATCH_PREFIX) || worldName.startsWith(SETUP_PREFIX));
    }

    public World createSetupWorld(String arenaName, String templateName) {
        // Setup worlds run in Creative so the admin can grab any chest/decoration to place.
        return createWorld(SETUP_PREFIX + arenaName, templateName, false, true);
    }

    public World createMatchWorld(String matchId, String templateName) {
        return createWorld(MATCH_PREFIX + matchId, templateName, true, false);
    }

    // A fresh void build world gets a configurable square spawn platform centered
    // inside chunk (0,0) so a single chunk load covers it and the builder cannot fall.
    // Block / size / height come from config (AeroWarsConfig.Platform).
    private static final int PLATFORM_CENTER = 8;

    private String platformBlock() {
        String block = config != null ? config.Platform.Block : null;
        return (block == null || block.isBlank()) ? "Rock_Stone" : block;
    }

    /** Platform side length, clamped odd and ≥ 1 so it centres cleanly on PLATFORM_CENTER. */
    private int platformSize() {
        int size = config != null ? config.Platform.Size : 9;
        if (size < 1) {
            size = 1;
        }
        if (size % 2 == 0) {
            size++;
        }
        return size;
    }

    private int platformY() {
        return config != null ? config.Platform.Height : 100;
    }

    /** Where {@link #createTemplateWorld(String)} drops the builder: on top of the platform. */
    public WorldPos templateSpawnPos() {
        return new WorldPos(PLATFORM_CENTER + 0.5, platformY() + 1, PLATFORM_CENTER + 0.5);
    }

    /**
     * Creates a brand-new, fully void world saved directly into the templates
     * directory (so it doubles as a reusable arena template) with a single 9x9
     * spawn platform. Returns {@code null} if a template with that name already
     * exists or creation fails.
     */
    public World createTemplateWorld(String name) {
        File dir = new File(worldsDirectory, name);
        if (dir.exists()) {
            Console.warning("Template world already exists: " + name);
            return null;
        }
        try {
            if (!dir.mkdirs()) {
                Console.error("Could not create template directory: " + dir.getAbsolutePath());
                return null;
            }
            WorldConfig config = new WorldConfig();
            config.setDisplayName(name);
            // Give the void an environment (biome/zone) + tint. Without it, grass/foliage render BLACK
            // because their colour comes from the biome tint (the no-arg VoidWorldGenProvider has none).
            config.setWorldGenProvider(new VoidWorldGenProvider(
                    com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider.DEFAULT_TINT,
                    "Env_Zone1_Plains"));
            config.setGameMode(GameMode.Creative);
            config.setFallDamageEnabled(false);
            config.setSpawningNPC(false);
            config.setTicking(true);
            config.setBlockTicking(true);
            config.setGameTimePaused(true);
            try {
                config.setGameTime(Instant.parse("0001-01-01T12:00:00Z"));
            } catch (Exception ignored) {
            }
            config.setSpawnProvider(new GlobalSpawnProvider(
                    new Transform(PLATFORM_CENTER + 0.5, platformY() + 1, PLATFORM_CENTER + 0.5)));
            config.markChanged();

            World world = Universe.get().makeWorld(name, dir.toPath(), config).join();
            buildSpawnPlatform(world);
            Console.success("Created void template world: " + name);
            return world;
        } catch (Exception e) {
            Console.error("Failed to create template world " + name + ": " + e);
            return null;
        }
    }

    private void buildSpawnPlatform(World world) {
        try {
            // Chunk key for chunk (0,0) is 0 under any packing scheme.
            world.getChunkAsync(0L).join();
        } catch (Exception e) {
            Console.warning("Could not preload spawn chunk: " + e.getMessage());
        }
        String block = platformBlock();
        int y = platformY();
        int half = platformSize() / 2;
        world.execute(() -> {
            for (int x = PLATFORM_CENTER - half; x <= PLATFORM_CENTER + half; x++) {
                for (int z = PLATFORM_CENTER - half; z <= PLATFORM_CENTER + half; z++) {
                    try {
                        world.setBlock(x, y, z, block, 0);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private World createWorld(String worldName, String templateName, boolean pvp, boolean creative) {
        int attempts = 3;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            World world = tryCreateWorld(worldName, templateName, pvp, creative, attempt, attempts);
            if (world != null) {
                return world;
            }
        }
        Console.error("Gave up creating world " + worldName + " after " + attempts + " attempts.");
        return null;
    }

    /** Removes a half-created world (registry + on-disk dir) left by a failed makeWorld attempt. */
    private void cleanupFailedWorld(String worldName) {
        try {
            if (Universe.get().getWorld(worldName) != null) {
                Universe.get().removeWorld(worldName);
            }
        } catch (Exception ignored) {
        }
        try {
            Path orphan = Universe.get().getPath().resolve("worlds").resolve(worldName);
            if (Files.exists(orphan)) {
                deleteDirectory(orphan);
            }
        } catch (Exception ignored) {
        }
    }

    private World tryCreateWorld(String worldName, String templateName, boolean pvp, boolean creative,
            int attempt, int attempts) {
        try {
            Path universePath = Universe.get().getPath();
            Path worldPath = universePath.resolve("worlds").resolve(worldName);

            // A leftover world from a crashed/killed session (its 3s deleteWorld never ran, or it
            // was auto-loaded on restart) makes makeWorld throw "already exists". Clear it first.
            removeExistingWorld(worldName, worldPath);

            boolean cloned = false;
            if (worldTemplateExists(templateName)) {
                copyDirectory(new File(worldsDirectory, templateName).toPath(), worldPath);
                Console.info("Cloned template '" + templateName + "' -> " + worldName);
                cloned = true;
            } else {
                Console.warning("Template '" + templateName + "' not found; creating empty world " + worldName);
            }

            // Preserve the template's WorldGen (e.g. Flat), seed, game mode and client
            // effects by loading its cloned config.json; only override the fields we manage.
            // Building a fresh WorldConfig here would reset the generator to Hytale/Default
            // and pick a random seed, so the saved chunks would not render (black world).
            WorldConfig config;
            Path configFile = worldPath.resolve("config.json");
            if (cloned && Files.exists(configFile)) {
                // load() reads the given file path (with a .bak fallback), not the directory.
                config = WorldConfig.load(configFile).join();
            } else {
                config = new WorldConfig();
                config.setSpawnProvider(new GlobalSpawnProvider(new Transform(0.0, 100.0, 0.0)));
            }
            config.setDisplayName(worldName);
            config.setTicking(true);
            config.setBlockTicking(true);
            config.setPvpEnabled(pvp);
            config.setSpawningNPC(false);
            config.setGameTimePaused(true);
            if (creative) {
                config.setGameMode(GameMode.Creative);
            } else {
                // Match worlds: real combat — fall damage on (setup/void worlds stay safe/Creative).
                config.setFallDamageEnabled(true);
            }
            // Ephemeral: delete the world dir when it is removed. (We deliberately do NOT set
            // DeleteOnUniverseStart — that makes the universe delete these at boot, which trips a
            // Hytale NPE in TriggerVolumesPlugin.onWorldRemoved and can hang startup. Orphans are
            // instead cleaned up by cleanupEphemeralWorlds() on plugin enable.)
            config.setDeleteOnRemove(true);
            config.markChanged();

            World world = Universe.get().makeWorld(worldName, worldPath, config).join();
            // The engine sometimes tears a just-created world down asynchronously (the flaky
            // TriggerVolumesPlugin.onWorldRemoved NPE race): makeWorld().join() RETURNS a world that is
            // removed a moment later, and a player teleported into it hangs until a read timeout. So give
            // that race a moment to surface, then verify the world is still registered before handing it
            // back — if it vanished, treat it as a failed attempt so the retry loop recreates it.
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (Universe.get().getWorld(worldName) == null) {
                throw new IllegalStateException("world '" + worldName
                        + "' was removed right after creation (flaky makeWorld race)");
            }
            Console.success("Created world: " + worldName);
            return world;
        } catch (Exception e) {
            // The engine's makeWorld intermittently completes exceptionally (a race → NPE in
            // TriggerVolumesPlugin.onWorldRemoved). It's FLAKY, so the caller retries. Clean the
            // half-created orphan dir between attempts so the next try starts fresh (and a final
            // failure never leaves a problematic world lying around to break the next boot).
            Console.warning("World create for " + worldName + " failed (attempt " + attempt + "/"
                    + attempts + "): " + e.getMessage());
            cleanupFailedWorld(worldName);
            if (attempt < attempts) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        }
    }

    /**
     * Moves any players still inside {@code world} to the default world before it is removed, so
     * they land on solid ground instead of being reset-to-default at a null location.
     */
    private void evacuatePlayers(World world) {
        try {
            var players = world.getPlayerRefs();
            if (players == null || players.isEmpty()) {
                return;
            }
            World fallback = Universe.get().getDefaultWorld();
            if (fallback == null || fallback == world) {
                return;
            }
            Console.info("Evacuating " + players.size() + " player(s) from " + world.getName()
                    + " before removal.");
            java.util.List<PlayerRef> drained = new ArrayList<>(players);
            world.drainPlayersTo(fallback, drained).join();
            // drainPlayersTo keeps each player's X/Y/Z, so they'd land at the (now-deleted) arena's mid-air
            // coordinates and fall from the sky. Snap them to the fallback world's ground spawn instead.
            for (PlayerRef pr : drained) {
                try {
                    dev.stoshe.aerowars.util.Teleports.immediate(pr, fallback,
                            dev.stoshe.aerowars.util.Teleports.lobbySpawn(fallback, null, pr.getUuid()));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Console.warning("Failed to evacuate players from " + world.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Persists the block edits made in a SETUP world back into its source TEMPLATE, so future clones
     * (matches, re-edits) include them. Flushes + cleanly unloads the setup world (saving its chunks to
     * disk), copies its {@code chunks/} + {@code resources/} over the template — keeping the template's own
     * {@code config.json} — then removes the setup world dir. Returns true on success. Falls back to a plain
     * delete of the setup world if anything goes wrong, so nothing is left orphaned.
     */
    public boolean saveSetupToTemplate(World setupWorld, String templateName) {
        if (setupWorld == null || templateName == null || !worldTemplateExists(templateName)) {
            if (setupWorld != null) {
                deleteWorld(setupWorld);
            }
            return false;
        }
        String worldName = setupWorld.getName();
        Path setupPath = Universe.get().getPath().resolve("worlds").resolve(worldName);
        Path templatePath = new File(worldsDirectory, templateName).toPath();
        boolean ok = false;
        try {
            evacuatePlayers(setupWorld);
            // Turn OFF delete-on-remove so removeWorld SAVES (clean unload) instead of deleting, then flush.
            try {
                setupWorld.getWorldConfig().setDeleteOnRemove(false);
                setupWorld.getWorldConfig().markChanged();
            } catch (Exception ignored) {
            }
            try {
                setupWorld.getChunkStore().getSaver().flush();
            } catch (Exception ignored) {
            }
            Universe.get().removeWorld(worldName); // unloads + persists dirty chunks to setupPath
            // Give the async unload/save a moment to finish writing the region files before we copy them.
            try {
                Thread.sleep(600);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            // Overwrite the template's block data with the freshly-saved setup world's data.
            if (Files.exists(setupPath.resolve("chunks"))) {
                copyDirectory(setupPath.resolve("chunks"), templatePath.resolve("chunks"));
            }
            if (Files.exists(setupPath.resolve("resources"))) {
                copyDirectory(setupPath.resolve("resources"), templatePath.resolve("resources"));
            }
            ok = true;
            Console.success("Saved setup edits into template '" + templateName + "'.");
        } catch (Exception e) {
            Console.error("Failed to persist setup to template " + templateName + ": " + e.getMessage());
        } finally {
            // The setup world is no longer delete-on-remove, so clean its dir up now that data is copied.
            try {
                if (Files.exists(setupPath)) {
                    deleteDirectory(setupPath);
                }
            } catch (Exception ignored) {
            }
        }
        return ok;
    }

    public void deleteWorld(World world) {
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        // Cancel any live match on this world and move its players to the lobby BEFORE it's torn down,
        // so a match world is never deleted out from under players (whatever the reason for deletion).
        runDeleteGuard(world);
        evacuatePlayers(world);
        try {
            Universe.get().removeWorld(worldName);
        } catch (Exception e) {
            Console.warning("removeWorld(" + worldName + ") failed: " + e.getMessage());
        }
        try {
            Path worldPath = Universe.get().getPath().resolve("worlds").resolve(worldName);
            if (Files.exists(worldPath)) {
                deleteDirectory(worldPath);
                Console.success("Deleted world: " + worldName);
            }
        } catch (Exception e) {
            Console.error("Failed to delete world " + worldName + ": " + e.getMessage());
        }
    }

    /**
     * Deletes leftover ephemeral setup/match world dirs from a previous run (e.g. a crash). Called
     * on plugin enable so orphans never accumulate and break a future boot.
     */
    public void cleanupEphemeralWorlds() {
        try {
            File[] dirs = Universe.get().getPath().resolve("worlds").toFile().listFiles(File::isDirectory);
            if (dirs == null) {
                return;
            }
            for (File dir : dirs) {
                String name = dir.getName();
                if (!isAeroWarsWorld(name)) {
                    continue;
                }
                try {
                    World loaded = Universe.get().getWorld(name);
                    if (loaded != null) {
                        runDeleteGuard(loaded);
                        Universe.get().removeWorld(name);
                    }
                } catch (Exception ignored) {
                }
                try {
                    deleteDirectory(dir.toPath());
                    Console.info("Cleaned up leftover ephemeral world: " + name);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Console.warning("cleanupEphemeralWorlds failed: " + e.getMessage());
        }
    }

    /** Removes any world already registered/saved under {@code worldName} (registry + disk). */
    private void removeExistingWorld(String worldName, Path worldPath) {
        try {
            World existing = Universe.get().getWorld(worldName);
            if (existing != null) {
                runDeleteGuard(existing);
                evacuatePlayers(existing);
                Universe.get().removeWorld(worldName);
                Console.info("Removed leftover world before recreate: " + worldName);
            }
        } catch (Exception e) {
            Console.warning("removeWorld(" + worldName + ") failed: " + e.getMessage());
        }
        try {
            if (Files.exists(worldPath)) {
                deleteDirectory(worldPath);
            }
        } catch (Exception e) {
            Console.warning("Could not delete leftover world dir " + worldPath + ": " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Source directory does not exist: " + source);
        }
        try (var stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        if (!Files.exists(targetPath)) {
                            Files.createDirectories(targetPath);
                        }
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Console.error("Failed to copy: " + sourcePath + " -> " + e.getMessage());
                }
            });
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    Console.error("Failed to delete: " + path);
                }
            });
        }
    }
}
