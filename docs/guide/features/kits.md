# Kits

Kits are the loadouts players start a match with. They're chosen before the game and can be **free**, **permission-locked**, or **purchasable**.

## Choosing a kit

- `/aerowars kit` — opens the kit picker, where a player can view a kit's items and select it.
- `/aerowars kit <id>` — selects a kit directly by id.

If a player hasn't picked one, they get `Kits.DefaultKit`. By default kits can also be swapped during the countdown (`Kits.SelectionDuringCountdown`).

## Kit tiers

| Tier | Access |
| --- | --- |
| **Free** | Available to everyone. |
| **Permission-locked** | Requires a permission node (e.g. `aerowars.kit.sniper`). Grant it via your permissions plugin to rank/donor groups. |
| **Purchasable** | Bought with coins through your [economy](/guide/integrations/economy) plugin. If the economy can't charge, the kit falls back to free. |

Armor in a kit is **worn**, not dropped into the backpack.

## Creating kits (admin)

There are two ways to author kits:

- **Quick save** — arrange the items in your own inventory, then run `/aerowars savekit <name>` to snapshot it into a kit.
- **Visual builder** — open `/aerowars admin` → **Kits**, where you can build a kit by clicking items from your inventory into the kit's slots, name it, and save.

::: info Why click-to-copy, not drag-and-drop
Hytale's custom UI can't drag live inventory items into a page, so the builder uses click-to-copy: click an inventory item to add it to the kit, click a kit item to remove it. Item icons render natively in the slots.
:::

Use `/aerowars reload` to apply hand-edited kit files without a restart.
