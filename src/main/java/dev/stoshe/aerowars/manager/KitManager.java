package dev.stoshe.aerowars.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.stoshe.aerowars.model.Kit;
import dev.stoshe.aerowars.model.KitItem;
import dev.stoshe.aerowars.util.Console;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Loads and persists selectable {@link Kit}s, seeding defaults on first run. */
public class KitManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File dataDir;
    private final File kitsDir;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();
    /** uuid -> ids of kits the player has purchased. */
    private final Map<UUID, java.util.Set<String>> ownedKits = new ConcurrentHashMap<>();
    private volatile boolean unlocksDirty;
    /** Per-player unlock persistence (JSON file by default, SQL when Database.Enabled). */
    private dev.stoshe.aerowars.data.IPlayerDataRepository unlockRepo;

    public KitManager(File dataDir) {
        this.dataDir = dataDir;
        this.kitsDir = new File(dataDir, "kits");
        if (!kitsDir.exists()) {
            kitsDir.mkdirs();
        }
    }

    // ------------------------------------------------------------ purchase ownership

    private dev.stoshe.aerowars.data.IPlayerDataRepository unlockRepo() {
        if (unlockRepo == null) {
            unlockRepo = dev.stoshe.aerowars.data.PlayerDataRepositoryFactory.create(dataDir,
                    dev.stoshe.aerowars.AeroWars.getInstance().getConfig(), "kit_unlocks.json",
                    "aerowars_kit_unlocks");
        }
        return unlockRepo;
    }

    public void loadUnlocks() {
        ownedKits.clear();
        java.lang.reflect.Type setType =
                new com.google.gson.reflect.TypeToken<java.util.HashSet<String>>() {
                }.getType();
        for (Map.Entry<UUID, com.google.gson.JsonElement> e : unlockRepo().loadAll().entrySet()) {
            try {
                java.util.HashSet<String> set = gson.fromJson(e.getValue(), setType);
                if (set != null) {
                    ownedKits.put(e.getKey(), set);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void saveUnlocks() {
        if (!unlocksDirty) {
            return;
        }
        Map<UUID, com.google.gson.JsonElement> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, java.util.Set<String>> e : ownedKits.entrySet()) {
            out.put(e.getKey(), gson.toJsonTree(e.getValue()));
        }
        unlockRepo().saveAll(out);
        unlocksDirty = false;
    }

    /** Flushes unlocks and releases the repository (SQL pool). Called on plugin shutdown. */
    public void shutdown() {
        unlocksDirty = true;
        saveUnlocks();
        if (unlockRepo != null) {
            unlockRepo.close();
        }
    }

    public boolean ownsKit(UUID uuid, String kitId) {
        if (uuid == null || kitId == null) {
            return false;
        }
        java.util.Set<String> owned = ownedKits.get(uuid);
        return owned != null && owned.contains(kitId.toLowerCase());
    }

    public void grantKit(UUID uuid, String kitId) {
        if (uuid == null || kitId == null) {
            return;
        }
        ownedKits.computeIfAbsent(uuid, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(kitId.toLowerCase());
        unlocksDirty = true;
        saveUnlocks();
    }

    /** A kit is unlocked if it is free, the player owns it (bought), or they hold its permission node. */
    public boolean isUnlocked(UUID uuid, Kit kit) {
        if (kit == null) {
            return false;
        }
        if (kit.isFree()) {
            return true;
        }
        if (kit.isPermission()
                && dev.stoshe.aerowars.util.PermissionUtil.has(uuid, kit.permission, false)) {
            return true;
        }
        if (kit.isPurchase()) {
            // Economy can't take money (no provider, or a deposit-only one) → paid kits are free,
            // otherwise they'd be permanently unbuyable AND not free (a dead end).
            if (!dev.stoshe.aerowars.AeroWars.getInstance().getEconomyService().canCharge()) {
                return true;
            }

            return ownsKit(uuid, kit.id);
        }
        return false;
    }

    public void loadKits() {
        kits.clear();
        File[] files = kitsDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            seedDefaults();
            return;
        }
        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Kit kit = gson.fromJson(reader, Kit.class);
                if (kit != null && kit.id != null) {
                    kits.put(kit.id.toLowerCase(), kit);
                }
            } catch (Exception e) {
                Console.error("Failed to load kit " + file.getName() + ": " + e.getMessage());
            }
        }
        if (kits.isEmpty()) {
            seedDefaults();
        }
        Console.info("Loaded " + kits.size() + " kit(s).");
    }

    private void seedDefaults() {
        // NOTE: item ids follow the verified material-first convention used by the loot tables and the
        // paid kits below (e.g. Armor_Iron_Chest, NOT Armor_Chest_Iron). Armor must use KitItem.ARMOR so
        // it is worn, not dropped into the backpack. Glass_Block is bundled by this plugin (guaranteed).
        Kit warrior = new Kit("warrior", "Guerreiro", "Weapon_Sword_Iron");
        warrior.items.add(new KitItem("Weapon_Sword_Iron", 1, 0, KitItem.HOTBAR));
        warrior.items.add(new KitItem("Food_Bread", 5, 8, KitItem.HOTBAR));
        warrior.items.add(new KitItem("Armor_Iron_Chest", 1, -1, KitItem.ARMOR));
        warrior.items.add(new KitItem("Armor_Iron_Head", 1, -1, KitItem.ARMOR));

        Kit archer = new Kit("archer", "Arqueiro", "Weapon_Shortbow_Crude");
        archer.items.add(new KitItem("Weapon_Shortbow_Crude", 1, 0, KitItem.HOTBAR));
        archer.items.add(new KitItem("Weapon_Arrow_Crude", 16, 1, KitItem.HOTBAR));
        archer.items.add(new KitItem("Weapon_Sword_Iron", 1, 2, KitItem.HOTBAR));
        archer.items.add(new KitItem("Armor_Leather_Medium_Chest", 1, -1, KitItem.ARMOR));

        Kit builder = new Kit("builder", "Construtor", "Glass_Block");
        builder.items.add(new KitItem("Weapon_Sword_Iron", 1, 0, KitItem.HOTBAR));
        builder.items.add(new KitItem("Glass_Block", 64, 1, KitItem.HOTBAR));
        builder.items.add(new KitItem("Glass_Block", 64, 2, KitItem.HOTBAR));

        // --- Example PAID kit (bought with coins via the economy) ---
        Kit berserker = new Kit("berserker", "Berserker", "Weapon_Longsword_Iron");
        berserker.cost = 500;
        berserker.items.add(new KitItem("Weapon_Longsword_Iron", 1, 0, KitItem.HOTBAR));
        berserker.items.add(new KitItem("Food_Bread", 5, 8, KitItem.HOTBAR));
        berserker.items.add(new KitItem("Potion_Health_Large", 2, 7, KitItem.HOTBAR));
        berserker.items.add(new KitItem("Armor_Iron_Chest", 1, -1, KitItem.ARMOR));
        berserker.items.add(new KitItem("Armor_Iron_Head", 1, -1, KitItem.ARMOR));

        // --- Example PAID kit (higher price, full iron armour) ---
        Kit tank = new Kit("tank", "Tanque", "Armor_Iron_Chest");
        tank.cost = 1000;
        tank.items.add(new KitItem("Weapon_Longsword_Iron", 1, 0, KitItem.HOTBAR));
        tank.items.add(new KitItem("Potion_Health_Greater", 3, 7, KitItem.HOTBAR));
        tank.items.add(new KitItem("Armor_Iron_Chest", 1, -1, KitItem.ARMOR));
        tank.items.add(new KitItem("Armor_Iron_Head", 1, -1, KitItem.ARMOR));
        tank.items.add(new KitItem("Armor_Iron_Legs", 1, -1, KitItem.ARMOR));
        tank.items.add(new KitItem("Armor_Iron_Hands", 1, -1, KitItem.ARMOR));

        // --- Example PERMISSION kit (unlocked by the node aerowars.kit.sniper) ---
        Kit sniper = new Kit("sniper", "Franco-Atirador", "Weapon_Shortbow_Combat");
        sniper.permission = "aerowars.kit.sniper";
        sniper.items.add(new KitItem("Weapon_Shortbow_Combat", 1, 0, KitItem.HOTBAR));
        sniper.items.add(new KitItem("Weapon_Arrow_Iron", 32, 1, KitItem.HOTBAR));
        sniper.items.add(new KitItem("Weapon_Sword_Iron", 1, 2, KitItem.HOTBAR));
        sniper.items.add(new KitItem("Armor_Iron_Legs", 1, -1, KitItem.ARMOR));

        for (Kit kit : new Kit[]{warrior, archer, builder, berserker, tank, sniper}) {
            saveKit(kit);
        }
        Console.info("Seeded default kits.");
    }

    public void saveKit(Kit kit) {
        if (kit == null || kit.id == null) {
            return;
        }
        kits.put(kit.id.toLowerCase(), kit);
        File file = new File(kitsDir, kit.id.toLowerCase() + ".json");
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(kit, writer);
        } catch (Exception e) {
            Console.error("Failed to save kit " + kit.id + ": " + e.getMessage());
        }
    }

    public Kit getKit(String id) {
        return id == null ? null : kits.get(id.toLowerCase());
    }

    public List<Kit> getAllKits() {
        return new ArrayList<>(kits.values());
    }

    public boolean hasKit(String id) {
        return id != null && kits.containsKey(id.toLowerCase());
    }

    /** Deletes a kit (map + file). Used by the admin panel. */
    public boolean deleteKit(String id) {
        if (id == null) {
            return false;
        }
        Kit removed = kits.remove(id.toLowerCase());
        File file = new File(kitsDir, id.toLowerCase() + ".json");
        boolean fileGone = !file.exists() || file.delete();
        return removed != null && fileGone;
    }

    /**
     * Snapshots a player's WHOLE inventory (hotbar, storage, backpack, armor, utility, tools) into a
     * kit and saves it. Returns the item count, {@code 0} if empty, or {@code -1} on failure.
     */
    public int saveFromInventory(String name, com.hypixel.hytale.server.core.inventory.Inventory inv) {
        if (name == null || inv == null) {
            return -1;
        }
        Kit kit = new Kit(name.toLowerCase(), name, null);
        snapshotSection(kit, inv.getHotbar(), dev.stoshe.aerowars.model.KitItem.HOTBAR);
        snapshotSection(kit, inv.getStorage(), dev.stoshe.aerowars.model.KitItem.STORAGE);
        snapshotSection(kit, inv.getBackpack(), dev.stoshe.aerowars.model.KitItem.BACKPACK);
        snapshotSection(kit, inv.getArmor(), dev.stoshe.aerowars.model.KitItem.ARMOR);
        snapshotSection(kit, inv.getUtility(), dev.stoshe.aerowars.model.KitItem.UTILITY);
        snapshotSection(kit, inv.getTools(), dev.stoshe.aerowars.model.KitItem.TOOLS);
        if (kit.items.isEmpty()) {
            return 0;
        }
        saveKit(kit);
        return kit.items.size();
    }

    private void snapshotSection(Kit kit, com.hypixel.hytale.server.core.inventory.container.ItemContainer c,
            int container) {
        if (c == null) {
            return;
        }
        c.forEach((slot, stack) -> {
            if (stack != null && !stack.isEmpty() && stack.getItemId() != null) {
                kit.items.add(new dev.stoshe.aerowars.model.KitItem(stack.getItemId(),
                        Math.max(1, stack.getQuantity()), slot, container));
                if (kit.iconItem == null) {
                    kit.iconItem = stack.getItemId();
                }
            }
        });
    }

    /** Valid kit name: 2-16 chars, letters/digits/underscore. */
    public static boolean isValidName(String name) {
        return name != null && name.length() >= 2 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$");
    }
}
