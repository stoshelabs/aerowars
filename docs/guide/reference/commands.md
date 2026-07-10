# Commands

The base command is `/aerowars`, with aliases `/aw`, `/sw`, and `/skywars`. Any alias can replace `aerowars` in every command below.

Arguments in `<angle brackets>` are required; `[square brackets]` are optional.

::: tip Access model
Only two hard permission gates exist: **`aerowars.admin`** for admin commands, and **`aerowars.play`** for joining/queueing (granted by default). Every other command is available to any player. See [Permissions](/guide/reference/permissions).
:::

## Player commands

| Command | Description |
| --- | --- |
| `/aerowars join` | Join a match (requires `aerowars.play`). |
| `/aerowars queue [mode]` | Enter matchmaking. `mode` = `solo` or `teams` (requires `aerowars.play`). |
| `/aerowars leave` | Leave your current match. |
| `/aerowars list` | List arenas. |
| `/aerowars kit [kit]` | Choose a kit by id, or open the kit menu with no argument. |
| `/aerowars stats` | View your own statistics. |
| `/aerowars top [metric]` | Leaderboard. `metric` = `kills` or `wins`. |
| `/aerowars rank [metric]` | Same as `top`, under a second name. |
| `/aerowars cosmetics` | Open the cosmetics shop. Aliases: `cosmetic`, `cosmeticos`. |
| `/aerowars party [action] [player]` | Open the party menu, or run an action (see below). |
| `/aerowars help` | Show help (admins see extra entries). |

### Party actions

`/aerowars party` with no arguments opens the party menu. It also accepts:

| Command | Description |
| --- | --- |
| `/aerowars party invite <player>` | Invite a player to your party. |
| `/aerowars party accept` | Accept a pending invite. |
| `/aerowars party decline` | Decline a pending invite. |
| `/aerowars party kick <player>` | Remove a member (leader only). |
| `/aerowars party leave` | Leave your party. |
| `/aerowars party disband` | Disband the party (leader only). |

See [Parties & Queues](/guide/features/parties-queues) for the full flow.

## Admin commands

All of the following require `aerowars.admin`.

| Command | Description |
| --- | --- |
| `/aerowars start` | Force-start the match you are in. |
| `/aerowars forcestop` | Force-end the match you are in. |
| `/aerowars admin` | Open the admin panel (arenas, loot, matches, kits, settings). |
| `/aerowars reload` | Reload config, language, loot tables, kits, maps and arenas without a restart. |
| `/aerowars setlobby` | Set the AeroWars lobby to your current position. |
| `/aerowars savekit <name>` | Save your current inventory as a kit. |
| `/aerowars wand` | Get the setup wand back into your hotbar. |
| `/aerowars changelog` | Show the latest release notes. |
| `/aerowars firework [show]` | Firework debug; add `show` for the full victory show. Alias: `fw`. |

### Setup wizard — `/aerowars setup …`

Builds and edits **maps** (spawns, chests, cages, spectator spawn). See [Maps & Arenas](/guide/features/maps-arenas).

| Command | Description |
| --- | --- |
| `/aerowars setup start <template>` | Start building a map from a world template. |
| `/aerowars setup edit <template>` | Edit an existing map. |
| `/aerowars setup set` | Set the position for the current step (your location). |
| `/aerowars setup done` | Finish the current step. |
| `/aerowars setup undo [count]` | Undo the last spawn/chest of the step (`count` defaults to 1). |
| `/aerowars setup skip` | Skip an optional step. |
| `/aerowars setup save` | Save the map. |
| `/aerowars setup cancel` | Cancel the current step. |
| `/aerowars setup exit` | Cancel everything and exit setup. |

### Arenas — `/aerowars arena …`

| Command | Description |
| --- | --- |
| `/aerowars arena create <name> <map> <solo\|teams> [teamSize]` | Create an arena over a configured map. |
| `/aerowars arena list` | List all arenas. |
| `/aerowars arena enable <arena>` | Enable an arena in rotation. |
| `/aerowars arena disable <arena>` | Disable an arena. |
| `/aerowars arena info <arena>` | Show an arena's details. |

### Map pool — `/aerowars maps …`

| Command | Description |
| --- | --- |
| `/aerowars maps add <arena> <map>` | Add a map to an arena's random pool. |
| `/aerowars maps remove <arena> <map>` | Remove a map from the pool. |
| `/aerowars maps list <arena>` | List an arena's pool. |
| `/aerowars maps random <arena> <on\|off>` | Toggle random map rotation for an arena. |

### Worlds — `/aerowars world …`

| Command | Description |
| --- | --- |
| `/aerowars world create <name>` | Create a void world with a spawn platform, saved as a reusable template. |
| `/aerowars world list` | List available world templates. |

::: info Tab-completion
Arena names, map names, and fixed choices (`solo`/`teams`, `on`/`off`) all tab-complete on the commands above.
:::
