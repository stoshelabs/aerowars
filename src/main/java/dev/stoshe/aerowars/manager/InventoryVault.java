package dev.stoshe.aerowars.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.util.Console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves a player's hotbar/storage/backpack and game mode when they enter a match,
 * then restores everything when they leave (win, death, /leave or match cleanup).
 * All inventory work is marshalled onto the owning world thread, and the player
 * ref is resolved fresh there (a cross-world teleport changes the ECS ref).
 */
public class InventoryVault {
    // Every inventory section a kit can write to must be captured AND cleared here, otherwise kit gear in
    // armor/utility/tools survives the match (that was the "winner kept their kit" bug — inv.clear() only
    // touches hotbar/storage/backpack).
    private static final int HOTBAR = 0;
    private static final int STORAGE = 1;
    private static final int BACKPACK = 2;
    private static final int ARMOR = 3;
    private static final int UTILITY = 4;
    private static final int TOOLS = 5;
    private static final int SECTION_COUNT = 6;

    private record SavedSlot(int container, short slot, ItemStack stack) {
    }

    private static final class Saved {
        GameMode mode;
        final List<SavedSlot> items = new ArrayList<>();
    }

    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();

    public boolean has(UUID uuid) {
        return saved.containsKey(uuid);
    }

    public void discard(UUID uuid) {
        saved.remove(uuid);
    }

    /** Snapshots the inventory + game mode, clears it, and switches to Adventure. */
    public void enterMatch(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return;
        }
        world.execute(() -> {
            try {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = pr.getReference();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                Saved s = new Saved();
                s.mode = player.getGameMode();
                Inventory inv = player.getInventory();
                for (int i = 0; i < SECTION_COUNT; i++) {
                    snapshot(container(inv, i), i, s.items);
                }
                saved.put(uuid, s);
                clearAll(inv);
                Player.setGameMode(ref, GameMode.Adventure, store);
            } catch (Exception e) {
                Console.warning("InventoryVault.enterMatch failed: " + e.getMessage());
            }
        });
    }

    /**
     * Restores the saved inventory + game mode once the player is actually inside {@code world}. Returns
     * {@code false} when the cross-world teleport hasn't settled yet (player unresolved / still in another
     * world) so the caller can RETRY — and crucially the snapshot is NOT consumed until it applies, so a
     * too-early call can't drop the original inventory (that was leaving spectators with the spec items and
     * no original gear). Returns {@code true} once applied (or there was nothing to restore).
     */
    public boolean restore(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return true;
        }
        if (!saved.containsKey(uuid)) {
            return true; // nothing to restore
        }
        // Only proceed once the player is resolvable AND actually in the target world.
        PlayerRef check = Universe.get().getPlayer(uuid);
        if (check == null || check.getWorldUuid() == null
                || Universe.get().getWorld(check.getWorldUuid()) != world) {
            return false; // teleport not settled — retry
        }
        Saved s = saved.remove(uuid);
        if (s == null) {
            return true;
        }
        world.execute(() -> {
            try {
                PlayerRef pr = Universe.get().getPlayer(uuid);
                if (pr == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = pr.getReference();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                Inventory inv = player.getInventory();
                clearAll(inv);
                for (SavedSlot slot : s.items) {
                    ItemContainer c = container(inv, slot.container());
                    if (c != null && slot.stack() != null) {
                        c.setItemStackForSlot(slot.slot(), slot.stack());
                    }
                }
                Player.setGameMode(ref, s.mode != null ? s.mode : GameMode.Adventure, store);
            } catch (Exception e) {
                Console.warning("InventoryVault.restore failed: " + e.getMessage());
            }
        });
        return true;
    }

    /**
     * Restores the saved inventory + game mode when a player DISCONNECTS mid-match.
     * Runs inline (not marshalled/deferred) using the disconnect event's own live
     * {@link PlayerRef} — {@code Universe.getPlayer(uuid)} already returns null at this
     * point, and deferring risks running after the engine has persisted the (cleared/
     * kit) inventory. This way the player logs back in with their original items, not
     * the match kit.
     */
    public void restoreOnDisconnect(PlayerRef pr) {
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        Saved s = saved.remove(uuid);
        if (s == null) {
            return;
        }
        World world = Universe.get().getWorld(pr.getWorldUuid());
        if (world == null) {
            return;
        }
        // The disconnect event fires on a network worker thread, NOT the world thread, so ECS access
        // must be marshalled onto the world thread (a direct call throws "Assert not in thread!").
        Ref<EntityStore> ref = pr.getReference();
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                Inventory inv = player.getInventory();
                clearAll(inv);
                for (SavedSlot slot : s.items) {
                    ItemContainer c = container(inv, slot.container());
                    if (c != null && slot.stack() != null) {
                        c.setItemStackForSlot(slot.slot(), slot.stack());
                    }
                }
                Player.setGameMode(ref, s.mode != null ? s.mode : GameMode.Adventure, store);
            } catch (Exception e) {
                Console.warning("InventoryVault.restoreOnDisconnect failed: " + e.getMessage());
            }
        });
    }

    private void snapshot(ItemContainer container, int index, List<SavedSlot> out) {
        if (container == null) {
            return;
        }
        container.forEach((slot, stack) -> {
            if (stack != null && !stack.isEmpty()) {
                out.add(new SavedSlot(index, slot, stack));
            }
        });
    }

    private ItemContainer container(Inventory inv, int index) {
        return switch (index) {
            case HOTBAR -> inv.getHotbar();
            case STORAGE -> inv.getStorage();
            case BACKPACK -> inv.getBackpack();
            case ARMOR -> inv.getArmor();
            case UTILITY -> inv.getUtility();
            case TOOLS -> inv.getTools();
            default -> null;
        };
    }

    /** Empties every captured section (hotbar/storage/backpack/armor/utility/tools) so no kit gear lingers. */
    private void clearAll(Inventory inv) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            ItemContainer c = container(inv, i);
            if (c != null) {
                try {
                    c.clear();
                } catch (Exception ignored) {
                    // Best effort — a section that can't be cleared is left to inv.clear() semantics.
                }
            }
        }
    }
}
