# Phase 3: Earnings - Research

**Researched:** 2026-03-19
**Domain:** NeoForge 1.21.1 event handling — mob kill detection, playtime tracking, AFK detection, TOML list config
**Confidence:** HIGH (core event APIs verified via official docs; AFK pattern MEDIUM)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Mob kill rewards:**
- Somente mobs hostis dao recompensa — passivos (vacas, porcos) nao dao moedas
- Cobblemon (Pokemon) NAO dao recompensa — economia de Cobblemon e separada
- Mobs de spawner (mob spawner block) NAO dao recompensa — previne farm de moeda infinita
- Lista explicita no TOML: somente mobs listados dao moedas. Sem valor default.
- Mobs pre-configurados: Zombie=10, Skeleton=10, Creeper=15, Spider=10, Enderman=25, Witch=20, Blaze=30, Wither Skeleton=40

**Playtime milestones:**
- Milestones default: 1h, 2h, 5h, 10h, 24h com recompensas crescentes
- Configuravel no TOML como lista de pares horas->reward
- Cada milestone e recebido exatamente uma vez por jogador
- Tempo AFK NAO conta para playtime — detectar AFK apos X minutos sem movimento/acao
- Timeout de AFK configuravel no TOML (default: 5 minutos)
- Persistencia: salvar progresso periodicamente (a cada 5 minutos) + no logout
- Progresso sobrevive a desconexoes e restarts do servidor

**Notificacoes:**
- Mob kill: notificacao ACUMULADA a cada 60 segundos (nao por kill individual)
  - Formato: "Voce recebeu J$ 50 por matar 5 mobs"
  - Intervalo configuravel no TOML (kill_notification_interval)
- Milestone: mensagem DESTAQUE com cor diferente
  - Formato: "§6[JBalance] §aVoce completou 5h de jogo! Recompensa: §6J$ 500"
  - Cor verde (§a) para texto de milestone, dourado (§6) para valores
- Todas as mensagens em PT-BR seguindo o padrao §6[JBalance]

**Config TOML:**
- Secao [earnings.mob_kills]: lista explicita de mob_type = reward_value
- Secao [earnings.milestones]: lista de pares [[milestones]] hours=N, reward=X
- kill_notification_interval = 60 (segundos, configuravel)
- afk_timeout_minutes = 5 (minutos, configuravel)
- Todos os valores usam defineInRange para evitar o bug de correcao infinita do NeoForge

### Claude's Discretion
- Deteccao de AFK: implementacao exata (movement events, action events, ou tick counter)
- Deteccao de mob spawner: como identificar se o mob veio de spawner (MobSpawnType ou tag)
- Estrutura da tabela de milestones no banco de dados
- Timer de flush periodico: ScheduledExecutorService ou server tick event
- Acumulador de kills: estrutura de dados in-memory para batching de notificacoes

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| EARN-01 | Player earns configurable coins per mob killed | LivingDeathEvent + DamageSource.getEntity() instanceof ServerPlayer pattern; TOML defineList for mob reward map |
| EARN-02 | Different mob types can have different reward values (configured in TOML) | BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() as TOML key; defineList<String> with "mob:reward" encoding or per-key defineInRange per mob entry |
| EARN-03 | Player earns coins upon reaching playtime milestones (1h, 2h, 5h, etc — configurable) | PlayerTickEvent.Post server-side tick counter; defineList for milestone pairs in TOML |
| EARN-04 | Playtime milestones are per-player and persist across sessions | New DB table jbalance_playtime (uuid, total_seconds, claimed_milestones); flush on logout + periodic timer |
| EARN-05 | Player receives chat message when earning coins from any source | Established Component.literal + ChatFormatting pattern; accumulated kill notifier map per UUID; milestone immediate notification |
</phase_requirements>

---

## Summary

Phase 3 adds two passive earning sources: mob kills and playtime milestones. Both are well-supported by NeoForge 1.21.1's event system. The core patterns are direct extensions of what Phases 1 and 2 established — the same async DB executor, the same TOML defineInRange discipline, the same Component.literal message pattern. No new third-party dependencies are needed.

The mob kill path uses `LivingDeathEvent` (NeoForge game bus). The event exposes `getSource()` returning a `DamageSource`, from which `getEntity()` gives the killing entity. Checking `instanceof ServerPlayer` limits rewards to direct player kills. Mob type is obtained via `BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString()` (e.g. `"minecraft:zombie"`), which maps to the TOML key. Spawner-origin blocking requires a two-event approach: `FinalizeSpawnEvent` tags the entity at spawn time (marking it as spawner-spawned via `entity.getPersistentData()`), and the kill handler skips tagged entities.

The playtime path uses `PlayerTickEvent.Post` (server-side only; guard with `!player.level().isClientSide()`). A `ConcurrentHashMap<UUID, PlayerPlaytimeData>` tracks each online player's AFK-adjusted active seconds and which milestones they have claimed in-memory. Periodic flush uses `ServerTickEvent.Post` with a tick counter (20 ticks/s × 60s × 5 = 6000 ticks). DB persistence goes through a new `PlaytimeRepository` following the same HikariCP pattern already established.

**Primary recommendation:** Extend `PlayerEventHandler` into a dedicated `EarningsEventHandler` class; add `PlaytimeRepository` and `PlaytimeService` mirroring the existing EconomyService structure; add two new TOML sections with `defineList` for mob rewards and milestones.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| NeoForge Event Bus | 21.1.x (already in project) | LivingDeathEvent, PlayerTickEvent.Post, FinalizeSpawnEvent | The only event system for NeoForge mods; no alternative |
| HikariCP | 7.0.2 (already in project) | Connection pool for PlaytimeRepository | Already established; Phase 1 decision: 7.0.2 required for Java 21 |
| ModConfigSpec | NeoForge bundled | TOML config for mob rewards and milestones | Already used project-wide with defineInRange pattern |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ConcurrentHashMap (JDK) | Java 21 | In-memory per-player playtime + kill accumulator state | Thread-safe; DB writes are async, in-memory reads stay on game thread |
| CompletableFuture (JDK) | Java 21 | Async DB flush for playtime | Already the pattern in EconomyService |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ConcurrentHashMap for kill accumulator | Capabilities (NeoForge) | Capabilities add complexity and serialization overhead; a plain map keyed by UUID is simpler for transient in-memory batching |
| Server tick counter for periodic flush | ScheduledExecutorService | Tick counter is on the game thread — no scheduling overhead, no thread management. ScheduledExecutorService requires careful synchronization with game state. Tick counter wins for simplicity. |
| FinalizeSpawnEvent tag for spawner detection | Check at kill time via entity NBT | Tagging at spawn is cleaner: the spawner context is known at FinalizeSpawnEvent but not at death. Kill-time NBT check is fragile. |

**Installation:** No new dependencies required. All libraries already present.

---

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/pweg0/jbalance/
├── event/
│   ├── PlayerEventHandler.java       # existing — login only
│   └── EarningsEventHandler.java     # NEW — mob kill + playtime events
├── service/
│   ├── EconomyService.java           # existing — unchanged
│   └── PlaytimeService.java          # NEW — playtime state + milestone logic
├── data/db/
│   ├── BalanceRepository.java        # existing — unchanged
│   └── PlaytimeRepository.java       # NEW — jbalance_playtime CRUD
└── config/
    └── JBalanceConfig.java           # EXTEND — add earnings sections
src/main/resources/schema/
    ├── schema_mysql.sql              # EXTEND — add jbalance_playtime table
    └── schema_sqlite.sql             # EXTEND — add jbalance_playtime table
```

### Pattern 1: Mob Kill Detection via LivingDeathEvent
**What:** Subscribe to LivingDeathEvent; check killer is a ServerPlayer; get entity type key; look up reward in config map; call EconomyService.give().
**When to use:** Every mob death event; early-return if not player-caused or if mob is not in reward list or is spawner-tagged.

```java
// Source: NeoForge LivingDeathEvent — https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/neoforged/neoforge/event/entity/living/LivingDeathEvent.html
// Source: BuiltInRegistries pattern — docs.neoforged.net/docs/1.21.1/concepts/registries/
@SubscribeEvent  // registered on NeoForge.EVENT_BUS
public static void onLivingDeath(LivingDeathEvent event) {
    // 1. Killer must be a ServerPlayer
    if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
    // 2. Dead entity must not be a player
    if (event.getEntity() instanceof Player) return;
    // 3. Skip spawner-spawned mobs (tagged at FinalizeSpawnEvent)
    if (event.getEntity().getPersistentData().getBoolean("jbalance_from_spawner")) return;
    // 4. Resolve mob type key, e.g. "minecraft:zombie"
    String typeKey = BuiltInRegistries.ENTITY_TYPE
        .getKey(event.getEntity().getType()).toString();
    // 5. Lookup reward from config (returns 0 if not configured)
    long reward = JBalanceConfig.getMobReward(typeKey);
    if (reward <= 0) return;
    // 6. Accumulate for batched notification
    EarningsEventHandler.accumulateKill(player.getUUID(), reward);
}
```

### Pattern 2: Spawner-Origin Tagging via FinalizeSpawnEvent
**What:** At mob spawn time, if the spawn type is SPAWNER, write a boolean flag into the entity's persistent data NBT. The flag survives until death.
**When to use:** Only spawner-type spawns. All other spawns are skipped.

```java
// Source: FinalizeSpawnEvent — https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent.html
@SubscribeEvent
public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
    if (event.getSpawnType() == MobSpawnType.SPAWNER) {
        event.getEntity().getPersistentData().putBoolean("jbalance_from_spawner", true);
    }
}
```

**Note:** `MobSpawnEvent.FinalizeSpawn` is the NeoForge 1.21.x name (nested class). Confirmed via javadoc — `MobSpawnType.SPAWNER` is a valid enum constant for block spawner origin.

### Pattern 3: Playtime Tracking via PlayerTickEvent.Post
**What:** Per server tick, per online player — if not AFK, increment active-second counter. On milestone crossings, grant reward and notify. On periodic flush (every 6000 ticks = 5 minutes), write to DB async.
**When to use:** Only on server side — guard with `!player.level().isClientSide()` or cast to `ServerPlayer`.

```java
// Source: PlayerTickEvent.Post — https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/event/tick/PlayerTickEvent.Post.html
@SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    PlaytimeService.getInstance().onTick(player);
}
```

### Pattern 4: AFK Detection — Position Comparison per Tick
**What:** Store last known (x, y, z, yaw, pitch) per player in PlaytimeService. On each tick, compare current position/look. If changed within afk_timeout window → reset AFK timer; if unchanged for `afk_timeout_minutes * 20 * 60` ticks → mark AFK. AFK players do not accumulate playtime.
**When to use:** Recommended over input event listening because position changes capture all activity types (movement, rotation, attacking — all cause position/look changes on the server). This is the approach used by community AFK mods (AFKStatus, AFK Auto Sleep) for NeoForge.

```java
// PlaytimeService fields per player:
record PlayerState(double x, double y, double z, float yRot, float xRot,
                   long activeTicks, long ticksSinceLastMove, Set<Long> claimedMilestoneHours) {}
```

### Pattern 5: In-Memory Kill Accumulator for Batched Notifications
**What:** A `ConcurrentHashMap<UUID, AtomicLong>` accumulates coin totals and a `ConcurrentHashMap<UUID, AtomicInteger>` accumulates kill counts since last notification. A separate tick counter triggers the batch notification flush every `kill_notification_interval` seconds.

```java
// All reads/writes on game thread (event callbacks), so ConcurrentHashMap is safe
private static final ConcurrentHashMap<UUID, Long>    pendingCoins  = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<UUID, Integer> pendingKills  = new ConcurrentHashMap<>();

// In accumulateKill():
pendingCoins.merge(uuid, reward, Long::sum);
pendingKills.merge(uuid, 1, Integer::sum);

// In flush (every N ticks):
pendingCoins.forEach((uuid, coins) -> {
    int kills = pendingKills.getOrDefault(uuid, 0);
    // send message, call EconomyService.give(), clear entries
});
```

### Pattern 6: TOML Config — Mob Reward List
**What:** ModConfigSpec does not support a native `Map<String, Long>` type. The established approach for mob-keyed rewards is a `List<String>` where each entry is `"namespace:mob_id=reward"`. Parse at read time into a `HashMap<String, Long>`. Use `defineListAllowEmpty` (avoids empty-list bug per Phase 1 decisions).

```java
// In JBalanceConfig static block:
builder.comment("Mob kill reward settings").push("earnings").push("mob_kills");
MOB_KILL_REWARDS = builder
    .comment("Format: \"minecraft:zombie=10\". Only listed mobs give coins.")
    .defineListAllowEmpty("rewards",
        List.of("minecraft:zombie=10", "minecraft:skeleton=10",
                "minecraft:creeper=15", "minecraft:spider=10",
                "minecraft:enderman=25", "minecraft:witch=20",
                "minecraft:blaze=30", "minecraft:wither_skeleton=40"),
        e -> e instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+=\\d+"));
builder.pop();

// Milestone list: each entry "hours=N,reward=X"
MILESTONES = builder
    .comment("Format: \"hours=N,reward=X\". Reached exactly once per player.")
    .defineListAllowEmpty("milestones",
        List.of("hours=1,reward=100", "hours=2,reward=200",
                "hours=5,reward=500", "hours=10,reward=1000", "hours=24,reward=2500"),
        e -> e instanceof String s && s.matches("hours=\\d+,reward=\\d+"));
builder.pop().pop();
```

Parse helper (called on game thread):
```java
public static Map<String, Long> parsedMobRewards() {
    Map<String, Long> map = new HashMap<>();
    for (Object entry : MOB_KILL_REWARDS.get()) {
        String[] parts = entry.toString().split("=", 2);
        if (parts.length == 2) map.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
    }
    return map;
}
```

**CRITICAL:** Config values must be read on the game thread before going async — same rule as `STARTING_BALANCE` in `EconomyService.initPlayerIfAbsent`. Never call `.get()` from DB_EXECUTOR threads.

### Pattern 7: Database Schema — Playtime Table
**What:** Two new tables (MySQL + SQLite variants) to persist total active seconds and claimed milestone hours.

```sql
-- MySQL (schema_mysql.sql addition)
CREATE TABLE IF NOT EXISTS jbalance_playtime (
    uuid             CHAR(36)   NOT NULL PRIMARY KEY,
    active_seconds   BIGINT     NOT NULL DEFAULT 0,
    claimed_hours    TEXT       NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SQLite (schema_sqlite.sql addition)
CREATE TABLE IF NOT EXISTS jbalance_playtime (
    uuid             TEXT    NOT NULL PRIMARY KEY,
    active_seconds   INTEGER NOT NULL DEFAULT 0,
    claimed_hours    TEXT    NOT NULL DEFAULT ''
);
```

`claimed_hours` is a comma-delimited string of milestone hours already granted, e.g. `"1,2,5"`. This avoids a separate junction table for a small, bounded list.

### Anti-Patterns to Avoid
- **Reading config off game thread:** Never call `JBalanceConfig.MOB_KILL_REWARDS.get()` inside a `CompletableFuture.supplyAsync` lambda. Read on game thread, pass result into async call.
- **Notification per kill:** User decision locks in batched notifications. Sending one message per kill causes chat spam and is explicitly out of scope.
- **Using `defineList` with empty default:** Use `defineListAllowEmpty` — `defineList` with an empty default triggers the NeoForge config loop bug (Issue #1768).
- **Blocking game thread for DB:** All playtime flush calls must use `CompletableFuture.supplyAsync(..., DB_EXECUTOR)`.
- **Forgetting logout flush:** If the server stops before the 5-minute periodic flush, playtime since last flush is lost. Must flush on `PlayerLoggedOutEvent` and on `ServerStoppingEvent`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Async DB access | Custom thread management | Existing `DB_EXECUTOR` in `EconomyService` (or mirror pattern in `PlaytimeService`) | Already handles daemon threads, shutdown sequence |
| Config hot-reload | Custom file watcher | ModConfigSpec + `ModConfigEvent.Reloading` (already wired in JBalance.java) | NeoForge reloads config on `/reload`; watcher already handles it |
| Currency formatting | Custom number formatter | `CurrencyFormatter.formatBalance(long)` already exists | PT-BR locale, symbol from config, handles dot separator |
| Entity type string key | Custom registry lookup | `BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()` | Standard NeoForge pattern; handles modded entities too |
| Milestone "claimed once" dedup | Custom bloom filter / set | `claimed_hours` TEXT column in DB + in-memory `Set<Long>` loaded at login | Simple, durable, tested pattern for small bounded sets |

**Key insight:** This phase is purely additive — every infrastructure concern (async DB, config, formatting, event bus wiring) already has a working, tested pattern in the codebase. The new work is domain logic only.

---

## Common Pitfalls

### Pitfall 1: Config Values Read Off Game Thread
**What goes wrong:** `NullPointerException` or stale/wrong value when `.get()` is called on a `ModConfigSpec.ConfigValue` from a non-game thread (e.g., inside `CompletableFuture.supplyAsync`).
**Why it happens:** NeoForge's config system is not thread-safe for reads from executor threads.
**How to avoid:** Read all config values on the game thread (inside the event handler), then pass plain Java primitives or collections into the async lambda.
**Warning signs:** `ClassCastException` or silent config-loop log spam in `latest.log`.

### Pitfall 2: defineList with Empty Default Triggers Config Loop
**What goes wrong:** Server logs fill with "Correcting [key] to [value]" repeatedly; server may hang on shutdown.
**Why it happens:** NeoForge Issue #1768 — `defineList` with empty default triggers infinite correction.
**How to avoid:** Always use `defineListAllowEmpty` for lists that may legitimately be empty; provide non-empty defaults.
**Warning signs:** Repeated identical correction log lines in `latest.log` within seconds.

### Pitfall 3: Playtime Lost on Crash / Server Kill
**What goes wrong:** Players lose progress toward next milestone if the server process is killed (SIGKILL / crash).
**Why it happens:** Only flushing at logout and periodic tick means a gap of up to 5 minutes may be lost.
**How to avoid:** The 5-minute periodic flush (every 6000 ticks) limits loss to at most 5 minutes. For the planner: document this as acceptable behavior — graceful shutdown via `ServerStoppingEvent` flushes all sessions, only hard crash loses partial progress.
**Warning signs:** Players report milestone not rewarded after reconnect despite having enough time.

### Pitfall 4: Double Milestone Award on Race
**What goes wrong:** A player's milestone fires twice if the DB write is in-flight and another milestone check runs.
**Why it happens:** In-memory set of claimed milestones and DB-persisted set can be out of sync briefly.
**How to avoid:** Always check AND update the in-memory `claimedMilestoneHours` set atomically before issuing the DB write + coin credit. Use the in-memory set as the authoritative source during a session; only write to DB for durability.

### Pitfall 5: LivingDeathEvent Fires for Players Being Killed
**What goes wrong:** A player is killed by a zombie; `event.getEntity()` is the player, not the zombie. The handler accidentally tries to reward the killer (another player or mob).
**Why it happens:** Confusion about who is `getEntity()` (the one dying) vs `getSource().getEntity()` (the killer).
**How to avoid:** `event.getEntity()` = the mob being killed. `event.getSource().getEntity()` = the killer. Guard `event.getEntity() instanceof Player` → return to prevent rewarding player kills.

### Pitfall 6: PlayerTickEvent.Post Fires on Client Side Too
**What goes wrong:** Economy operations trigger on the client side, causing NullPointerException on `EconomyService.getInstance()`.
**Why it happens:** `PlayerTickEvent.Post` fires on both logical client and server. In a dedicated server environment the client side is never loaded, but integrated server (singleplayer) would trigger both sides.
**How to avoid:** Guard with `if (!(event.getEntity() instanceof ServerPlayer player)) return;` — `ServerPlayer` only exists on the logical server.

### Pitfall 7: Cobblemon Entities Rewarded Accidentally
**What goes wrong:** Cobblemon Pokemon deaths fire `LivingDeathEvent`. If the kill handler only checks "is entity in reward config list", Cobblemon mobs not in the list are correctly excluded. But if a default reward path is accidentally added, Cobblemon entities could earn coins.
**Why it happens:** Locked decision states no default reward — list is explicit. As long as there is no fallback reward, Cobblemon entities are safe. Guard explicitly: reward = 0 if key not in map → return.
**How to avoid:** Enforce "no default" in the reward lookup: `Map.getOrDefault(typeKey, 0L)` — return immediately if 0.

---

## Code Examples

### Resolve Entity Type Key (NeoForge 1.21.1)
```java
// Source: https://docs.neoforged.net/docs/1.21.1/concepts/registries/
import net.minecraft.core.registries.BuiltInRegistries;

String typeKey = BuiltInRegistries.ENTITY_TYPE
    .getKey(event.getEntity().getType())  // returns ResourceLocation
    .toString();  // e.g. "minecraft:zombie"
```

### Register Multiple Listeners in JBalance Constructor
```java
// Pattern from existing JBalance.java
NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onLivingDeath);
NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onFinalizeSpawn);
NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onPlayerTick);
NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onServerTick);  // for periodic flush
NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onPlayerLoggedOut);
```

### Async Playtime Flush (mirrors EconomyService pattern)
```java
// Read active seconds and claimed hours on game thread, pass into async
long activeSeconds = playtimeService.getActiveSeconds(uuid);  // game thread
String claimedHours = playtimeService.getClaimedHoursString(uuid); // game thread
CompletableFuture.runAsync(
    () -> playtimeRepo.upsertPlaytime(uuid, activeSeconds, claimedHours),
    DB_EXECUTOR
);
```

### Send Accumulated Kill Notification
```java
// On game thread (server tick flush)
server.execute(() -> {
    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
    if (p != null) {
        p.sendSystemMessage(Component.literal(
            "§6[JBalance] §7Voce recebeu §6" + CurrencyFormatter.formatBalance(totalCoins)
            + " §7por matar §6" + killCount + " §7mobs"
        ));
    }
});
```

### Send Milestone Notification
```java
// Milestone uses §a (green) for highlight — locked decision
p.sendSystemMessage(Component.literal(
    "§6[JBalance] §aVoce completou " + hours + "h de jogo! "
    + "Recompensa: §6" + CurrencyFormatter.formatBalance(reward)
));
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `getRegistryName()` on Entity/Block | `BuiltInRegistries.X.getKey(obj)` | NeoForge 1.19+ | `getRegistryName()` removed; must use registry lookup |
| `defineList` with empty default | `defineListAllowEmpty` | NeoForge 1.20+ (Issue #1768) | Empty-default lists must use `defineListAllowEmpty` |
| `@Mod.EventBusSubscriber` static class | `NeoForge.EVENT_BUS.addListener(...)` in constructor | NeoForge 1.20+ | Explicit registration in constructor is the established pattern in this project |
| `MobSpawnType.SPAWNER` (Forge) | `MobSpawnType.SPAWNER` still valid | N/A | Enum constant unchanged; `FinalizeSpawnEvent` is the correct event |

---

## Open Questions

1. **MobSpawnEvent.FinalizeSpawn exact import path in NeoForge 1.21.1**
   - What we know: Package is `net.neoforged.neoforge.event.entity.living.MobSpawnEvent.FinalizeSpawn` based on 1.20.6 javadoc; outer class is `MobSpawnEvent`, inner is `FinalizeSpawn`.
   - What's unclear: Whether the inner class name changed between 1.20.6 and 1.21.1.
   - Recommendation: Confirm at implementation time by checking the decompiled NeoForge jar (same approach used in Phase 1 for `getEventBus()` verification).

2. **`defineListAllowEmpty` default value parameter behavior in 1.21.1**
   - What we know: `defineListAllowEmpty` requires a supplier for the "new entry default" in some versions.
   - What's unclear: Whether the 1.21.1 API signature differs from 1.20.6.
   - Recommendation: Compile against the project's actual NeoForge jar; if method signature mismatch, check the ModConfigSpec javadoc in the local Gradle cache.

3. **Cobblemon entity namespace**
   - What we know: Cobblemon entities use their own namespace (not `minecraft:`).
   - What's unclear: Exact namespace string (likely `cobblemon:`).
   - Recommendation: Not needed for implementation — since the reward list is explicit and Cobblemon entities will not appear in it, they are automatically excluded. No defensive check needed beyond "not in map → 0 reward".

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | None detected — no test sources directory, no test config files in project |
| Config file | None — Wave 0 gap |
| Quick run command | `./gradlew test` (once test sources exist) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EARN-01 | Player kills mob → receives configured reward | unit (mock event) | `./gradlew test --tests "*EarningsTest.testMobKillReward"` | Wave 0 |
| EARN-02 | Different mob types return different rewards; unlisted mob returns 0 | unit | `./gradlew test --tests "*EarningsTest.testMobRewardLookup"` | Wave 0 |
| EARN-03 | Playtime milestone crossed → reward granted + notification | unit | `./gradlew test --tests "*PlaytimeTest.testMilestoneTriggered"` | Wave 0 |
| EARN-04 | Milestone claimed exactly once even after disconnect/reconnect | unit (repo) | `./gradlew test --tests "*PlaytimeTest.testMilestoneNotDuplicated"` | Wave 0 |
| EARN-05 | Chat message sent for kill accumulation and milestone | unit | `./gradlew test --tests "*EarningsTest.testKillNotification"` | Wave 0 |

**Note:** NeoForge mod testing without a running Minecraft server requires mocking event objects or using GameTestFramework (NeoForge's in-game test system). Given the project has no tests yet, starting with plain JUnit 5 unit tests for pure logic (config parsing, reward lookup, milestone dedup) is the practical Wave 0 path. Event integration is manual/smoke test.

### Sampling Rate
- **Per task commit:** Manual smoke test — start test server, kill a zombie, check chat message
- **Per wave merge:** `./gradlew build` — confirms compilation; manual integration smoke test
- **Phase gate:** All acceptance criteria in ROADMAP.md verified manually before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/pweg0/jbalance/` — test sources directory does not exist
- [ ] `build.gradle` test dependency — JUnit 5 not yet declared
- [ ] No test infrastructure exists at all in this project — treat as manual-verify phase

---

## Sources

### Primary (HIGH confidence)
- [NeoForge LivingDeathEvent javadoc (1.20.6)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/neoforged/neoforge/event/entity/living/LivingDeathEvent.html) — event API, getSource(), getEntity()
- [NeoForge FinalizeSpawnEvent javadoc (1.20.6)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.20.6-neoforge/net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent.html) — MobSpawnType.SPAWNER, getSpawnType()
- [NeoForge PlayerTickEvent.Post javadoc (1.21.x)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/event/tick/PlayerTickEvent.Post.html) — per-player server tick, server-side guard
- [NeoForge Configuration docs](https://docs.neoforged.net/docs/misc/config/) — defineList, defineListAllowEmpty, defineInRange
- [NeoForge Registries docs 1.21.1](https://docs.neoforged.net/docs/1.21.1/concepts/registries/) — BuiltInRegistries.ENTITY_TYPE.getKey() pattern
- Existing project source: `EconomyService.java`, `JBalanceConfig.java`, `PlayerEventHandler.java`, `DatabaseManager.java`, `BalanceRepository.java` — all patterns directly reused

### Secondary (MEDIUM confidence)
- [NeoForge Issue #1768](https://github.com/neoforged/NeoForge/issues/1768) — defineList empty default bug; resolved by using defineListAllowEmpty (established in Phase 1)
- [NeoForge living entity package summary (1.21.x)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/event/entity/living/package-summary.html) — confirms LivingDeathEvent present in 1.21.x

### Tertiary (LOW confidence — flagged for validation at impl time)
- MobSpawnEvent.FinalizeSpawn inner class path in 1.21.1 — verified as outer pattern from 1.20.6 docs; exact 1.21.1 import confirmed by decompiling at implementation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project, no new deps
- Architecture patterns: HIGH — LivingDeathEvent + DamageSource pattern confirmed via javadoc; existing code patterns directly extended
- Pitfalls: HIGH — config thread safety (Phase 1 established), defineList bug (Phase 1 established), event side-guard (confirmed via PlayerTickEvent docs)
- AFK detection approach: MEDIUM — position-comparison-per-tick is community-proven pattern; exact tick count arithmetic is straightforward but implementation needs care

**Research date:** 2026-03-19
**Valid until:** 2026-04-19 (NeoForge 1.21.1 is stable; APIs are frozen)
