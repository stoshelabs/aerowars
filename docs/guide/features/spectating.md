# Spectating

When a player dies — and `Match.SpectateOnDeath` is enabled — they don't leave the match. They become a **spectator**: flying, invulnerable, and invisible to the players still fighting.

## Spectator state

- **Flight** is granted so you can follow the action.
- **Invulnerable** — no damage can touch you.
- **Hidden** — living players can't see you, so you never interfere.

Spectators can't break or place blocks. All of this is stripped cleanly when you return to the lobby.

## The spectator hotbar

Spectators get a small Hypixel-style hotbar (configurable in the [`Spectator`](/guide/setup/config#spectator) section):

| Item | Slot (default) | Action |
| --- | --- | --- |
| **Player tracker** | 0 | Opens a menu listing living players; pick one to teleport to them. |
| **Return to lobby** | 8 | Leaves the match and sends you back to the lobby. |

::: info How the items trigger
Air-clicks have no reliable server event in Hytale, so these tools fire when you **click a block** with them in hand (aim at any block near the spectator platform). The tracker opens a teleport menu even when nobody is alive yet.
:::

## Inventory safety

Your pre-match inventory and game mode are saved on join (`Match.SaveInventory`) and restored when you leave — whether you leave normally, get eliminated, or disconnect mid-match. Spectator items never leak back to the lobby.

## Admin spectate

Admins can silently drop into a running match as a hidden spectator from the admin panel (`/aerowars admin` → **Matches** → **Spectate**), without appearing in the roster or triggering a join message. Leave with `/aerowars leave`.
