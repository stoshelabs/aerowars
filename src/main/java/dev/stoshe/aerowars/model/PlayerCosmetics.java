package dev.stoshe.aerowars.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Per-player cosmetics state: which cosmetics they own (bought) and which is selected per category. */
public class PlayerCosmetics {
    /** Ids of cosmetics the player has purchased (permission/free ones are not stored here). */
    public Set<String> owned = new HashSet<>();
    /** category id -> selected cosmetic id. */
    public Map<String, String> selected = new HashMap<>();
}
