# Stack Research

**Domain:** NeoForge 1.21.1 economy/server mod (Java)
**Researched:** 2026-03-19
**Confidence:** HIGH (build tools, config, events); MEDIUM (database bundling, Votifier); LOW (FTB Chunks API details)

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java (JDK) | 21 (LTS) | Mod language | Minecraft 1.21.1 ships Java 21 to end users; JDK 21 is the mandatory target per NeoForge docs |
| NeoForge | 21.1.220 | Mod loader API | The specified platform — all hooks, events, config, networking, and GUI live here |
| ModDevGradle | 2.0.x (latest) | Gradle plugin | Stable since Jan 2025; simpler and faster than NeoGradle for single-version mods; official recommendation for new projects |
| Gradle | 8.x (bundled by MDK) | Build system | NeoForge's only supported build system; version is pinned by the Gradle wrapper in the MDK template |
| Parchment mappings | 2024.11.17 for MC 1.21.1 | Human-readable param names | Adds crowdsourced parameter names to Minecraft internals — eliminates `p_1234_` names in dev; zero runtime cost |

### Database Libraries

| Library | Version | Purpose | Why Recommended |
|---------|---------|---------|-----------------|
| HikariCP | 7.0.2 | JDBC connection pool | Industry standard; Java 11+ compatible (Java 21 supported); minimal overhead; used widely in Minecraft mod/plugin ecosystem |
| MySQL Connector/J | 9.6.0 | MySQL/MariaDB JDBC driver | Official Oracle driver; compatible with MySQL 5.7+ and MariaDB; groupId changed to `com.mysql` in 8.x — use that, not the legacy `mysql:mysql-connector-java` |
| xerial sqlite-jdbc | 3.51.3.0 | SQLite JDBC driver + native libs | Bundles native SQLite binaries for Windows/Linux/macOS in one JAR; no separate install; follows SQLite version numbering |

All three database libraries must be bundled via NeoForge's **jarJar** system (see Installation section). They are not available at runtime otherwise.

### Config System

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| NeoForge ModConfigSpec | Built into NeoForge 21.1.x | TOML config definition | Native NeoForge config API; produces `.toml` files server admins recognize; SERVER type syncs to clients automatically; no extra dependency |
| NightConfig (via NeoForge) | Bundled with NeoForge | TOML parsing | Used internally by NeoForge's config system; not accessed directly — interact through ModConfigSpec.Builder only |

Use `ModConfigSpec.Builder` with **SERVER type** for all economy values. SERVER configs are loaded before `ServerAboutToStartEvent` and synced to clients — correct for per-world economy settings.

### Networking

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| NeoForge CustomPacketPayload | Built into NeoForge 21.1.x | Client-server packets | The new NeoForge standard since 1.20.4; `SimpleChannel` is gone — do not use it; payloads are `record` classes implementing `CustomPacketPayload`, registered via `RegisterPayloadHandlersEvent` |

For JBalance, custom packets are only needed if GUIs (shop, auction screens) need server data. The core economy (balance, pay, top commands) is purely server-side and needs no custom packets.

### GUI

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| NeoForge AbstractContainerScreen | Built into NeoForge 21.1.x | Inventory-style GUIs | Standard pattern for slot-based screens (shop, auction); pair with `AbstractContainerMenu` server-side and `AbstractContainerScreen` client-side; `MenuType` registered via `DeferredRegister` |
| Vanilla chat/text components | Built into Minecraft 1.21.1 | Command feedback, text displays | `Component.translatable()` / `Component.literal()` for all player-facing messages; no separate GUI library needed for command responses |

The shop and auction systems will need container screens. Simpler displays (balance, top) use only chat components — no GUI framework required.

### Voting Integration

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Custom TCP socket listener | N/A | Receive Votifier v1 vote callbacks | Votifier/NuVotifier uses a simple TCP socket protocol; implement a dedicated listener thread that accepts connections and parses the RSA-encrypted vote packet — this is the standard pattern |
| "Votifier for Neoforge" mod | 1.1 for NeoForge 1.21.1 | Alternative: use as optional dependency | Exists on Modrinth; provides NeoForge events for received votes; reduces implementation effort if the server already runs it — treat as `optional` dependency |

Decision point: If the server will always have "Votifier for Neoforge" installed, depend on it optionally and fire on its events. If not, implement the raw socket listener. The raw socket approach has zero external dependencies. See PITFALLS.md for threading caution.

### FTB Chunks Integration

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| FTB Chunks NeoForge | 2101.1.14 (latest for 1.21.1) | Chunk claim events for payment integration | FTB Chunks exposes `ChunksUpdatedFromServerEvent` for client-side tracking; server-side claim counting requires listening to NeoForge events or querying FTB Chunks' API directly |

**Confidence: LOW** — FTB Chunks' public API surface for server-side integration (charging per claim) is not well-documented. The implementation may require reading their source code. Mark as needing phase-specific research.

---

## Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SLF4J (via NeoForge) | Bundled | Logging | Use `LogUtils.getLogger()` from NeoForge — wraps Log4j2; never instantiate your own logger |
| Gson (via Minecraft) | Bundled with MC | JSON serialization | Available on classpath from Minecraft; use for any JSON if needed (API responses, shop data serialization) |
| Minecraft NBT API | Built-in | Player data in world saves | Alternative to DB for simple persistent data — not recommended for this project since MySQL/SQLite are explicit requirements |

---

## Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| IntelliJ IDEA | IDE | Best Gradle + Java support; NeoForge MDK generates run configs automatically on import |
| NeoForge MDK template (ModDevGradle) | Project scaffold | Clone from `github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle` — not the NeoGradle variant |
| Parchment mappings | Dev-time param names | Configure in `gradle.properties`: `parchment.mappingsVersion=2024.11.17` and `parchment.minecraftVersion=1.21.1` |
| gradlew genSources | Source decompilation | Run once after setup; downloads and decompiles Minecraft source for IDE navigation |
| gradlew runServer | Local test server | Starts a dev server with the mod loaded; use this for all economy/DB testing |

---

## Installation

```gradle
// build.gradle — dependency configuration for JBalance

dependencies {
    // NeoForge (already present in MDK)
    implementation "net.neoforged:neoforge:21.1.220"

    // Database: bundle all three via jarJar
    jarJar(implementation("com.zaxxer:HikariCP")) {
        version {
            strictly "[5.0,8.0)"
            prefer "7.0.2"
        }
    }
    jarJar(implementation("com.mysql:mysql-connector-j")) {
        version {
            strictly "[9.0,10.0)"
            prefer "9.6.0"
        }
    }
    jarJar(implementation("org.xerial:sqlite-jdbc")) {
        version {
            strictly "[3.40,4.0)"
            prefer "3.51.3.0"
        }
    }

    // Also add to additionalRuntimeClasspath so dev runs can find the classes
    additionalRuntimeClasspath "com.zaxxer:HikariCP:7.0.2"
    additionalRuntimeClasspath "com.mysql:mysql-connector-j:9.6.0"
    additionalRuntimeClasspath "org.xerial:sqlite-jdbc:3.51.3.0"
}
```

```properties
# gradle.properties — key version pins
neo_version=21.1.220
minecraft_version=1.21.1
parchment_mappings_version=2024.11.17
parchment_minecraft_version=1.21.1
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| ModDevGradle 2.0.x | NeoGradle 7.x | Only if supporting multiple Minecraft versions in one build; unnecessary complexity for a single-version mod |
| HikariCP + raw JDBC | jOOQ | If SQL complexity grows; overkill for this project's simple CRUD patterns |
| HikariCP + raw JDBC | Hibernate/JPA | Never in a Minecraft mod — too heavy, too many classpath conflicts with Minecraft's bundled libraries |
| MySQL Connector/J 9.x (`com.mysql` groupId) | Legacy `mysql:mysql-connector-java` | Never — the old groupId is abandoned at 8.0.33; new releases are on `com.mysql` only |
| NeoForge SERVER config type | COMMON config type | Only if values must be shared without server sync; SERVER is correct for economy balancing values |
| Custom Votifier socket | VotifierNext mod | VotifierNext targets 1.21.8+ only, not 1.21.1 |
| Raw JDBC (HikariCP) | EasyDB / JDBI | Reasonable alternative for JDBI's fluent API; adds another jarJar dependency; not necessary for this scope |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `SimpleChannel` (Forge legacy) | Removed in NeoForge; will not compile | `CustomPacketPayload` + `RegisterPayloadHandlersEvent` |
| `mysql:mysql-connector-java` (old groupId) | Abandoned; last release 8.0.33 (2023); no security updates | `com.mysql:mysql-connector-j:9.6.0` |
| Hibernate / JPA | Classpath conflicts with Minecraft's bundled libraries; startup time penalty; overkill | Raw JDBC with HikariCP |
| STARTUP config type | Cannot be safely used for feature toggles; causes client-server desyncs | SERVER type for all economy config values |
| Kotlin | Explicitly out of scope per PROJECT.md; adds runtime dependency overhead | Java 21 |
| LuckPerms API | Explicitly out of scope per PROJECT.md; adds dependency | NeoForge OP level checks (`player.hasPermissions(level)`) |
| `TickEvent.ServerTickEvent` for playtime tracking | Fires every tick (20/second); excessive DB writes if used naively | `PlayerTickEvent.Post` with tick counter accumulated in memory, flushed to DB on milestones |
| Shadow / ShadowJar plugin | NeoForge's jarJar achieves the same goal with proper class deduplication across mods | `jarJar` configuration in ModDevGradle |

---

## Stack Patterns by Variant

**For development / local testing:**
- Use SQLite JDBC driver (`jdbc:sqlite:./jbalance.db`)
- No MySQL server needed
- HikariCP works with SQLite (use `maximumPoolSize=1` for SQLite — it does not support concurrent writes)

**For production (Pterodactyl / MySQL):**
- Use MySQL Connector/J driver (`jdbc:mysql://...`)
- HikariCP pool size: 5-10 connections is sufficient for a Minecraft server
- MariaDB is a drop-in replacement; MySQL Connector/J works with both

**For shop/auction GUIs:**
- Server-side: extend `AbstractContainerMenu`, register `MenuType` via `DeferredRegister`
- Client-side: extend `AbstractContainerScreen`, register via `RegisterMenuScreensEvent`
- Send open packet with `NetworkHooks.openScreen` (server → client trigger)

**For Votifier integration (if not using mod dependency):**
- Spin up a dedicated daemon thread on server start (`ServerStartedEvent`)
- Open `ServerSocket` on configurable port (default 8192)
- Accept connections, decrypt RSA payload, parse vote, fire internal event on NeoForge event bus
- Shut down cleanly on `ServerStoppingEvent`

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| NeoForge 21.1.220 | Minecraft 1.21.1, Java 21 | Do not mix NeoForge 21.1.x with Minecraft 1.21.x other than 1.21.1 |
| HikariCP 7.0.2 | Java 11+ (Java 21 confirmed working) | Minor concern: high CPU with virtual threads on JDK 21 + HikariCP 6.3.0 reported; 7.0.x resolves this |
| MySQL Connector/J 9.6.0 | MySQL 5.7+, MariaDB 10.x+ | JDBC 4.3 driver; compatible with Java 21 |
| xerial sqlite-jdbc 3.51.3.0 | SQLite 3.x, Java 8+ | Native binaries for Windows/Linux/macOS bundled; no separate SQLite install |
| ModDevGradle 2.0.x | NeoForge 1.20.4+, Minecraft 1.17-1.20.1 (legacy mode) | Stable since Jan 1 2025; use latest 2.0.x patch |
| Parchment 2024.11.17 | Minecraft 1.21.1 | Dev-only mapping; zero runtime impact |
| FTB Chunks (NeoForge) | 2101.1.14 for MC 1.21.1 | Optional dependency; API surface for charging per claim needs further investigation |

---

## Sources

- `https://docs.neoforged.net/docs/1.21.1/gettingstarted/` — Java 21 requirement, MDK setup confirmed (HIGH)
- `https://raw.githubusercontent.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle/main/gradle.properties` — NeoForge 21.1.220, Parchment 2024.11.17 confirmed (HIGH)
- `https://raw.githubusercontent.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle/main/build.gradle` — NeoGradle 7.1.21, Java 21 toolchain confirmed (HIGH)
- `https://neoforged.net/news/moddevgradle2/` — ModDevGradle 2.0.x stable Jan 2025, recommended for new projects (HIGH)
- `https://docs.neoforged.net/docs/1.21.1/misc/config/` — SERVER/COMMON/CLIENT/STARTUP config types, NightConfig, ModConfigSpec (HIGH)
- `https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/` — jarJar dependency bundling pattern with version ranges (HIGH)
- `https://central.sonatype.com/artifact/com.zaxxer/HikariCP` — HikariCP 7.0.2 latest stable (HIGH)
- `https://mvnrepository.com/artifact/com.mysql/mysql-connector-j` — MySQL Connector/J 9.6.0 latest, new groupId (HIGH)
- `https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc` — xerial sqlite-jdbc 3.51.3.0 latest (HIGH)
- `https://docs.neoforged.net/docs/networking/` — CustomPacketPayload, RegisterPayloadHandlersEvent (HIGH)
- `https://docs.neoforged.net/docs/1.21.1/gui/menus/` — AbstractContainerMenu, MenuType registration (HIGH)
- `https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/` — PlayerTickEvent.Post, LivingDeathEvent JavaDoc (MEDIUM)
- `https://modrinth.com/mod/votifier-for-neoforge` — Votifier for Neoforge exists for 1.21.1 (MEDIUM — page content not accessible)
- `https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge/files/7608681` — FTB Chunks 2101.1.14 for 1.21.1 confirmed (MEDIUM)
- FTB Chunks server-side API for charge-per-claim — not found in public docs (LOW — needs phase-specific research)

---

*Stack research for: NeoForge 1.21.1 economy mod (JBalance)*
*Researched: 2026-03-19*
