package dev.stoshe.aerowars.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * File-backed per-player store. The on-disk shape is {@code { "<uuid>": <value>, ... }}, i.e. identical to
 * the old hand-written {@code kit_unlocks.json} / {@code cosmetics_players.json} files — so existing data
 * loads unchanged.
 */
public class JsonPlayerDataRepository implements IPlayerDataRepository {
    private static final Type TYPE = new TypeToken<LinkedHashMap<UUID, JsonElement>>() {
    }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File file;

    public JsonPlayerDataRepository(File file) {
        this.file = file;
    }

    @Override
    public Map<UUID, JsonElement> loadAll() {
        Map<UUID, JsonElement> out = new LinkedHashMap<>();
        if (!file.exists()) {
            return out;
        }
        try (Reader reader = new FileReader(file)) {
            Map<UUID, JsonElement> loaded = gson.fromJson(reader, TYPE);
            if (loaded != null) {
                out.putAll(loaded);
            }
        } catch (Exception e) {
            Console.error("Failed to load " + file.getName() + ": " + e.getMessage());
        }
        return out;
    }

    @Override
    public void saveAll(Map<UUID, JsonElement> data) {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data == null ? new LinkedHashMap<>() : data, TYPE, writer);
        } catch (Exception e) {
            Console.error("Failed to save " + file.getName() + ": " + e.getMessage());
        }
    }
}
