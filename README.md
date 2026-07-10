<p align="center">
  <img src="https://github.com/stoshelabs/aerowars/blob/main/aerowars.png?raw=true" alt="AeroWars" width="720">
</p>

<p align="center">
  <b>A competitive SkyWars-style minigame for Hytale.</b><br>
  Multi-arena Solo &amp; Teams, spawn cages, chest loot tables, selectable kits, spectator mode, cosmetics, parties &amp; matchmaking, and a fully custom UI/HUD.
</p>

<p align="center">
  <a href="https://stoshelabs.github.io/aerowars/"><img src="https://img.shields.io/badge/docs-online-8A2BE2?style=for-the-badge" alt="Documentation"></a>
  <img src="https://img.shields.io/badge/version-1.1.0-2ea44f?style=for-the-badge" alt="version 1.1.0">
  <img src="https://img.shields.io/badge/modes-Solo%20%7C%20Teams-FF8C00?style=for-the-badge" alt="Solo | Teams">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/PlaceholderAPI-supported-1E90FF?style=flat-square" alt="PlaceholderAPI supported">
  <img src="https://img.shields.io/badge/TaleGuard-supported-1E90FF?style=flat-square" alt="TaleGuard supported">
  <img src="https://img.shields.io/badge/Economy-optional-1E90FF?style=flat-square" alt="Economy optional">
</p>

<p align="center">
  📖 <b><a href="https://stoshelabs.github.io/aerowars/">Read the full documentation →</a></b>
</p>

---

## Overview

AeroWars drops each player (or team) onto a floating island. Grab loot from island and middle chests, gear
up, and be the last one standing. Matches run in isolated, disposable worlds cloned from map templates, so
any number of games can run at once without touching your main world.

- **Solo (FFA)** and **co-op Teams** arenas, with configurable team size
- **Maps are first-class** — build a map once and reuse it across many arenas, optionally with a random map pool
- **Spawn cages** that hold players during the countdown, then shatter open on start
- **Weighted chest loot** (island / middle tiers) with a unified, scheduleable **events** system
- **Selectable kits** — pick before the match, or build them visually in the admin panel
- **Spectator mode**, **parties &amp; matchmaking queues**, and **cosmetics** (cage themes, kill effects, trails)
- **Stats &amp; leaderboards**, **economy rewards**, **PlaceholderAPI**, and **TaleGuard** protection

---

## Quick start

1. Drop `AeroWars-1.1.0.jar` into your server's `mods/` folder.
2. Start the server once to generate the config, language, loot, and kit files.
3. Build your first arena:

```text
/aerowars world create skyisland      # make a void template world and build your map in it
/aerowars setup start skyisland       # run the setup wizard (spawns, chests, spectator)
/aerowars setup save                  # save the map
/aerowars arena create arena1 skyisland solo   # turn the map into a playable arena
/aerowars setlobby                    # set where players return after a match
```

Then `/aerowars join` (or `/sw join`) to play. The **[Getting Started](https://stoshelabs.github.io/aerowars/guide/intro/getting-started)** guide covers this in full.

---

## Documentation

Everything lives on the docs site — commands, config, integrations, and per-version references:

| | |
| --- | --- |
| 🚀 [Getting Started](https://stoshelabs.github.io/aerowars/guide/intro/getting-started) | Install to first playable arena |
| 🏝️ [Maps &amp; Arenas](https://stoshelabs.github.io/aerowars/guide/features/maps-arenas) | Build maps, create arenas, random pools |
| ⚔️ [Match Flow](https://stoshelabs.github.io/aerowars/guide/features/match-flow) · [Kits](https://stoshelabs.github.io/aerowars/guide/features/kits) · [Spectating](https://stoshelabs.github.io/aerowars/guide/features/spectating) · [Parties &amp; Queues](https://stoshelabs.github.io/aerowars/guide/features/parties-queues) · [Cosmetics](https://stoshelabs.github.io/aerowars/guide/features/cosmetics) | Gameplay |
| ⚙️ [Config Reference](https://stoshelabs.github.io/aerowars/guide/setup/config) · [Loot &amp; Events](https://stoshelabs.github.io/aerowars/guide/setup/loot-events) | Configuration |
| 🔌 [Economy](https://stoshelabs.github.io/aerowars/guide/integrations/economy) · [PlaceholderAPI](https://stoshelabs.github.io/aerowars/guide/integrations/placeholders) · [TaleGuard](https://stoshelabs.github.io/aerowars/guide/integrations/taleguard) | Integrations |
| 📖 [Commands](https://stoshelabs.github.io/aerowars/guide/reference/commands) · [Permissions](https://stoshelabs.github.io/aerowars/guide/reference/permissions) · [Placeholders](https://stoshelabs.github.io/aerowars/guide/reference/placeholders) | Reference |
| 📝 [Changelog](https://stoshelabs.github.io/aerowars/guide/changelog) | What changed in each release |

---

## Building from source

```sh
./gradlew jar        # → build/libs/AeroWars-<version>.jar
```

The docs live in [`docs/`](docs) (VitePress) and deploy to GitHub Pages automatically on push to `main`.

---

<sub>Built for Hytale. Optional companions: <a href="https://placeholderapi.com">PlaceholderAPI</a> &amp; <a href="https://github.com/stoshelabs/taleguard">TaleGuard</a>.</sub>
