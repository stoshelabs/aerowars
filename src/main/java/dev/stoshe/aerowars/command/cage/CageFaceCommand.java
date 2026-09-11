package dev.stoshe.aerowars.command.cage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.command.CageArgType;
import dev.stoshe.aerowars.command.ChoiceArgType;
import dev.stoshe.aerowars.manager.CageManager;
import dev.stoshe.aerowars.model.Cosmetic;
import dev.stoshe.aerowars.util.ChatUtil;
import dev.stoshe.aerowars.util.PermissionUtil;
import dev.stoshe.aerowars.util.Tr;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars cage face <cage> <north|east|south|west|off>} — points a cage prefab's front and turns
 * on auto-facing the middle-chests. The direction is where the cage's front currently faces as captured
 * (one of the four cardinals — never diagonal); the plugin stores the calibration and, at match start,
 * rotates each spawn's cage so that front points at the arena centre. {@code off} disables auto-facing.
 * The setting is written to the cage's entry in {@code cosmetics.json} ({@code rotation}/{@code faceMiddle}).
 */
public class CageFaceCommand extends AbstractPlayerCommand {
    private final AeroWars plugin;
    private final CageManager cageManager;
    private final RequiredArg<String> cageArg;
    private final RequiredArg<String> dirArg;

    public CageFaceCommand(@Nonnull AeroWars plugin) {
        super("face", Tr.t("command.cage.face"));
        this.plugin = plugin;
        this.cageManager = plugin.getCageManager();
        this.cageArg = withRequiredArg("cage", Tr.t("command.arg.cage"), ArgTypes.STRING)
                .withSuggestionOverride(new CageArgType(cageManager));
        this.dirArg = withRequiredArg("direction", Tr.t("command.arg.direction"), ArgTypes.STRING)
                .withSuggestionOverride(new ChoiceArgType("direction", "north", "east", "south", "west", "off"));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!PermissionUtil.isAdmin(playerRef.getUuid())) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("general.no_permission")));
            return;
        }

        String input = cageArg.get(context);
        if (!cageManager.exists(input)) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cage.not_found", "cage", String.valueOf(input))));
            return;
        }
        String name = CageManager.normalize(input);

        String dir = dirArg.get(context);
        dir = dir == null ? "" : dir.trim().toLowerCase();

        // "off" only clears auto-facing; the base rotation calibration is left as-is.
        if (dir.equals("off") || dir.equals("desligar") || dir.equals("none") || dir.equals("nao")) {
            Cosmetic c = plugin.getCosmeticsManager().setCageOrientation(name, null, false);
            if (c == null) {
                playerRef.sendMessage(ChatUtil.error(Tr.t("cage.no_cosmetic", "cage", name)));
                return;
            }
            playerRef.sendMessage(ChatUtil.success(Tr.t("cage.face_off", "cage", name)));
            return;
        }

        // Base calibration: rotate the prefab so the stated front becomes the engine's canonical front
        // (North / -Z), which the middle-chest auto-facing math assumes. Cardinal only — never diagonal.
        Integer rotation = switch (dir) {
            case "north", "norte", "n" -> 0;
            case "east", "leste", "e", "l" -> 270;
            case "south", "sul", "s" -> 180;
            case "west", "oeste", "w", "o" -> 90;
            default -> null;
        };
        if (rotation == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cage.face_usage")));
            return;
        }

        Cosmetic c = plugin.getCosmeticsManager().setCageOrientation(name, rotation, true);
        if (c == null) {
            playerRef.sendMessage(ChatUtil.error(Tr.t("cage.no_cosmetic", "cage", name)));
            return;
        }
        playerRef.sendMessage(ChatUtil.success(Tr.t("cage.face_set", "cage", name, "dir", dir)));
    }
}
