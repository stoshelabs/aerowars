# Config Reference

AeroWars is configured through `config.json` in its data folder. Edit it and run `/aerowars reload` to apply changes live — no restart needed. Color codes use `{#RRGGBB}` tags (and legacy `&` codes where noted). Editing the file in-game keeps its comments intact.

## General

| Key | Default | Description |
| --- | --- | --- |
| `General.Language` | `"en_us"` | Language file loaded from the `lang/` folder (`en_us` / `pt_br`). Every message, menu, title and command hint comes from it. Your exported copy overrides the bundled text; keys it lacks fall back to the bundled file, so after an update delete (or merge) it to pick up reworded lines. |
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
| `Match.EndCelebrationSeconds` | `15` | Length of the end/victory celebration. Victory fireworks keep launching for this whole window. |
| `Match.EmptyMatchDeletionSeconds` | `30` | Delay before an emptied match/world is torn down. |
| `Match.FriendlyFire` | `false` | Allow team damage (Teams arenas only). |
| `Match.AllowSpectators` | `true` | Whether spectators are permitted. |
| `Match.SpectateOnDeath` | `true` | Dead players become spectators instead of leaving. |
| `Match.SaveInventory` | `true` | Save inventory + game mode on join, restore on leave; players enter in Adventure. |
| `Match.RandomizeSpawns` | `true` | Shuffle which island (spawn point) each player/team starts on per match. When off, islands fill in order (1, 2, 3…). |
| `Match.AnnounceWinner` | `true` | Tell everyone on the server who isn't in the match who won it and on which arena. The text is `match.win_announce_solo` / `match.win_announce_team` in the language files, so it follows `General.Language`. |

## Cages

Cages are now **prefabs** (`.prefab.json` files in the `cages/` folder), not a hard-coded box — see [Cages](/guide/features/cages).

| Key | Default | Description |
| --- | --- | --- |
| `Cages.Enabled` | `true` | Build countdown holding cages around each spawn. |
| `Cages.FallImmunitySeconds` | `5` | Seconds of fall-damage immunity for everyone from match start (when the cages drop), so a high cage drop can't hurt players on landing. Only fall damage is waived; `0` disables it. |
| Cage orientation | — | A cage's **rotation** (degrees) and **`faceMiddle`** flag now live on its **cosmetic** entry in `cosmetics.json`, not here — so a cage's whole definition is in one place. See [Cages → Orienting a cage](../features/cages#orienting-a-cage). |

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
| `Kits.PersistLastKit` | `true` | Remember each player's last-selected kit across matches (they keep it until they pick another). Off = every match starts on `DefaultKit` until selected. |
| `Kits.WaitingItems` | `true` | Give waiting players (in their cage) a locked hotbar: a kit-selector item (opens the kit menu) and a return-to-lobby item. Non-droppable; removed when the match starts. |
| `Kits.SelectorItem` | `"AeroWars_KitSelector"` | Item id of the waiting-phase kit-selector tool. |
| `Kits.SelectorSlot` | `0` | Hotbar slot of the kit-selector item. |
| `Kits.LobbyItem` | `"AeroWars_SpecLobby"` | Item id of the waiting-phase return-to-lobby tool. |
| `Kits.LobbySlot` | `8` | Hotbar slot of the return-to-lobby item. |

## Rewards

| Key | Default | Description |
| --- | --- | --- |
| `Rewards.WinnerCommands[]` | `[]` | Extra commands run on a solo win; `{player}` and `{arena}` placeholders, supports `broadcast <message>`. Empty by default — the winner announcement itself is `Match.AnnounceWinner`, which is translated. Older configs' default broadcast line is removed automatically. |
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
| `Spectator.NativeMode` | `true` | Run spectators on Hytale's native spectator mode (flight, no-clip, invulnerable, hidden from living players). `false` uses AeroWars' own flight + invulnerability + hiding. See [Spectating](/guide/features/spectating). |
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
| `Setup.SpawnWand` | `"AeroWars_SetupWand"` | Item id of the setup wand (a weapon-style item so both clicks register: left-click marks the spawn at your position, right-click removes the spawn in front of you). |

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
