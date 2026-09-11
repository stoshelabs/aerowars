# Spectating

When a player dies — and `Match.SpectateOnDeath` is enabled — they don't leave the match. They become a **spectator**: flying, invulnerable, and invisible to the players still fighting.

## Spectator state

Spectators run on **Hytale's native spectator mode** (Hytale 0.6+), so the engine itself enforces it:

- **Flight and no-clip** — fly freely and pass through blocks to follow the action.
- **Invulnerable and intangible** — no damage touches you, and hits and projectiles pass through you.
- **Hidden** — living players can't see you, and you're left out of their player list. Spectators still see each other.
- **Spectator voice channel** — dead players talk among themselves, not with the players still fighting.

Spectators can't break, place or use blocks, or pick up items. All of this is stripped cleanly when you return to the lobby — including after a mid-match disconnect: log back in and you're out of spectator mode.

When the match ends, spectators are revealed to everyone for the victory celebration, still flying and invulnerable.

::: tip Legacy spectator state
Set [`Spectator.NativeMode`](/guide/setup/config#spectator) to `false` to use AeroWars' own spectator state instead: flight, invulnerability and per-player hiding, without no-clip. AeroWars also falls back to it automatically if the native mode can't be entered (for example, while another plugin has the player in its own game mode).
:::

## The spectator hotbar

Spectators get a small Hypixel-style hotbar (configurable in the [`Spectator`](/guide/setup/config#spectator) section):

| Item | Slot (default) | Action |
| --- | --- | --- |
| **Player tracker** | 0 | Opens a menu listing living players; pick one to teleport to them. |
| **Return to lobby** | 8 | Leaves the match and sends you back to the lobby. |

::: info How the items trigger
Both tools fire on **left- and right-click**, whether you aim at a block or click in open air — they keep their distinct chest/torch/bed models and icons. The tracker opens its teleport menu even when nobody is alive yet.
:::

## Inventory safety

Your pre-match inventory and game mode are saved on join (`Match.SaveInventory`) and restored when you leave — whether you leave normally, get eliminated, or disconnect mid-match. Spectator items never leak back to the lobby.

## Admin spectate

Admins can silently drop into a running match as a hidden spectator from the admin panel (`/aerowars admin` → **Matches** → **Spectate**), without appearing in the roster or triggering a join message. Living players can't see you; in native mode, other spectators can. Leave with `/aerowars leave`.
