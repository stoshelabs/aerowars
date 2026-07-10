# Getting Started

This guide takes you from a fresh install to a playable arena.

## 1. Install

1. Drop `AeroWars-<version>.jar` into your server's plugins/mods folder.
2. Start the server once so AeroWars generates its data folder (config, language, loot, kits).
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

A **map** is the layout: player spawns, chests, cages, and the spectator spawn. Start the setup wizard on your template:

```
/aerowars setup start mymap
```

Follow the on-screen steps — use the **setup wand** (left-click a block) or `/aerowars setup set` to mark positions, `/aerowars setup done` to advance, and `/aerowars setup undo` to fix mistakes. When you're finished:

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

Several arenas can share the same map, and you can give an arena a [random map pool](/guide/features/maps-arenas#random-map-pool).

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
`/aerowars admin` opens a panel to manage arenas, loot tables, live matches, kits, and settings without touching config files. Use `/aerowars reload` to apply file edits live.
:::
