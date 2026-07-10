# Changelog

All notable changes to AeroWars. The newest release is at the top — update this page whenever you ship a new version.

## v1.1.0

A big architecture update (maps are now first-class), a batch of correctness fixes, and new admin quality-of-life commands.

### ⭐ Maps are now separate from arenas

- Map layout (spawns, chests, spawn cages, spectator spawn) is stored **per map** in `maps/<template>.json`, not on the arena — so **several arenas can share the same map**.
- New map-first setup flow:
  - `/aerowars setup start <template>` — build the **map**.
  - `/aerowars arena create <name> <map> <solo|teams> [teamSize]` — create an arena over a configured map.
- **Random map pool** per arena, opt-in (off by default): `/aerowars maps <arena> add <map>` then `/aerowars maps <arena> random on`. Each match then clones a random map from the pool.
- `setup mode` was removed — mode/team size is chosen at `arena create`.
- Existing arenas are **migrated automatically** on load (their inline layout is lifted into a map file).

### New commands

- `/aerowars reload` — reload config, language, loot, kits, maps and arenas without a restart.
- `/aerowars forcestop` — admin abort of the match you're in (the counterpart to `/aerowars start`).
- `/aerowars arena create|enable|disable|info` — arena administration.
- `/aerowars maps add|remove|list|random` — manage an arena's map pool.
- Full **tab-completion** across the new commands (arena names, maps, and fixed choices).

### Fixes

- **Stats no longer lost on a crash** — periodic auto-save plus a flush at match end.
- **Default kits** now use valid item IDs, and kit armor is actually **worn** instead of dropped into the backpack.
- **Editing config in-game no longer wipes the comments** in `config.json`.
- **Friendly-fire / self-hit** checks now compare players correctly.
- **Thread-safety** — match rosters and joins are safe against the match tick; simultaneous joins can't overfill an arena.
- **Paid kits/cosmetics** fall back to free when a deposit-only economy provider can't charge.
- A **teams match can no longer start with a single team**.
- **Games-played** is counted at match start, not on join.
- Removed a stray per-projectile log, a raw stack trace, and a possible void-mid-teleport crash.

### Changed / removed

- The unused HUD toggle and dead `MatchHud`/`StatusHud` were removed (the scoreboard and titles cover everything). The `Hud` config block is gone.
- `Effects` (fireworks/podium) is now documented in `config.json`.
- The countdown **shortens to 5s when an arena fills** (early start).
- `aerowars.play` permission is now enforced on join/queue.

### Added

- **Changelog popup** (admin): on join after an update, admins see a modal with the latest release notes. Re-open any time with `/aerowars changelog`.

## v1.0.0

**Initial public release** — a competitive SkyWars-style minigame for Hytale, featuring Solo and Teams arenas with spawn cages, chest loot, selectable kits, spectator mode, parties, cosmetics, and a fully custom UI.

### Gameplay

- Solo (FFA) and co-op Teams arenas, with configurable team size.
- Isolated, disposable match worlds cloned from arena templates, so many games run at once.
- Spawn cages hold players during the countdown, then shatter open on start.
- Configurable countdown, match duration, end-celebration, and min players.
- Win / draw / time-up resolution, with a victory podium and firework show.

### Arenas, loot, and kits

- In-game setup wizard, weighted chest loot (normal/middle tiers), and schedulable in-match events (refill, gradual loot upgrade).
- Selectable kits with an in-game picker and a visual admin kit builder; free, permission-locked, and purchasable kits.

### Spectator, parties, cosmetics

- Dead players become spectators (flight, invulnerable, invisible) with tracker/lobby hotbar tools and full inventory save/restore.
- Party system (invite/accept/kick/promote/disband) with a management menu, plus Solo/Teams matchmaking queues.
- Cage themes, kill effects, victory shows, and movement trails — free, permission-locked, or purchasable.

### Stats, economy, integrations, interface

- Stats and leaderboards with optional SQL persistence (SQLite / MySQL / MariaDB / Postgres).
- Optional economy rewards, a PlaceholderAPI expansion, and a TaleGuard protection hook.
- Custom scoreboard, admin panel, update checker, and English + Portuguese localization.
