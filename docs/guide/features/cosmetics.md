# Cosmetics

Cosmetics are visual extras players can equip. Open the shop with `/aerowars cosmetics` (aliases `cosmetic`, `cosmeticos`). Like kits, each cosmetic is **free**, **permission-locked**, or **purchasable** through your [economy](/guide/integrations/economy) plugin.

## Categories

| Category | What it does |
| --- | --- |
| **Cage themes** | Change the block/appearance of your countdown spawn cage. |
| **Kill effects** | A visual effect played when you eliminate someone. |
| **Victory shows** | A special celebration effect when you win. |
| **Trails** | Particle trails that follow your projectiles/movement (fire, rainbow dust, and more). |

## Unlocking

- **Free** cosmetics are available to everyone.
- **Permission-locked** cosmetics need a node — the built-in examples are `aerowars.cosmetic.cage.vip`, `aerowars.cosmetic.kill.royal`, `aerowars.cosmetic.victory.grand`, and `aerowars.cosmetic.trail.rainbow`. Grant them through your permissions plugin.
- **Purchasable** cosmetics are bought with coins; if the economy can't charge, they fall back to free.

Cosmetic names and descriptions are localized (English + Portuguese), and admins can add their own cosmetics with custom names, prices, and permission nodes.
