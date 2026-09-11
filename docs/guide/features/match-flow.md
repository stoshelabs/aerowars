# Match Flow

Every match runs in its own disposable world cloned from the map's template, then goes through a fixed lifecycle. Most timings are configurable in the [`Match`](/guide/setup/config#match) and [`Effects`](/guide/setup/config#effects) config sections.

## Lifecycle

1. **Waiting** — players join (directly, via [queue](/guide/features/parties-queues), or as a [party](/guide/features/parties-queues)) and are placed on an island — shuffled per match when `Match.RandomizeSpawns` is on. The match waits until `Match.MinPlayers` are present.
2. **Countdown** — a `Match.CountdownSeconds` timer runs. Players sit in [spawn cages](#spawn-cages) with a locked hotbar (kit selector + return to lobby) and can pick a [kit](/guide/features/kits). The countdown **shortens to 5 seconds when the arena fills up**.
3. **Start grace** — a brief `Match.StartGraceSeconds` delay as cages open and the world settles.
4. **Active** — combat is live. Loot your island and the middle, then fight. The match caps at `Match.MaxDurationSeconds`.
5. **Ending** — once resolved, a `Match.EndCelebrationSeconds` celebration plays: the [victory podium](#victory) rises and fireworks keep launching for the whole window.
6. **Cleanup** — the world is torn down and players return to the lobby.

## Spawn cages

While the countdown runs, each player (or team) is enclosed in a cage so nobody can move or fight early. On start the cages **drop away** — and for arenas whose cages sit high above the island, everyone is immune to fall damage for the first `Cages.FallImmunitySeconds` (default 5), so the drop can't hurt anyone.

Each cage is a **prefab** from the `cages/` folder, placed with its anchor on the spawn. Which one a player gets: their own cage cosmetic, else the arena default, else `default`. A prefab is a fixed size — it does not grow with team size. See [Cages](/guide/features/cages) for authoring, orientation, and the per-arena default.

## The void

Falling below the arena is fatal. When a player drops into the void they're eliminated — including the very last player, who is routed cleanly into spectator mode rather than falling forever.

## Winning, draws & time-up

- **Last team standing** wins.
- If the clock runs out with exactly one team alive, that team wins; otherwise the match ends as a **draw / time-up** with no winner.
- Winners are made invulnerable during the celebration so nothing can spoil the podium.

## Victory

On a win, AeroWars can:

- Raise the winner(s) onto a **podium** above the arena center (`Effects.PodiumEnabled`).
- Play a **firework show** from the arena center (`Effects.VictoryFireworks`) that keeps launching for the whole `Match.EndCelebrationSeconds`, with configurable burst count, height, scale, and particle set — or the winner's [victory cosmetic](/guide/features/cosmetics).
- Run any `Rewards.WinnerCommands` and pay out [economy rewards](/guide/integrations/economy).

## Death & spectating

When a player dies (and `Match.SpectateOnDeath` is on) they become a spectator on Hytale's native spectator mode — flying through blocks, invulnerable, and invisible to the living. See [Spectating](/guide/features/spectating).
