---
phase: 03-earnings
plan: 01
subsystem: earnings
tags: [neoforge, events, mob-kills, toml, config, batch-notifications]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: JBalanceConfig TOML config system with defineInRange/defineListAllowEmpty patterns
  - phase: 02-currency
    provides: EconomyService.give() for async coin credits, CurrencyFormatter for PT-BR formatting

provides:
  - EarningsEventHandler with mob kill reward mechanics, spawner exclusion, and batched notifications
  - JBalanceConfig [earnings.mob_kills] TOML section with 8 pre-configured mob rewards
  - parsedMobRewards() helper method for game-thread reward lookups
  - Kill accumulator (ConcurrentHashMap per-player) flushed at configurable interval

affects: [03-02-playtime, future-shop-features]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - FinalizeSpawnEvent (top-level class in NeoForge 1.21.1, NOT MobSpawnEvent.FinalizeSpawn nested)
    - ConcurrentHashMap kill accumulator with merge() for lock-free batching
    - ServerTickEvent.Post tick counter pattern for periodic flush (20 ticks/second)
    - Config values read on game thread inside event handler, never inside CompletableFuture lambda

key-files:
  created:
    - src/main/java/com/pweg0/jbalance/event/EarningsEventHandler.java
  modified:
    - src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java
    - src/main/java/com/pweg0/jbalance/JBalance.java

key-decisions:
  - "FinalizeSpawnEvent is a top-level class in NeoForge 1.21.1 (not MobSpawnEvent.FinalizeSpawn inner class as documented in 1.20.6 javadoc)"
  - "Kill accumulator uses ConcurrentHashMap with merge() for thread-safe batching on game thread"
  - "Notification format locked: §6[JBalance] §7Voce recebeu §6<amount> §7por matar §6<n> §7mobs (PT-BR)"

patterns-established:
  - "Pattern: Spawner exclusion via FinalizeSpawnEvent persistent data tag jbalance_from_spawner"
  - "Pattern: Config values (MOB_KILL_REWARDS, KILL_NOTIFICATION_INTERVAL) read on game thread in event handler, passed as plain values into async path"
  - "Pattern: ServerTickEvent.Post with flushTickCounter (flushTickCounter >= intervalSeconds * 20) for periodic batch flush"

requirements-completed: [EARN-01, EARN-02, EARN-05]

# Metrics
duration: 3min
completed: 2026-03-19
---

# Phase 3 Plan 01: Mob Kill Earnings Summary

**Mob kill reward system: LivingDeathEvent handler with spawner-tagged mob exclusion, TOML reward map, and ConcurrentHashMap kill accumulator flushed as PT-BR batched notifications via ServerTickEvent**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-19T17:14:15Z
- **Completed:** 2026-03-19T17:17:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Extended JBalanceConfig with `[earnings.mob_kills]` TOML section: 8 pre-configured mobs (Zombie=10, Skeleton=10, Creeper=15, Spider=10, Enderman=25, Witch=20, Blaze=30, Wither Skeleton=40), configurable notification interval (default 60s), and `parsedMobRewards()` helper
- Created EarningsEventHandler with three event handlers: spawner tagging at spawn (FinalizeSpawnEvent), kill reward accumulation (LivingDeathEvent), and batched notification flush (ServerTickEvent.Post)
- Wired all three EarningsEventHandler listeners into JBalance constructor

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend JBalanceConfig with mob kill TOML section** - `fbe7389` (feat)
2. **Task 2: Create EarningsEventHandler with mob kill logic, spawner tagging, and batch notifications** - `1797f24` (feat)

## Files Created/Modified
- `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` - Added earnings.mob_kills TOML section, MOB_KILL_REWARDS list, KILL_NOTIFICATION_INTERVAL, and parsedMobRewards() helper
- `src/main/java/com/pweg0/jbalance/event/EarningsEventHandler.java` - New file: spawner tagger, kill handler, accumulator state, tick-based batch flush
- `src/main/java/com/pweg0/jbalance/JBalance.java` - Added EarningsEventHandler import and three addListener() registrations

## Decisions Made
- FinalizeSpawnEvent is a top-level class in NeoForge 1.21.1 (not MobSpawnEvent.FinalizeSpawn inner class as documented in 1.20.6 javadoc) — confirmed by inspecting neoforge-21.1.220-sources.jar
- Kill accumulator uses ConcurrentHashMap.merge() for atomic per-player coin and kill count batching
- Notification format (PT-BR, locked): "§6[JBalance] §7Voce recebeu §6<amount> §7por matar §6<n> §7mobs"

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed MobSpawnEvent.FinalizeSpawn import — top-level class in NeoForge 1.21.1**
- **Found during:** Task 2 (EarningsEventHandler creation)
- **Issue:** Plan specified `MobSpawnEvent.FinalizeSpawn` as the event type (based on 1.20.6 javadoc), but in NeoForge 1.21.1 `FinalizeSpawnEvent` is a separate top-level class that extends `MobSpawnEvent` — the inner class does not exist
- **Fix:** Changed import from `net.neoforged.neoforge.event.entity.living.MobSpawnEvent` to `net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent`; updated method signature to `onFinalizeSpawn(FinalizeSpawnEvent event)`
- **Files modified:** EarningsEventHandler.java
- **Verification:** Build passed with `BUILD SUCCESSFUL`
- **Committed in:** `1797f24` (part of Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug: wrong event class name from outdated docs)
**Impact on plan:** Necessary fix to compile. API change between 1.20.6 and 1.21.1 — inner class became top-level. No scope creep.

## Issues Encountered
- NeoForge 1.21.1 renamed `MobSpawnEvent.FinalizeSpawn` (inner class) to `FinalizeSpawnEvent` (top-level class). Caught at first build, confirmed by inspecting neoforge-21.1.220-sources.jar per the research document's recommendation.

## Next Phase Readiness
- Mob kill earnings fully functional: config, event handling, accumulator, batch notifications
- Ready for Phase 3 Plan 02: playtime milestone earnings
- Pattern established: ServerTickEvent.Post tick counter for periodic flush (reusable for playtime flush in Plan 02)

---
*Phase: 03-earnings*
*Completed: 2026-03-19*
