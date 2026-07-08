package dev.stoshe.aerowars.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.stoshe.aerowars.model.ChestLocation;
import dev.stoshe.aerowars.model.LootItem;
import dev.stoshe.aerowars.model.LootTable;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages weighted loot tables and fills chest blocks with rolled loot. Loot
 * generation is deterministic-free (weighted random); chest population is done
 * on the world thread by the {@link MatchManager}.
 */
public class LootManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File lootFile;
    private final Random random = new Random();
    private final Map<String, LootTable> tables = new ConcurrentHashMap<>();

    public LootManager(File dataDir) {
        this.lootFile = new File(dataDir, "loot.json");
    }

    public void loadLootTables() {
        tables.clear();
        if (lootFile.exists()) {
            try (Reader reader = new FileReader(lootFile)) {
                Type type = new TypeToken<Map<String, LootTable>>() {
                }.getType();
                Map<String, LootTable> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    tables.putAll(loaded);
                }
            } catch (Exception e) {
                Console.error("Failed to load loot tables: " + e.getMessage());
            }
        }
        if (tables.isEmpty()) {
            seedDefaults();
            save();
        }
        Console.info("Loaded " + tables.size() + " loot table(s).");
    }

    private void seedDefaults() {
        // Item ids verified against the current Assets.zip. Island (normal) chests give basic gear;
        // middle chests give the stronger loot. weight = relative chance, min/maxAmount = stack size.
        LootTable normal = new LootTable("normal", 3, 6);
        normal.items.add(new LootItem("Weapon_Longsword_Crude", 1, 1, 18));
        normal.items.add(new LootItem("Weapon_Sword_Iron", 1, 1, 8));
        normal.items.add(new LootItem("Armor_Leather_Medium_Chest", 1, 1, 10));
        normal.items.add(new LootItem("Armor_Iron_Head", 1, 1, 6));
        normal.items.add(new LootItem("Food_Bread", 1, 3, 24));
        normal.items.add(new LootItem("Rock_Stone", 16, 32, 22));
        normal.items.add(new LootItem("Wood_Deadwood_Planks", 16, 32, 16));
        normal.items.add(new LootItem("Weapon_Shortbow_Crude", 1, 1, 6));
        normal.items.add(new LootItem("Weapon_Arrow_Crude", 4, 8, 15));
        normal.items.add(new LootItem("Potion_Health_Lesser", 1, 2, 8));
        tables.put(normal.name, normal);

        // Middle chests = the strong "top-tier" example loot (Adamantite/Onyxium/Mithril gear, legendary
        // Void/Flame weapons, bombs, big potions). This is an EXAMPLE table — admins can trim/swap it via
        // the admin panel Loot tab or by editing loot.json. All ids verified against the current Assets.zip.
        LootTable middle = new LootTable("middle", 5, 10);
        // Top weapons.
        middle.items.add(new LootItem("Weapon_Longsword_Adamantite", 1, 1, 9));
        middle.items.add(new LootItem("Weapon_Longsword_Onyxium", 1, 1, 6));
        middle.items.add(new LootItem("Weapon_Longsword_Void", 1, 1, 3));
        middle.items.add(new LootItem("Weapon_Axe_Adamantite", 1, 1, 6));
        middle.items.add(new LootItem("Weapon_Shortbow_Adamantite", 1, 1, 7));
        middle.items.add(new LootItem("Weapon_Shortbow_Flame", 1, 1, 3));
        middle.items.add(new LootItem("Weapon_Arrow_Iron", 12, 24, 14));
        // Top armour (mostly Adamantite, some Mithril/Onyxium for variety).
        middle.items.add(new LootItem("Armor_Adamantite_Chest", 1, 1, 8));
        middle.items.add(new LootItem("Armor_Adamantite_Legs", 1, 1, 8));
        middle.items.add(new LootItem("Armor_Adamantite_Head", 1, 1, 8));
        middle.items.add(new LootItem("Armor_Adamantite_Hands", 1, 1, 8));
        middle.items.add(new LootItem("Armor_Mithril_Chest", 1, 1, 6));
        middle.items.add(new LootItem("Armor_Onyxium_Head", 1, 1, 4));
        // Consumables + utility.
        middle.items.add(new LootItem("Potion_Health_Large", 1, 3, 12));
        middle.items.add(new LootItem("Potion_Regen_Health_Large", 1, 2, 8));
        middle.items.add(new LootItem("Food_Pie_Meat", 2, 4, 14));
        middle.items.add(new LootItem("Weapon_Bomb_Fire", 1, 3, 8));
        middle.items.add(new LootItem("Weapon_Grenade_Frag", 1, 2, 6));
        tables.put(middle.name, middle);
    }

    public void save() {
        try (Writer writer = new FileWriter(lootFile)) {
            gson.toJson(tables, writer);
        } catch (Exception e) {
            Console.error("Failed to save loot tables: " + e.getMessage());
        }
    }

    public LootTable getTable(String name) {
        return name == null ? null : tables.get(name);
    }

    /** Loot-table names (e.g. "normal", "middle"). */
    public List<String> tableNames() {
        return new ArrayList<>(tables.keySet());
    }

    /** Items of a table (empty list if unknown). Used by the admin panel. */
    public List<LootItem> getItems(String table) {
        LootTable t = getTable(table);
        return t == null || t.items == null ? new ArrayList<>() : t.items;
    }

    /** Adds an item to a table and persists. */
    public boolean addItem(String table, String itemId, int min, int max, int weight) {
        LootTable t = getTable(table);
        if (t == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        int lo = Math.max(1, min);
        t.items.add(new LootItem(itemId.trim(), lo, Math.max(lo, max), Math.max(1, weight)));
        save();
        return true;
    }

    /** Removes the item at {@code index} from a table and persists. */
    public boolean removeItem(String table, int index) {
        LootTable t = getTable(table);
        if (t == null || t.items == null || index < 0 || index >= t.items.size()) {
            return false;
        }
        t.items.remove(index);
        save();
        return true;
    }

    /** Rolls a fresh set of item stacks for a chest tier. */
    public List<ItemStack> rollLoot(String tableName) {
        LootTable table = getTable(tableName);
        List<ItemStack> result = new ArrayList<>();
        if (table == null || table.items == null || table.items.isEmpty()) {
            return result;
        }
        int count = table.minItems + random.nextInt(Math.max(1, table.maxItems - table.minItems + 1));
        int totalWeight = table.totalWeight();
        for (int i = 0; i < count && totalWeight > 0; i++) {
            LootItem picked = pickWeighted(table, totalWeight);
            if (picked == null) {
                continue;
            }
            int amount = picked.minAmount + random.nextInt(Math.max(1, picked.maxAmount - picked.minAmount + 1));
            result.add(new ItemStack(picked.itemId, amount));
        }
        return result;
    }

    private LootItem pickWeighted(LootTable table, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int acc = 0;
        for (LootItem item : table.items) {
            acc += Math.max(1, item.weight);
            if (roll < acc) {
                return item;
            }
        }
        return null;
    }

    /**
     * Places the chest block for a match. MUST be called on the world thread
     * (wrap in {@code world.execute(...)}).
     *
     * <p>NOTE: auto-filling the chest inventory is deferred — the block-container
     * state API changed in the current server build and no reference plugin
     * demonstrates it yet. {@link #rollLoot(String)} already produces the loot;
     * wiring it into the placed chest is the one remaining follow-up.
     */
    public void populateChest(World world, ChestLocation chest) {
        if (world == null || chest == null || chest.pos == null) {
            return;
        }
        int x = chest.pos.blockX();
        int y = chest.pos.blockY();
        int z = chest.pos.blockZ();
        String blockId = (chest.blockId == null || chest.blockId.isBlank())
                ? "Furniture_Crude_Chest_Small" : chest.blockId;
        try {
            // The 5th arg is the block rotation index — reapply what the admin placed during setup.
            world.setBlock(x, y, z, blockId, chest.rotationIndex);
        } catch (Exception e) {
            Console.warning("Failed to place chest at " + x + "," + y + "," + z + ": " + e.getMessage());
        }
    }

    /**
     * Rolls loot for the chest's tier (or {@code tableOverride} when non-null) and writes it into the
     * placed chest's container. MUST run on the world thread, and AFTER {@link #populateChest} has
     * placed the block on a prior tick (the {@code ItemContainerBlock} component is created by a block
     * SYSTEM, not synchronously).
     *
     * @return {@code true} once the chest was actually filled (or is empty because the table is);
     *         {@code false} if the container isn't ready yet (chunk not loaded / component/ capacity
     *         not created) so the caller can retry. We deliberately do NOT force-create the component
     *         — an ensured component comes back with capacity 0 and masks the real one, leaving the
     *         chest permanently empty. Retrying until the block system builds the real container fixes
     *         the "chests never fill" bug.
     */
    public boolean fillChest(World world, ChestLocation chest, String tableOverride) {
        if (world == null || chest == null || chest.pos == null) {
            return true;
        }
        int x = chest.pos.blockX();
        int y = chest.pos.blockY();
        int z = chest.pos.blockZ();
        String at = x + "," + y + "," + z;
        try {
            ChunkStore chunkStore = world.getChunkStore();
            if (chunkStore == null) {
                return false; // world not ready — retry
            }
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) {
                return false; // chunk not loaded yet — retry
            }
            // The LIVE block-component entity ref. getBlockComponentHolder(x,y,z) returns a DETACHED COPY
            // (a Holder), so mutating it + replaceComponent never reached the chest the player actually
            // opens — that was the "logs say placed but chest is empty" bug. OpenContainerInteraction reads
            // this same live component via the chunk store, so we mutate it directly here.
            Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
            if (blockRef == null) {
                return false; // block-component entity not built yet — retry
            }
            Store<ChunkStore> store = chunkStore.getStore();
            ItemContainerBlock block = store.getComponent(blockRef, ItemContainerBlock.getComponentType());
            if (block == null) {
                return false; // container component not created yet — retry
            }
            // Never mutate a chest a player is currently viewing: clearing the live container under an open
            // ContainerBlockWindow desyncs/bugs it (the chest then won't open again). A refill/loot-event
            // simply skips a chest in use — it'll be refreshed the next cycle once closed.
            if (block.getWindows() != null && !block.getWindows().isEmpty()) {
                return true; // in use (open window) — skip this fill, refresh next cycle
            }
            SimpleItemContainer container = block.getItemContainer();
            if (container == null || container.getCapacity() <= 0) {
                return false; // container not ready — retry
            }
            short capacity = container.getCapacity();
            String tableName = tableOverride != null ? tableOverride : chest.type().defaultLootTable();
            List<ItemStack> loot = rollLoot(tableName);
            // Mutate the LIVE container in place. clear()/setItemStackForSlot() fire the container change
            // events that sync any open window and mark state dirty — no setItemContainer/replaceComponent
            // needed (those operated on the detached copy). Clear first so a refill replaces, not stacks.
            container.clear();
            int n = 0;
            if (!loot.isEmpty()) {
                // Scatter the rolled stacks into random distinct slots.
                List<Short> slots = new ArrayList<>();
                for (short s = 0; s < capacity; s++) {
                    slots.add(s);
                }
                Collections.shuffle(slots, random);
                n = Math.min(loot.size(), slots.size());
                for (int i = 0; i < n; i++) {
                    container.setItemStackForSlot(slots.get(i), loot.get(i));
                }
            }
            // Persist across chunk save/unload.
            chunk.getBlockComponentChunk().markNeedsSaving();
            return true;
        } catch (Exception e) {
            Console.warning("[loot] Failed to fill chest " + at + ": " + e);
            return true; // don't retry on a hard error
        }
    }
}
