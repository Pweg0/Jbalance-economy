# Roadmap: JBalance

## Overview

JBalance is built in three phases that reflect the natural dependency order of an economy mod. The database and project foundation must come first — every other feature sits on top of it. Currency commands follow immediately after, as they are the visible surface players and admins interact with daily. Earning mechanics give players a way to accumulate currency without manual admin intervention.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundation** - NeoForge project scaffold, dual-database layer, TOML config, and project infrastructure (completed 2026-03-19)
- [x] **Phase 2: Currency** - Full player and admin currency command suite with balance persistence (completed 2026-03-19)
- [ ] **Phase 3: Earnings** - Passive currency earning from mob kills and playtime milestones

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
**Plans:** 3/3 plans complete

Plans:
- [x] 01-01-PLAN.md — Project scaffold: ModDevGradle 2.0.x, NeoForge 21.1.220, jarJar deps, @Mod entry point
- [x] 01-02-PLAN.md — TOML config: ModConfigSpec SERVER type, currency formatter, hot-reload wiring
- [x] 01-03-PLAN.md — Database layer: HikariCP dual-DB, BalanceRepository, EconomyService, player first-join handler

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
**Plans:** 3/3 plans complete

Plans:
- [ ] 02-01-PLAN.md — Service layer extensions: BalanceRepository new methods, EconomyService wrappers, config values, CommandRegistrar skeleton
- [ ] 02-02-PLAN.md — Player commands: /eco balance, /eco pay, /eco top with PT-BR messages, cache, and guards
- [ ] 02-03-PLAN.md — Admin commands: /ecoadmin give, take, set with offline player resolution and OP 4 gating

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
**Plans:** 2 plans

Plans:
- [ ] 03-01-PLAN.md — Mob kill earnings: TOML config for mob rewards, EarningsEventHandler with LivingDeathEvent, spawner tagging, kill accumulator, batched notifications
- [ ] 03-02-PLAN.md — Playtime milestones: milestone TOML config, PlaytimeRepository, PlaytimeService with AFK detection, milestone tracking, DB persistence


## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 3/3 | Complete   | 2026-03-19 |
| 2. Currency | 3/3 | Complete   | 2026-03-19 |
| 3. Earnings | 0/2 | Not started | - |
