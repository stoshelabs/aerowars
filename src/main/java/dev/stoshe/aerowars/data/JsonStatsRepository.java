package dev.stoshe.aerowars.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.stoshe.aerowars.model.PlayerStats;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Default stats backend: a pretty-printed {@code stats.json} file (the original behavior). */
public class JsonStatsRepository implements IStatsRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File statsFile;

    public JsonStatsRepository(File statsFile) {
        this.statsFile = statsFile;
    }

    @Override
    public Map<UUID, PlayerStats> loadAll() {
        Map<UUID, PlayerStats> out = new HashMap<>();
        if (statsFile.exists()) {
            try (Reader reader = new FileReader(statsFile)) {
                Type type = new TypeToken<Map<UUID, PlayerStats>>() {
                }.getType();
                Map<UUID, PlayerStats> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    out.putAll(loaded);
                }
            } catch (Exception e) {
                Console.error("Failed to load stats: " + e.getMessage());
            }
        }
        return out;
    }

    @Override
    public void saveAll(Map<UUID, PlayerStats> stats) {
        try (Writer writer = new FileWriter(statsFile)) {
            gson.toJson(stats, writer);
        } catch (Exception e) {
            Console.error("Failed to save stats: " + e.getMessage());
        }
    }
}
