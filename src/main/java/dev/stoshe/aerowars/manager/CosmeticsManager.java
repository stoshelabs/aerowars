package dev.stoshe.aerowars.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.model.Cosmetic;
import dev.stoshe.aerowars.model.CosmeticCategory;
import dev.stoshe.aerowars.model.PlayerCosmetics;
import dev.stoshe.aerowars.util.Console;
import dev.stoshe.aerowars.util.PermissionUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.stoshe.aerowars.util.Tr;

/**
 * Owns the cosmetics catalogue ({@code cosmetics.json}, seeded on first run) and each player's
 * owned/selected state ({@code cosmetics_players.json}). Handles unlock checks, permission gating and
 * economy purchases. In-game hooks (cage block, kill/victory particles) read the selected value here.
 */
public class CosmeticsManager {
    /** Result of a buy/select attempt (drives the UI + chat feedback). */
    public enum Result {
        OK, ALREADY_OWNED, LOCKED_PERMISSION, NO_ECONOMY, INSUFFICIENT_FUNDS, NOT_UNLOCKED, UNKNOWN
    }

    private final AeroWars plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File catalogueFile;
    private final File playersFile;

    /** id -> cosmetic, insertion-ordered so tabs render in catalogue order. */
    private final Map<String, Cosmetic> catalogue = new LinkedHashMap<>();
    private final Map<UUID, PlayerCosmetics> players = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    private final File dataDir;
    /** Per-player owned/selected persistence (JSON file by default, SQL when Database.Enabled). */
    private dev.stoshe.aerowars.data.IPlayerDataRepository playerRepo;

    public CosmeticsManager(AeroWars plugin, File dataDir) {
        this.plugin = plugin;
        this.dataDir = dataDir;
        this.catalogueFile = new File(dataDir, "cosmetics.json");
        this.playersFile = new File(dataDir, "cosmetics_players.json");
    }

    private dev.stoshe.aerowars.data.IPlayerDataRepository playerRepo() {
        if (playerRepo == null) {
            playerRepo = dev.stoshe.aerowars.data.PlayerDataRepositoryFactory.create(dataDir,
                    plugin.getConfig(), "cosmetics_players.json", "aerowars_cosmetics");
        }
        return playerRepo;
    }

    // ------------------------------------------------------------ load / save

    public void load() {
        loadCatalogue();
        loadPlayers();
    }

    private void loadCatalogue() {
        catalogue.clear();
        if (catalogueFile.exists()) {
            try (Reader reader = new FileReader(catalogueFile)) {
                Type type = new TypeToken<List<Cosmetic>>() {
                }.getType();
                List<Cosmetic> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    for (Cosmetic c : loaded) {
                        if (c != null && c.id != null) {
                            catalogue.put(c.id, c);
                        }
                    }
                }
            } catch (Exception e) {
                Console.error("Failed to load cosmetics: " + e.getMessage());
            }
        }
        if (catalogue.isEmpty()) {
            seedDefaults();
            saveCatalogue();
        }
        migrateCageValuesToPrefabs();
        Console.info("Loaded " + catalogue.size() + " cosmetic(s).");
    }

    /**
     * Cages moved from a raw block id / {@code "rainbow"} value to a {@code prefab:<name>} reference (all
     * cages are prefabs now). Rewrites the known built-in cage values in an already-existing
     * {@code cosmetics.json} so upgrades keep working; leaves unrecognised/custom values untouched (the
     * cage build falls back to the default prefab for those with a warning). No-op for a fresh seed.
     */
    private void migrateCageValuesToPrefabs() {
        boolean changed = false;
        for (Cosmetic c : catalogue.values()) {
            if (c == null || c.categoryEnum() != CosmeticCategory.CAGE || c.value == null) {
                continue;
            }
            if (c.value.regionMatches(true, 0, "prefab:", 0, "prefab:".length())) {
                continue; // already migrated
            }
            String mapped = switch (c.value) {
                case "Glass_Block" -> "prefab:default";
                case "Glass_Block_Yellow" -> "prefab:gold";
                case "Glass_Block_Red" -> "prefab:red";
                case "Glass_Block_Blue" -> "prefab:blue";
                case "Glass_Block_Lime" -> "prefab:lime";
                case "Glass_Block_Magenta" -> "prefab:vip";
                case "rainbow" -> "prefab:rainbow";
                default -> null;
            };
            if (mapped != null) {
                c.value = mapped;
                changed = true;
            }
        }
        if (changed) {
            saveCatalogue();
            Console.info("Migrated built-in cage cosmetics to prefab cages.");
        }
    }

    private void loadPlayers() {
        players.clear();
        for (Map.Entry<UUID, com.google.gson.JsonElement> e : playerRepo().loadAll().entrySet()) {
            try {
                PlayerCosmetics pc = gson.fromJson(e.getValue(), PlayerCosmetics.class);
                if (pc != null) {
                    players.put(e.getKey(), pc);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void saveCatalogue() {
        try (Writer writer = new FileWriter(catalogueFile)) {
            gson.toJson(new ArrayList<>(catalogue.values()), writer);
        } catch (Exception e) {
            Console.error("Failed to save cosmetics catalogue: " + e.getMessage());
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        Map<UUID, com.google.gson.JsonElement> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, PlayerCosmetics> e : players.entrySet()) {
            out.put(e.getKey(), gson.toJsonTree(e.getValue()));
        }
        playerRepo().saveAll(out);
        dirty = false;
    }

    /** Flushes player cosmetics and releases the repository (SQL pool). Called on plugin shutdown. */
    public void shutdown() {
        dirty = true;
        save();
        if (playerRepo != null) {
            playerRepo.close();
        }
    }

    private void seedDefaults() {
        // Unlock types: "default" = comes with the account (auto-owned); "free" = free but must be CLAIMED
        // ("resgatar"); "purchase" = bought with coins; "permission" = unlocked by a permission node.

        // CAGES — every cage is now a prefab ("prefab:<name>") in the cages/ folder; the built-in ones
        // (default glass, colours, rainbow) are generated on first run (see CageManager). Admins add more
        // by capturing them with Hytale's prefab tool and dropping the .prefab.json in cages/.
        add(new Cosmetic("cage_glass", "cage", "Classic Glass", "The default glass cage.", "default", 0, "", "prefab:default"));
        add(new Cosmetic("cage_gold", "cage", "Amber Glass", "Golden glass cage.", "free", 0, "", "prefab:gold"));
        add(new Cosmetic("cage_red", "cage", "Ruby Glass", "Red glass cage.", "purchase", 200, "", "prefab:red"));
        add(new Cosmetic("cage_blue", "cage", "Sapphire Glass", "Blue glass cage.", "purchase", 200, "", "prefab:blue"));
        add(new Cosmetic("cage_lime", "cage", "Emerald Glass", "Green glass cage.", "purchase", 250, "", "prefab:lime"));
        add(new Cosmetic("cage_vip", "cage", "Royal Glass", "Exclusive VIP magic cage.", "permission", 0,
                "aerowars.cosmetic.cage.vip", "prefab:vip"));
        add(new Cosmetic("cage_rainbow", "cage", "Rainbow Glass", "A random multicolored glass cage.",
                "purchase", 300, "", "prefab:rainbow"));

        // KILL EFFECTS — a firework burst at the victim on an eliminating hit ("none" = disabled).
        add(new Cosmetic("kill_none", "kill", "None", "No kill effect.", "default", 0, "", ""));
        add(new Cosmetic("kill_spark", "kill", "Sparks", "A burst of sparks on a kill.", "free", 0, "", "Firework_Mix2"));
        add(new Cosmetic("kill_blast", "kill", "Blast", "A big explosion on a kill.", "purchase", 250, "", "Firework_GS"));
        add(new Cosmetic("kill_royal", "kill", "Royalty", "Exclusive kill effect.", "permission", 0,
                "aerowars.cosmetic.kill.royal", "Cinematic_Fireworks_Red_XL"));

        // VICTORY — overrides the winner's firework show particle ("default" = the config mix).
        add(new Cosmetic("victory_default", "victory", "Default", "The standard firework show.", "default", 0, "", ""));
        add(new Cosmetic("victory_mix", "victory", "Festival", "Varied colorful fireworks.", "purchase", 300, "", "Firework_Mix3"));
        add(new Cosmetic("victory_grand", "victory", "Grand", "Exclusive victory show.", "permission", 0,
                "aerowars.cosmetic.victory.grand", "Cinematic_Fire_Firework"));

        // TRAILS — ONLY point-emitting particle systems (EmitOffset ~0) so the trail comes off the body,
        // not a wide area around the player. Weather systems (Snow/Embers/Dust_Sparkles, 8-20 block spread)
        // were removed — those are what looked like a cloud "around" the player. value = "ParticleId" or
        // "ParticleId|rainbow" (rainbow cycles the sent colour on tint-capable systems).
        add(new Cosmetic("trail_none", "trail", "None", "No trail.", "default", 0, "", ""));
        add(new Cosmetic("trail_smoke", "trail", "Smoke", "Smoke coming off your body.", "default", 0, "", "Smoke_Fluffy_Floor"));
        add(new Cosmetic("trail_dust", "trail", "Dust", "The dust of your steps.", "free", 0, "", "Block_Sprint_Dust"));
        add(new Cosmetic("trail_pink", "trail", "Pink Smoke", "A rosy smoke.", "purchase", 150, "", "Cinematic_Pink_Smoke"));
        add(new Cosmetic("trail_fireflies", "trail", "Fireflies", "Fireflies right beside you.", "purchase", 150, "", "Example_Fireflies"));
        add(new Cosmetic("trail_magic", "trail", "Magic", "Magic sparks.", "purchase", 200, "", "Magic_Hit"));
        add(new Cosmetic("trail_fire", "trail", "Fire", "Burning flames come off you.", "purchase", 200, "", "Effect_Fire"));
        add(new Cosmetic("trail_fire_blue", "trail", "Blue Fire", "Mystic blue flames.", "purchase", 250, "", "Fire_Blue"));
        add(new Cosmetic("trail_orb", "trail", "Orb", "A glowing orb follows you.", "purchase", 250, "", "Flying_Orb"));
        // Rainbow = the point-emitting dust (BlendLinear, tint-capable) with a colour that cycles — a
        // multicoloured version of the Dust trail, not an area cloud.
        add(new Cosmetic("trail_rainbow", "trail", "Rainbow", "Exclusive multicolored trail.", "permission", 0,
                "aerowars.cosmetic.trail.rainbow", "Block_Sprint_Dust|rainbow"));
    }

    private void add(Cosmetic c) {
        catalogue.put(c.id, c);
    }

    // ------------------------------------------------------------ queries

    /**
     * Localized display name: uses the lang key {@code cosmetics.item.<id>.name} when present, otherwise
     * falls back to the literal {@code name} stored in the catalogue (so admin-added custom cosmetics still
     * show their own text). Same idea for {@link #displayDescription}.
     */
    public String displayName(Cosmetic c) {
        return localized(c == null ? null : c.id, "name", c == null ? "" : c.name);
    }

    public String displayDescription(Cosmetic c) {
        return localized(c == null ? null : c.id, "desc", c == null ? "" : c.description);
    }

    private String localized(String id, String field, String fallback) {
        if (id == null) {
            return fallback == null ? "" : fallback;
        }
        String key = "cosmetics.item." + id + "." + field;
        String v = dev.stoshe.aerowars.util.Tr.t(key);
        return (v == null || v.equals(key)) ? (fallback == null ? "" : fallback) : v;
    }

    public List<Cosmetic> byCategory(CosmeticCategory category) {
        List<Cosmetic> out = new ArrayList<>();
        for (Cosmetic c : catalogue.values()) {
            if (c.categoryEnum() == category) {
                out.add(c);
            }
        }
        return out;
    }

    public Cosmetic get(String id) {
        return id == null ? null : catalogue.get(id);
    }

    /**
     * The CAGE cosmetic that references {@code cageRef} (a {@code "prefab:<name>"} or bare {@code "<name>"}
     * reference), matched by normalized cage name — so cage orientation ({@code rotation}/{@code faceMiddle})
     * can be read wherever a cage is used (a cosmetic pick OR an arena default). Null if no cage cosmetic
     * points at it.
     */
    public Cosmetic cageCosmeticFor(String cageRef) {
        if (cageRef == null) {
            return null;
        }
        String target = dev.stoshe.aerowars.manager.CageManager.normalize(cageRef);
        for (Cosmetic c : catalogue.values()) {
            if (c != null && c.categoryEnum() == CosmeticCategory.CAGE && c.value != null
                    && dev.stoshe.aerowars.manager.CageManager.normalize(c.value).equals(target)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Sets the facing/orientation of the CAGE cosmetic that references {@code cageRef} and persists it to
     * {@code cosmetics.json}. {@code rotationDegrees} (nullable, normalised to 0/90/180/270) calibrates
     * which way the cage's front points at 0°; {@code faceMiddle} (nullable) toggles auto-rotating the cage
     * so its front faces the arena centre (middle-chests). Returns the updated cosmetic, or {@code null}
     * when no cage cosmetic points at that cage (orientation can only live on a cosmetic entry).
     */
    public Cosmetic setCageOrientation(String cageRef, Integer rotationDegrees, Boolean faceMiddle) {
        Cosmetic c = cageCosmeticFor(cageRef);
        if (c == null) {
            return null;
        }
        if (rotationDegrees != null) {
            c.rotation = ((rotationDegrees % 360) + 360) % 360;
        }
        if (faceMiddle != null) {
            c.faceMiddle = faceMiddle;
        }
        saveCatalogue();
        return c;
    }

    private PlayerCosmetics of(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerCosmetics());
    }

    /**
     * A cosmetic is unlocked (owned) if it is a {@code default} (comes with the account), the player holds
     * its permission node, or it is in the player's owned set (a {@code free} cosmetic they CLAIMED, or a
     * {@code purchase} one they bought). Note: {@code free} is NOT auto-owned — it must be claimed first
     * ("resgatar"), which is why the "Adquiridos" filter no longer lists unclaimed free cosmetics.
     */
    public boolean isUnlocked(UUID uuid, Cosmetic c) {
        if (c == null) {
            return false;
        }
        if (c.isDefault()) {
            return true;
        }
        if (c.isPermission()) {
            return PermissionUtil.has(uuid, c.permission, false);
        }
        PlayerCosmetics pc = players.get(uuid);
        return pc != null && pc.owned.contains(c.id);
    }

    /**
     * True when the economy can't take money (no provider, or a deposit-only one). Paid cosmetics are
     * then claimable for free — otherwise a purchase could never succeed and they'd be stuck locked.
     */
    private boolean cannotCharge() {
        return !plugin.getEconomyService().canCharge();
    }

    /** True if this cosmetic can be claimed for free right now (a {@code free} one, or a paid one we can't charge for). */
    public boolean isClaimable(Cosmetic c) {
        return c != null && (c.isFree() || (c.isPurchase() && cannotCharge()));
    }

    public boolean isSelected(UUID uuid, Cosmetic c) {
        if (c == null) {
            return false;
        }
        PlayerCosmetics pc = players.get(uuid);
        return pc != null && c.id.equals(pc.selected.get(c.categoryEnum().id()));
    }

    /** The selected cosmetic's {@link Cosmetic#value} for a category, or {@code null} if none/empty. */
    public String selectedValue(UUID uuid, CosmeticCategory category) {
        PlayerCosmetics pc = players.get(uuid);
        if (pc == null) {
            return null;
        }
        String id = pc.selected.get(category.id());
        if (id == null) {
            return null;
        }
        Cosmetic c = catalogue.get(id);
        if (c == null || c.value == null || c.value.isBlank()) {
            return null;
        }
        // Guard: never apply something the player no longer has unlocked.
        return isUnlocked(uuid, c) ? c.value : null;
    }

    // ------------------------------------------------------------ actions

    /** Selects an already-unlocked cosmetic. */
    public Result select(UUID uuid, String cosmeticId) {
        Cosmetic c = get(cosmeticId);
        if (c == null) {
            return Result.UNKNOWN;
        }
        if (!isUnlocked(uuid, c)) {
            return c.isPermission() ? Result.LOCKED_PERMISSION : Result.NOT_UNLOCKED;
        }
        of(uuid).selected.put(c.categoryEnum().id(), c.id);
        dirty = true;
        save();
        return Result.OK;
    }

    /** Claims a free cosmetic (or a paid one when no economy is present) at no cost, then equips it. */
    public Result claim(UUID uuid, String cosmeticId) {
        Cosmetic c = get(cosmeticId);
        if (c == null) {
            return Result.UNKNOWN;
        }
        if (c.isDefault() || isUnlocked(uuid, c)) {
            return select(uuid, cosmeticId);
        }
        if (!isClaimable(c)) {
            return c.isPermission() ? Result.LOCKED_PERMISSION : Result.NOT_UNLOCKED;
        }
        PlayerCosmetics pc = of(uuid);
        pc.owned.add(c.id);
        pc.selected.put(c.categoryEnum().id(), c.id);
        dirty = true;
        save();
        return Result.OK;
    }

    /** Buys a purchase cosmetic through the economy, then owns + selects it. Free/no-economy → claim. */
    public Result buy(UUID uuid, String cosmeticId) {
        Cosmetic c = get(cosmeticId);
        if (c == null) {
            return Result.UNKNOWN;
        }
        if (c.isDefault() || isUnlocked(uuid, c)) {
            return select(uuid, cosmeticId);
        }
        if (c.isPermission()) {
            return Result.LOCKED_PERMISSION;
        }
        // Free cosmetics and (when no economy is installed) paid ones are claimed for free.
        if (isClaimable(c)) {
            return claim(uuid, cosmeticId);
        }
        // Paid purchase with an economy present.
        if (!plugin.getEconomyService().charge(uuid, c.price)) {
            return Result.INSUFFICIENT_FUNDS;
        }
        PlayerCosmetics pc = of(uuid);
        pc.owned.add(c.id);
        pc.selected.put(c.categoryEnum().id(), c.id);
        dirty = true;
        save();
        return Result.OK;
    }
}
