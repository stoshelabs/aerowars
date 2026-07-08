package dev.stoshe.aerowars.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stoshe.aerowars.model.Arena;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Loads and persists {@link Arena} definitions as one JSON file per arena. */
public class ArenaManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File arenasDir;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    public ArenaManager(File dataDir) {
        this.arenasDir = new File(dataDir, "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
        }
    }

    public void loadArenas() {
        arenas.clear();
        File[] files = arenasDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Arena arena = gson.fromJson(reader, Arena.class);
                if (arena != null && arena.name != null) {
                    arenas.put(arena.name.toLowerCase(), arena);
                }
            } catch (Exception e) {
                Console.error("Failed to load arena " + file.getName() + ": " + e.getMessage());
            }
        }
        Console.info("Loaded " + arenas.size() + " arena(s).");
    }

    public void saveArena(Arena arena) {
        if (arena == null || arena.name == null) {
            return;
        }
        arenas.put(arena.name.toLowerCase(), arena);
        File file = new File(arenasDir, arena.name.toLowerCase() + ".json");
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(arena, writer);
        } catch (Exception e) {
            Console.error("Failed to save arena " + arena.name + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Arena arena : arenas.values()) {
            saveArena(arena);
        }
    }

    public boolean deleteArena(String name) {
        if (name == null) {
            return false;
        }
        Arena removed = arenas.remove(name.toLowerCase());
        File file = new File(arenasDir, name.toLowerCase() + ".json");
        boolean fileGone = !file.exists() || file.delete();
        return removed != null && fileGone;
    }

    public Arena getArena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public boolean hasArena(String name) {
        return name != null && arenas.containsKey(name.toLowerCase());
    }

    public List<Arena> getAllArenas() {
        return new ArrayList<>(arenas.values());
    }

    public List<Arena> getPlayableArenas() {
        return arenas.values().stream().filter(Arena::isPlayable).collect(Collectors.toList());
    }

    /**
     * Clears an arena's chests (both tiers) and persists. Used by the admin panel. Clearing chests puts
     * the arena into DRAFT so it stops appearing in matchmaking — a SkyWars map with no chests isn't
     * meant to be played; re-adding chests via setup edit lifts the draft flag.
     */
    public boolean clearChests(String name) {
        Arena arena = getArena(name);
        if (arena == null) {
            return false;
        }
        arena.normalChests.clear();
        arena.middleChests.clear();
        arena.draft = true;
        saveArena(arena);
        return true;
    }
}
