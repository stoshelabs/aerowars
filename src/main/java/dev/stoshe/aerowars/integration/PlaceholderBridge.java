package dev.stoshe.aerowars.integration;

import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.util.Console;

/**
 * Soft hook into the Placeholder plugin. Registration touches {@link AeroWarsPlaceholders} (which extends
 * the placeholder API's class) ONLY inside this try/catch, so when the Placeholder plugin isn't installed
 * the resulting {@link NoClassDefFoundError} is swallowed and AeroWars runs fine without placeholders.
 */
public final class PlaceholderBridge {
    private PlaceholderBridge() {
    }

    public static void register(AeroWars plugin) {
        try {
            boolean ok = new AeroWarsPlaceholders(plugin).register();
            if (ok) {
                Console.success("Registered PlaceholderAPI expansion 'aerowars'.");
            }
        } catch (Throwable t) {
            // Placeholder plugin not present (NoClassDefFoundError) or registration failed — optional dep.
        }
    }
}
