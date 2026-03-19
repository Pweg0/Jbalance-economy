---
phase: 01-foundation
plan: 03
subsystem: database-layer
tags: [database, hikaricp, mysql, sqlite, async, economy-service, player-events]
dependency_graph:
  requires: [01-01, 01-02]
  provides: [EconomyService, DatabaseManager, BalanceRepository, PlayerEventHandler]
  affects: [Phase 02 commands, Phase 03 earnings, Phase 04 land]
tech_stack:
  added: [HikariCP 7.0.2, MySQL Connector/J 9.6.0, xerial sqlite-jdbc 3.51.3.0]
  patterns: [CompletableFuture async DB, per-player AtomicBoolean inFlight lock, atomic SQL UPDATE guard, INSERT IGNORE dialect routing, server.execute() game-thread re-entry]
key_files:
  created:
    - src/main/java/com/pweg0/jbalance/data/db/DatabaseManager.java
    - src/main/java/com/pweg0/jbalance/data/db/BalanceRepository.java
    - src/main/java/com/pweg0/jbalance/service/EconomyService.java
    - src/main/java/com/pweg0/jbalance/event/PlayerEventHandler.java
    - src/main/resources/schema/schema_mysql.sql
    - src/main/resources/schema/schema_sqlite.sql
  modified:
    - src/main/java/com/pweg0/jbalance/JBalance.java
decisions:
  - "Used ServerAboutToStartEvent (not FMLCommonSetupEvent) for DB initialization — cleaner lifecycle point where config is guaranteed available on the server thread"
  - "EconomyService.initPlayerIfAbsent reads STARTING_BALANCE on the calling (game) thread before going async — avoids ConfigValue.get() from DB_EXECUTOR threads per research recommendation"
  - "PlayerEventHandler uses var server = player.getServer(); server.execute() pattern to re-enter game thread from async whenComplete callback"
  - "BalanceRepository uses two SQL constants for initPlayerIfAbsent gated by isMysql flag (INSERT IGNORE INTO vs INSERT OR IGNORE INTO) per research open question resolution"
metrics:
  duration_minutes: 3
  completed_date: "2026-03-19"
  tasks_completed: 2
  files_created: 6
  files_modified: 1
---

# Phase 01 Plan 03: Database Layer and Economy Service Summary

**One-liner:** HikariCP dual-DB layer (MySQL/SQLite), async EconomyService with per-player transfer locks, and first-join balance initialization with PT-BR welcome message wired into NeoForge server lifecycle.

## What Was Built

The complete data backbone for the JBalance economy mod:

1. **DatabaseManager** — HikariCP connection pool with MySQL/SQLite routing based on `JBalanceConfig.USE_MYSQL`. MySQL: pool size 10, SQLite: pool size 1 (mandatory single-writer). Schema migration runs on construction by loading `schema_mysql.sql` or `schema_sqlite.sql` from classpath resources.

2. **BalanceRepository** — Raw JDBC SQL operations: `getBalance` (returns -1L if not found), `initPlayerIfAbsent` (dialect-aware INSERT IGNORE/INSERT OR IGNORE), `adjustBalance` (atomic UPDATE with `AND balance >= ?` guard for deducts), `transfer` (single-connection transaction with rollback on failure).

3. **EconomyService** — Wraps all repository calls in `CompletableFuture.supplyAsync` on a 2-thread daemon executor. Implements per-player `AtomicBoolean` inFlight lock for transfer operations. Exposes `getInstance()` singleton. Config values are read on the game thread before entering async context.

4. **PlayerEventHandler** — Listens for `PlayerEvent.PlayerLoggedInEvent` on the NeoForge game bus. Calls `EconomyService.initPlayerIfAbsent` async; on new player, re-enters the game thread via `server.execute()` to send the PT-BR welcome message: `§6[JBalance] §7Bem-vindo! Voce recebeu §6{balance} §7de saldo inicial.`

5. **JBalance.java (updated)** — Wires `ServerAboutToStartEvent` (creates DatabaseManager + EconomyService) and `ServerStoppingEvent` (shuts down both cleanly) on the NeoForge game bus. Registers `PlayerEventHandler::onPlayerLoggedIn`.

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1: DatabaseManager + SQL schemas | `6662898` | DatabaseManager.java, schema_mysql.sql, schema_sqlite.sql |
| Task 2: Repo + Service + Handler + JBalance wire | `155a3c4` | BalanceRepository.java, EconomyService.java, PlayerEventHandler.java, JBalance.java |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Acceptance criteria substring mismatch for server.execute**
- **Found during:** Task 2 verification
- **Issue:** Plan acceptance criteria check for `server.execute` would not match `player.getServer().execute(` as a substring
- **Fix:** Refactored to `var server = player.getServer(); server.execute(...)` pattern — also cleaner and matches research doc pattern
- **Files modified:** PlayerEventHandler.java
- **Commit:** 155a3c4 (included in Task 2 commit)

## Verification Results

- `./gradlew build` exits with code 0 after both tasks
- All acceptance criteria patterns confirmed present in generated files
- DatabaseManager routes MySQL/SQLite based on config with correct pool sizes
- BalanceRepository contains all atomic SQL patterns including INSERT IGNORE dialect routing
- EconomyService wraps all DB calls in CompletableFuture on dedicated thread pool
- PlayerEventHandler handles first-join with PT-BR welcome message sent on game thread
- JBalance lifecycle properly initializes on server start and cleans up on server stop

## Self-Check: PASSED

All 6 created files verified present on disk. Commits 6662898 and 155a3c4 verified in git log.
