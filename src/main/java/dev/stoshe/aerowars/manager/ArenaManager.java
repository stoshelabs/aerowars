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
    private final MapManager mapManager;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    public ArenaManager(File dataDir, MapManager mapManager) {
        this.arenasDir = new File(dataDir, "arenas");
        this.mapManager = mapManager;
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
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                migrateLegacyLayout(json);
                Arena arena = gson.fromJson(json, Arena.class);

                if (arena != null && arena.name != null) {
                    arenas.put(arena.name.toLowerCase(), arena);
                }
            } catch (Exception e) {
                Console.error("Failed to load arena " + file.getName() + ": " + e.getMessage());
            }
        }
        Console.info("Loaded " + arenas.size() + " arena(s).");
    }

    /**
     * One-time migration for arenas written before the per-map refactor: they stored the layout
     * (spawns/chests/spectator) inline. If this file still carries it and the map has no layout yet,
     * lift it into a {@code maps/<template>.json} so the arena keeps working (and can share the map).
     */
    private void migrateLegacyLayout(com.google.gson.JsonObject json) {
        if (json == null || !json.has("spawnPoints") || !json.has("worldTemplate")) {
            return;
        }

        String template = json.get("worldTemplate").getAsString();
        if (template == null || template.isBlank() || mapManager.hasLayout(template)) {
            return;
        }

        dev.stoshe.aerowars.model.MapLayout layout = gson.fromJson(json, dev.stoshe.aerowars.model.MapLayout.class);
        layout.template = template;
        mapManager.saveMap(layout);
        Console.info("Migrated legacy arena layout into map '" + template + "'.");
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
        return arenas.values().stream().filter(mapManager::isArenaPlayable).collect(Collectors.toList());
    }

    /**
     * Clears the chests on the arena's PRIMARY map (both tiers) and drafts the arena. Used by the admin
     * panel. Chests live per-map now, so this affects every arena sharing that map; the arena is drafted
     * so matchmaking skips it until chests are re-added via setup.
     */
    public boolean clearChests(String name) {
        Arena arena = getArena(name);
        if (arena == null) {
            return false;
        }

        dev.stoshe.aerowars.model.MapLayout layout = mapManager.getLayout(arena.worldTemplate);
        if (layout != null) {
            layout.normalChests.clear();
            layout.middleChests.clear();
            mapManager.saveMap(layout);
        }

        arena.draft = true;
        saveArena(arena);
        return true;
    }
}
