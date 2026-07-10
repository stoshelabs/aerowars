package dev.stoshe.aerowars.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stoshe.aerowars.model.MapLayout;
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

/**
 * Loads and persists {@link MapLayout}s as one JSON file per map under {@code maps/<template>.json}.
 * Layouts are keyed by world template (lowercased) so several arenas — and a random-map pool — can
 * share the same physical map without duplicating its spawns/chests.
 */
public class MapManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File mapsDir;
    private final Map<String, MapLayout> layouts = new ConcurrentHashMap<>();

    public MapManager(File dataDir) {
        this.mapsDir = new File(dataDir, "maps");

        if (!mapsDir.exists()) {
            mapsDir.mkdirs();
        }
    }

    public void loadMaps() {
        layouts.clear();
        File[] files = mapsDir.listFiles((d, n) -> n.endsWith(".json"));

        if (files == null) {
            return;
        }

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                MapLayout layout = gson.fromJson(reader, MapLayout.class);

                if (layout != null && layout.template != null) {
                    layouts.put(layout.template.toLowerCase(), layout);
                }
            } catch (Exception e) {
                Console.error("Failed to load map " + file.getName() + ": " + e.getMessage());
            }
        }

        Console.info("Loaded " + layouts.size() + " map layout(s).");
    }

    public void saveMap(MapLayout layout) {
        if (layout == null || layout.template == null) {
            return;
        }

        layouts.put(layout.template.toLowerCase(), layout);
        File file = new File(mapsDir, layout.template.toLowerCase() + ".json");

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(layout, writer);
        } catch (Exception e) {
            Console.error("Failed to save map " + layout.template + ": " + e.getMessage());
        }
    }

    /** The layout for a template, or null when that map hasn't been set up. */
    public MapLayout getLayout(String template) {
        return template == null ? null : layouts.get(template.toLowerCase());
    }

    public boolean hasLayout(String template) {
        return template != null && layouts.containsKey(template.toLowerCase());
    }

    public boolean deleteMap(String template) {
        if (template == null) {
            return false;
        }

        MapLayout removed = layouts.remove(template.toLowerCase());
        File file = new File(mapsDir, template.toLowerCase() + ".json");
        boolean fileGone = !file.exists() || file.delete();
        return removed != null && fileGone;
    }

    /** The primary map layout backing an arena (its {@code worldTemplate}), or null when unset. */
    public MapLayout layoutFor(dev.stoshe.aerowars.model.Arena arena) {
        return arena == null ? null : getLayout(arena.worldTemplate);
    }

    /**
     * Representative capacity of an arena, computed from its PRIMARY map layout and the arena's
     * mode/team size (0 when the map has no layout yet). Used for display/matchmaking sizing; a live
     * match uses the capacity of the specific map it cloned.
     */
    public int maxPlayersFor(dev.stoshe.aerowars.model.Arena arena) {
        MapLayout layout = layoutFor(arena);
        return layout == null ? 0 : layout.maxPlayers(arena.mode(), arena.effectiveTeamSize());
    }

    /** True when the arena's primary map has a layout complete for the arena's mode/team size. */
    public boolean isArenaComplete(dev.stoshe.aerowars.model.Arena arena) {
        MapLayout layout = layoutFor(arena);
        return layout != null && layout.isComplete(arena.mode(), arena.effectiveTeamSize());
    }

    /** Eligible for a real match: not a draft AND its primary map is complete. */
    public boolean isArenaPlayable(dev.stoshe.aerowars.model.Arena arena) {
        return arena != null && !arena.draft && isArenaComplete(arena);
    }

    /** Templates that have a saved layout (for tab-completion / listing). */
    public List<String> layoutTemplates() {
        List<String> names = new ArrayList<>();

        for (MapLayout layout : layouts.values()) {
            if (layout.template != null) {
                names.add(layout.template);
            }
        }

        return names;
    }
}
