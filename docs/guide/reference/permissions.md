# Permissions

AeroWars keeps its permission model deliberately small. There are only **two hard gates**; everything else is either open to all players or unlocked by an optional cosmetic/kit node.

## Core nodes

| Node | Default | Grants |
| --- | --- | --- |
| `aerowars.play` | **true** | Joining a match (`/aerowars join`) and queueing (`/aerowars queue`). Granted unless explicitly denied. |
| `aerowars.admin` | **false** | All admin commands (`admin`, `start`, `forcestop`, `setlobby`, `reload`, `changelog`, `savekit`, `wand`, `firework`, and every `setup`, `arena`, `maps`, and `world` subcommand). Also bypasses TaleGuard build restrictions, enables the hidden admin-spectator, and reveals admin entries in `/aerowars help` and update notifications. |

Any command not listed under `aerowars.admin` is usable by every player.

## Cosmetic & kit unlock nodes

Purchasable and free cosmetics/kits don't need a node. These built-in nodes gate the **permission-locked** examples that ship with the plugin — and every cosmetic and kit carries its own configurable `permission` field, so you can add your own.

| Node | Unlocks |
| --- | --- |
| `aerowars.cosmetic.cage.vip` | "Vidro Real" VIP glass cage theme. |
| `aerowars.cosmetic.kill.royal` | "Realeza" kill effect. |
| `aerowars.cosmetic.victory.grand` | "Grandioso" victory show. |
| `aerowars.cosmetic.trail.rainbow` | "Arco-Íris" projectile trail. |
| `aerowars.kit.sniper` | The permission-locked "sniper" example kit. |

::: tip
To lock a cosmetic or kit behind rank/donor perks, set its `permission` field in the relevant config/data file and grant that node through your permissions plugin.
:::
