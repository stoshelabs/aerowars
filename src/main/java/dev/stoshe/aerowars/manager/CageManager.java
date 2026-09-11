package dev.stoshe.aerowars.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stoshe.aerowars.model.CagePrefab;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads cage prefabs from {@code cages/*.prefab.json} under the plugin data dir — one file per cage,
 * the Hytale-native format the in-game prefab capture tool exports (see {@link CagePrefab}). Mirrors
 * {@link MapManager}: admins author new cages with Hytale's prefab tool and drop the file in the folder.
 *
 * <p>On first run (empty folder) the built-in cages that used to be procedural — the default glass box,
 * the coloured variants and the rainbow one — are generated to match the old geometry, so a cage always
 * exists out of the box and the shipped CAGE cosmetics ({@code prefab:<name>}) resolve.
 */
public class CageManager {
    /** Vivid glass colours cycled for the generated rainbow cage (deterministic by block order). */
    private static final String[] RAINBOW_GLASS = {
            "Glass_Block_Red", "Glass_Block_Orange", "Glass_Block_Yellow", "Glass_Block_Lime",
            "Glass_Block_Green", "Glass_Block_Cyan", "Glass_Block_Blue", "Glass_Block_Purple",
            "Glass_Block_Pink", "Glass_Block_Magenta"
    };

    /** The default cage, used as the guard fallback when a selected cage prefab is missing. */
    public static final String DEFAULT_CAGE = "default";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File cagesDir;
    private final Map<String, CagePrefab> cages = new ConcurrentHashMap<>();

    public CageManager(File dataDir) {
        this.cagesDir = new File(dataDir, "cages");
        if (!cagesDir.exists()) {
            cagesDir.mkdirs();
        }
    }

    /** Normalizes a cage reference ({@code "prefab:Red"}, {@code "red.prefab.json"}, {@code "Red"}) to a key. */
    public static String normalize(String ref) {
        if (ref == null) {
            return "";
        }
        String s = ref.trim();
        if (s.regionMatches(true, 0, "prefab:", 0, "prefab:".length())) {
            s = s.substring("prefab:".length());
        }
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.toLowerCase(Locale.ROOT).endsWith(".prefab.json")) {
            s = s.substring(0, s.length() - ".prefab.json".length());
        } else if (s.toLowerCase(Locale.ROOT).endsWith(".json")) {
            s = s.substring(0, s.length() - ".json".length());
        }
        return s.toLowerCase(Locale.ROOT);
    }

    public void loadCages() {
        cages.clear();

        File[] files = cagesDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".prefab.json"));
        // First run (nothing authored yet): generate the built-in cages so defaults always resolve.
        if (files == null || files.length == 0) {
            seedBuiltins();
            files = cagesDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".prefab.json"));
        }
        if (files == null) {
            return;
        }

        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                CagePrefab prefab = gson.fromJson(reader, CagePrefab.class);
                if (prefab == null) {
                    continue;
                }
                String key = normalize(file.getName());
                prefab.name = key;
                if (prefab.isEmpty()) {
                    Console.warning("Cage prefab '" + file.getName() + "' has no blocks — skipping.");
                    continue;
                }
                cages.put(key, prefab);
            } catch (Exception e) {
                Console.error("Failed to load cage " + file.getName() + ": " + e.getMessage());
            }
        }

        Console.info("Loaded " + cages.size() + " cage prefab(s).");
        if (!cages.containsKey(DEFAULT_CAGE)) {
            Console.warning("No '" + DEFAULT_CAGE + "' cage prefab present — matches will have no cage "
                    + "if a player's selected cage prefab is also missing. Add cages/" + DEFAULT_CAGE
                    + ".prefab.json (capture one with Hytale's prefab tool).");
        }
    }

    /** The cage for a reference (cosmetic value / arena default), or {@code null} if unknown. */
    public CagePrefab get(String ref) {
        return cages.get(normalize(ref));
    }

    public boolean exists(String ref) {
        return cages.containsKey(normalize(ref));
    }

    /** Filesystem path of a cage's {@code .prefab.json} (for loading it as a native prefab / BlockSelection). */
    public java.nio.file.Path pathFor(String ref) {
        return new File(cagesDir, normalize(ref) + ".prefab.json").toPath();
    }

    public CagePrefab getDefault() {
        return cages.get(DEFAULT_CAGE);
    }

    /** Cage names (keys), sorted, for tab-completion and admin listing. */
    public List<String> names() {
        List<String> out = new ArrayList<>(cages.keySet());
        out.sort(String::compareTo);
        return out;
    }

    public int count() {
        return cages.size();
    }

    /** Deletes a cage prefab (map + file). Returns false if it doesn't exist or the file couldn't be removed. */
    public boolean deleteCage(String ref) {
        String key = normalize(ref);
        if (!cages.containsKey(key)) {
            return false;
        }
        File file = new File(cagesDir, key + ".prefab.json");
        boolean removedFile = !file.exists() || file.delete();
        if (removedFile) {
            cages.remove(key);
        }
        return removedFile;
    }

    // ------------------------------------------------------------ built-in generation

    private void seedBuiltins() {
        int r = 1;
        int h = 2;
        writeBuiltin(DEFAULT_CAGE, r, h, "Glass_Block", false);
        writeBuiltin("gold", r, h, "Glass_Block_Yellow", false);
        writeBuiltin("red", r, h, "Glass_Block_Red", false);
        writeBuiltin("blue", r, h, "Glass_Block_Blue", false);
        writeBuiltin("lime", r, h, "Glass_Block_Lime", false);
        writeBuiltin("vip", r, h, "Glass_Block_Magenta", false);
        writeBuiltin("rainbow", r, h, null, true);
        Console.info("Seeded built-in cage prefabs in " + cagesDir.getName() + "/.");
    }

    /**
     * Generates a hollow glass box matching the old procedural cage and writes it as a {@code .prefab.json}.
     * Local coords are non-negative with an explicit anchor at the player's standing block (interior
     * centre, floor one below): {@code anchor = (r, 1, r)}, floor at localY 0, ceiling at localY h+1.
     * So on paste, {@code world = spawn + (block - anchor)} puts the player's feet at the spawn block.
     */
    private void writeBuiltin(String name, int r, int h, String block, boolean rainbow) {
        CagePrefab prefab = new CagePrefab();
        prefab.version = 8;
        prefab.blockIdVersion = 10;
        prefab.anchorX = r;
        prefab.anchorY = 1;
        prefab.anchorZ = r;
        prefab.blocks = new ArrayList<>();

        int sizeXZ = 2 * r;   // localX/Z span 0..2r
        int top = h + 1;      // localY span 0..h+1
        int idx = 0;
        for (int x = 0; x <= sizeXZ; x++) {
            for (int z = 0; z <= sizeXZ; z++) {
                for (int y = 0; y <= top; y++) {
                    boolean shell = x == 0 || x == sizeXZ || z == 0 || z == sizeXZ || y == 0 || y == top;
                    if (!shell) {
                        continue;
                    }
                    CagePrefab.PrefabBlock b = new CagePrefab.PrefabBlock();
                    b.x = x;
                    b.y = y;
                    b.z = z;
                    b.name = rainbow ? RAINBOW_GLASS[idx % RAINBOW_GLASS.length] : block;
                    prefab.blocks.add(b);
                    idx++;
                }
            }
        }

        File file = new File(cagesDir, name + ".prefab.json");
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(prefab, writer);
        } catch (Exception e) {
            Console.error("Failed to write built-in cage '" + name + "': " + e.getMessage());
        }
    }
}
