# Roadmap: JBalance

## Overview

JBalance is built in four phases that reflect the natural dependency order of an economy mod. The database and project foundation must come first — every other feature sits on top of it. Currency commands follow immediately after, as they are the visible surface players and admins interact with daily. Earning mechanics give players a way to accumulate currency without manual admin intervention. Finally, the admin land/terrain system delivers the most complex feature — property sales, protection, and rent — which only makes sense once an active economy already exists.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation** - NeoForge project scaffold, dual-database layer, TOML config, and project infrastructure
- [ ] **Phase 2: Currency** - Full player and admin currency command suite with balance persistence
- [ ] **Phase 3: Earnings** - Passive currency earning from mob kills and playtime milestones
- [ ] **Phase 4: Land** - Admin terrain zone definition, property sales, protection, and monthly rent

## Phase Details

### Phase 1: Foundation
**Goal**: The mod loads cleanly on a NeoForge 1.21.1 dedicated server with a working database connection, TOML config, and async infrastructure ready for all feature phases to build on.
**Depends on**: Nothing (first phase)
**Requirements**: INFR-01, INFR-02, INFR-03, INFR-04, INFR-05, CURR-08, CURR-09, CURR-10
**Success Criteria** (what must be TRUE):
  1. The mod loads on a NeoForge 1.21.1 dedicated server without errors or warnings in the log
  2. Player balances survive a server restart and match exactly what they were before (MySQL primary, SQLite fallback both confirmed working)
  3. New players joining for the first time receive the configured starting balance automatically
  4. An admin can change a reward value in the TOML config, and the server applies it without a restart
  5. The currency name and symbol shown in chat reflect whatever is set in the TOML config
**Plans**: TBD

Plans:
- [ ] 01-01: Project scaffold — ModDevGradle 2.0.x setup, NeoForge 21.1.220, jarJar for HikariCP + MySQL Connector/J + sqlite-jdbc
- [ ] 01-02: DatabaseManager — HikariCP pool, schema migration, MySQL/SQLite routing, async executor
- [ ] 01-03: TOML config — ModConfigSpec SERVER type, all configurable values, hot-reload support

### Phase 2: Currency
**Goal**: Players can check balances, send coins to each other, and view the wealth rankings; admins can give, take, and set any player's balance from the command line.
**Depends on**: Phase 1
**Requirements**: CURR-01, CURR-02, CURR-03, CURR-04, CURR-05, CURR-06, CURR-07
**Success Criteria** (what must be TRUE):
  1. A player can type `/eco balance` and see their current balance in PT-BR formatted chat
  2. A player can type `/eco balance <player>` and see another online or recently-seen player's balance
  3. A player can type `/eco pay <player> <amount>` and the coins move atomically — the sender's balance decreases and the receiver's increases with no possibility of a double-spend
  4. A player can type `/eco top` and see the top 10 richest players ranked by balance
  5. An admin (OP level 4) can give, take, or set any player's balance via `/ecoadmin give|take|set <player> <amount>` and the change is immediately reflected
**Plans**: TBD

Plans:
- [ ] 02-01: EconomyService — atomic balance CRUD and transfer with per-player in-flight locks
- [ ] 02-02: Player commands — /eco balance, /eco pay, /eco top with PT-BR messages
- [ ] 02-03: Admin commands — /ecoadmin give, take, set with OP level 4 permission gating

### Phase 3: Earnings
**Goal**: Players passively earn coins by killing mobs and reaching playtime milestones, with configurable reward rates and persistent milestone tracking that survives disconnects.
**Depends on**: Phase 2
**Requirements**: EARN-01, EARN-02, EARN-03, EARN-04, EARN-05
**Success Criteria** (what must be TRUE):
  1. When a player kills a mob, they receive the configured coin reward for that mob type and see a PT-BR chat notification immediately
  2. Mob types not individually configured fall back to the default kill reward value from TOML
  3. When a player's total playtime crosses a configured milestone (e.g. 1h, 2h, 5h), they receive the milestone reward exactly once and are notified in chat
  4. If a player disconnects mid-session, their playtime progress toward the next milestone is preserved and they continue accumulating toward it on next login
  5. An admin can change any mob reward or milestone threshold in TOML and the updated values apply on next server reload
**Plans**: TBD

Plans:
- [ ] 03-01: Mob kill earning — LivingDeathEvent handler, per-mob-type reward lookup, EconomyService credit
- [ ] 03-02: Playtime milestone tracking — in-memory counter with login/logout events, DB flush at milestones and logout

### Phase 4: Land
**Goal**: Admins can define geographic terrain zones, mark them for sale, and players can purchase them; terrain owners get building rights and owe configurable monthly rent, all persisted across restarts.
**Depends on**: Phase 2
**Requirements**: LAND-01, LAND-02, LAND-03, LAND-04, LAND-05, LAND-06, LAND-07, LAND-08
**Success Criteria** (what must be TRUE):
  1. An admin can select two opposite corners of a 3D zone using the designated item and confirm the selection, then mark it for sale with `/ecoadmin venda <price>`
  2. A terrain marked for sale is protected — non-owners receive a PT-BR denial message and cannot place or break blocks inside it
  3. A player can purchase an available terrain and the price is atomically deducted from their balance; they immediately gain build/break rights inside the zone
  4. A terrain owner who fails to pay monthly rent loses ownership and the terrain reverts to for-sale or admin-locked status
  5. An admin can run a command to list all terrain zones with their owner, price, and rent status
  6. All terrain data (zone bounds, owner, status) persists across server restarts
**Plans**: TBD

Plans:
- [ ] 04-01: Terrain data model — DB schema for zones, corner selection tool, TerrainService
- [ ] 04-02: Protection and sale system — block event cancellation, /ecoadmin venda, purchase command
- [ ] 04-03: Rent scheduler — monthly rent deduction, ownership revocation, admin terrain list command

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 0/3 | Not started | - |
| 2. Currency | 0/3 | Not started | - |
| 3. Earnings | 0/2 | Not started | - |
| 4. Land | 0/3 | Not started | - |
