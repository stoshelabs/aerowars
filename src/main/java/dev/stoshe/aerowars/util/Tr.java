package dev.stoshe.aerowars.util;

import dev.stoshe.aerowars.AeroWars;
import dev.stoshe.aerowars.manager.TranslationManager;

/**
 * Tiny convenience facade over {@link TranslationManager}. Translations are
 * server-wide; a missing key resolves to the key itself, so callers can
 * reference keys before they exist without crashing.
 */
public final class Tr {

    private Tr() {
    }

    public static String t(String key) {
        if (key == null) {
            return "";
        }
        try {
            TranslationManager tm = AeroWars.getInstance().getTranslationManager();
            return tm != null ? tm.get(key) : key;
        } catch (Exception ex) {
            return key;
        }
    }

    public static String t(String key, Object... args) {
        if (key == null) {
            return "";
        }
        try {
            TranslationManager tm = AeroWars.getInstance().getTranslationManager();
            return tm != null ? tm.get(key, args) : key;
        } catch (Exception ex) {
            return key;
        }
    }
}
