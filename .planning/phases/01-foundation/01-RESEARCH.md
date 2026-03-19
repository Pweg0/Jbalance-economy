# Phase 1: Foundation - Research

**Researched:** 2026-03-19
**Domain:** NeoForge 1.21.1 mod scaffold + HikariCP dual-database layer + ModConfigSpec TOML hot-reload
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Currency name:** JCoins
- **Symbol:** J$ (appears before value)
- **Format:** J$ 1.500 — thousand separator is a dot (Brazilian standard), no decimal places
- **Value color:** gold (§6) in chat
- **Currency is integer (long)** — no decimals or cents
- **Starting balance for new players:** 100 JCoins
- **Minimum transfer (/eco pay):** configurable in TOML (suggested default: 1)
- **Maximum balance:** no cap
- **All values configurable in TOML**
- **Messages language:** PT-BR, clean and direct, no emoji, no informality
- **Message prefix:** §6[JBalance] on all mod messages
- **Color scheme:** golden prefix (§6) + gray text (§7) + values in gold (§6)
- **Example:** `§6[JBalance] §7Você recebeu §6J$ 500 §7de Steve`
- **Group ID:** com.pweg0.jbalance
- **Mod ID:** jbalance
- **Version:** 1.21.1-1.0.0 (MC version prefix + semver)
- **License:** All Rights Reserved
- **Language:** Java 21
- **Platform:** NeoForge 1.21.1
- **Database:** MySQL primary (Pterodactyl), SQLite fallback

### Claude's Discretion

- Exact sub-package structure (config, db, command, service, event, etc.)
- Database schema (table/column names)
- HikariCP connection pool implementation details
- TOML hot-reload mechanism
- Error handling and technical error messages

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| INFR-01 | Mod loads on NeoForge 1.21.1 dedicated server without errors | ModDevGradle 2.0.x scaffold + correct sided separation pattern documented |
| INFR-02 | All monetary transactions are atomic (no double-spend via race conditions) | SQL atomic UPDATE pattern + per-player in-flight lock pattern documented |
| INFR-03 | Database operations run async (no server tick blocking) | CompletableFuture + dedicated ExecutorService pattern + server.execute() re-entry documented |
| INFR-04 | TOML config with hot-reload support for value changes | ModConfigEvent.Reloading via NeoForge file watcher documented; correct SERVER type timing documented |
| INFR-05 | Currency name and symbol configurable in TOML | ModConfigSpec.Builder.define() for String values; ConfigValue<String> pattern documented |
| CURR-08 | Player balances persist across server restarts (MySQL primary, SQLite fallback) | HikariCP dual-DB routing pattern + schema migration approach documented |
| CURR-09 | New players start with configurable initial balance | PlayerLoggedInEvent + DB row upsert-if-absent pattern documented |
| CURR-10 | All currency values configurable via TOML config | ModConfigSpec SERVER type + defineInRange/define patterns documented |
</phase_requirements>

---

## Summary

Phase 1 is a greenfield NeoForge 1.21.1 mod scaffold built with ModDevGradle 2.0.x. The three deliverables — project scaffold, DatabaseManager, and TOML config — form a dependency chain: the scaffold sets up the build system that enables jarJar bundling; DatabaseManager consumes those bundled JARs; the config system initializes before the database and feeds connection parameters into it.

The stack is well-researched and fully verified through Maven Central and official NeoForge docs (all versions confirmed as of 2025-08). The hardest technical challenges in this phase are: (1) correctly routing HikariCP with SQLite vs MySQL without code duplication, (2) implementing async DB operations without ever blocking the server thread, and (3) triggering the initial player balance creation at the right event point before the player can interact with the economy. Config hot-reload comes "for free" from NeoForge's file watcher as long as the mod subscribes to `ModConfigEvent.Reloading` on the mod bus.

**Primary recommendation:** Build in strict plan order — scaffold first (jarJar must work before any DB code compiles), DatabaseManager second (schema migration must run before config values can be tested end-to-end), TOML config third (wire config values into DB initialization parameters and expose currency formatting to all future phases).

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| NeoForge | 21.1.220 | Mod loader API | Platform requirement — all hooks, events, config live here |
| ModDevGradle | 2.0.x (latest 2.0.107+) | Gradle build plugin | Official recommendation for new single-version mods since Jan 2025; simpler than NeoGradle |
| Java | 21 (LTS) | Language | Minecraft 1.21.1 ships Java 21; mandatory per NeoForge docs |
| Parchment | 2024.11.17 for MC 1.21.1 | Dev-time parameter names | Eliminates `p_1234_` in IDE; zero runtime cost |

### Database Libraries (all bundled via jarJar)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| HikariCP | 7.0.2 | JDBC connection pool | Industry standard; Java 21 confirmed; 7.0.x fixes CPU issue with virtual threads reported on 6.3.0 |
| MySQL Connector/J | 9.6.0 | MySQL/MariaDB JDBC driver | Official Oracle driver; groupId `com.mysql` (old `mysql:mysql-connector-java` abandoned at 8.0.33) |
| xerial sqlite-jdbc | 3.51.3.0 | SQLite JDBC + native binaries | Bundles SQLite native libs for Windows/Linux/macOS; no separate install; dev/fallback mode |

### Config System

| Technology | Version | Purpose | Why Standard |
|------------|---------|---------|--------------|
| NeoForge ModConfigSpec | Built into 21.1.x | TOML config definition | Native NeoForge API; produces standard .toml files; SERVER type is correct for per-world economy values |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ModDevGradle 2.0.x | NeoGradle 7.x | Only if supporting multiple MC versions in one build — unnecessary here |
| HikariCP + raw JDBC | JDBI fluent API | Reasonable if SQL complexity grows; adds another jarJar dependency |
| HikariCP + raw JDBC | Hibernate/JPA | Never — classpath conflicts with Minecraft's bundled libraries; startup penalty |
| `com.mysql:mysql-connector-j` | Legacy `mysql:mysql-connector-java` | Never — old groupId abandoned; no security updates after 8.0.33 |

**Installation (build.gradle):**
```gradle
plugins {
    id 'net.neoforged.moddev' version '2.0.107'  // use latest 2.0.x
}

neoForge {
    version = "21.1.220"
    runs {
        server { server() }
        client { client() }
    }
    mods {
        jbalance {
            sourceSet sourceSets.main
        }
    }
}

dependencies {
    // Database: bundle all three via jarJar
    jarJar(implementation("com.zaxxer:HikariCP")) {
        version {
            strictly "[7.0,8.0)"
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

    // Required for dev runs (Minecraft 1.21.1 and older need this)
    additionalRuntimeClasspath "com.zaxxer:HikariCP:7.0.2"
    additionalRuntimeClasspath "com.mysql:mysql-connector-j:9.6.0"
    additionalRuntimeClasspath "org.xerial:sqlite-jdbc:3.51.3.0"
}
```

**gradle.properties:**
```properties
neo_version=21.1.220
minecraft_version=1.21.1
parchment_mappings_version=2024.11.17
parchment_minecraft_version=1.21.1
```

**Version verification (confirmed 2026-03-19 against Maven Central):**
- HikariCP 7.0.2 — latest stable (released Aug 19, 2025)
- mysql-connector-j 9.6.0 — latest stable on `com.mysql` groupId
- sqlite-jdbc 3.51.3.0 — latest stable

---

## Architecture Patterns

### Recommended Project Structure (Phase 1 scope)

```
src/main/java/com/pweg0/jbalance/
├── JBalance.java                      # @Mod entry point, mod-bus wiring
├── config/
│   └── JBalanceConfig.java            # ModConfigSpec SERVER type, all TOML values
├── data/
│   ├── db/
│   │   ├── DatabaseManager.java       # HikariCP pool, MySQL/SQLite routing, schema migration
│   │   └── BalanceRepository.java     # SQL for player balance CRUD
│   └── schema/
│       ├── schema_mysql.sql           # CREATE TABLE statements for MySQL dialect
│       └── schema_sqlite.sql          # CREATE TABLE statements for SQLite dialect
├── event/
│   └── PlayerEventHandler.java        # PlayerLoggedInEvent for initial balance creation
└── service/
    └── EconomyService.java            # Balance get/set with async executor; Phase 2 extends this

src/main/resources/
└── META-INF/
    └── neoforge.mods.toml             # modId=jbalance, loaderVersion="[1,)", license="All Rights Reserved"
```

### Pattern 1: ModDevGradle 2.0.x Mod Entry Point

**What:** The `@Mod` annotated class is the bootstrap point. It receives a `ModContainer` in its constructor (NeoForge injects it), allowing config registration and mod-bus listener wiring.

**When to use:** Always — this is the single entry point for all initialization.

```java
// Source: NeoForge docs — https://docs.neoforged.net/docs/gettingstarted/
@Mod("jbalance")
public class JBalance {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static DatabaseManager dbManager;
    private static EconomyService economyService;

    public JBalance(ModContainer container) {
        // 1. Register SERVER config (available before ServerAboutToStartEvent)
        container.registerConfig(ModConfig.Type.SERVER, JBalanceConfig.SPEC);

        // 2. Subscribe to mod-bus events (config reload, setup, capabilities)
        IEventBus modBus = container.eventBus();
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onConfigReloading);

        // 3. Subscribe to game-bus events (player join)
        NeoForge.EVENT_BUS.addListener(PlayerEventHandler::onPlayerLoggedIn);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // DB and service initialization via enqueueWork for thread safety
        event.enqueueWork(() -> {
            dbManager = new DatabaseManager(JBalanceConfig.SPEC);
            economyService = new EconomyService(dbManager);
        });
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        // File watcher triggers this when the TOML changes on disk
        // No action needed for config values (they auto-cache); log for visibility
        LOGGER.info("[JBalance] Config reloaded — new values will apply immediately");
    }
}
```

### Pattern 2: SERVER Config with Hot-Reload

**What:** `ModConfigSpec.Builder` defines all TOML values. NeoForge's file watcher fires `ModConfigEvent.Reloading` on the mod bus automatically when the .toml file changes on disk. `ConfigValue.get()` always returns the freshly cached value after reload — no manual re-read required.

**When to use:** All economy-configurable values. Use `defineInRange` for numerics to prevent infinite correction loop bug.

**Critical timing note:** SERVER configs are available before `ServerAboutToStartEvent`. Reading a `ConfigValue` before the config is loaded throws `IllegalStateException`. Never read config values in static initializers.

```java
// Source: NeoForge docs — https://docs.neoforged.net/docs/1.21.1/misc/config/
public class JBalanceConfig {
    public static final ModConfigSpec SPEC;
    public static final ConfigValue<String> CURRENCY_NAME;
    public static final ConfigValue<String> CURRENCY_SYMBOL;
    public static final ConfigValue<Long> STARTING_BALANCE;
    public static final ConfigValue<Long> MIN_TRANSFER;
    // Database connection (SERVER config, not synced to clients in 1.21.1)
    public static final ConfigValue<Boolean> USE_MYSQL;
    public static final ConfigValue<String> DB_HOST;
    public static final ConfigValue<String> DB_NAME;
    public static final ConfigValue<String> DB_USER;
    public static final ConfigValue<String> DB_PASSWORD;
    public static final ConfigValue<Integer> DB_PORT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("JBalance Currency Settings").push("currency");
        CURRENCY_NAME   = builder.comment("Name of the currency").define("name", "JCoins");
        CURRENCY_SYMBOL = builder.comment("Symbol shown before the value").define("symbol", "J$");
        STARTING_BALANCE = builder.comment("Balance granted to new players")
                                  .defineInRange("starting_balance", 100L, 0L, Long.MAX_VALUE);
        MIN_TRANSFER    = builder.comment("Minimum amount for /eco pay")
                                  .defineInRange("min_transfer", 1L, 1L, Long.MAX_VALUE);
        builder.pop();

        builder.comment("JBalance Database Settings").push("database");
        USE_MYSQL    = builder.comment("true = MySQL, false = SQLite").define("use_mysql", false);
        DB_HOST      = builder.define("host", "localhost");
        DB_PORT      = builder.defineInRange("port", 3306, 1, 65535);
        DB_NAME      = builder.define("database", "jbalance");
        DB_USER      = builder.define("user", "jbalance");
        DB_PASSWORD  = builder.define("password", "changeme");
        builder.pop();

        SPEC = builder.build();
    }
}
```

**Key rule:** Always use `defineInRange` for numeric values. Using `define` with a numeric type and then reading it as another type triggers the config infinite correction loop (NeoForge GitHub Issue #1768).

### Pattern 3: Dual-Database HikariCP Routing

**What:** `DatabaseManager` reads the `USE_MYSQL` config value at startup to configure a single `HikariDataSource`. All repositories receive this `DataSource` — they never know which DB they talk to.

**When to use:** Required — MySQL in production (Pterodactyl), SQLite in local dev.

**Critical SQLite note:** SQLite does not support concurrent writers. Set `maximumPoolSize=1` when using SQLite. MySQL should use 5-10.

```java
// Source: HikariCP docs + STACK.md patterns
public class DatabaseManager {
    private final HikariDataSource dataSource;
    private final boolean isMysql;

    public DatabaseManager(ModConfigSpec configSpec) {
        HikariConfig hc = new HikariConfig();
        this.isMysql = JBalanceConfig.USE_MYSQL.get();

        if (isMysql) {
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                JBalanceConfig.DB_HOST.get(),
                JBalanceConfig.DB_PORT.get(),
                JBalanceConfig.DB_NAME.get());
            hc.setJdbcUrl(url);
            hc.setUsername(JBalanceConfig.DB_USER.get());
            hc.setPassword(JBalanceConfig.DB_PASSWORD.get());
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setMaximumPoolSize(10);
            hc.setMinimumIdle(2);
        } else {
            Path dbPath = FMLPaths.GAMEDIR.get().resolve("jbalance.db");
            hc.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setMaximumPoolSize(1);  // SQLite: single writer mandatory
        }

        hc.setConnectionTimeout(5000);
        hc.setPoolName("JBalance-DB");
        this.dataSource = new HikariDataSource(hc);
        runMigrations();
    }

    private void runMigrations() {
        String schema = isMysql
            ? readResource("/schema/schema_mysql.sql")
            : readResource("/schema/schema_sqlite.sql");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(schema);
        } catch (SQLException e) {
            throw new RuntimeException("[JBalance] Schema migration failed", e);
        }
    }

    public DataSource getDataSource() { return dataSource; }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
```

### Pattern 4: Async DB Operations with Game-Thread Re-Entry

**What:** All DB writes that are not critical to the current player action (mob kill rewards, playtime updates) run on a dedicated `ExecutorService`. The result is marshalled back to the game thread via `server.execute()`. For critical balance mutations (transfers, initial balance creation), the async operation completes before the player receives feedback.

**When to use:** Any JDBC call — synchronous DB on the server thread causes TPS drops with MySQL network latency.

```java
// Source: NeoForge patterns + PITFALLS.md research
public class EconomyService {
    private static final ExecutorService DB_EXECUTOR =
        Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "JBalance-DB");
            t.setDaemon(true);
            return t;
        });

    private final BalanceRepository repo;
    // Per-player in-flight lock prevents concurrent balance mutations
    private final ConcurrentHashMap<UUID, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    public CompletableFuture<Long> getBalance(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> repo.getBalance(playerId), DB_EXECUTOR);
    }

    // Atomic SQL-level update — no read-modify-write at Java level
    public CompletableFuture<Boolean> give(UUID playerId, long amount) {
        return CompletableFuture.supplyAsync(() -> repo.adjustBalance(playerId, amount), DB_EXECUTOR);
    }

    // Transfer uses SQL transaction; double-spend prevention at DB level
    public CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount) {
        AtomicBoolean lock = inFlight.computeIfAbsent(from, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false); // already in flight
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repo.transfer(from, to, amount);
            } finally {
                lock.set(false);
            }
        }, DB_EXECUTOR);
    }
}
```

### Pattern 5: Atomic Balance SQL (No Race Condition)

**What:** Balance mutations happen in SQL, not in Java. The SQL enforces the business invariant atomically with no read-modify-write cycle in Java.

**When to use:** Every balance write operation.

```sql
-- MySQL schema_mysql.sql
CREATE TABLE IF NOT EXISTS jbalance_players (
    uuid        CHAR(36)     NOT NULL PRIMARY KEY,
    display_name VARCHAR(16) NOT NULL DEFAULT '',
    balance     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Atomic deduct (fails if insufficient; no negative balance possible)
UPDATE jbalance_players
   SET balance = balance - ?
 WHERE uuid = ?
   AND balance >= ?;
-- Check rows affected = 1 to confirm success

-- Atomic credit (always succeeds if player exists)
UPDATE jbalance_players
   SET balance = balance + ?
 WHERE uuid = ?;
```

```sql
-- SQLite schema_sqlite.sql (AUTOINCREMENT vs AUTO_INCREMENT dialect difference)
CREATE TABLE IF NOT EXISTS jbalance_players (
    uuid         TEXT    NOT NULL PRIMARY KEY,
    display_name TEXT    NOT NULL DEFAULT '',
    balance      INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);
```

### Pattern 6: Initial Balance on First Join

**What:** Listen to `PlayerLoggedInEvent` on the game bus (fired server-side when a player joins). Check if a DB row exists; if not, INSERT with the configured starting balance.

**When to use:** Required for CURR-09.

**Event choice:** `PlayerLoggedInEvent` (equivalent to `PlayerEvent.PlayerLoggedInEvent`) fires on the server thread when the player's entity joins the level. This is the correct event — it fires once per login, including first-ever join.

```java
// Source: NeoForge event docs; PlayerLoggedInEvent confirmed in CraftTweaker docs for 1.21.1
@EventBusSubscriber(modid = "jbalance", bus = EventBusSubscriber.Bus.GAME)
public class PlayerEventHandler {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        // Async: initialize balance if new player, update display name
        EconomyService.getInstance().initPlayerIfAbsent(uuid, name)
            .whenComplete((isNew, ex) -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Failed to initialize player {}", uuid, ex);
                    return;
                }
                if (isNew) {
                    long startBal = JBalanceConfig.STARTING_BALANCE.get();
                    // Re-enter game thread for chat message
                    player.getServer().execute(() ->
                        player.sendSystemMessage(Component.literal(
                            "§6[JBalance] §7Bem-vindo! Você recebeu §6" +
                            formatBalance(startBal) + " §7de saldo inicial.")));
                }
            });
    }
}
```

### Pattern 7: Currency Formatting (Brazilian Standard)

**What:** Format long values with dot as thousands separator (Brazilian PT-BR standard). Symbol from config, prefix before value.

**When to use:** Any time a balance amount is displayed in chat.

```java
// Produces: "J$ 1.500" for 1500
public static String formatBalance(long amount) {
    String symbol = JBalanceConfig.CURRENCY_SYMBOL.get();
    // NumberFormat with Portuguese locale uses dot as thousand separator
    NumberFormat fmt = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));
    return symbol + " " + fmt.format(amount);
}
```

### Pattern 8: neoforge.mods.toml Minimum Required Fields

```toml
modLoader="javafml"
loaderVersion="[1,)"
license="All Rights Reserved"

[[mods]]
modId="jbalance"
version="1.21.1-1.0.0"
displayName="JBalance"
description="Economy mod for ALL THE MONS server."

[[dependencies.jbalance]]
    modId="neoforge"
    type="required"
    versionRange="[21.1.220,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.jbalance]]
    modId="minecraft"
    type="required"
    versionRange="[1.21.1,1.22)"
    ordering="NONE"
    side="BOTH"
```

### Anti-Patterns to Avoid

- **Reading `ConfigValue.get()` in a static initializer:** Throws `IllegalStateException` — config is not loaded yet at class load time. Always read values at runtime inside event handlers or service methods.
- **Calling `future.get()` on the server thread:** Blocks the tick. Always use `.thenAccept()` / `.whenComplete()` with `server.execute()` for game-thread re-entry.
- **`maximumPoolSize > 1` with SQLite:** SQLite does not support concurrent writers. Pool size must be 1 for SQLite.
- **Putting SQL in event handlers or command executors:** SQL belongs only in Repository classes. Event handlers and commands call services; services call repositories.
- **Using `@EventBusSubscriber` without specifying `bus`:** Player events are on `Bus.GAME`, not `Bus.MOD`. Wrong bus means the event never fires.
- **Client-only imports in common code:** Any reference to `net.minecraft.client.*` without `@EventBusSubscriber(value = Dist.CLIENT)` crashes a dedicated server.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JDBC connection pooling | Custom connection factory | HikariCP | Handles connection validation, timeout, eviction; thread-safe; battle-tested |
| Config file parsing | Custom TOML reader | NeoForge ModConfigSpec | Provides type safety, validation, hot-reload, server sync for free |
| SQLite native binaries | Compile SQLite from source | xerial sqlite-jdbc | Bundles native libs for all platforms in one JAR |
| DB schema management | Manual `CREATE TABLE` on each startup | Schema SQL files loaded at startup (migration pattern) | Idempotent `CREATE TABLE IF NOT EXISTS`; safe to run on every start |
| Async thread dispatch | Raw `Thread.start()` | `Executors.newFixedThreadPool()` with named threads | Managed lifecycle, daemon threads that don't prevent JVM shutdown |

**Key insight:** The NeoForge config system gives hot-reload and file watching for zero code cost. The only requirement is subscribing to `ModConfigEvent.Reloading` on the mod bus and reading `ConfigValue.get()` at usage time (not caching to a plain field at startup).

---

## Common Pitfalls

### Pitfall 1: Config Infinite Correction Loop

**What goes wrong:** If a `ModConfigSpec` entry uses `define()` with a mismatched type (e.g., defining a value as `String` but NeoForge expects an `Integer`), the config system enters a correction loop — it writes the "corrected" value, the file watcher detects the change, it corrects again, forever. This fills disk and prevents manual editing.

**Why it happens:** Open-ended `define()` calls on numeric values; type mismatch between Java type and TOML type.

**How to avoid:** Use `defineInRange(key, defaultLong, minLong, maxLong)` for all numeric values. Test with invalid values in dev. After any schema change, delete the existing .toml and let it regenerate.

**Warning signs:** Log file growing at 1MB/minute; server admin reports the config file reverts instantly.

**Reference:** NeoForge GitHub Issue #1768.

### Pitfall 2: Balance Race Condition (Double-Spend)

**What goes wrong:** Two concurrent operations both pass the "sufficient funds" check in Java, then both deduct — leaving a negative balance.

**Why it happens:** Read-modify-write at Java level (read balance, check, write) is not atomic. Async DB callbacks can re-enter from off-thread.

**How to avoid:**
- All balance deductions use `UPDATE ... WHERE balance >= ?` at SQL level — atomicity guaranteed by the database engine.
- Per-player in-flight `AtomicBoolean` lock in `EconomyService` rejects overlapping mutations for the same player.
- Never maintain a Java-side balance cache as the write target.

### Pitfall 3: Server-Side-Only Crashed by Client Code

**What goes wrong:** Any reference to `net.minecraft.client.*` on a dedicated server causes `ClassNotFoundException` at startup.

**Why it happens:** Dev testing in single-player (combined client+server) hides sided bugs. They appear only on dedicated server.

**How to avoid:**
- Run `./gradlew runServer` before every commit.
- All client code goes in classes annotated `@EventBusSubscriber(value = Dist.CLIENT, ...)` and is never referenced from common code paths.
- Phase 1 has no GUI or HUD — the sided risk here is minimal but the `runServer` habit must be established from the start.

### Pitfall 4: Reading Config Before It Is Loaded

**What goes wrong:** `JBalanceConfig.STARTING_BALANCE.get()` called in a static initializer or at class-load time throws `IllegalStateException: Cannot get config value before config is loaded`.

**Why it happens:** SERVER configs are loaded before `ServerAboutToStartEvent`, but after class initialization.

**How to avoid:** Never call `ConfigValue.get()` in static initializers or `@Mod` constructor body. Call it inside event handlers and service methods that execute after the server starts.

### Pitfall 5: SQLite Concurrent Writes (Data Corruption)

**What goes wrong:** Multiple HikariCP pool connections attempt concurrent writes to SQLite, corrupting the database file.

**Why it happens:** SQLite uses file-level locking; only one writer at a time is supported.

**How to avoid:** When `USE_MYSQL = false`, set `hikariConfig.setMaximumPoolSize(1)` unconditionally. Document this in the config comment.

### Pitfall 6: jarJar Dependencies Not Available at Runtime in Dev

**What goes wrong:** `jarJar` bundles deps into the final JAR for production, but in dev runs (`runServer`), the classpath does not include them unless `additionalRuntimeClasspath` is also configured.

**Why it happens:** `jarJar` is a production-only bundling mechanism for Minecraft 1.21.1 and older.

**How to avoid:** Add `additionalRuntimeClasspath "com.zaxxer:HikariCP:7.0.2"` etc. for all three DB libraries. This is mandatory for `runServer` to find the classes.

---

## Code Examples

### Full Balance Repository (SQL pattern)

```java
// Source: ARCHITECTURE.md patterns + atomic SQL research
public class BalanceRepository {
    private final DataSource dataSource;

    public BalanceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns balance, or -1 if player not found. */
    public long getBalance(UUID uuid) {
        String sql = "SELECT balance FROM jbalance_players WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("balance") : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBalance failed", e);
        }
    }

    /** Inserts player with starting balance if they do not exist. Returns true if newly created. */
    public boolean initPlayerIfAbsent(UUID uuid, String displayName, long startingBalance) {
        // MySQL: INSERT IGNORE; SQLite: INSERT OR IGNORE — both work
        String sql = "INSERT OR IGNORE INTO jbalance_players (uuid, display_name, balance) VALUES (?, ?, ?)";
        // For MySQL, use: "INSERT IGNORE INTO jbalance_players ..."
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, displayName);
            ps.setLong(3, startingBalance);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("initPlayerIfAbsent failed", e);
        }
    }

    /** Atomic balance credit. Returns true on success. */
    public boolean adjustBalance(UUID uuid, long delta) {
        String sql = delta >= 0
            ? "UPDATE jbalance_players SET balance = balance + ? WHERE uuid = ?"
            : "UPDATE jbalance_players SET balance = balance + ? WHERE uuid = ? AND balance >= ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, delta);
            ps.setString(2, uuid.toString());
            if (delta < 0) ps.setLong(3, -delta); // ensure balance >= |delta|
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("adjustBalance failed", e);
        }
    }

    /** Atomic transfer using a single DB transaction. Returns true if transfer succeeded. */
    public boolean transfer(UUID from, UUID to, long amount) {
        String deduct = "UPDATE jbalance_players SET balance = balance - ? WHERE uuid = ? AND balance >= ?";
        String credit = "UPDATE jbalance_players SET balance = balance + ? WHERE uuid = ?";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement d = c.prepareStatement(deduct);
                 PreparedStatement cr = c.prepareStatement(credit)) {
                d.setLong(1, amount);
                d.setString(2, from.toString());
                d.setLong(3, amount);
                if (d.executeUpdate() != 1) { c.rollback(); return false; }
                cr.setLong(1, amount);
                cr.setString(2, to.toString());
                cr.executeUpdate();
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("transfer failed", e);
        }
    }
}
```

**Note on `INSERT IGNORE` vs `INSERT OR IGNORE`:** MySQL uses `INSERT IGNORE INTO`; SQLite uses `INSERT OR IGNORE INTO`. Since the schema files are separate, use the dialect-correct form in each file. `DatabaseManager` must track which dialect is active and either (a) use two separate `initPlayerIfAbsent` SQL strings, or (b) use a portable alternative like `INSERT INTO ... ON CONFLICT DO NOTHING` (MySQL 8.0.19+ and SQLite 3.24+).

### ModConfigEvent.Reloading Subscription

```java
// Source: NeoForge config docs — mod bus listener pattern
// In JBalance constructor or via modBus.addListener():
private void onConfigReloading(ModConfigEvent.Reloading event) {
    if (event.getConfig().getSpec() == JBalanceConfig.SPEC) {
        JBalance.LOGGER.info("[JBalance] Config reloaded — new reward values active immediately");
        // No manual cache invalidation needed; ConfigValue.get() re-fetches automatically
    }
}
```

### ServerStoppingEvent Cleanup

```java
// Source: NeoForge event lifecycle
@SubscribeEvent
public static void onServerStopping(ServerStoppingEvent event) {
    if (JBalance.getDatabaseManager() != null) {
        JBalance.getDatabaseManager().shutdown(); // closes HikariCP pool cleanly
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `mysql:mysql-connector-java` (old groupId) | `com.mysql:mysql-connector-j` | 8.x transition (2022) | Old groupId abandoned at 8.0.33; use new groupId exclusively |
| NeoGradle 7.x | ModDevGradle 2.0.x | Jan 2025 | Simpler, faster, recommended for new single-version mods |
| `SimpleChannel` Forge networking | `CustomPacketPayload` + `RegisterPayloadHandlersEvent` | NeoForge 1.20.4+ | `SimpleChannel` removed; custom packets are records implementing `CustomPacketPayload` |
| Shadow/ShadowJar plugin for bundling | `jarJar` configuration | NeoForge MDK | jarJar handles deduplication across mods correctly; ShadowJar does not |
| HikariCP 6.x | HikariCP 7.0.2 | Aug 2025 | Resolves high CPU usage reported with virtual threads on Java 21 in 6.3.0 |

**Deprecated/outdated:**
- `ForgeConfigRegistry` / old config API: Use `ModConfigSpec.Builder` with `ModContainer#registerConfig`
- `@Config` annotation (old Forge pattern): Not present in NeoForge; use `ModConfigSpec.Builder`
- `TickEvent.ServerTickEvent` for DB writes: Removed/changed; use `ServerTickEvent.Post` if tick-based tracking is needed (Phase 3, not Phase 1)

---

## Open Questions

1. **INSERT OR IGNORE portability**
   - What we know: MySQL uses `INSERT IGNORE`; SQLite uses `INSERT OR IGNORE`
   - What's unclear: Whether the two separate schema SQL files (already planned) cleanly handle this or whether `initPlayerIfAbsent` needs two separate code paths
   - Recommendation: Keep two schema files; also keep two `initPlayerIfAbsent` SQL constants in `BalanceRepository` gated by `DatabaseManager.isMysql()` flag.

2. **ModConfigEvent.Reloading vs `ConfigValue.get()` caching**
   - What we know: NeoForge's file watcher triggers `ModConfigEvent.Reloading`; `ConfigValue.get()` fetches the fresh value post-reload
   - What's unclear: Whether `ConfigValue.get()` is safe to call from async DB threads (off-server-thread) — it reads from an internal `NightConfig` cache
   - Recommendation: Read all config values on the game thread (inside event handlers), then pass them as method arguments into async operations. Do not call `ConfigValue.get()` from `DB_EXECUTOR` threads.

3. **NeoForge 21.1.220 vs latest 21.1.x patch**
   - What we know: 21.1.220 is specified as the target version in STACK.md and MDK
   - What's unclear: Whether a more recent 21.1.x patch has released since the research was done
   - Recommendation: Verify the NeoForge version at scaffold time by checking `https://maven.neoforged.net/releases/net/neoforged/neoforge/` — use 21.1.220 as the minimum; upgrade to latest 21.1.x patch if available.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None detected — greenfield project |
| Config file | None (Wave 0 gap) |
| Quick run command | `./gradlew runServer` (integration smoke test — confirms load without crash) |
| Full suite command | `./gradlew test` (once JUnit is configured) |

**Note:** NeoForge mods are difficult to unit test in isolation because of classloading requirements. The practical validation for Phase 1 is:
1. `./gradlew runServer` completes without `ClassNotFoundException` or `ExceptionInInitializerError`
2. The mod appears in the server log as loaded
3. Manual functional tests against the running server

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFR-01 | Mod loads without errors on dedicated server | smoke | `./gradlew runServer` (check exit code + log) | ❌ Wave 0 — runServer task exists after scaffold |
| INFR-02 | Balance transfers are atomic (no negative balances) | manual/integration | Manual: two concurrent `/eco pay` commands | ❌ Wave 0 — requires running server |
| INFR-03 | DB operations are async (TPS not degraded) | manual | Manual: check TPS via F3 or `/forge tps` during DB load | ❌ Wave 0 |
| INFR-04 | TOML config hot-reload works | manual | Manual: edit TOML, verify log line and new value applies | ❌ Wave 0 |
| INFR-05 | Currency name/symbol reflects TOML config | manual | Manual: change symbol in TOML, trigger balance display | ❌ Wave 0 |
| CURR-08 | Balance persists across restart | manual | Manual: set balance, stop server, start server, check balance | ❌ Wave 0 |
| CURR-09 | New player receives starting balance | manual | Manual: join with fresh player UUID, check balance | ❌ Wave 0 |
| CURR-10 | All currency values configurable | manual | Manual: change starting_balance in TOML, new player joins | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew build` — confirms compilation succeeds
- **Per wave merge:** `./gradlew runServer` — confirms server loads without crash
- **Phase gate:** All manual functional tests pass before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] No test infrastructure exists — project is greenfield
- [ ] JUnit 5 with NeoForge's test framework (if using `net.neoforged:testframework`) — evaluate during scaffold
- [ ] `./gradlew runServer` is the primary smoke test; this task is created by ModDevGradle automatically once scaffold is complete
- [ ] Functional test checklist document needed for manual verification of all 8 requirements

---

## Sources

### Primary (HIGH confidence)

- [NeoForge Configuration Docs](https://docs.neoforged.net/docs/1.21.1/misc/config/) — ModConfigSpec.Builder API, SERVER type, ModConfigEvent.Loading/Reloading, config timing
- [ModDevGradle Docs](https://docs.neoforged.net/toolchain/docs/plugins/mdg/) — jarJar configuration, additionalRuntimeClasspath requirement for Minecraft 1.21.1 and older
- [NeoForge Mod Files Docs](https://docs.neoforged.net/docs/gettingstarted/modfiles/) — neoforge.mods.toml structure, required fields, loaderVersion format
- [Maven Central — HikariCP 7.0.2](https://mvnrepository.com/artifact/com.zaxxer/HikariCP/7.0.2) — version confirmed, released Aug 19, 2025
- [Maven Central — mysql-connector-j 9.6.0](https://mvnrepository.com/artifact/com.mysql/mysql-connector-j) — version confirmed, `com.mysql` groupId
- [Maven Central — sqlite-jdbc 3.51.3.0](https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc) — version confirmed
- `.planning/research/STACK.md` — comprehensive technology stack research (conducted 2026-03-19)
- `.planning/research/ARCHITECTURE.md` — component architecture and data flow patterns (conducted 2026-03-19)
- `.planning/research/PITFALLS.md` — critical pitfalls and prevention strategies (conducted 2026-03-19)

### Secondary (MEDIUM confidence)

- [PlayerLoggedInEvent — CraftTweaker Docs 1.21.1](https://docs.blamejared.com/1.21.1/en/neoforge/api/event/entity/player/PlayerLoggedInEvent/) — confirms event exists in NeoForge 1.21.1 on game bus
- [NeoForge Events Docs](https://docs.neoforged.net/docs/1.21.1/concepts/events/) — event bus subscriptions, @EventBusSubscriber usage

### Tertiary (LOW confidence — marked for validation)

- NeoForge GitHub Issue #1768 — config infinite correction loop: claimed to be reproducible with mismatched type predicates; version-specific fix status unclear

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all library versions verified against Maven Central as of 2026-03-19
- Architecture: HIGH — patterns sourced from official NeoForge docs; dual-DB routing is standard pattern
- Config hot-reload: HIGH — ModConfigEvent.Reloading confirmed in official docs; file watcher is built-in
- PlayerLoggedInEvent timing: MEDIUM — confirmed event exists; exact first-join detection behavior verified via CraftTweaker docs
- Pitfalls: HIGH — config correction loop verified via NeoForge GitHub; race condition prevention via SQL atomic UPDATE is standard

**Research date:** 2026-03-19
**Valid until:** 2026-04-19 (30 days — NeoForge 1.21.1 is stable; library versions may update)
