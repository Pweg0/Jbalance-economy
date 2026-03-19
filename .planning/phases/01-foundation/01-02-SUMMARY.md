---
phase: 01-foundation
plan: 02
subsystem: infra
tags: [neoforge, modconfigspec, toml, hot-reload, currency-formatting, java21, pt-br]

# Dependency graph
requires:
  - phase: 01-foundation plan 01
    provides: Compilable @Mod scaffold with ModContainer constructor injection
provides:
  - JBalanceConfig with ModConfigSpec SERVER type and all economy-configurable values (JCoins, J$, 100 starting, 1 min transfer)
  - CurrencyFormatter producing PT-BR standard "J$ 1.500" format for 1500
  - Config registered as SERVER type in JBalance entry point
  - Hot-reload listener subscribed on mod bus via ModConfigEvent.Reloading
affects: [01-03, all-subsequent-phases]

# Tech tracking
tech-stack:
  added:
    - ModConfigSpec.Builder (NeoForge built-in) with SERVER config type
    - java.text.NumberFormat with PT-BR locale for dot-thousands formatting
  patterns:
    - defineInRange for all numeric config values (prevents NeoForge config correction loop Issue #1768)
    - LongValue for currency amounts (STARTING_BALANCE, MIN_TRANSFER)
    - IntValue for DB_PORT, BooleanValue for USE_MYSQL
    - ConfigValue.get() read at call time only — never cached to plain field (supports hot-reload)
    - container.getEventBus() is the correct method in FancyModLoader 4.0.42 / NeoForge 21.1.220

key-files:
  created:
    - src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java
    - src/main/java/com/pweg0/jbalance/util/CurrencyFormatter.java
  modified:
    - src/main/java/com/pweg0/jbalance/JBalance.java

key-decisions:
  - "getEventBus() (not eventBus()) is the correct ModContainer method in FancyModLoader 4.0.42 — verified by decompiling loader-4.0.42.jar with javap; research doc had the method name wrong"
  - "All numeric config values use defineInRange to prevent the NeoForge config infinite correction loop (GitHub Issue #1768)"

patterns-established:
  - "Pattern: ModConfigSpec.Builder with push/pop section grouping for clean TOML structure"
  - "Pattern: CurrencyFormatter reads config values at call time via .get() for automatic hot-reload support"
  - "Pattern: Config registered via container.registerConfig(ModConfig.Type.SERVER, SPEC) in @Mod constructor"
  - "Pattern: ModConfigEvent.Reloading listener checks event.getConfig().getSpec() == SPEC before acting"

requirements-completed: [INFR-04, INFR-05, CURR-10]

# Metrics
duration: 3min
completed: 2026-03-19
---

# Phase 1 Plan 2: TOML Config System and PT-BR Currency Formatter Summary

**ModConfigSpec SERVER config with all economy values (JCoins, J$, 100 starting balance, 1 min transfer), CurrencyFormatter producing "J$ 1.500" via PT-BR locale, and config registration with hot-reload wired into the @Mod entry point**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-19T04:13:23Z
- **Completed:** 2026-03-19T04:16:00Z
- **Tasks:** 2
- **Files modified:** 2 created, 1 modified

## Accomplishments

- JBalanceConfig defines all economy-configurable TOML values with correct ModConfigSpec types (LongValue, IntValue, BooleanValue, ConfigValue<String>) and safe defaults
- CurrencyFormatter produces "J$ 1.500" for 1500 using NumberFormat with PT-BR locale — reads config at call time for hot-reload
- JBalance.java wired with SERVER config registration and ModConfigEvent.Reloading listener on the mod bus

## Task Commits

Each task was committed atomically:

1. **Task 1: Create JBalanceConfig with ModConfigSpec SERVER type and all configurable values** - `71267df` (feat)
2. **Task 2: Create CurrencyFormatter utility and wire config into JBalance entry point** - `35457e2` (feat)

## Files Created/Modified

- `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` - ModConfigSpec with currency (name, symbol, starting_balance, min_transfer) and database (use_mysql, host, port, database, user, password) sections
- `src/main/java/com/pweg0/jbalance/util/CurrencyFormatter.java` - formatBalance(long) using PT-BR NumberFormat; getCurrencyName() for config-driven name
- `src/main/java/com/pweg0/jbalance/JBalance.java` - Added config registration and ModConfigEvent.Reloading subscription

## Decisions Made

- **getEventBus() confirmed as correct method name:** The plan noted uncertainty between `getEventBus()` and `eventBus()`. Decompiled `loader-4.0.42.jar` (FancyModLoader) with `javap` and confirmed the method signature is `public abstract IEventBus getEventBus()`. The research doc's example code was incorrect.
- **All numeric values use defineInRange:** Follows NeoForge anti-pattern guidance from research to prevent the config infinite correction loop (Issue #1768). All numeric types (LongValue, IntValue) use defineInRange.

## Deviations from Plan

None - plan executed exactly as written. The only uncertainty (getEventBus vs eventBus method name) was resolved by inspecting the NeoForge FancyModLoader JAR before writing code — the plan's action block specified `getEventBus()` which is correct.

## Issues Encountered

None - both tasks compiled on the first attempt. `./gradlew build` passed after each task.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Config system is complete and ready for Plan 01-03 (DatabaseManager)
- Plan 01-03 reads `JBalanceConfig.USE_MYSQL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` to configure HikariCP
- `CurrencyFormatter.formatBalance()` is available for all subsequent phases that display balances in chat
- Hot-reload is wired — admins can edit the TOML file and values update immediately without server restart

---
*Phase: 01-foundation*
*Completed: 2026-03-19*
