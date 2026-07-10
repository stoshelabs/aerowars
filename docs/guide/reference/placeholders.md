# Placeholders

When [PlaceholderAPI](/guide/integrations/placeholders) is installed, AeroWars registers the `aerowars` expansion. Every placeholder uses the form `%aerowars_<name>%`.

## Global

| Placeholder | Value |
| --- | --- |
| `%aerowars_arenas%` | Number of playable arenas. |
| `%aerowars_arenas_total%` | Number of all arenas. |
| `%aerowars_matches%` | Live match count. |
| `%aerowars_matches_solo%` | Live Solo matches. |
| `%aerowars_matches_teams%` | Live Teams matches. |
| `%aerowars_players%` | Players currently in games. |
| `%aerowars_queue%` | Players queued (all modes). |
| `%aerowars_queue_solo%` | Players in the Solo queue. |
| `%aerowars_queue_teams%` | Players in the Teams queue. |

## Player — current match

| Placeholder | Value |
| --- | --- |
| `%aerowars_player_arena%` | Arena name of the player's match, else `-`. Alias: `arena_name`. |
| `%aerowars_player_status%` | `none`, `waiting`, `playing`, or `spectating`. |
| `%aerowars_player_mode%` | `solo`, `teams`, or `-`. Alias: `arena_type`. |
| `%aerowars_player_kills%` | Kills in the current match. |
| `%aerowars_player_alive%` | Players still alive in the current match. |

## Player — state (true / false)

| Placeholder | True when… |
| --- | --- |
| `%aerowars_in_arena%` | The player is in a match. |
| `%aerowars_in_queue%` | The player is queued. |
| `%aerowars_is_spectator%` | The player is spectating. |
| `%aerowars_is_busy%` | The player is in a match **or** queued. |
| `%aerowars_in_group%` | The player is in a party of more than one. |
| `%aerowars_is_leader%` | The player is the party leader. |

## Party

| Placeholder | Value |
| --- | --- |
| `%aerowars_group_size%` | Party member count. |
| `%aerowars_group_online%` | Online party members. |
| `%aerowars_group_leader%` | Party leader's name. |

## Lifetime stats

| Placeholder | Value |
| --- | --- |
| `%aerowars_kills%` | Lifetime kills. |
| `%aerowars_deaths%` | Lifetime deaths. |
| `%aerowars_wins%` | Lifetime wins. |
| `%aerowars_losses%` | Lifetime losses (games − wins). |
| `%aerowars_games%` | Lifetime games played. |
| `%aerowars_kdr%` | Kill/death ratio (2 decimals). |
| `%aerowars_wlr%` | Win/loss ratio (2 decimals). |

## Parameterized

Leaderboard — `metric` is one of `kills`, `wins`, `deaths`, `games`; `pos` is a 1-based position:

| Placeholder | Value |
| --- | --- |
| `%aerowars_top_<metric>_name_<pos>%` | Name at that leaderboard position. |
| `%aerowars_top_<metric>_value_<pos>%` | Value at that position. |

Per-arena — `name` is the arena name:

| Placeholder | Value |
| --- | --- |
| `%aerowars_arena_<name>_players%` | Players currently on that arena. |
| `%aerowars_arena_<name>_max%` | Max players for that arena. |
| `%aerowars_arena_<name>_mode%` | `solo` or `teams`. |
| `%aerowars_arena_<name>_state%` | `waiting`, `countdown`, `active`, `ending`, or `idle`. |

::: tip Example
`%aerowars_top_kills_name_1%` → the name of the #1 killer; `%aerowars_top_kills_value_1%` → their kill count.
:::
