# AeroWars — Roadmap

SkyWars-style minigame for Hytale. This tracks what's done and what's next.

## Status: MVP (playable core)

The plugin compiles and jars against the live server API and the full match loop
works end-to-end. Build with `./gradlew jar` → `build/libs/AeroWars-1.0.0.jar`.

### Done (MVP)
- Multi-arena, **Solo (FFA)** and **Teams**, with lobby queue and on-demand match creation
- Match lifecycle `WAITING → COUNTDOWN → ACTIVE → ENDING → CLEANUP` (1 Hz scheduler, all
  world/entity mutations marshalled onto the world thread via `world.execute`)
- Glass **spawn cages** built and dropped with `world.setBlock`
- **Teleport** via the `Teleport` component (cross-world)
- **Death detection** (`RefChangeSystem<DeathComponent>`) with kill credit
- **Combat control** (`EntityEventSystem<Damage>`): no PvP before cages drop, none to/from
  spectators, optional friendly-fire in team arenas
- **Kits** applied to the player inventory; picked with `/aerowars kit <id>`
- **Spectator on death**, **win/elimination** detection (last team standing)
- **Custom HUD** (`CustomUIHud` + `Common/UI/Custom/HUD/AeroWarsHud.ui`): phase, big timer,
  players-alive, kit — attached reflectively via `player.getHudManager()`
- **In-game arena setup wizard** (`/aerowars setup start|set|done|skip|save|cancel`)
- **i18n** (pt_br / en_us), JSON config
- Optional **TaleGuard** protection bridge (reflective hook, lobby + phase protection)

### Commands
`/aerowars` (aliases `/aw`, `/sw`) → `join`, `leave`, `kit <id>`, `list`, `start` (admin),
`help`, `setup start|set|done|skip|save|cancel`.

---

## Next up

### 0. Chest loot-fill ✅ DONE
`LootManager.fillChest()` writes rolled loot into the placed chest via
`world.getBlockComponentHolder(x,y,z).getComponent(ItemContainerBlock.getComponentType())
.getItemContainer()` (`SimpleItemContainer.setItemStackForSlot`). `MatchManager.startMatch`
force-loads each chest's chunk (`ChunkUtil.indexChunkFromBlock` + `getChunkAsync`) before place/fill.
Loot ids in `loot.json`/`seedDefaults` replaced with verified ids.

### 1. Chest refill ✅ DONE
Mid-match refill on `Loot.RefillEnabled` every `RefillIntervalSeconds` (tick ACTIVE →
`prepareChests(match, false)`; `fillChest` clears the container first so it replaces, not stacks).

### 2. Kit selection UI — DONE (picker + paid/locked + admin price/permission editor)
`/aerowars savekit <name>` snapshots the admin's hotbar into a kit; the kit-picker modal
(`ui/KitSelectPage`, `/aerowars kit`) lists/views/selects kits; the admin panel Kits tab creates/deletes.
**Paid/locked kits ✅:** a kit's `cost` (coins) and `permission` fields now gate selection —
`KitManager.isUnlocked` = free OR owned OR has-permission; the picker shows Select / Buy(cost) / Locked
per state; `MatchManager.buyKit` charges the economy and grants ownership (persisted to
`kit_unlocks.json`). Set `cost`/`permission` in the kit's `kits/<id>.json`. STILL TODO: custom kit
images, in-UI price/permission editor, drag-drop item arranging.

### 3. Stats & leaderboards ✅ DONE
`StatsManager` persists kills/deaths/wins/games to `stats.json`; hooked into
addPlayer/handleDeath/endMatch. Commands `/aerowars stats` and `/aerowars top [kills|wins]`.

### 4. Economy integration ✅ DONE
`integration/EconomyService` reflectively resolves an economy provider (primary: the sibling
`com.overworldlabs.economy` via `Economy.getAPI().deposit`; fallbacks: EliteEssentials, Ecotale) with
**zero compile-time dependency** (same style as `TaleGuardBridge`). `MatchManager.awardEconomy` pays
`Rewards.Economy.KillAmount` on each credited kill (`handleDeath`) and `WinAmount` to every winner
(`endMatch`), gated on `Rewards.Economy.Enabled`, and messages the player (`economy.reward_kill` /
`economy.reward_win`). If no provider is installed the service is inert (silent no-op).

### 5. Party / matchmaking — DONE (parties + modal + mode queue; NPC join points dropped)
`PartyManager` + a full custom-UI **management modal** (`/aerowars party`, Plots trust/warps style):
`ui/PartyMenuPage` (members, invite, kick, transfer leadership, leave/disband, queue) with an
online-player invite picker (`PartyPlayerPickerPage`) and a confirm popup for every action
(`PartyConfirmPopupPage` — confirm/cancel both return to the menu). Chat fallback still works:
`/aerowars party invite|accept|decline|leave|disband|kick`. The leader's `/aerowars join` (or the
modal's Queue) pulls the whole party into one match (`MatchManager.joinParty`): SOLO arenas share the
game as FFA rivals (1-per-team), TEAMS arenas cluster the party onto the same team and spill to the
next team when one fills (teams stay balanced). Config `Party.{Enabled,MaxSize,InviteExpirySeconds}`.
STILL TODO: matchmaking queue, arena selection via sign / NPC join points.

### 6. Cosmetics ✅ DONE
Tabbed cosmetics shop (`/aerowars cosmetics`, `ui/CosmeticsPage` + `AeroWarsCosmetics.ui`) with four
tabs — **Cages / Kill Effects / Victory / Trails** — each cosmetic **free**, **purchasable** (economy) or
**permission-gated**. `CosmeticsManager` owns the catalogue (`cosmetics.json`, seeded) + per-player
owned/selected state (`cosmetics_players.json`, saved on buy/select). Hooks: selected cage cosmetic
themes the player's cage (`setCage` block override), kill cosmetic spawns a particle burst at the victim
on an eliminating hit, victory cosmetic overrides the winner's firework show, and the selected **trail** spawns a coloured
particle at the player's feet every 350ms while alive (`MatchManager.tickTrails`, value
`"ParticleId|#RRGGBB"` or `"...|rainbow"` — colour is sent to the client so tint-capable particles vary
by colour). Trails reuse existing game particles (Cinematic_Pink_Smoke, Embers, Dust_Sparkles,
Example_Firework_ColorBase). `EconomyService` gained `charge`/`has`/`balance`. Permission nodes:
`aerowars.cosmetic.{cage.vip,kill.royal,victory.grand,trail.rainbow}`. (Victory dances/taunts dropped.)

### 7. Per-player themed cages & configurable platform — PARTIAL (platform done)
**Configurable platform ✅:** `AeroWarsConfig.Platform {Block, Size, Height}` drives the void-template
spawn platform in `WorldManager` (was hardcoded `Rock_Stone` 9×9 @ Y100). Size is clamped odd/≥1;
keep ≤15 so it stays inside the preloaded chunk (0,0). **Per-player themed cages ✅** — delivered via the
cosmetics shop (see #6): each player's selected cage cosmetic overrides their island cage block.

### 8. Side scoreboard ✅ DONE
Sidebar scoreboard (config field `Scoreboard.Enabled`) alongside the HUD. `ScoreboardHud` +
`Common/UI/Custom/HUD/AeroWarsScoreboard.ui`, driven by `HudManager`. Shows map, mode, time, alive,
kills (tracked in `Match.kills`), kit. Independent of `Hud.Enabled`.

### 9. End-game polish ✅ DONE
Victory podium / fireworks, refined return-to-lobby, spectator flight/vanish — all done. Plus a
**Hypixel-style spectator hotbar** ✅: on death the player gets named, tooltipped tool items
(`AeroWars_SpecTracker` → opens a living-player teleport modal `SpectatorTrackerPage`;
`AeroWars_SpecLobby` → returns to lobby), locked in configurable slots (`Spectator.{TrackerSlot,
TrackerItem,LobbySlot,LobbyItem}`) and removed/restored on leave. Triggered by hotbar slot selection
(`SpectatorItemSystem` on `InventorySetActiveSlotEvent` — no reliable air-use event server-side).
Spectators can't break/place/attack (existing systems + a new attacker-is-spectator guard in
`CombatControlSystem`). Custom items inherit base art via `Parent` (Deco_Map/Deco_Scroll), names via
`Server/Languages/*/aerowars.lang`. **Winner podium ✅** — `MatchManager.buildPodium` raises the
winner(s) onto a block platform above the arena centre during the end celebration
(`Effects.{PodiumEnabled,PodiumBlock,PodiumHeight}`), with temporary invulnerability (stripped on
return to lobby). Nothing left for #9.

### 10. Database backend (pluggable persistence) — DONE (per-player stores migrated)
Built exactly like `plots`: a `Database` config block `{ Enabled, JdbcUrl, Username, Password,
MaxPoolSize }` with the driver chosen by the JDBC URL (sqlite/mysql/mariadb/postgres). The **stats**
store now sits behind an `IStatsRepository` interface with two impls — `JsonStatsRepository` (default
`stats.json`) and `SqlStatsRepository` (HikariCP + JDBC, table `aerowars_stats`, portable
UPDATE-then-INSERT upsert). `StatsRepositoryFactory` picks one from config and **degrades gracefully to
JSON** if the DB is disabled or HikariCP/the driver isn't on the classpath (driver classes are compiled
against but NOT shaded into the jar — same as plots; an admin drops them on the server classpath).
Enables shared leaderboards across servers. **Per-player stores now migrated too**: a generic
`IPlayerDataRepository` (JSON-blob-per-UUID) with `JsonPlayerDataRepository` (default, byte-compatible with
the old files) + `SqlPlayerDataRepository` (one `(uuid PK, data TEXT)` table each) + `PlayerDataRepositoryFactory`
backs **kit unlocks** (`aerowars_kit_unlocks`) and **player cosmetics** (`aerowars_cosmetics`) — so
purchases/unlocks sync across servers when SQL is enabled, degrading to JSON otherwise. Config stores
(kit definitions, cosmetics catalogue, arenas, loot tables) intentionally stay JSON — they're admin-edited
files, not per-player data.

---

## Notes
- Build compiles against the auto-detected install `HytaleServer.jar` (flatpak on Linux),
  which is a **newer API** than some bundled `libs/HytaleServer.jar`. The `plots` plugin is the
  reference for the current API.
- Arena templates live in `<pluginDataDir>/worlds/<template>/` (copied per match). Drop a world
  template there before running `/aerowars setup start <arena> <template>`.
