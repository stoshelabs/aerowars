# Match Flow

Every match runs in its own disposable world cloned from the map's template, then goes through a fixed lifecycle. Most timings are configurable in the [`Match`](/guide/setup/config#match) and [`Effects`](/guide/setup/config#effects) config sections.

## Lifecycle

1. **Waiting** — players join (directly, via [queue](/guide/features/parties-queues), or as a [party](/guide/features/parties-queues)). The match waits until `Match.MinPlayers` are present.
2. **Countdown** — a `Match.CountdownSeconds` timer runs. Players sit in [spawn cages](#spawn-cages) and can pick a [kit](/guide/features/kits). The countdown **shortens to 5 seconds when the arena fills up**.
3. **Start grace** — a brief `Match.StartGraceSeconds` delay as cages open and the world settles.
4. **Active** — combat is live. Loot your island and the middle, then fight. The match caps at `Match.MaxDurationSeconds`.
5. **Ending** — once resolved, a `Match.EndCelebrationSeconds` celebration plays: the [victory podium](#victory) rises and fireworks fire.
6. **Cleanup** — the world is torn down and players return to the lobby.

## Spawn cages

While the countdown runs, each player (or team) is enclosed in a cage so nobody can move or fight early. On start the cages **shatter open**. Cages are configurable in the [`Cages`](/guide/setup/config#cages) section — block type (with 12 colored glass variants), radius, and height — and they scale up for larger teams.

## The void

Falling below the arena is fatal. When a player drops into the void they're eliminated — including the very last player, who is routed cleanly into spectator mode rather than falling forever.

## Winning, draws & time-up

- **Last team standing** wins.
- If the clock runs out with exactly one team alive, that team wins; otherwise the match ends as a **draw / time-up** with no winner.
- Winners are made briefly invulnerable during the celebration so nothing can spoil the podium.

## Victory

On a win, AeroWars can:

- Raise the winner(s) onto a **podium** above the arena center (`Effects.PodiumEnabled`).
- Play a **firework show** from the arena center (`Effects.VictoryFireworks`), with configurable burst count, height, scale, and particle set.
- Run any `Rewards.WinnerCommands` and pay out [economy rewards](/guide/integrations/economy).

## Death & spectating

When a player dies (and `Match.SpectateOnDeath` is on) they become a spectator — flying, invulnerable, and invisible to the living. See [Spectating](/guide/features/spectating).
