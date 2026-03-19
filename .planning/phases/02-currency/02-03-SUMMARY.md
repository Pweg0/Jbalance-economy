---
phase: 02-currency
plan: 03
subsystem: commands
tags: [brigadier, admin-commands, offline-player-resolution, neoforge, minecraft]

# Dependency graph
requires:
  - phase: 02-currency-01
    provides: EconomyService.give/take/setBalance/findByDisplayName, BalanceRepository.PlayerRecord
  - phase: 02-currency-02
    provides: EcoCommand pattern for Brigadier async command structure
provides:
  - /ecoadmin give <player> <amount> with offline player resolution
  - /ecoadmin take <player> <amount> with insufficient funds guard
  - /ecoadmin set <player> <amount> with exact balance override
  - OP level 4 permission gate hiding /ecoadmin from non-admins
  - StringArgumentType offline name resolution via findByDisplayName
  - Tab-complete listing online players via SharedSuggestionProvider
affects: [03-shop, future admin tooling phases]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Offline player resolution via StringArgumentType.word() + BalanceRepository.findByDisplayName()"
    - "Admin-only feedback: target never notified (sendSuccess to src only)"
    - "(jogador offline) tag via PlayerList.getPlayer(uuid) null check"
    - "LongArgumentType.longArg(0) for set (allows zero), longArg(1) for give/take (minimum 1)"

key-files:
  created: []
  modified:
    - src/main/java/com/pweg0/jbalance/command/EcoAdminCommand.java

key-decisions:
  - "StringArgumentType.word() used instead of EntityArgument to support offline player names typed manually"
  - "SharedSuggestionProvider.suggest(server.getPlayerNames(), builder) for tab-complete (online only, per locked decision)"
  - "/ecoadmin set uses longArg(0) to allow zeroing a balance; give and take use longArg(1) to require minimum 1 coin"
  - "Target player is NEVER notified of admin changes (locked decision confirmed)"

patterns-established:
  - "Async admin command pattern: findByDisplayName -> check null -> online tag -> service call -> sendSuccess"
  - "All async callbacks re-enter game thread via src.getServer().execute() before touching MC API"

requirements-completed: [CURR-05, CURR-06, CURR-07]

# Metrics
duration: 4min
completed: 2026-03-19
---

# Phase 02 Plan 03: EcoAdmin Commands Summary

**Brigadier /ecoadmin give/take/set with OP-4 gating, StringArgumentType offline player resolution, and PT-BR admin-only feedback**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-03-19T15:40:15Z
- **Completed:** 2026-03-19T15:44:06Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Replaced EcoAdminCommand stub with full three-subcommand implementation (give, take, set)
- Offline player resolution via `EconomyService.findByDisplayName()` using StringArgumentType — works even when target is not online
- Tab-complete lists online players via `SharedSuggestionProvider.suggest(server.getPlayerNames(), builder)`
- OP level 4 permission gate via `.requires(src -> src.hasPermission(4))` — /ecoadmin hidden from non-admins entirely
- `(jogador offline)` tag appended to confirmation when target is not on the server
- Insufficient funds guard on `take` command returns PT-BR error without crashing
- All async DB callbacks re-enter game thread via `server.execute()` before touching any Minecraft API

## Task Commits

1. **Task 1: Implement /ecoadmin give, take, set with offline player resolution and tab-complete** - `032b950` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/java/com/pweg0/jbalance/command/EcoAdminCommand.java` - Full admin command implementation replacing stub (179 lines)

## Decisions Made

- `StringArgumentType.word()` chosen over `EntityArgument.player()` to support offline player names typed manually — EntityArgument only resolves currently online players
- `/ecoadmin set` uses `longArg(0)` (allows zeroing a balance) while `give`/`take` use `longArg(1)` (must transfer at least 1 coin)
- Tab-complete suggests online players only per locked project decision — offline resolution still works when name is typed manually
- Target player is never notified — only the admin issuing the command sees feedback (locked decision confirmed from plan context)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All six economy commands (/eco balance, /eco pay, /eco top, /ecoadmin give, /ecoadmin take, /ecoadmin set) are fully implemented and build successfully
- Phase 02 (currency) is complete — ready for Phase 03 (shop)
- EconomyService interface is stable and proven in production patterns; shop phase can call give/take/getBalance safely

---
*Phase: 02-currency*
*Completed: 2026-03-19*
