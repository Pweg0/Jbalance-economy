---
phase: 02-currency
plan: 01
subsystem: database
tags: [java, neoforge, brigadier, hikaricp, sqlite, mysql, sql]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: BalanceRepository, EconomyService, DatabaseManager, JBalanceConfig, HikariCP dual-DB setup

provides:
  - BalanceRepository.getTopBalances(int limit) — ranked top-N query
  - BalanceRepository.getPlayerRank(UUID) — 1-based rank via subquery count
  - BalanceRepository.setBalance(UUID, long) — exact-value UPDATE
  - BalanceRepository.findByDisplayName(String) — dialect-aware case-insensitive lookup
  - EconomyService async wrappers for all 4 new repo methods
  - JBalanceConfig.TOP_CACHE_SECONDS (default 60) and TRANSFER_COOLDOWN_SECONDS (default 3)
  - CommandRegistrar skeleton wired via RegisterCommandsEvent
  - EcoCommand and EcoAdminCommand stub classes ready for Plans 02 and 03

affects: [02-02-player-commands, 02-03-admin-commands]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Brigadier CommandDispatcher import is com.mojang.brigadier.CommandDispatcher (not net.minecraft.commands)"
    - "Command stubs register empty dispatcher trees so Plans 02/03 only need to fill in logic"
    - "All new repo methods follow existing try-with-resources + RuntimeException on SQLException pattern"

key-files:
  created:
    - src/main/java/com/pweg0/jbalance/command/CommandRegistrar.java
    - src/main/java/com/pweg0/jbalance/command/EcoCommand.java
    - src/main/java/com/pweg0/jbalance/command/EcoAdminCommand.java
  modified:
    - src/main/java/com/pweg0/jbalance/data/db/BalanceRepository.java
    - src/main/java/com/pweg0/jbalance/service/EconomyService.java
    - src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java
    - src/main/java/com/pweg0/jbalance/JBalance.java

key-decisions:
  - "com.mojang.brigadier.CommandDispatcher is the correct import — net.minecraft.commands.CommandDispatcher does not exist"
  - "findByDisplayName uses LOWER(display_name) = LOWER(?) for SQLite and plain = ? for MySQL (utf8mb4_general_ci is already case-insensitive)"
  - "getPlayerRank uses COUNT(*) WHERE balance > subquery + 1 for the 1-based rank"

patterns-established:
  - "CommandRegistrar pattern: static onRegisterCommands method called via NeoForge.EVENT_BUS.addListener"
  - "Stub pattern: register() methods are empty shells allowing Plans 02/03 to fill in Brigadier command trees"

requirements-completed: [CURR-01, CURR-02, CURR-03, CURR-04, CURR-05, CURR-06, CURR-07]

# Metrics
duration: 9min
completed: 2026-03-19
---

# Phase 02 Plan 01: Data Layer Extension and Command Skeleton Summary

**SQL extensions for top-N ranking, rank lookup, exact-value set, and offline name lookup, plus async wrappers, new TOML config values, and a Brigadier CommandRegistrar skeleton wired into JBalance**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-19T15:19:38Z
- **Completed:** 2026-03-19T15:28:50Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Extended BalanceRepository with 4 new SQL methods and 2 inner records (TopEntry, PlayerRecord)
- Added 4 async wrappers to EconomyService completing the service API surface Plans 02 and 03 need
- Added TOP_CACHE_SECONDS and TRANSFER_COOLDOWN_SECONDS config values to JBalanceConfig
- Created CommandRegistrar, EcoCommand stub, and EcoAdminCommand stub, all wired into JBalance

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend BalanceRepository** - `f4f9cb3` (feat)
2. **Task 2: EconomyService + JBalanceConfig + CommandRegistrar + JBalance wiring** - `6d203af` (feat)

**Plan metadata:** (docs commit — created after self-check)

## Files Created/Modified
- `src/main/java/com/pweg0/jbalance/data/db/BalanceRepository.java` - Added TopEntry, PlayerRecord records; getTopBalances, getPlayerRank, setBalance, findByDisplayName methods
- `src/main/java/com/pweg0/jbalance/service/EconomyService.java` - Added setBalance, getTopBalances, getPlayerRank, findByDisplayName async wrappers
- `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` - Added TOP_CACHE_SECONDS and TRANSFER_COOLDOWN_SECONDS config fields
- `src/main/java/com/pweg0/jbalance/command/CommandRegistrar.java` - Created; central RegisterCommandsEvent listener
- `src/main/java/com/pweg0/jbalance/command/EcoCommand.java` - Created; stub for /eco commands (Plan 02)
- `src/main/java/com/pweg0/jbalance/command/EcoAdminCommand.java` - Created; stub for /ecoadmin commands (Plan 03)
- `src/main/java/com/pweg0/jbalance/JBalance.java` - Added CommandRegistrar event listener wiring

## Decisions Made
- `com.mojang.brigadier.CommandDispatcher` is the correct import in NeoForge 1.21.1 — the plan specified `net.minecraft.commands.CommandDispatcher` which does not exist; auto-fixed during Task 2 (Rule 3 — blocking issue).
- `findByDisplayName` uses dialect-aware SQL: plain `= ?` on MySQL (case-insensitive collation), `LOWER(display_name) = LOWER(?)` on SQLite.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed incorrect CommandDispatcher import**
- **Found during:** Task 2 (command stub creation)
- **Issue:** Plan specified `import net.minecraft.commands.CommandDispatcher` which does not exist in NeoForge 21.1.220 / MC 1.21.1; compiler returned "cannot find symbol" for all 3 command files
- **Fix:** Changed to `import com.mojang.brigadier.CommandDispatcher` (Brigadier is bundled with MC and the correct type for `CommandDispatcher<CommandSourceStack>`)
- **Files modified:** CommandRegistrar.java, EcoCommand.java, EcoAdminCommand.java
- **Verification:** `./gradlew build` exits with BUILD SUCCESSFUL
- **Committed in:** `6d203af` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking import error)
**Impact on plan:** Auto-fix necessary for compilation. No scope creep. The correct Brigadier import is what Plans 02 and 03 will also use for full command implementation.

## Issues Encountered
- None beyond the auto-fixed import issue above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plans 02 and 03 can now implement command logic without any data-layer changes
- EcoCommand.register() and EcoAdminCommand.register() are the only entry points needed
- All service methods, config values, and DB queries are ready
- CommandDispatcher import pattern established for Plans 02 and 03

---
*Phase: 02-currency*
*Completed: 2026-03-19*
