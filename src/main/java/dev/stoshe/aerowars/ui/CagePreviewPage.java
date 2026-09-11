package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Dedicated full-screen page that shows ONE cage prefab as a rotatable 3D model. Opened from the admin
 * panel's Cages tab ("Preview" — Prev/Next cycles the whole cage list, Back returns to the tab) and from the
 * cosmetics shop's Cages tab ("Preview" — a single cage, Back returns to the shop). The 3D model is rendered
 * by the {@code PrefabPreviewComponent} widget in {@code AeroWarsCagePreview.ui}, fed by the
 * {@link CagePreviewService} packet — the inline approach didn't render because the menu has no such widget.
 */
public class CagePreviewPage extends InteractiveCustomUIPage<CagePreviewPage.PageData> {

    private final PlayerRef playerRef;
    private final World sourceWorld;
    private final AeroWars plugin;
    /** Index into the (sorted) cage-name list this page is currently showing (admin cycling mode). */
    private final int index;
    /** True when opened from the cosmetics shop: previews a single cage and returns there (no cycling). */
    private final boolean fromCosmetics;
    /** The specific cage reference to preview in cosmetics mode (a cosmetic's {@code value}); null for admin. */
    private final String cageRef;

    private CagePreviewPage(PlayerRef playerRef, World world, AeroWars plugin, int index, boolean fromCosmetics,
            String cageRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.sourceWorld = world;
        this.plugin = plugin;
        this.index = index;
        this.fromCosmetics = fromCosmetics;
        this.cageRef = cageRef;
    }

    /** Admin-panel entry: cycle the whole cage list from {@code index}, Back returns to the admin Cages tab. */
    public static void open(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
            World world, AeroWars plugin, int index) {
        if (player == null || playerRef == null || plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new CagePreviewPage(playerRef, world, plugin, index, false, null));
    }

    /** Cosmetics-shop entry: preview one cage by reference, Back returns to the cosmetics Cages tab. */
    public static void openForCosmetic(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
            PlayerRef playerRef, World world, AeroWars plugin, String cageRef) {
        if (player == null || playerRef == null || plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                (CustomUIPage) new CagePreviewPage(playerRef, world, plugin, 0, true, cageRef));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cb,
            @Nonnull UIEventBuilder eb, @Nonnull Store<EntityStore> store) {
        cb.append("Pages/AeroWarsCagePreview.ui");
        cb.set("#CagePreviewTitle.Text", Tr.t("cage.preview_title"));
        cb.set("#BtnCagePreviewBack.Text", Tr.t("cage.preview_back"));
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCagePreviewBack",
                EventData.of("Action", "Back"), false);

        // Cosmetics mode: a single cage, no Prev/Next cycling.
        if (this.fromCosmetics) {
            String name = plugin.getCageManager().normalize(this.cageRef);
            cb.set("#BtnCagePrev.Visible", false);
            cb.set("#BtnCageNext.Visible", false);
            cb.set("#CagePreviewCounter.Text", "");
            boolean exists = this.cageRef != null && plugin.getCageManager().exists(this.cageRef);
            System.out.println("[AeroWars] Cosmetic cage preview: ref='" + this.cageRef + "' -> name='" + name
                    + "' exists=" + exists + " path=" + plugin.getCageManager().pathFor(this.cageRef));
            if (!exists) {
                cb.set("#CagePreviewName.Text", Tr.t("cage.preview_none"));
                CagePreviewService.clear(playerRef);
                return;
            }
            cb.set("#CagePreviewName.Text", name);
            int quarters = plugin.getMatchManager().cageRotationQuarters(name);
            CagePreviewService.preview(playerRef, plugin.getCageManager().pathFor(this.cageRef), quarters);
            return;
        }

        List<String> names = plugin.getCageManager().names();
        if (names.isEmpty()) {
            cb.set("#CagePreviewName.Text", Tr.t("cage.preview_none"));
            cb.set("#CagePreviewCounter.Text", "");
            cb.set("#BtnCagePrev.Visible", false);
            cb.set("#BtnCageNext.Visible", false);
            CagePreviewService.clear(playerRef);
            return;
        }

        int i = Math.max(0, Math.min(this.index, names.size() - 1));
        String name = names.get(i);
        cb.set("#CagePreviewName.Text", name);
        cb.set("#CagePreviewCounter.Text", names.size() > 1 ? (i + 1) + " / " + names.size() : "");

        boolean many = names.size() > 1;
        cb.set("#BtnCagePrev.Visible", many);
        cb.set("#BtnCageNext.Visible", many);
        if (many) {
            eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCagePrev",
                    EventData.of("Action", "Prev"), false);
            eb.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCageNext",
                    EventData.of("Action", "Next"), false);
        }

        // Feed the 3D viewer with this cage's model — load the real prefab file via PrefabStore (Aero's path),
        // rotated to match its cosmetic's configured rotation so the preview shows the in-match orientation.
        int quarters = plugin.getMatchManager().cageRotationQuarters(name);
        CagePreviewService.preview(playerRef, plugin.getCageManager().pathFor(name), quarters);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull PageData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        World world = resolveWorld(player);
        String action = data.action == null ? "" : data.action.trim();

        // Cosmetics mode: no cycling — any action (Back / dismissal) drops the preview and returns to the shop.
        if (this.fromCosmetics) {
            CagePreviewService.clear(playerRef);
            CosmeticsPage.open(player, ref, store, playerRef, world, plugin,
                    dev.stoshe.aerowars.model.CosmeticCategory.CAGE);
            return;
        }

        List<String> names = plugin.getCageManager().names();
        int size = Math.max(1, names.size());
        switch (action) {
            case "Prev" -> {
                CagePreviewService.clear(playerRef);
                open(player, ref, store, playerRef, world, plugin, ((this.index - 1) % size + size) % size);
            }
            case "Next" -> {
                CagePreviewService.clear(playerRef);
                open(player, ref, store, playerRef, world, plugin, (this.index + 1) % size);
            }
            default -> {
                // Back (and any dismissal): drop the preview and return to the admin Cages tab.
                CagePreviewService.clear(playerRef);
                AeroAdminPage.open(player, ref, store, playerRef, world, plugin, "cages");
            }
        }
    }

    private World resolveWorld(Player player) {
        try {
            if (player != null && player.getWorld() != null) {
                return player.getWorld();
            }
        } catch (Exception ignored) {
        }
        return this.sourceWorld;
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action).add()
                .build();
        public String action;
    }
}
