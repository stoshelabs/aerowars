# Config Reference

AeroWars is configured through `config.json` in its data folder. Edit it and run `/aerowars reload` to apply changes live — no restart needed. Color codes use `{#RRGGBB}` tags (and legacy `&` codes where noted). Editing the file in-game keeps its comments intact.

## General

| Key | Default | Description |
| --- | --- | --- |
| `General.Language` | `"en_us"` | Language file loaded from the `lang/` folder (`en_us` / `pt_br`). |
| `General.AutoSaveIntervalSeconds` | `300` | How often stats/data are auto-saved. |
| `General.LobbyWorld` | `"world"` | World players return to after a match. |
| `General.LobbySpawn` | `""` | Optional `x,y,z,yaw,pitch` lobby spawn; empty = world default. Set easily with `/aerowars setlobby`. |
| `General.PrefixEnabled` | `true` | Whether the chat prefix is prepended to plugin messages. |
| `General.Prefix` | `"{#55ccff}[AeroWars] {#ffffff}"` | The chat/branding prefix. |

## Match

| Key | Default | Description |
| --- | --- | --- |
| `Match.MinPlayers` | `2` | Minimum players to start (per-arena min/max derive from spawn count). |
| `Match.MaxDurationSeconds` | `900` | Max match length before time-up resolution. |
| `Match.CountdownSeconds` | `15` | Pre-start countdown (shortens to 5s when the arena fills). |
| `Match.StartGraceSeconds` | `3` | Grace delay at start before cages open / combat. |
| `Match.EndCelebrationSeconds` | `30` | Length of the end/victory celebration. |
| `Match.EmptyMatchDeletionSeconds` | `30` | Delay before an emptied match/world is torn down. |
| `Match.FriendlyFire` | `false` | Allow team damage (Teams arenas only). |
| `Match.AllowSpectators` | `true` | Whether spectators are permitted. |
| `Match.SpectateOnDeath` | `true` | Dead players become spectators instead of leaving. |
| `Match.SaveInventory` | `true` | Save inventory + game mode on join, restore on leave; players enter in Adventure. |

## Cages

| Key | Default | Description |
| --- | --- | --- |
| `Cages.Enabled` | `true` | Build countdown holding cages around each spawn. |
| `Cages.BlockId` | `"Glass_Block"` | Cage block. Ships colored variants (`Glass_Block_Red`, `_Blue`, `_Green`, `_Cyan`, `_Yellow`, `_Lime`, `_Orange`, `_Pink`, `_Purple`, `_Magenta`, `_White`, `_Black`). |
| `Cages.Radius` | `1` | Cage radius in blocks. |
| `Cages.Height` | `2` | Cage height in blocks. |

## Loot

The loot events system has its own page — see [Loot & Events](/guide/setup/loot-events).

| Key | Default | Description |
| --- | --- | --- |
| `Loot.FillOnStart` | `true` | Fill every chest once when the match starts. |
| `Loot.Events.Enabled` | `true` | Master toggle for in-match chest events. |
| `Loot.Events.Randomize` | `false` | Fire enabled events at random times/order instead of their fixed `Time`. |
| `Loot.Events.MinGapSeconds` | `90` | Minimum spacing (and earliest fire time) for randomized events. |
| `Loot.Events.List[]` | see page | Ordered events, each with `Type`, `Time`, `Enabled`. |

## Kits

| Key | Default | Description |
| --- | --- | --- |
| `Kits.Enabled` | `true` | Enable the kit selection system. |
| `Kits.DefaultKit` | `"warrior"` | Kit given when a player hasn't chosen one. |
| `Kits.SelectionDuringCountdown` | `true` | Allow opening the kit menu during the countdown. |

## Rewards

| Key | Default | Description |
| --- | --- | --- |
| `Rewards.WinnerCommands[]` | one broadcast line | Console commands run on win; `{player}` and `{arena}` placeholders. |
| `Rewards.Economy.Enabled` | `false` | Pay coins on kill/win via an installed economy plugin. |
| `Rewards.Economy.WinAmount` | `100` | Coins paid to each winner. |
| `Rewards.Economy.KillAmount` | `10` | Coins paid per kill. |

See [Economy](/guide/integrations/economy) for provider details.

## Scoreboard

| Key | Default | Description |
| --- | --- | --- |
| `Scoreboard.Enabled` | `true` | Show the custom scoreboard. |
| `Scoreboard.Footer` | `"{#55ccff}stoshe.dev"` | Bottom footer line; supports `{#RRGGBB}` and `&` codes; blank hides it. |

## Effects

| Key | Default | Description |
| --- | --- | --- |
| `Effects.VictoryFireworks` | `true` | Launch a firework show from the arena center on victory. |
| `Effects.FireworkParticles[]` | 9 native particle ids | Firework particle systems; one is picked at random per burst. |
| `Effects.FireworkBursts` | `14` | Number of bursts in the show. |
| `Effects.FireworkDurationSeconds` | `6` | Total show duration. |
| `Effects.FireworkMaxRise` | `24` | Max height above the arena the bursts pop. |
| `Effects.FireworkScale` | `1.5` | Firework particle scale. |
| `Effects.PodiumEnabled` | `true` | Raise winner(s) onto a podium during the celebration. |
| `Effects.PodiumBlock` | `"Glass_Block_Yellow"` | Block the podium is built from. |
| `Effects.PodiumHeight` | `8` | Podium height above the arena center. |

## Party

| Key | Default | Description |
| --- | --- | --- |
| `Party.Enabled` | `true` | Enable the party system. |
| `Party.MaxSize` | `4` | Max members including the leader. |
| `Party.InviteExpirySeconds` | `60` | How long a pending invite stays valid. |
| `Party.KeepTogetherByDefault` | `false` | Default of the keep-together preference (see [Parties & Queues](/guide/features/parties-queues)). |

## Spectator

| Key | Default | Description |
| --- | --- | --- |
| `Spectator.Enabled` | `true` | Give the spectator hotbar on death. |
| `Spectator.TrackerSlot` | `0` | Hotbar slot of the living-player tracker. |
| `Spectator.TrackerItem` | `"AeroWars_SpecTracker"` | Item id of the tracker. |
| `Spectator.LobbySlot` | `8` | Hotbar slot of the return-to-lobby item. |
| `Spectator.LobbyItem` | `"AeroWars_SpecLobby"` | Item id of the return-to-lobby tool. |

## Platform

Used when `/aerowars world create` builds a fresh void template.

| Key | Default | Description |
| --- | --- | --- |
| `Platform.Block` | `"Rock_Stone"` | Block the platform is paved with. |
| `Platform.Size` | `9` | Square side length; clamped odd ≥1 (keep ≤15). |
| `Platform.Height` | `100` | Y level of the platform. |

## Setup

| Key | Default | Description |
| --- | --- | --- |
| `Setup.SpawnWand` | `"AeroWars_SetupWand"` | Item id of the setup wand (a breaking tool; left-click marks spawns). |

## Database

Optional SQL persistence for stats and per-player data. Falls back to JSON when disabled.

| Key | Default | Description |
| --- | --- | --- |
| `Database.Enabled` | `false` | Enable SQL persistence (else `stats.json`). |
| `Database.JdbcUrl` | `"jdbc:sqlite:aerowars.db"` | JDBC URL; the driver (sqlite/mysql/mariadb/postgres) is chosen from it. |
| `Database.Username` | `""` | DB username. |
| `Database.Password` | `""` | DB password. |
| `Database.MaxPoolSize` | `10` | Connection pool size. |

::: warning Drivers not bundled
JDBC drivers and the connection pool are **not** shipped with AeroWars. For anything other than the bundled-in-runtime case, make sure the driver is on your server's classpath.
:::
