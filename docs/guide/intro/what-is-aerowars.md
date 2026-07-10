# What is AeroWars?

**AeroWars** is a competitive SkyWars-style minigame plugin for Hytale servers. It drops each player — or team — onto a floating island. You loot the chests on your island and in the center, gear up, and fight to be the last one standing.

Every match runs in its own **isolated, disposable world** cloned from a template, so any number of games can run at the same time without ever touching your main world.

## Highlights

- **Solo (FFA) and co-op Teams** arenas, with a configurable team size.
- **Maps are first-class** — build a map's layout once and reuse it across many arenas, optionally with a random map pool per arena.
- **Spawn cages** hold players on their island during the countdown, then shatter open on start.
- **Weighted chest loot** with island / middle tiers, plus a schedulable in-match **events** system (refills and gradual loot upgrades).
- **Selectable kits** — free, permission-locked, or purchasable — chosen from an in-game picker or built visually in the admin panel.
- **Spectator mode** on death: fly around invulnerable and invisible, teleport to living players, or return to lobby.
- **Parties & matchmaking queues** for Solo and Teams, with keep-together or auto-split behavior.
- **Cosmetics**: cage themes, kill effects, victory shows, and movement trails.
- **Stats & leaderboards** with optional SQL persistence, plus **economy rewards**, a **PlaceholderAPI** expansion, and a **TaleGuard** protection hook.
- A fully **custom scoreboard/HUD**, an in-game **admin panel** and setup wizard, an update checker, and **English + Portuguese** localization.

## How a match works

1. Players join an arena (directly, through a queue, or as a party).
2. During the **countdown** everyone waits in a spawn cage; kits can be picked.
3. On start the cages shatter and combat opens. Grab loot, cross to the middle, and fight.
4. Falling into the **void** eliminates you — on death you become a spectator.
5. When one team (or player) remains, or time runs out, the match resolves with a **victory podium and firework show**, then the world is cleaned up.

Ready to set it up? Head to [Getting Started](/guide/intro/getting-started).
