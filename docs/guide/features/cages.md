# Cages

The **cage** is the glass box that holds each player on their island during the pre-match countdown; when the match starts, every cage drops away at once. In AeroWars a cage is a **prefab** — a `.prefab.json` file in the plugin's `cages/` folder — so you can design any shape you like instead of a plain box.

::: tip Fall-immunity on the drop
For arenas whose cages sit high above the island, everyone is immune to **fall damage** for the first few seconds after the cages drop (`Cages.FallImmunitySeconds`, default 5), so the initial drop can't hurt players on landing. Only fall damage is waived — all other combat is normal — and it resumes once the window passes. Set it to `0` to disable.
:::

## Where cages live

```
plugins/AeroWars/cages/
  default.prefab.json    ← the fallback cage (always keep one named "default")
  gold.prefab.json
  red.prefab.json
  rainbow.prefab.json
  ...
```

The built-in cages (default glass, colours, rainbow) are generated automatically the first time the plugin runs, so everything works out of the box. Delete the folder to regenerate them.

## Authoring a new cage

Cages use Hytale's **native prefab format**, so you build one with the game's own prefab tool:

1. Build the cage in the world (any blocks; glass is typical).
2. Capture it with Hytale's in-game prefab tool. Put the **anchor on the block a player should stand on** — that anchor is aligned to the player's spawn when the cage is pasted, so the player ends up inside.
3. Drop the exported `.prefab.json` into `plugins/AeroWars/cages/`.
4. Run `/aerowars reload` (or restart). The cage is now available.

::: tip Anchor = spawn
When pasted, a block at prefab-local `(x,y,z)` lands at `spawn + ((x,y,z) − anchor)`. If a prefab has no anchor, the bounding-box min corner is used instead. Make sure the interior encloses the anchor so players can't escape early.
:::

::: warning Fixed size
A prefab is a fixed size — it does **not** grow with team size. For large teams, author a roomier cage and set it as the arena default.
:::

## Orienting a cage

Cages are placed through the engine's **native prefab placement**, so each block's captured **rotation** is reproduced exactly (directional blocks like stairs keep their facing) and the prefab **anchor** lands on the player's spawn. The anchor cell itself is kept **clear** — the anchor is the spawn point, so a prefab that captured a block there won't spawn the player inside it.

Orientation is configured **per cage on its cosmetic entry** in `cosmetics.json` (not `config.json`), so a cage's whole definition stays in one place. Two fields on a `"category": "cage"` entry:

- **`rotation`** — degrees (`0` / `90` / `180` / `270`). Rotates the whole cage clockwise (positions **and** each block's facing). Use it to point a sideways prefab the right way, or to calibrate which way the cage's front points at `0°`.
- **`faceMiddle`** — when `true`, the cage is additionally rotated so its **front faces the arena centre** (the middle-chests, else the average spawn), using `rotation` as the front calibration. Handy when spawns ring the island and every cage should face inward.

```json
{
  "id": "cage_testprefab",
  "category": "cage",
  "value": "prefab:testprefab",
  "rotation": 90,
  "faceMiddle": true
}
```

The [3D cage preview](#managing-cages) (admin panel and cosmetics shop) reflects the `rotation`, so you can eyeball the facing before a match. (The `faceMiddle` turn depends on the spawn, so it's applied in-match, not in the preview.)

## Managing cages

- `/aerowars cage list` — list the cage prefabs in the folder.
- `/aerowars cage delete <name>` — delete a cage (behind a confirmation prompt).
- **Admin panel** (`/aerowars admin` → **Cages** tab): list every cage, **Preview** a floating 3D model of it, and **Delete** it with a confirmation modal.

## Which cage a player gets

Cage selection follows this precedence when a match starts:

1. The player's own **cage cosmetic** (`/aerowars cosmetics` → Cage themes) — always wins.
2. The **arena default**, set with `/aerowars arena cage <arena> <cage>` (`reset` clears it).
3. The **`default`** cage prefab.

Cage cosmetics reference a prefab by name (`prefab:<name>`); the shipped ones map to the generated built-ins. Add your own cosmetic entries pointing at your custom cages to sell or gate them like any other [cosmetic](/guide/features/cosmetics).
