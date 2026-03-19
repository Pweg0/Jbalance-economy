# Architecture Research

**Domain:** NeoForge 1.21.1 economy mod (JBalance)
**Researched:** 2026-03-19
**Confidence:** HIGH (NeoForge docs) / MEDIUM (economy-specific patterns from community)

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                        ENTRY POINTS                                   │
│                                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  Commands   │  │ Event Listen │  │ Votifier │  │  API Surface │  │
│  │ /eco, /leil │  │ Mob/Tick/Log │  │ TCP Svr  │  │ (Cobblemon)  │  │
│  └──────┬──────┘  └──────┬───────┘  └────┬─────┘  └──────┬───────┘  │
│         │                │               │               │           │
├─────────┴────────────────┴───────────────┴───────────────┴───────────┤
│                        SERVICE LAYER                                  │
│                                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────┐  │
│  │  Economy    │  │  Payload     │  │   Shop /      │  │  Terrain │  │
│  │  Service   │  │  Service     │  │   Auction Svc │  │  Service │  │
│  └──────┬──────┘  └──────┬───────┘  └───────┬───────┘  └────┬─────┘  │
│         │                │                   │               │        │
├─────────┴────────────────┴───────────────────┴───────────────┴────────┤
│                        DATA LAYER                                     │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │   DatabaseManager (JDBC abstraction — MySQL / SQLite)         │     │
│  │   Repositories: BalanceRepo | TransactionRepo | ShopRepo      │     │
│  │                 AuctionRepo | TerrainRepo | PlaytimeRepo      │     │
│  └──────────────────────────────────────────────────────────────┘     │
│                                                                       │
│  ┌─────────────────────────┐  ┌──────────────────────────────────┐    │
│  │  NeoForge SavedData     │  │ NeoForge Config (TOML)           │    │
│  │  (audit / transient)    │  │ SERVER type — world-synced       │    │
│  └─────────────────────────┘  └──────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| Main Mod Class | Bootstrap, register event buses | `@Mod` annotated class; constructor wires mod-bus listeners |
| Event Handlers | React to game events (login, death, mob kill, tick) | `@EventBusSubscriber` static classes per domain |
| Command Tree | Expose /eco and /ecoadmin subcommands | `RegisterCommandsEvent` → Brigadier `LiteralArgumentBuilder` |
| VotifierServer | Listen for incoming vote packets (TCP) | Standalone `ServerThread` started on `ServerStartedEvent` |
| EconomyService | Balance CRUD, transfer logic, concurrency guard | Stateless service class; delegates to BalanceRepository |
| PayloadService | Playtime milestones, mob kill rewards | Stateful per-player tracker; flushed to DB on threshold |
| ShopService | Player shop lifecycle, purchase flow | Manages shop state; validates buyer balance before commit |
| AuctionService | Auction lifecycle, bidding, expiry | Scheduled task checks expiry each server tick (or timer) |
| TerrainService | Admin land sales, protection, rent | Stores terrain bounds + owner; scheduled rent collection |
| FTBChunks Hook | Intercept chunk claim events, charge player | Listens to FTBChunks claim event; calls EconomyService |
| DatabaseManager | Connection pool, schema migration, dual-DB routing | HikariCP pool; detects configured DB type at startup |
| Repositories | SQL query execution, result mapping | One class per aggregate (balance, shop, auction, terrain) |
| API Capability | Expose IEconomyAPI for Cobblemon Arena | `EntityCapability` registered on `RegisterCapabilitiesEvent` |
| Config | All tunable values (prices, rewards, DB connection) | `ModConfigSpec` SERVER type; loaded before server startup |
| Networking | Sync balance/shop data to clients for UI display | `CustomPacketPayload` registered via `RegisterPayloadHandlersEvent` |

## Recommended Project Structure

```
src/main/java/com/yourname/jbalance/
├── JBalance.java                   # @Mod entry point, bus registration
├── api/
│   ├── IEconomyAPI.java            # Public interface for other mods
│   └── EconomyCapability.java      # Capability definition and registration
├── command/
│   ├── EcoCommand.java             # /eco root + subcommands (pay, balance, top)
│   ├── EcoAdminCommand.java        # /ecoadmin root
│   ├── LeilaoCommand.java          # /leilao auction command
│   └── CommandRegistrar.java       # Registers all commands on RegisterCommandsEvent
├── config/
│   └── JBalanceConfig.java         # ModConfigSpec SERVER; all tunable values
├── data/
│   ├── db/
│   │   ├── DatabaseManager.java    # HikariCP pool, schema migration, MySQL/SQLite switch
│   │   ├── BalanceRepository.java
│   │   ├── PlaytimeRepository.java
│   │   ├── ShopRepository.java
│   │   ├── AuctionRepository.java
│   │   └── TerrainRepository.java
│   └── schema/
│       ├── schema_mysql.sql
│       └── schema_sqlite.sql
├── event/
│   ├── PlayerEventHandler.java     # Login, logout, death, join → session tracking
│   ├── MobKillEventHandler.java    # LivingDeathEvent → kill reward dispatch
│   ├── TickEventHandler.java       # ServerTickEvent → playtime milestone checks
│   ├── FTBChunksEventHandler.java  # FTB chunk claim → charge player
│   └── CommandEventHandler.java    # RegisterCommandsEvent listener
├── network/
│   ├── BalanceSyncPayload.java     # S→C: send updated balance to player
│   └── NetworkRegistrar.java      # RegisterPayloadHandlersEvent wiring
├── service/
│   ├── EconomyService.java         # Balance get/set/transfer; thread-safe
│   ├── PayloadService.java         # Playtime milestone + kill reward logic
│   ├── ShopService.java            # Player shop business logic
│   ├── AuctionService.java         # Auction state machine, expiry
│   └── TerrainService.java         # Land sale, rent collection
└── votifier/
    ├── VotifierServer.java         # TCP server thread for Votifier protocol
    └── VoteHandler.java            # Processes incoming vote, calls EconomyService
```

```
src/main/resources/
├── META-INF/
│   └── neoforge.mods.toml
├── assets/jbalance/
│   └── lang/pt_br.json             # All in-game messages in Portuguese (BR)
└── data/jbalance/
    └── (data-driven configs if needed)
```

### Structure Rationale

- **api/:** Isolated from internals so Cobblemon Arena can soft-depend without importing service classes
- **command/:** Brigadier trees are verbose; keeping them separate from business logic prevents bloat
- **data/db/:** Repository pattern means SQL never leaks into services — easy to swap implementations
- **event/:** One handler class per event domain; avoids giant catch-all event class
- **network/:** Payload records are isolated; networking knowledge stays contained here
- **service/:** Stateless services own all business rules; called by both commands and event handlers
- **votifier/:** Runs a separate TCP thread; isolation prevents errors from affecting main game loop

## Architectural Patterns

### Pattern 1: Service + Repository Separation

**What:** Services hold business rules and call repositories for persistence. Commands and event handlers call services, never repositories directly.

**When to use:** Any time a business rule spans more than one SQL query, or validation must happen before write.

**Trade-offs:** Adds a layer of indirection, but prevents business logic from scattering across commands.

**Example:**
```java
// EconomyService.java
public class EconomyService {
    private final BalanceRepository balanceRepo;

    public boolean transfer(UUID from, UUID to, long amount) {
        long fromBalance = balanceRepo.getBalance(from);
        if (fromBalance < amount) return false; // rule: can't overdraft
        balanceRepo.adjustBalance(from, -amount);
        balanceRepo.adjustBalance(to,  +amount);
        return true;
    }
}

// EcoCommand.java — calls service, not repo
public static int executePay(CommandContext<CommandSourceStack> ctx) {
    boolean ok = economyService.transfer(senderId, receiverId, amount);
    ctx.getSource().sendSuccess(() -> ok ? MSG_SUCCESS : MSG_INSUFFICIENT, false);
    return ok ? Command.SINGLE_SUCCESS : 0;
}
```

### Pattern 2: @EventBusSubscriber per Domain

**What:** Each game domain (player lifecycle, mob kills, ticks) gets its own `@EventBusSubscriber` class with static methods. No single catch-all event class.

**When to use:** Always — prevents any one file growing beyond ~200 lines.

**Trade-offs:** More files, but each is focused and independently testable.

**Example:**
```java
@EventBusSubscriber(modid = "jbalance", bus = EventBusSubscriber.Bus.GAME)
public class MobKillEventHandler {
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        String mobKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        payloadService.handleMobKill(player.getUUID(), mobKey);
    }
}
```

### Pattern 3: EntityCapability for Inter-Mod API

**What:** Define `IEconomyAPI` interface and expose it as a `EntityCapability<IEconomyAPI, Void>` on player entities. Other mods query the capability to call economy functions without a hard compile dependency.

**When to use:** For the Cobblemon Arena integration — the arena mod can soft-depend on jbalance and query the capability at runtime.

**Trade-offs:** Slightly more setup than a static API class, but works even if jbalance is absent (capability query returns null and arena handles gracefully).

**Example:**
```java
// api/IEconomyAPI.java
public interface IEconomyAPI {
    long getBalance(UUID playerId);
    boolean give(UUID playerId, long amount);
    boolean take(UUID playerId, long amount);
}

// api/EconomyCapability.java
public class EconomyCapability {
    public static final EntityCapability<IEconomyAPI, Void> ECONOMY =
        EntityCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("jbalance", "economy_api"),
            IEconomyAPI.class);

    // Register in RegisterCapabilitiesEvent on mod bus
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerEntity(ECONOMY, EntityType.PLAYER,
            (player, ctx) -> economyServiceInstance);
    }
}

// Cobblemon Arena mod — queries without hard dependency
IEconomyAPI api = player.getCapability(EconomyCapability.ECONOMY);
if (api != null) api.give(player.getUUID(), winnings);
```

### Pattern 4: Dual-Database Abstraction at Startup

**What:** `DatabaseManager` reads config to select MySQL or SQLite. All repositories receive a `DataSource` from the manager and never know which DB they talk to.

**When to use:** Required — MySQL in prod (Pterodactyl), SQLite in local dev.

**Trade-offs:** Must keep SQL ANSI-compatible or maintain two query sets for dialect differences (e.g., `AUTO_INCREMENT` vs `AUTOINCREMENT`).

**Example:**
```java
public class DatabaseManager {
    public DataSource initialize(JBalanceConfig config) {
        HikariConfig hc = new HikariConfig();
        if (config.useMySQL()) {
            hc.setJdbcUrl("jdbc:mysql://" + config.dbHost() + "/" + config.dbName());
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            hc.setJdbcUrl("jdbc:sqlite:" + gameDir + "/jbalance.db");
            hc.setDriverClassName("org.sqlite.JDBC");
        }
        return new HikariDataSource(hc);
    }
}
```

## Data Flow

### Player Balance Transfer (/eco pay)

```
Player types /eco pay <target> <amount>
    ↓
EcoCommand.executePay()
    ↓ validates arguments (target online, amount > 0)
EconomyService.transfer(from, to, amount)
    ↓ checks balance, applies transaction atomically
BalanceRepository.adjustBalance() × 2  (within one DB transaction)
    ↓ on success
PacketDistributor.sendToPlayer(new BalanceSyncPayload(newBalance))
    ↓ client receives payload
HUD / chat feedback updated
```

### Mob Kill Reward

```
Entity dies (LivingDeathEvent)
    ↓
MobKillEventHandler — checks killer is ServerPlayer
    ↓
PayloadService.handleMobKill(playerId, mobKey)
    ↓ looks up reward amount from config
EconomyService.give(playerId, reward)
    ↓
BalanceRepository.adjustBalance()
    ↓
Player notified via chat message (server-side sendSystemMessage)
```

### Playtime Milestone

```
ServerTickEvent.Post (every tick)
    ↓
TickEventHandler — tracks online players with in-memory counter
    ↓ every 20 ticks = 1 second; milestone thresholds checked
PayloadService.checkMilestone(playerId, totalSeconds)
    ↓ queries PlaytimeRepository for last rewarded milestone
    ↓ if new milestone reached:
EconomyService.give(playerId, milestoneReward)
    ↓
PlaytimeRepository.recordMilestone(playerId, milestone)
```

### Votifier Vote Received

```
External vote site sends TCP packet to VotifierServer port
    ↓
VotifierServer (background thread) decodes packet
    ↓ dispatches to main thread via server.execute()
VoteHandler.onVote(playerName, serviceName)
    ↓ resolves UUID from name (PlayerList lookup)
EconomyService.give(playerId, config.voteReward)
    ↓
Player notified via chat (if online) or on next login
```

### FTB Chunks Claim

```
Player claims chunk via FTB Chunks GUI / command
    ↓
FTBChunksAPI fires ChunkClaimedEvent (on Game Bus)
    ↓
FTBChunksEventHandler.onChunkClaimed(event)
    ↓ calculates cost from config (base cost per chunk)
EconomyService.take(playerId, cost)
    ↓ if insufficient balance:
    event.setCanceled(true) — claim denied, player notified
```

### Cobblemon Arena API Call

```
Arena mod's match concludes
    ↓
Arena queries player.getCapability(EconomyCapability.ECONOMY)
    ↓ IEconomyAPI.give(winnerId, prize)
    ↓ IEconomyAPI.take(loserId, wager)
EconomyService executes transaction
    ↓
BalanceRepository persists change
```

## Scaling Considerations

This is a single-server mod, not a distributed system. Scaling considerations are about operational correctness, not traffic volume.

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1-20 players | SQLite is fine; in-memory playtime tracking; synchronous DB calls acceptable |
| 20-100 players | MySQL required (concurrent writes); HikariCP connection pool (min 5 connections); async DB writes for non-critical paths (playtime updates) |
| 100+ players | Async DB writes mandatory; batch milestone writes (buffer N seconds); consider read replica for /eco top queries |

### Scaling Priorities

1. **First bottleneck:** Synchronous DB calls on the main game thread. Prevention: wrap BalanceRepository writes in `CompletableFuture.runAsync()` for non-critical paths (kill rewards, playtime). Balance transfers must remain synchronous to prevent race conditions.
2. **Second bottleneck:** /eco top leaderboard query on every request. Prevention: cache the top-10 list and rebuild it on a 60-second timer via `ServerTickEvent`.

## Anti-Patterns

### Anti-Pattern 1: Putting SQL in Command Classes

**What people do:** Query the database directly inside the Brigadier command executor lambda.

**Why it's wrong:** Commands become untestable, business rules scatter, and swapping the DB layer requires editing every command.

**Do this instead:** Commands call a service method. Services call repositories. SQL only lives in repositories.

### Anti-Pattern 2: Single Giant Event Handler

**What people do:** One `EventHandler.java` class with `@SubscribeEvent` methods for every event in the mod.

**Why it's wrong:** File grows to 1000+ lines, unrelated concerns couple together, hard to find what fires what.

**Do this instead:** One `@EventBusSubscriber` class per domain (`PlayerEventHandler`, `MobKillEventHandler`, etc.). Each stays under ~150 lines.

### Anti-Pattern 3: Blocking Main Thread for DB I/O

**What people do:** Call `connection.prepareStatement(...)` synchronously during `ServerTickEvent` or `LivingDeathEvent`.

**Why it's wrong:** Any DB hiccup (slow query, lock wait) freezes the entire server tick. Players experience rubber-banding and timeout disconnects.

**Do this instead:** Wrap non-critical writes in `CompletableFuture.runAsync(dbPool)`. For critical reads (balance check before transfer), synchronous is acceptable only if queries are fast (indexed primary-key lookups).

### Anti-Pattern 4: Static Service Singletons Initialized at Class Load

**What people do:** `public static final EconomyService INSTANCE = new EconomyService()` at class level.

**Why it's wrong:** Database connection is not yet available at class load time. Crashes with `NullPointerException` on server start.

**Do this instead:** Initialize services inside `FMLCommonSetupEvent` or `ServerStartingEvent` after config is loaded and DB manager is connected. Store in a holder that the mod main class exposes after setup.

### Anti-Pattern 5: Hard Dependency on FTB Chunks / Cobblemon

**What people do:** Import FTB Chunks classes directly in core service code.

**Why it's wrong:** If FTB Chunks is absent, the mod crashes at classload even if FTB integration is never needed.

**Do this instead:** Place FTB integration code in dedicated handler class loaded conditionally. Check mod presence with `ModList.get().isLoaded("ftbchunks")` before registering that handler. Same for Cobblemon capability exposure.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| MySQL (Pterodactyl) | JDBC via HikariCP connection pool | Bundle `mysql-connector-j` jar; shade into mod jar |
| SQLite (local/dev) | JDBC via SQLite-JDBC driver | Bundle `sqlite-jdbc` jar; shade into mod jar |
| Votifier/NuVotifier | Raw TCP socket server (Votifier v1 protocol) | Runs on separate port (default 8192); background thread |
| Vote sites (external) | Incoming TCP to VotifierServer | No outbound calls needed |

### Internal Mod Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Commands → Services | Direct method call | Commands are thin; all logic in service |
| Services → Repositories | Direct method call | Services own transaction boundaries |
| Event Handlers → Services | Direct method call | Handlers are thin dispatchers |
| VotifierServer → Services | `server.execute(runnable)` for main thread dispatch | Vote handler runs on network thread; must marshal to game thread before calling service |
| API → Services | Direct method call (same JVM) | Capability wraps service instance; no serialization |
| FTBChunks Hook → Services | Direct method call, conditional class load | Guard with `ModList.get().isLoaded("ftbchunks")` |
| Networking → Client | `PacketDistributor.sendToPlayer()` | Balance sync packet after any balance-changing operation |
| Config → All Components | Read-only config spec accessed at startup | Services read config values once at init; don't re-read each call |

### Suggested Build Order (Phase Dependencies)

The following order respects component dependencies — each phase can stand alone before the next begins:

```
Phase 1: Foundation
  DatabaseManager + schema migration (MySQL + SQLite)
  JBalanceConfig (TOML)
  BalanceRepository
  EconomyService (balance CRUD only)
  EcoCommand (/eco balance, /eco pay, /eco top)
  BalanceSyncPayload (client notification)

Phase 2: Earnings / Payload System
  PlaytimeRepository
  PayloadService (milestones + mob kills)
  TickEventHandler (playtime tracking)
  MobKillEventHandler
  PlayerEventHandler (login/logout session)
  (depends on: Phase 1 EconomyService)

Phase 3: Vote Rewards
  VotifierServer (TCP listener)
  VoteHandler
  (depends on: Phase 1 EconomyService)

Phase 4: Player Shops
  ShopRepository
  ShopService
  ShopCommand (details TBD)
  (depends on: Phase 1 EconomyService)

Phase 5: Auctions
  AuctionRepository
  AuctionService
  LeilaoCommand (/leilao)
  (depends on: Phase 1 EconomyService)

Phase 6: FTB Chunks Integration
  FTBChunksEventHandler
  TerrainService (land sales)
  TerrainRepository
  EcoAdminCommand (/ecoadmin terrain)
  (depends on: Phase 1 EconomyService; soft-dep on FTB Chunks)

Phase 7: Cobblemon Arena API
  IEconomyAPI interface (finalised)
  EconomyCapability (EntityCapability registration)
  (depends on: Phase 1 EconomyService; soft-dep on Cobblemon Arena)
```

## Sources

- [NeoForge Events — Official Documentation](https://docs.neoforged.net/docs/concepts/events/) — HIGH confidence
- [NeoForge Configuration — Official Documentation](https://docs.neoforged.net/docs/1.21.1/misc/config/) — HIGH confidence
- [NeoForge Networking Payloads — Official Documentation](https://docs.neoforged.net/docs/networking/payload/) — HIGH confidence
- [NeoForge Data Attachments — Official Documentation](https://docs.neoforged.net/docs/datastorage/attachments/) — HIGH confidence
- [NeoForge SavedData — Official Documentation](https://docs.neoforged.net/docs/datastorage/saveddata/) — HIGH confidence
- [NeoForge Capabilities — Official Documentation](https://docs.neoforged.net/docs/1.21.5/inventories/capabilities/) — HIGH confidence (1.21.5 docs; API stable since 1.21.x capability rework)
- [NeoForge Mod Structuring — Official Documentation](https://docs.neoforged.net/docs/gettingstarted/structuring/) — HIGH confidence
- [NeoForge Networking Rework Announcement](https://neoforged.net/news/20.4networking-rework/) — HIGH confidence
- [Grand Economy mod — GitHub](https://github.com/The-Fireplace-Minecraft-Mods/Grand-Economy) — MEDIUM confidence (reference architecture pattern)
- [Votifier for NeoForge — Modrinth](https://modrinth.com/mod/votifier-for-neoforge) — MEDIUM confidence (confirms Votifier protocol port exists for NeoForge 1.21.1)
- [FTB Chunks NeoForge 1.21.1 — CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge) — MEDIUM confidence (API existence confirmed; specific event names need verification against source)

---
*Architecture research for: JBalance — NeoForge 1.21.1 economy mod*
*Researched: 2026-03-19*
