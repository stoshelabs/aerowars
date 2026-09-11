package dev.stoshe.aerowars.command.cage;

import dev.stoshe.aerowars.util.Tr;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.stoshe.aerowars.AeroWars;

import javax.annotation.Nonnull;

/**
 * {@code /aerowars cage ...} — manages the cage prefabs in the {@code cages/} folder. New cages are
 * authored with Hytale's native prefab tool; this command lists them and deletes them (with a
 * confirmation modal). The 3D preview lives in the admin panel (later phase).
 */
public class CageCommand extends AbstractCommandCollection {

    public CageCommand(@Nonnull AeroWars plugin) {
        super("cage", Tr.t("command.cage"));
        addSubCommand(new CageListCommand(plugin));
        addSubCommand(new CageFaceCommand(plugin));
        addSubCommand(new CageDeleteCommand(plugin));
    }
}
