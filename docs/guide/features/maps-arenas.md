# Maps & Arenas

AeroWars separates **maps** from **arenas** — one of the biggest changes in v1.1.0.

- A **map** is a layout: player spawns, chests (island and middle), and the spectator spawn. It's stored per template in `maps/<template>.json`.
- An **arena** is something players join. It references a map and defines the **mode** (Solo or Teams), the **team size**, and optionally a default [cage](/guide/features/cages).

Because arenas only *reference* maps, **several arenas can share one map** — and an arena can even draw from a **random pool** of maps.

::: info Upgrading from 1.0.0
Arenas created before v1.1.0 had their layout stored inline. They are **migrated automatically** on load: the layout is lifted into a map file, and nothing needs to be redone.
:::

## Building a map

1. Create (or pick) a world template — for a blank slate: `/aerowars world create mymap`.
2. Start the wizard: `/aerowars setup start mymap`.
3. Work through the steps:
   - **Player spawns** — one per island. Left-click with the **setup wand** (at a block or in open air) to mark a spawn at your position, or stand in place and run `/aerowars setup set`. Right-click with the wand removes the spawn in front of you.
   - **Chests** — island chests, then middle chests (the tier is set by the step). Place a chest block where each one goes; breaking it removes it again.
   - **Spectator spawn** — where dead players watch from. Stand there and run `/aerowars setup set`.
4. Use `/aerowars setup undo [count]` to remove the last spawn/chest, `/aerowars setup skip` for optional steps, and `/aerowars setup done` to advance.
5. Save with `/aerowars setup save`.

Edit a saved map later with `/aerowars setup edit mymap`.

::: tip The setup wand
Setup worlds are creative, so you can freely edit the terrain. The wand (`/aerowars wand` to get it back) is locked in your hotbar until you finish. It is a weapon-style item, so both clicks register — which is why a replacement set in [`Setup.SpawnWand`](/guide/setup/config#setup) should be weapon-style too.
:::

## Creating arenas

Turn a map into a joinable arena, choosing the mode here:

```
/aerowars arena create sky1 mymap solo
/aerowars arena create duos1 mymap teams 2
```

Manage arenas with:

- `/aerowars arena list` — list every arena.
- `/aerowars arena info <arena>` — see its map, mode, status, and default cage.
- `/aerowars arena enable <arena>` / `disable <arena>` — control rotation.
- `/aerowars arena cage <arena> <cage>` — set the arena's default [cage](/guide/features/cages) (`reset` clears it).

An arena needs a fully configured map (enough spawns for its mode) to be playable. Incomplete or chest-less maps are treated as drafts and hidden from matchmaking.

With `Match.RandomizeSpawns` on (the default), the island each player or team starts on is shuffled every match; turn it off to fill islands in order (1, 2, 3…).

## Random map pool

By default an arena always uses its base map. You can instead give it a **pool** of maps and have each match pick one at random — and the pooled maps can have completely different layouts.

```
/aerowars maps add sky1 desert
/aerowars maps add sky1 volcano
/aerowars maps random sky1 on
```

Now every `sky1` match clones a random map from `{ mymap, desert, volcano }`. Manage the pool with `/aerowars maps list <arena>`, `remove`, and `random <on|off>`.
