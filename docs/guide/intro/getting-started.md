# Getting Started

This guide takes you from a fresh install to a playable arena.

## Requirements

| | |
| --- | --- |
| **Hytale server** | `{{HYTALE_LINE}}.0` or any later `{{HYTALE_LINE}}.x` — the manifest declares `{{SERVER_VERSION}}` |
| **Java** | 25 (the same runtime the server ships with) |

The range is enforced by the server itself: on a version outside it AeroWars is **refused at load** with an incompatibility message rather than half-working against an API it wasn't built for.

::: tip Still on a 0.5.x server?
Use **AeroWars 1.1.0** — its documentation stays online under **v1.1 · Hytale 0.5** in the version menu. Update the plugin and the server together: 1.1.0 is not built for 0.6.
:::

## 1. Install

1. Drop `AeroWars-{{PLUGIN_VERSION}}-hytale-{{HYTALE_LINE}}.jar` into your server's `mods/` folder. The `hytale-{{HYTALE_LINE}}` part is the game line the jar is built for — it runs on any `{{HYTALE_LINE}}.x` server the manifest range allows.
2. Start the server once so AeroWars generates its data folder (config, language, loot, kits, cages).
3. Stop the server, review [`config.json`](/guide/setup/config) if you like, and start it again.

::: warning Never hot-swap the jar
Always **stop → replace the jar → start**. Swapping the jar under a running server leaves the old classes loaded and causes errors.
:::

Optional companions — all soft dependencies, none required:

- [Economy](/guide/integrations/economy) — pay coins for kills/wins and sell kits/cosmetics.
- [PlaceholderAPI](/guide/integrations/placeholders) — expose AeroWars data to holograms and scoreboards.
- [TaleGuard](/guide/integrations/taleguard) — protection enforcement in AeroWars worlds.

## 2. Create a world template

Match worlds are cloned from a template. Create a simple void template with a spawn platform to build on:

```
/aerowars world create mymap
```

You'll be dropped onto the platform in creative. This world is saved as a reusable template.

## 3. Build a map

A **map** is the layout: player spawns, chests, and the spectator spawn. Start the setup wizard on your template:

```
/aerowars setup start mymap
```

Follow the on-screen steps — mark spawns with the **setup wand** (left-click) or `/aerowars setup set`, place chest blocks for the chest steps, `/aerowars setup done` to advance, and `/aerowars setup undo` to fix mistakes. When you're finished:

```
/aerowars setup save
```

See [Maps & Arenas](/guide/features/maps-arenas) for the full walkthrough.

## 4. Create an arena

An **arena** turns a map into something players can join, choosing the mode and team size here:

```
/aerowars arena create sky1 mymap solo
/aerowars arena create duos1 mymap teams 2
```

Several arenas can share the same map, and you can give an arena a [random map pool](/guide/features/maps-arenas#random-map-pool) and a default [cage](/guide/features/cages).

## 5. Set the lobby

Stand where players should return after a match and run:

```
/aerowars setlobby
```

## 6. Play

```
/aerowars join      # join an arena
/aerowars queue     # or use matchmaking
```

Once at least `Match.MinPlayers` have joined, the countdown begins. An admin can force it with `/aerowars start`.

::: tip Admin panel
`/aerowars admin` opens a panel to manage arenas, loot tables, live matches, kits, cages, and settings without touching config files. Use `/aerowars reload` to apply file edits live.
:::
