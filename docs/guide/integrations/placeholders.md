# PlaceholderAPI

AeroWars ships a **PlaceholderAPI** expansion so you can show live game data in holograms, scoreboards, tab lists, and anywhere else placeholders are supported.

## Setup

1. Install [PlaceholderAPI](https://placeholderapi.com) (`at.helpch.placeholderapi`) on your server.
2. Start AeroWars — it registers the `aerowars` expansion automatically at startup. No configuration needed.

This is a **soft dependency**: if PlaceholderAPI isn't installed, registration is skipped and nothing breaks.

## Verify it works

```
/papi list                       # aerowars should appear
/papi parse <player> %aerowars_players%
/papi bcparse <player> Arenas: %aerowars_arenas%
```

## Placeholders

Every placeholder uses the form `%aerowars_<name>%`. A few examples:

- `%aerowars_players%` — players currently in games.
- `%aerowars_player_status%` — `none` / `waiting` / `playing` / `spectating`.
- `%aerowars_kills%` — the player's lifetime kills.
- `%aerowars_top_kills_name_1%` — the name of the #1 killer.

See the full list on the [Placeholders reference](/guide/reference/placeholders) page.
