# Changelog

All notable changes to AeroWars. The newest release is at the top — update this page whenever you ship a new version. Older lines keep their own documentation — pick one from the version menu.

## Server compatibility

Each release line targets one Hytale line. From 1.2 the jar declares that range in its `manifest.json`, and the server enforces it: outside it the plugin is **refused at load** instead of half-working against an API it wasn't built for.

| AeroWars | Declared range | Loads on |
| --- | --- | --- |
| **1.2.0** (current line) | `{{SERVER_VERSION}}` | `0.6.0` and any later `0.6.x` |
| 1.1.0, 1.0.0 | `*` | **any** server version — no check at all; built for Hytale 0.5 |

::: warning Update the plugin and the server together
Hytale 0.6 changed APIs AeroWars is built on (movement, chunk storage, the prefab-preview packet), so 1.1.0 does not work on a 0.6 server — and because it declares `"*"`, it loads anyway and misbehaves instead of refusing. The jar name says which line a build is for (`AeroWars-{{PLUGIN_VERSION}}-hytale-{{HYTALE_LINE}}.jar`). Still on 0.5? Stay on 1.1.0; its docs are under **v1.1 · Hytale 0.5** in the version menu.
:::

## v1.2.0

Hytale 0.6 support: prefab cages, spectators on Hytale's native spectator mode, every player-facing text translated, and a round of fixes.

### New

- **Fall-damage grace window on the cage drop** (`Cages.FallImmunitySeconds`, default 5). For arenas whose cages sit high above the island, everyone is immune to fall damage for the first few seconds of the match, so the initial drop can't hurt players on landing. Only fall damage is waived; all other combat is normal, and fall damage resumes once the window passes. Set to `0` to disable.
- **Per-arena default cage** — `/aerowars arena cage <arena> <cage>` sets the cage used for players who haven't picked their own cage cosmetic (`reset` clears it back to the global default). `/aerowars arena info` now shows it. Cage precedence when a match starts: the player's own cosmetic > the arena default > the default cage.
- **Cages are now prefabs.** Every cage is a `.prefab.json` in the plugin's `cages/` folder — the format Hytale's in-game prefab tool exports — instead of a hard-coded glass box. Author a cage by capturing it with Hytale's prefab tool and dropping the file in `cages/`; the built-in cages (default glass, colours, rainbow) are generated there on first run. The prefab's anchor is the player's spawn point, so cages of any shape are pasted correctly and the arena is restored exactly when they open. Cage cosmetics and arena defaults reference a cage by name (`prefab:<name>`); existing installs are migrated automatically.
- **Cage admin commands** — `/aerowars cage list` and `/aerowars cage delete <name>` (deletion behind a confirmation modal).
- **Cages tab in the admin panel** (`/aerowars admin`) — lists every cage prefab and deletes it with a confirmation modal. **Preview** opens a dedicated page with a rotatable/zoomable **3D model** of the cage.
- **Kit persistence** (`Kits.PersistLastKit`, default on) — your last-picked kit is remembered across matches; you keep it until you choose another. A remembered kit you no longer have unlocked falls back to the default.
- **Waiting-phase hotbar** (`Kits.WaitingItems`, default on) — while you wait in your cage you get a **Kit Selector** item (opens the kit menu) and a **Return to Lobby** item, locked in place (non-droppable) and removed the moment the match starts, like the spectator hotbar.
- **Cage-facing command** — `/aerowars cage face <cage> <north|east|south|west|off>` points a cage's front (the cardinal direction it faces as captured) and turns on auto-facing, so at match start each spawn's cage rotates to face the **middle chest**. Stored on the cage's entry in `cosmetics.json` (`rotation` + `faceMiddle`); `off` disables auto-facing.
- **Randomized starting islands** (`Match.RandomizeSpawns`, default on) — the island each player/team starts on is shuffled per match instead of filling islands 1, 2, 3… in join order. Toggleable live from the admin panel's Settings tab.
- **Update banner** — the "new version available" notice now prints as a bright boxed banner in the console, and waits for the server to finish booting so it lands at the very end of the startup log.
- **Spectators run on Hytale's native spectator mode** (`Spectator.NativeMode`, default on). Dead players and admin spectators enter `AeroWars_Spectator` — Hytale 0.6's spectator with the hotbar kept, so the tracker and return-to-lobby tools still work. The engine enforces flight, no-clip, invulnerability and intangibility, hides spectators from living players and the player list (spectators still see each other), and puts them on the spectator voice channel. A spectator who disconnects mid-match is let out of the mode on their next login. Set it to `false` for the previous hand-rolled state; AeroWars also falls back to it if the mode can't be entered. See [Spectating](/guide/features/spectating).

_Note: the fixed-size prefab replaces the old cage that auto-grew with team size — author a roomier cage prefab (or a per-arena default) for large teams._

### Changes & fixes

- **Victory fireworks now last the whole end celebration.** The winner's firework show keeps launching every second for the full `Match.EndCelebrationSeconds` window (until the room closes), instead of a single burst — and it follows the window if you change the config.
- **Shorter default end celebration** — `Match.EndCelebrationSeconds` now defaults to **15** (was 30).
- **Death now reliably drops you at the spectator spawn**, including fall/void deaths — a delayed re-assert overrides the engine's own death→respawn that could otherwise land you at the world spawn.
- **Removed the "Clear chests" button from the admin panel's Arenas tab.** Chests are managed through the setup/edit flow; the one-click wipe was too easy to hit by accident.
- **Hotbar tools respond to both clicks.** The kit-selector and spectator tools (tracker, return-to-lobby) now fire on **left- and right-click**, block-aimed or in open air, while keeping their distinct chest/torch/bed models and icons. The setup wand also now shows a proper hotbar icon. Item ids stay configurable in `config.json`.
- **Kit picker reworked, cosmetics-style.** The kit menu now has an ownership **filter** (All / Owned / To Buy), a **per-kit icon**, an at-a-glance status (Free / Owned / price / Permission), and it flags the kit you'll spawn with as **IN USE**. View still opens the full item breakdown.
- **Preview cages from the cosmetics shop.** The Cages tab in `/aerowars cosmetics` gets a **Preview** button on each cage that opens the same rotatable **3D model** viewer as the admin panel.
- **No more logging back in inside a dead match world.** If the server stops or crashes while you're in a match, on connect we detect the stale (deleted) arena world and **reroute you into the lobby**, rewriting the persisted pointer so future logins are clean too.
- **Logging back in after a mid-match disconnect keeps you in the lobby.** Disconnecting from inside a match rewrites your saved world to the lobby, so you don't reconnect into the deleted arena world.
- **Spectators can't touch the match world.** Dead-player and hidden admin spectators can no longer break, place or use blocks (chests, doors) or pick up dropped items.
- **Fixed a crash in the disconnect handler** when a player aborted the login before joining a world; each disconnect cleanup step is now isolated so one failure can't skip the others.
- **Custom cage prefabs paste faithfully.** Cages are now placed through the engine's **native prefab placement** (the same path the 3D preview uses), so block ids, per-block **rotations** and the prefab **anchor** are reproduced exactly — the old paste dropped rotations and, with a `(0,0,0)` anchor, shifted the whole cage by a block. The **anchor cell is kept clear** (it's the spawn), so a prefab that captured a block there no longer spawns you inside it. Cage **orientation lives on the cage's cosmetic** entry in `cosmetics.json`: **`rotation`** (degrees) turns the whole cage, and **`faceMiddle`** rotates each spawn's cage so its front faces the arena centre. The 3D preview reflects the `rotation`.
- **Kit picker empty state is accurate.** "No kits configured" now shows only when there genuinely are no kits; a filter (Owned / To Buy) with nothing to show reads "No other kits available — check back for new ones." Kit icons in the list no longer render stretched.
- **Hytale 0.6.5 compatibility.** Builds against the 0.6.x server API: spectator flight uses the new `FlyMode` movement setting, chest-loot saving marks the chunk itself, and the 3D cage preview sends the packet's new fields.
- **The jar is named after its Hytale line and declares its server range.** Builds are `AeroWars-<version>-hytale-<line>.jar` (e.g. `-hytale-0.6`), and the manifest declares `ServerVersion` `>=0.6.0 <0.7.0`, so a server on another line refuses the jar instead of loading it and breaking mid-match.
- **Mod icon.** The jar ships `icon-256.png`, shown in a world's Mods tab instead of the "?" placeholder.
- **The optional TaleGuard dependency actually matches** — it is now declared as `Stoshe:TaleGuard` (the old `stoshe.taleguard` id never matched anything).
- **Versioned docs.** This site now keeps one version per release line — pick one from the version menu, which names the Hytale line each targets. 1.1 (Hytale 0.5) stays online under **v1.1**.
- **No more hardcoded player-facing text.** Command argument hints, the setup wizard's subcommands and summary, team names (always Portuguese before), the party picker's "(you)", the admin panel's arena line and loot buttons all come from the language files now. Kit item names use the item's own translation, so each player reads them in their client's language. The Portuguese translation says "jaula" / "abates" / "visualizar" where English words were left over.
- **Translated winner announcement** (`Match.AnnounceWinner`, default on). The server-wide "X won the match on Y" line used to be a hardcoded default `Rewards.WinnerCommands` broadcast; it's now translated (`match.win_announce_solo` / `_team`) and sent to everyone not in the match. `WinnerCommands` is empty by default, and the old default line is removed from existing configs automatically.

::: tip Existing servers: refresh your language files
AeroWars exports `lang/en_us.json` and `lang/pt_br.json` once, and your copy overrides the bundled text. New keys appear automatically, but reworded lines only show after you delete (or merge) your exported copy.
:::

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
