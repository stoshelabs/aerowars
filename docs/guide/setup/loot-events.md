# Loot & Events

Loot is what makes each match — the chests you place during [map setup](/guide/features/maps-arenas) are filled from **weighted loot tables**, and **in-match events** keep the loot flowing.

## Loot tables

Loot lives in `loot.json` in the data folder. There are two tiers:

- **Normal (island)** — the chests on each player's starting island.
- **Middle** — the richer chests in the center of the arena.

Each entry has an item id, a min/max quantity, and a weight. You can edit the tables by hand (then `/aerowars reload`) or from the admin panel: `/aerowars admin` → **Loot**, which lets you switch tier, add items (`itemId [min] [max] [weight]`), and remove them.

When `Loot.FillOnStart` is on, every chest is filled once at match start.

## In-match events

Events refresh or upgrade loot partway through a match, driving players back toward the chests. Configure them under `Loot.Events`:

```json
"Loot": {
  "Events": {
    "Enabled": true,
    "Randomize": false,
    "MinGapSeconds": 90,
    "List": [
      { "Type": "loot_upgrade", "Time": 300, "Enabled": true },
      { "Type": "refill",       "Time": 480, "Enabled": true },
      { "Type": "loot_upgrade", "Time": 660, "Enabled": true }
    ]
  }
}
```

### Event types

| Type | Effect |
| --- | --- |
| `refill` | Re-rolls the loot in **every** chest. |
| `loot_upgrade` | Upgrades a **growing share** of the normal chests to roll the **middle** table, then refills. Each successive upgrade event promotes more of them. |

### Fixed vs. randomized timing

- **Fixed** (`Randomize: false`) — each event fires at its `Time` (seconds since match start).
- **Randomized** (`Randomize: true`) — the `Time` values are ignored; enabled events fire at random moments in a random order, always at least `MinGapSeconds` apart (and no earlier than `MinGapSeconds` into the match).

Set an individual event's `"Enabled": false` to keep it in the list but skip it.

::: tip Scoreboard countdown
The custom scoreboard shows a live countdown to the next event and its name, so players know when the next refill or upgrade is coming.
:::
