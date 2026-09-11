package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPrefabPreview;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.stoshe.aerowars.util.Console;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Sends the floating 3D prefab preview (the same one Hytale's builder-tools prefab tool shows) for a cage,
 * so an admin can eyeball it from the panel. Mirrors the aero (skyblock) plugin's {@code PrefabPreviewService}:
 * load the cage's {@code .prefab.json} as a real {@link BlockSelection} through {@link PrefabStore} (which
 * builds the block holders the viewer needs), then turn it into the client packet
 * {@link BuilderToolPrefabPreview} via {@link BlockSelection#toPacket()}.
 */
public final class CagePreviewService {

    /** Idle spin for the floating preview — a gentle rotation so the cage reads as a 3D object. */
    private static final int SPIN_SPEED = 15;
    /** Preview zoom: lower = more zoomed OUT (the whole cage fits with room to spare). 100 was too close. */
    private static final int PREVIEW_SCALE = 55;

    private CagePreviewService() {
    }

    /** Shows the cage at {@code prefabPath} as a floating 3D preview (clears it if the file can't load). */
    public static void preview(@Nonnull PlayerRef playerRef, @Nullable Path prefabPath) {
        preview(playerRef, prefabPath, 0);
    }

    /**
     * Shows the cage at {@code prefabPath} as a floating 3D preview, rotated by {@code quarters} 90° turns
     * (so the preview matches a cage configured with its cosmetic's {@code rotation}). Clears it if the file can't load.
     */
    public static void preview(@Nonnull PlayerRef playerRef, @Nullable Path prefabPath, int quarters) {
        BlockSelection selection = null;
        if (prefabPath != null) {
            try {
                selection = PrefabStore.get().getPrefab(prefabPath);
                int q = ((quarters % 4) + 4) % 4;
                if (selection != null && q != 0) {
                    // Native rotation — rotates positions AND per-block facings, matching the in-match paste.
                    selection = selection.rotate(com.hypixel.hytale.math.Axis.Y, q);
                }
            } catch (Exception e) {
                Console.warning("Cage preview: PrefabStore.getPrefab failed for " + prefabPath + ": "
                        + e.getMessage());
            }
        }
        send(playerRef, selection);
    }

    public static void clear(@Nonnull PlayerRef playerRef) {
        send(playerRef, null);
    }

    private static void send(@Nonnull PlayerRef playerRef, @Nullable BlockSelection selection) {
        PacketHandler handler = playerRef.getPacketHandler();
        if (handler == null) {
            return;
        }
        try {
            if (selection == null) {
                handler.writeNoCache(new BuilderToolPrefabPreview(
                        null, null, null, null, null, null, null, null, false, null));
                return;
            }
            EditorBlocksChange blocks = selection.toPacket();
            handler.writeNoCache(new BuilderToolPrefabPreview(
                    blocks.blocksChange,
                    blocks.fluidsChange,
                    blocks.entityChanges,
                    0,             // tilt
                    SPIN_SPEED,    // spinSpeed
                    PREVIEW_SCALE, // previewScale (lower = more zoomed out)
                    null,          // biomeTint
                    null,          // waterTint
                    false,         // blocksOmitted — we always send the full block list
                    null           // bounds — optional, the client sizes the preview from the blocks
            ));
        } catch (Exception e) {
            Console.error("Failed to send cage preview: " + e.getMessage());
        }
    }
}
