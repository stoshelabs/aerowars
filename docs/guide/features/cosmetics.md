# Cosmetics

Cosmetics are visual extras players can equip. Open the shop with `/aerowars cosmetics` (aliases `cosmetic`, `cosmeticos`). Like kits, each cosmetic is **free**, **permission-locked**, or **purchasable** through your [economy](/guide/integrations/economy) plugin.

## Categories

| Category | What it does |
| --- | --- |
| **Cage themes** | Change the [prefab](/guide/features/cages) of your countdown spawn cage. Each cage has a **Preview** button that opens a rotatable, zoomable 3D model of it (reflecting the cage's configured rotation); Back returns to the shop. |
| **Kill effects** | A firework burst at the victim when you eliminate someone. |
| **Victory shows** | Replaces the particle of your victory firework show when you win. |
| **Trails** | A particle trail that comes off your body as you move (smoke, fireflies, fire, a colour-cycling rainbow dust, and more). |

## Unlocking

- **Free** cosmetics are available to everyone.
- **Permission-locked** cosmetics need a node — the built-in examples are `aerowars.cosmetic.cage.vip`, `aerowars.cosmetic.kill.royal`, `aerowars.cosmetic.victory.grand`, and `aerowars.cosmetic.trail.rainbow`. Grant them through your permissions plugin.
- **Purchasable** cosmetics are bought with coins; if the economy can't charge, they fall back to free.

Cosmetic names and descriptions are localized (English + Portuguese), and admins can add their own cosmetics with custom names, prices, and permission nodes.
