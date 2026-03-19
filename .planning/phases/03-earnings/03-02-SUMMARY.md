---
phase: 03-earnings
plan: 02
subsystem: database
tags: [playtime, milestones, afk-detection, hikari, sqlite, mysql, neoforge]

# Dependency graph
requires:
  - phase: 03-earnings-01
    provides: EarningsEventHandler, JBalanceConfig earnings section, EconomyService.give()
  - phase: 01-foundation
    provides: DatabaseManager, BalanceRepository pattern, HikariCP connection pool
provides:
  - jbalance_playtime table (MySQL + SQLite) with active_seconds and claimed_hours columns
  - DatabaseManager multi-statement schema migration (semicolon split)
  - PlaytimeRepository with loadPlaytime/upsertPlaytime (dialect-aware)
  - PlaytimeService with AFK detection, active tick accumulation, milestone reward grants
  - JBalanceConfig MILESTONES list and AFK_TIMEOUT_MINUTES config with parsedMilestones() helper
  - EarningsEventHandler extended with onPlayerTick, onPlayerLoggedIn, onPlayerLoggedOut
  - JBalance lifecycle: PlaytimeService init on server start, flushAll on server stop
affects: [04-shops, future-retention-features]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Mutable inner class PlayerState avoids per-tick object allocation in hot path
    - Config values (parsedMilestones, AFK_TIMEOUT_MINUTES) always read on game thread inside onTick
    - Milestone claim-then-reward: claimedHours updated in-memory before async DB write to prevent double award
    - Dedicated single-thread daemon executor for playtime DB writes (separate from EconomyService.DB_EXECUTOR)
    - onLogin loads DB async then executes state mutation back on game thread via server.execute()

key-files:
  created:
    - src/main/java/com/pweg0/jbalance/data/db/PlaytimeRepository.java
    - src/main/java/com/pweg0/jbalance/service/PlaytimeService.java
  modified:
    - src/main/resources/schema/schema_mysql.sql
    - src/main/resources/schema/schema_sqlite.sql
    - src/main/java/com/pweg0/jbalance/data/db/DatabaseManager.java
    - src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java
    - src/main/java/com/pweg0/jbalance/event/EarningsEventHandler.java
    - src/main/java/com/pweg0/jbalance/JBalance.java

key-decisions:
  - "PlayerState uses mutable class (not record) to avoid creating a new object every tick (20/sec per player)"
  - "activeTicks (not activeSeconds) stored in-memory; seconds derived as activeTicks/20 — avoids fractional accumulation"
  - "Milestone claimed in-memory BEFORE DB write to prevent double-award on async lag"
  - "PlaytimeService has its own single-thread daemon executor separate from EconomyService.DB_EXECUTOR to avoid contention"
  - "DatabaseManager.runMigrations() now splits on semicolons to support multi-statement schema files"
  - "earnings TOML config restructured: single push(earnings) wrapping push(mob_kills) and push(milestones)"

patterns-established:
  - "Async DB load on login with game-thread callback via server.execute() for safe state mutation"
  - "Periodic flush counter in service class (flushTickCounter) driven by server tick delegation"
  - "claimedHours stored as comma-delimited string in DB, parsed to Set<Long> in-memory"

requirements-completed: [EARN-03, EARN-04, EARN-05]

# Metrics
duration: 3min
completed: 2026-03-19
---

# Phase 03 Plan 02: Playtime Milestone Earnings Summary

**Playtime milestone system with AFK detection, DB persistence, and per-player active-time tracking rewarding cumulative hours with configurable coin grants**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-19T17:19:23Z
- **Completed:** 2026-03-19T17:22:00Z
- **Tasks:** 2
- **Files modified:** 7 (2 created, 5 modified)

## Accomplishments

- jbalance_playtime table added to both MySQL and SQLite schemas; DatabaseManager fixed to execute multi-statement schema files
- PlaytimeRepository provides loadPlaytime (null-safe) and upsertPlaytime (dialect-aware ON DUPLICATE KEY / INSERT OR REPLACE)
- PlaytimeService tracks per-player active ticks with AFK detection (position+rotation comparison), checks milestones each tick, grants rewards exactly once via in-memory claimedHours set, flushes to DB every 5 minutes and on logout/shutdown
- JBalanceConfig extended with MILESTONES list and AFK_TIMEOUT_MINUTES under a properly nested earnings/milestones TOML section

## Task Commits

Each task was committed atomically:

1. **Task 1: Add playtime DB schema, PlaytimeRepository, and milestone TOML config** - `35203f2` (feat)
2. **Task 2: Create PlaytimeService and wire playtime event handlers** - `d6904c4` (feat)

## Files Created/Modified

- `src/main/resources/schema/schema_mysql.sql` - Added jbalance_playtime table (InnoDB, utf8mb4)
- `src/main/resources/schema/schema_sqlite.sql` - Added jbalance_playtime table (TEXT/INTEGER types)
- `src/main/java/com/pweg0/jbalance/data/db/DatabaseManager.java` - runMigrations() now splits on semicolons
- `src/main/java/com/pweg0/jbalance/data/db/PlaytimeRepository.java` - New: CRUD for jbalance_playtime
- `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` - MILESTONES, AFK_TIMEOUT_MINUTES, MilestoneEntry, parsedMilestones(); earnings push/pop restructured
- `src/main/java/com/pweg0/jbalance/service/PlaytimeService.java` - New: AFK detection, milestone tracking, DB flush
- `src/main/java/com/pweg0/jbalance/event/EarningsEventHandler.java` - Added onPlayerTick, onPlayerLoggedIn, onPlayerLoggedOut; onServerTick delegates to PlaytimeService
- `src/main/java/com/pweg0/jbalance/JBalance.java` - Wires 3 new listeners, initializes PlaytimeService on server start, flushAll on stop

## Decisions Made

- PlayerState uses mutable inner class (not record) to avoid creating a new object every tick at 20/sec per player
- activeTicks stored in-memory (not activeSeconds) to avoid fractional accumulation; seconds derived as activeTicks/20
- Milestone claim-in-memory-first pattern: claimedHours.add() happens before give() and upsertPlaytime() to prevent double-award
- PlaytimeService gets a dedicated single-thread daemon executor to avoid contention with EconomyService.DB_EXECUTOR
- earnings TOML section restructured to properly wrap both mob_kills and milestones under a single push("earnings")

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - both tasks built cleanly on the first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 03 complete: both mob kill earnings (Plan 01) and playtime milestones (Plan 02) are implemented
- Phase 04 (shops) can reference EconomyService.give/take for purchase transactions
- No blockers

---
*Phase: 03-earnings*
*Completed: 2026-03-19*
