---
phase: 03-earnings
verified: 2026-03-19T18:00:00Z
status: passed
score: 10/10 must-haves verified
re_verification: false
gaps: []
human_verification:
  - test: "Kill a TOML-listed mob and wait 60 seconds"
    expected: "Player receives chat message: §6[JBalance] §7Voce recebeu §6J$ 10 §7por matar §61 §7mob"
    why_human: "Requires a live server with NeoForge to trigger LivingDeathEvent and observe chat output"
  - test: "Kill a mob spawned by a mob spawner block"
    expected: "No coins credited, no notification sent to player"
    why_human: "Requires in-game spawner placement — spawner tag logic (jbalance_from_spawner) cannot be triggered without running the server"
  - test: "Stay logged in for 1 hour without going AFK"
    expected: "Player receives green milestone notification: §6[JBalance] §aVoce completou 1h de jogo! Recompensa: §6J$ 100"
    why_human: "Requires running server and actual elapsed time — cannot simulate tick accumulation programmatically"
  - test: "Disconnect and reconnect — milestone must not re-trigger"
    expected: "After reconnect, already-claimed milestone hours are not re-granted"
    why_human: "Requires live DB round-trip to verify claimedHours is persisted and re-loaded correctly"
  - test: "Stand still for AFK_TIMEOUT_MINUTES (default 5 min)"
    expected: "Active playtime counter stops incrementing; no milestone progress"
    why_human: "AFK detection logic reads position/rotation each tick — requires running server to validate the float comparison behavior"
---

# Phase 3: Earnings Verification Report

**Phase Goal:** Players passively earn coins by killing mobs and reaching playtime milestones, with configurable reward rates and persistent milestone tracking that survives disconnects.
**Verified:** 2026-03-19T18:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | When a player kills a mob listed in TOML config, they are credited the configured coin amount | VERIFIED | `EarningsEventHandler.onLivingDeath` reads `JBalanceConfig.parsedMobRewards()`, looks up the entity type key, calls `EconomyService.getInstance().give(uuid, coins)` via `accumulateKill` + `onServerTick` flush |
| 2 | Mobs NOT in the TOML config list yield zero reward (no default fallback) | VERIFIED | `Long reward = rewards.get(typeKey); if (reward == null \|\| reward <= 0) return;` — explicit null guard, no default fallback path |
| 3 | Mobs spawned from mob spawner blocks yield zero reward (tagged at spawn time) | VERIFIED | `onFinalizeSpawn` tags with `putBoolean("jbalance_from_spawner", true)` on `MobSpawnType.SPAWNER`; `onLivingDeath` guards with `getBoolean("jbalance_from_spawner")` |
| 4 | Kill notifications are batched every kill_notification_interval seconds, not per-kill | VERIFIED | `flushTickCounter >= intervalSeconds * 20` in `onServerTick`; `pendingCoins.merge` accumulates across ticks |
| 5 | The batched notification shows total coins earned and total mob count in PT-BR | VERIFIED | `"§6[JBalance] §7Voce recebeu §6" + CurrencyFormatter.formatBalance(coins) + " §7por matar §6" + killCount + " §7mobs"` |
| 6 | All mob reward values and notification interval are configurable in TOML | VERIFIED | `MOB_KILL_REWARDS` (defineListAllowEmpty, 8 defaults) and `KILL_NOTIFICATION_INTERVAL` (defineInRange, default 60s) both present in `JBalanceConfig` |
| 7 | When a player's cumulative active playtime crosses a milestone threshold, they receive the configured coin reward exactly once | VERIFIED | `state.claimedHours.contains(milestone.hours())` guard in `PlaytimeService.onTick`; `claimedHours.add()` happens before `give()` to prevent double-award |
| 8 | Milestone rewards are never granted twice for the same threshold, even after disconnect/reconnect or server restart | VERIFIED | `onLogout` flushes `claimedHours` to DB; `onLogin` reloads from DB into `state.claimedHours` set; in-memory set is authoritative during session |
| 9 | AFK players (no position/look change for AFK_TIMEOUT_MINUTES) do NOT accumulate playtime | VERIFIED | `ticksSinceLastMove >= afkTimeoutTicks` early return in `PlaytimeService.onTick`; position AND rotation (getX, getY, getZ, getYRot, getXRot) all compared |
| 10 | Playtime progress is flushed to DB on player logout and server shutdown | VERIFIED | `onLogout` calls `repo.upsertPlaytime` async; `flushAll()` called in `JBalance.onServerStopping` via `playtimeService.flushAll()` with `CompletableFuture.allOf(...).join()` for synchronous shutdown flush |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` | TOML earnings.mob_kills section with reward list and notification interval | VERIFIED | `MOB_KILL_REWARDS` (defineListAllowEmpty, 8 mobs), `KILL_NOTIFICATION_INTERVAL` (defineInRange 60L), `parsedMobRewards()` helper present |
| `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` | TOML earnings.milestones section with milestone list and AFK timeout | VERIFIED | `MILESTONES` (defineListAllowEmpty, 5 thresholds), `AFK_TIMEOUT_MINUTES` (defineInRange 5L), `MilestoneEntry` record, `parsedMilestones()` helper present |
| `src/main/java/com/pweg0/jbalance/event/EarningsEventHandler.java` | LivingDeathEvent handler, FinalizeSpawn spawner tagger, kill accumulator, batch notifier | VERIFIED | All 6 static handlers present: `onFinalizeSpawn`, `onLivingDeath`, `onServerTick`, `onPlayerTick`, `onPlayerLoggedIn`, `onPlayerLoggedOut` |
| `src/main/java/com/pweg0/jbalance/service/PlaytimeService.java` | In-memory playtime tracker with AFK detection, milestone checking, periodic flush | VERIFIED | `onLogin`, `onLogout`, `onTick`, `onServerTick`, `flushAll` all substantively implemented; AFK detection via `ticksSinceLastMove`; milestone deduplicated via `claimedHours` set |
| `src/main/java/com/pweg0/jbalance/data/db/PlaytimeRepository.java` | CRUD for jbalance_playtime table (load, upsert) | VERIFIED | `loadPlaytime` (null-safe) and `upsertPlaytime` (MySQL ON DUPLICATE KEY / SQLite INSERT OR REPLACE) both present and substantive |
| `src/main/resources/schema/schema_mysql.sql` | jbalance_playtime table definition for MySQL | VERIFIED | `CREATE TABLE IF NOT EXISTS jbalance_playtime` with `active_seconds BIGINT`, `claimed_hours TEXT`, InnoDB engine |
| `src/main/resources/schema/schema_sqlite.sql` | jbalance_playtime table definition for SQLite | VERIFIED | `CREATE TABLE IF NOT EXISTS jbalance_playtime` with `active_seconds INTEGER`, `claimed_hours TEXT` |
| `src/main/java/com/pweg0/jbalance/JBalance.java` | Event bus registration for all EarningsEventHandler listeners | VERIFIED | 6 `addListener` calls: `onFinalizeSpawn`, `onLivingDeath`, `onServerTick`, `onPlayerTick`, `onPlayerLoggedIn`, `onPlayerLoggedOut` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `EarningsEventHandler.onLivingDeath` | `JBalanceConfig.parsedMobRewards()` | reward lookup by entity type key | WIRED | Line 64: `Map<String, Long> rewards = JBalanceConfig.parsedMobRewards();` |
| `EarningsEventHandler.onLivingDeath` | `EconomyService.getInstance().give()` | async coin credit via kill accumulator flush in onServerTick | WIRED | `accumulateKill` -> `onServerTick` flush -> `EconomyService.getInstance().give(uuid, coins)` at line 119 |
| `EarningsEventHandler.onFinalizeSpawn` | `EarningsEventHandler.onLivingDeath` | jbalance_from_spawner persistent data tag | WIRED | `putBoolean("jbalance_from_spawner", true)` at spawn; `getBoolean("jbalance_from_spawner")` guard at kill |
| `JBalance constructor` | `EarningsEventHandler` | `NeoForge.EVENT_BUS.addListener` | WIRED | Lines 45-50 in JBalance.java register all 6 EarningsEventHandler methods |
| `EarningsEventHandler.onPlayerTick` | `PlaytimeService.getInstance().onTick()` | per-tick delegation for active player | WIRED | `PlaytimeService.getInstance().onTick(player)` at EarningsEventHandler line 135 |
| `PlaytimeService.onTick` | `EconomyService.getInstance().give()` | milestone reward credit | WIRED | `EconomyService.getInstance().give(uuid, milestone.reward())` at PlaytimeService line 184 |
| `PlaytimeService.onTick` | `PlaytimeRepository.upsertPlaytime()` | periodic async DB flush via onServerTick | WIRED | `repo.upsertPlaytime(uuid, activeSeconds, claimedStr)` in `onServerTick` and `onLogout` |
| `JBalance.onServerAboutToStart` | `PlaytimeService constructor` | singleton initialization with PlaytimeRepository | WIRED | `new PlaytimeRepository(...)` then `new PlaytimeService(playtimeRepo, ...)` at JBalance lines 62-67 |
| `DatabaseManager.runMigrations()` | multi-statement schema split | `schema.split(";")` loop | WIRED | `for (String sql : schema.split(";"))` at DatabaseManager line 71 |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| EARN-01 | 03-01 | Player earns configurable coins per mob killed | SATISFIED | `onLivingDeath` reads `parsedMobRewards()` and calls `accumulateKill` -> `give()` |
| EARN-02 | 03-01 | Different mob types can have different reward values (configured in TOML) | SATISFIED | `MOB_KILL_REWARDS` list with 8 distinct mob-to-reward mappings; `parsedMobRewards()` returns `Map<String, Long>` keyed by entity type |
| EARN-03 | 03-02 | Player earns coins upon reaching playtime milestones (1h, 2h, 5h, etc — configurable) | SATISFIED | `PlaytimeService.onTick` checks `parsedMilestones()` against `activeSeconds`, grants reward and sends notification on threshold crossing |
| EARN-04 | 03-02 | Playtime milestones are per-player and persist across sessions | SATISFIED | `claimedHours` set flushed to DB via `upsertPlaytime` on logout and shutdown; reloaded via `loadPlaytime` on login; `jbalance_playtime` table with PRIMARY KEY uuid |
| EARN-05 | 03-01, 03-02 | Player receives chat message when earning coins from any source | SATISFIED | Kill earnings: batched notification via `sendSystemMessage` in `onServerTick`; Milestone: `sendSystemMessage` in `PlaytimeService.onTick` on each milestone claim |

**All 5 requirements EARN-01 through EARN-05 are satisfied. No orphaned requirements for Phase 3.**

### Anti-Patterns Found

None detected. Scanned all 7 modified/created files for TODO/FIXME/placeholder comments, empty return stubs, and console-log-only implementations.

### Human Verification Required

#### 1. Live Mob Kill Reward

**Test:** Kill a TOML-listed mob (e.g. zombie) and wait 60 seconds.
**Expected:** Player receives: `§6[JBalance] §7Voce recebeu §6J$ 10 §7por matar §61 §7mobs`
**Why human:** Requires a running NeoForge server to trigger `LivingDeathEvent` and observe the batched chat output.

#### 2. Spawner Mob Exclusion

**Test:** Kill a mob that was spawned by a mob spawner block.
**Expected:** No coins credited and no notification sent.
**Why human:** Requires in-game spawner block — `FinalizeSpawnEvent` with `MobSpawnType.SPAWNER` cannot be triggered without a running server.

#### 3. Playtime Milestone Grant

**Test:** Stay logged in for 1 cumulative hour of active movement.
**Expected:** Player receives: `§6[JBalance] §aVoce completou 1h de jogo! Recompensa: §6J$ 100`
**Why human:** Requires elapsed real game time — cannot simulate `activeTicks` accumulation at 20 ticks/sec programmatically.

#### 4. Milestone Persistence After Reconnect

**Test:** Earn the 1h milestone, disconnect, reconnect, and wait past 1h mark again.
**Expected:** Milestone is NOT re-granted after reconnect.
**Why human:** Requires a live DB round-trip to confirm `claimedHours` is persisted and re-loaded correctly, preventing double-award.

#### 5. AFK Detection

**Test:** Log in, stand still for 5 minutes (default AFK timeout), then check if playtime progress stops.
**Expected:** Active playtime counter does not advance while AFK; resumes on movement.
**Why human:** AFK detection relies on floating-point position/rotation comparison each tick — requires running server to observe actual behavior (e.g., floating-point jitter from server physics).

### Gaps Summary

No gaps. All automated checks passed: all artifacts exist and are substantively implemented, all key links are wired, all 5 requirements are satisfied. 5 items flagged for human verification due to behavioral runtime dependencies (in-game events, elapsed time, DB round-trips).

---

_Verified: 2026-03-19T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
