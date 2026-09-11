# Kits

Kits are the loadouts players start a match with. They're chosen before the game and can be **free**, **permission-locked**, or **purchasable**.

## Choosing a kit

- `/aerowars kit` — opens the kit picker.
- `/aerowars kit <id>` — selects a kit directly by id.

The picker is laid out cosmetics-style: an ownership **filter** (All / Owned / To Buy), a **per-kit icon**, and an at-a-glance status for each kit — **Free**, **Owned**, its **price**, or **Permission**. The kit you'll actually spawn with is flagged **IN USE** (its button reads "In use" instead of Select). **View** opens the full item breakdown of a kit.

If a player hasn't picked one, they get `Kits.DefaultKit`. By default kits can also be swapped during the countdown (`Kits.SelectionDuringCountdown`).

## Kit persistence

With `Kits.PersistLastKit` on (the default), each player's last-picked kit is remembered across matches — they keep it until they choose another. A remembered kit they no longer have unlocked falls back to `Kits.DefaultKit`. Turn it off to start every match on the default kit until a selection is made.

## Waiting-phase hotbar

With `Kits.WaitingItems` on (the default), players waiting in their cage get a locked hotbar:

| Item | Slot (default) | Action |
| --- | --- | --- |
| **Kit Selector** | 0 | Opens the kit menu. |
| **Return to Lobby** | 8 | Leaves the match and returns to the lobby. |

Both items are non-droppable and are removed the moment the match starts, just like the [spectator hotbar](/guide/features/spectating). They respond to both left- and right-click, aimed at a block or in open air. Item ids and slots are configurable under [`Kits`](/guide/setup/config#kits).

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
