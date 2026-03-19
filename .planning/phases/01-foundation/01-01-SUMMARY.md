---
phase: 01-foundation
plan: 01
subsystem: infra
tags: [neoforge, moddevgradle, gradle, hikaricp, mysql, sqlite, jarjar, java21]

# Dependency graph
requires: []
provides:
  - NeoForge 1.21.1 mod scaffold with ModDevGradle 2.0.107
  - Gradle 8.12 wrapper with build configuration
  - jarJar bundled dependencies: HikariCP 7.0.2, MySQL Connector/J 9.6.0, sqlite-jdbc 3.51.3.0
  - "@Mod entry point: com.pweg0.jbalance.JBalance"
  - neoforge.mods.toml with modId=jbalance, license, and NeoForge/MC dependencies
  - Compilable project foundation for all subsequent plans
affects: [01-02, 01-03, all-subsequent-phases]

# Tech tracking
tech-stack:
  added:
    - ModDevGradle 2.0.107 (NeoForge build plugin)
    - NeoForge 21.1.220
    - HikariCP 7.0.2 (jarJar bundled)
    - mysql-connector-j 9.6.0 (jarJar bundled)
    - sqlite-jdbc 3.51.3.0 (jarJar bundled)
    - Gradle 8.12 wrapper
    - Parchment mappings 2024.11.17 for MC 1.21.1
  patterns:
    - jarJar dependency bundling with version ranges and prefer constraints
    - additionalRuntimeClasspath for dev-run classpath availability
    - resolutionStrategy.force to resolve transitive dependency conflicts with NeoForge
    - @Mod constructor injection (ModContainer) as NeoForge mod entry point

key-files:
  created:
    - build.gradle
    - settings.gradle
    - gradle.properties
    - gradlew
    - gradlew.bat
    - gradle/wrapper/gradle-wrapper.jar
    - gradle/wrapper/gradle-wrapper.properties
    - src/main/java/com/pweg0/jbalance/JBalance.java
    - src/main/resources/META-INF/neoforge.mods.toml
    - .gitignore
  modified: []

key-decisions:
  - "slf4j-api forced to 2.0.9 to resolve conflict between HikariCP 7.0.2 (wants 2.0.17) and NeoForge 21.1.220 (strictly 2.0.9) — HikariCP is runtime-compatible with 2.0.9"
  - "Gradle 8.12 wrapper created manually (Gradle not installed globally) using curl to download gradle-wrapper.jar from GitHub"
  - "ModDevGradle 2.0.107 selected as specified in RESEARCH.md as the recommended plugin for new single-version NeoForge 1.21.1 mods"

patterns-established:
  - "Pattern: jarJar with strictly range + prefer for all bundled DB libraries"
  - "Pattern: additionalRuntimeClasspath mirrors jarJar deps for dev runs"
  - "Pattern: @Mod class receives ModContainer in constructor for NeoForge 1.21.1"

requirements-completed: [INFR-01]

# Metrics
duration: 12min
completed: 2026-03-19
---

# Phase 1 Plan 1: NeoForge Mod Scaffold Summary

**ModDevGradle 2.0.107 scaffold with jarJar-bundled HikariCP 7.0.2 / MySQL Connector/J 9.6.0 / sqlite-jdbc 3.51.3.0 and a compilable @Mod entry point for NeoForge 1.21.1**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-19T03:57:27Z
- **Completed:** 2026-03-19T04:09:25Z
- **Tasks:** 2
- **Files modified:** 10 created, 1 modified (build.gradle)

## Accomplishments

- Complete Gradle 8.12 build system with ModDevGradle 2.0.107, NeoForge 21.1.220 configured, jarJar bundling for all three database libraries
- Minimal `@Mod("jbalance")` entry point (`JBalance.java`) compiling cleanly against NeoForge 1.21.1 API
- Valid `neoforge.mods.toml` with modId, license, and version-range dependencies on NeoForge and Minecraft

## Task Commits

Each task was committed atomically:

1. **Task 1: Gradle build system with ModDevGradle 2.0.x and jarJar dependencies** - `761809f` (chore)
2. **Task 1 followup: .gitignore** - `7cc10ef` (chore)
3. **Task 2: @Mod entry point and neoforge.mods.toml metadata** - `fc62032` (feat)

## Files Created/Modified

- `build.gradle` - ModDevGradle 2.0.107 plugin, NeoForge config, jarJar deps for HikariCP/MySQL/SQLite, slf4j resolution fix
- `settings.gradle` - Plugin management with NeoForge maven repository
- `gradle.properties` - Version constants: neo_version=21.1.220, mod_id=jbalance, mod_version=1.21.1-1.0.0
- `gradlew` / `gradlew.bat` - Gradle 8.12 wrapper scripts
- `gradle/wrapper/gradle-wrapper.jar` - Wrapper bootstrap (downloaded from GitHub)
- `gradle/wrapper/gradle-wrapper.properties` - Points to Gradle 8.12 distribution
- `src/main/java/com/pweg0/jbalance/JBalance.java` - @Mod entry point with Logger and ModContainer constructor
- `src/main/resources/META-INF/neoforge.mods.toml` - Mod metadata: modId=jbalance, NeoForge 21.1.220+, MC 1.21.1
- `.gitignore` - Ignores .gradle/, build/, runs/

## Decisions Made

- **slf4j-api forced to 2.0.9:** HikariCP 7.0.2 requests slf4j 2.0.17 but NeoForge 21.1.220 has a strict constraint on 2.0.9. Since NeoForge bundles and provides slf4j at runtime, forcing 2.0.9 at compile time is correct — HikariCP is binary-compatible with the older minor version.
- **Gradle wrapper created manually:** Gradle is not installed in the environment. The wrapper was created by downloading `gradle-wrapper.jar` via curl from GitHub and writing the properties file and shell scripts manually.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed slf4j version conflict preventing compilation**
- **Found during:** Task 2 (compileJava during build verification)
- **Issue:** HikariCP 7.0.2 transitively requires `org.slf4j:slf4j-api:2.0.17`, but NeoForge 21.1.220 uses a strict constraint of `2.0.9`. Gradle could not resolve both simultaneously, causing `BUILD FAILED`.
- **Fix:** Added `configurations.configureEach { resolutionStrategy.force 'org.slf4j:slf4j-api:2.0.9' }` to `build.gradle`. NeoForge bundles slf4j at runtime so 2.0.9 is the correct version to enforce.
- **Files modified:** `build.gradle`
- **Verification:** `./gradlew build` exits with code 0
- **Committed in:** `fc62032` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - transitive dependency conflict)
**Impact on plan:** Essential fix for compilation. No scope creep.

## Issues Encountered

- Gradle not installed globally — created wrapper files manually (wrapper jar downloaded via curl, gradlew shell script written from scratch). Build succeeded after wrapper was functional.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Project compiles with `./gradlew build` — foundation for Plan 01-02 (DatabaseManager) is ready
- All three database libraries are jarJar-bundled and available on the classpath for dev runs via `additionalRuntimeClasspath`
- `JBalance.java` entry point is intentionally minimal — Plans 01-02 and 01-03 will add config registration, event bus subscriptions, and database initialization

---
*Phase: 01-foundation*
*Completed: 2026-03-19*

## Self-Check: PASSED

- build.gradle: FOUND
- settings.gradle: FOUND
- gradle.properties: FOUND
- src/main/java/com/pweg0/jbalance/JBalance.java: FOUND
- src/main/resources/META-INF/neoforge.mods.toml: FOUND
- .planning/phases/01-foundation/01-01-SUMMARY.md: FOUND
- Commit 761809f: FOUND
- Commit 7cc10ef: FOUND
- Commit fc62032: FOUND
